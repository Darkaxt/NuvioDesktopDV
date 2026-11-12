package com.nuvio.app.features.playback.animejanai

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.writeText

sealed interface AnimeJaNaiInstallState {
    data object Idle : AnimeJaNaiInstallState
    data class Running(val step: String, val downloadedBytes: Long = 0, val totalBytes: Long = 0) : AnimeJaNaiInstallState
    data object Installed : AnimeJaNaiInstallState
    data class Failed(val reason: String) : AnimeJaNaiInstallState
}

/**
 * Owner-initiated installation of the pinned AnimeJaNai runtime (spec P4) into the app-owned
 * [AnimeJaNaiRuntime.rootDir]. Every download is pinned by SHA-256 and every installed file is
 * verified against a pinned per-file hash before it replaces anything:
 *
 * - AnimeJaNai 3.11.0 overlay (aji inference library + the Balanced HD and SD models). Its DLLs
 *   are BCJ2-compressed, so extraction uses the pinned standalone 7-Zip `7zr.exe` (LGPL).
 * - TensorRT runtime and the GPU-matching TensorRT builder resource from the pinned
 *   `animejanai-inference` runtime-2 pack index (per-file hashes come from that index).
 *
 * Prepared engines in `onnx/` are kept. Downloads live under `rootDir/.install` and are removed
 * afterwards. Nothing updates itself: a new version needs new pins in this file.
 */
internal class AnimeJaNaiRuntimeInstaller(private val runtime: AnimeJaNaiRuntime) {
    private val log = Logger.withTag("AnimeJaNaiInstall")
    private val json = Json { ignoreUnknownKeys = true }
    private val running = AtomicBoolean(false)
    private val http by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    private val _state = MutableStateFlow<AnimeJaNaiInstallState>(AnimeJaNaiInstallState.Idle)
    val state: StateFlow<AnimeJaNaiInstallState> = _state.asStateFlow()

    /** Starts installation on a background thread; returns a refusal reason, or null when started. */
    fun install(): String? {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
            return "AnimeJaNai is only supported on Windows"
        }
        if (AnimeJaNaiPreparationController.isPreparing) return "An engine preparation is running"
        // Installed DLLs are loaded (and locked) by any player hosting the filter.
        if (AnimeJaNaiPreparationController.hasActivePlayback) return "Close the player before installing"
        if (!running.compareAndSet(false, true)) return "Installation is already running"
        _state.value = AnimeJaNaiInstallState.Running("Starting")
        Thread({
            _state.value = try {
                runInstall()
                AnimeJaNaiInstallState.Installed
            } catch (error: Throwable) {
                log.w(error) { "AnimeJaNai runtime installation failed" }
                AnimeJaNaiInstallState.Failed(error.message ?: error.javaClass.simpleName)
            } finally {
                running.set(false)
            }
        }, "nuvio-animejanai-install").apply {
            isDaemon = true
            start()
        }
        return null
    }

    val isInstalling: Boolean get() = running.get()

    private fun runInstall() {
        val root = runtime.rootDir
        val work = root.resolve(".install")
        if (work.exists()) work.toFile().deleteRecursively()
        val extracted = work.resolve("extracted").createDirectories()
        try {
            val sm = detectComputeCapability()
            val sevenZip = download(SEVEN_ZIP, work, "7-Zip extractor")

            val indexFile = download(PACK_INDEX, work, "TensorRT pack index")
            val index = json.decodeFromString(PackIndex.serializer(), Files.readString(indexFile))
            val packs = listOf(
                index.packs.single { it.name == "trt-runtime" },
                index.packs.firstOrNull { it.name == "trt-$sm" } ?: index.packs.single { it.name == "trt-ptx" },
            )

            val overlay = download(OVERLAY, work, "AnimeJaNai models")
            extract(sevenZip, overlay, extracted.resolve("overlay"), OVERLAY_FILES.map { it.archivePath })
            val stagedOverlay = OVERLAY_FILES.map { file ->
                val path = extracted.resolve("overlay").resolve(file.archivePath.replace('\\', '/'))
                verify(path, file.sha256)
                path to file.destination
            }

            val stagedPacks = packs.flatMap { pack ->
                val archive = download(Pinned(pack.url, pack.sha256, pack.bytes, pack.asset), work, "TensorRT ${pack.name}")
                val out = extracted.resolve(pack.name)
                extract(sevenZip, archive, out, emptyList())
                archive.deleteIfExists()
                pack.files.map { file ->
                    val path = out.resolve(file.path)
                    verify(path, file.sha256)
                    path to "inference/${file.path}"
                }
            }

            _state.value = AnimeJaNaiInstallState.Running("Installing")
            (stagedOverlay + stagedPacks).forEach { (source, destination) ->
                val target = root.resolve(destination)
                target.parent.createDirectories()
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
            runtime.configFile.writeText(CONFIG_FILE)
            root.resolve(MANIFEST_NAME).writeText(
                json.encodeToString(
                    InstalledManifest.serializer(),
                    InstalledManifest(
                        animeJaNaiVersion = OVERLAY_VERSION,
                        overlaySha256 = OVERLAY.sha256,
                        runtimeTag = index.tag,
                        packIndexSha256 = PACK_INDEX.sha256,
                        packs = packs.map { "${it.name}@${it.version}" },
                        computeCapability = sm,
                    ),
                ),
            )
            val availability = runtime.availability()
            if (availability is AnimeJaNaiRuntime.Availability.Unavailable) error(availability.reason)
            log.i { "installed AnimeJaNai $OVERLAY_VERSION runtime=${index.tag} packs=${packs.map { it.name }}" }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    /** `sm89` style name from `nvidia-smi` (NVIDIA driver tool); TensorRT needs an NVIDIA GPU. */
    private fun detectComputeCapability(): String {
        _state.value = AnimeJaNaiInstallState.Running("Detecting GPU")
        val windir = System.getenv("WINDIR") ?: "C:\\Windows"
        val tool = Path.of(windir, "System32", "nvidia-smi.exe")
        if (!tool.isRegularFile()) error("No NVIDIA driver found; AnimeJaNai TensorRT needs an NVIDIA GPU")
        val process = ProcessBuilder(tool.toString(), "--query-gpu=compute_cap", "--format=csv,noheader")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            process.destroyForcibly()
            error("nvidia-smi failed: ${output.trim()}")
        }
        val capability = output.lineSequence().map(String::trim).firstOrNull { it.matches(Regex("""\d+\.\d+""")) }
            ?: error("Unrecognised GPU compute capability: ${output.trim()}")
        return "sm" + capability.replace(".", "")
    }

    private fun download(pinned: Pinned, dir: Path, label: String): Path {
        val target = dir.resolve(pinned.fileName)
        val request = HttpRequest.newBuilder(URI.create(pinned.url))
            .timeout(Duration.ofMinutes(30))
            .header("User-Agent", "NuvioDV")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("$label download failed: HTTP ${response.statusCode()}")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var downloaded = 0L
        response.body().use { input ->
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(1 shl 16)
                var lastReport = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastReport >= 1 shl 20 || downloaded == pinned.bytes) {
                        lastReport = downloaded
                        _state.value = AnimeJaNaiInstallState.Running("Downloading $label", downloaded, pinned.bytes)
                    }
                }
            }
        }
        val actual = digest.digest().toHex()
        if (actual != pinned.sha256) {
            target.deleteIfExists()
            throw IOException("$label failed verification (SHA-256 $actual)")
        }
        return target
    }

    private fun extract(sevenZip: Path, archive: Path, out: Path, entries: List<String>) {
        _state.value = AnimeJaNaiInstallState.Running("Extracting ${archive.name}")
        out.createDirectories()
        // -y: no prompts; -bso0/-bsp0: no console output; java starts it without a window.
        val command = listOf(sevenZip.toString(), "x", "-y", "-bso0", "-bsp0", "-o$out", archive.toString()) + entries
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            error("Extraction of ${archive.name} timed out")
        }
        if (process.exitValue() != 0) error("Extraction of ${archive.name} failed: ${output.trim().take(300)}")
    }

    private fun verify(path: Path, sha256: String) {
        if (!path.isRegularFile()) error("Extracted file is missing: ${path.name}")
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().toHex()
        if (actual != sha256) error("${path.name} failed verification (SHA-256 $actual)")
    }

    private data class Pinned(val url: String, val sha256: String, val bytes: Long, val fileName: String)

    private data class OverlayFile(val archivePath: String, val destination: String, val sha256: String)

    @Serializable
    private data class PackIndex(val tag: String, val packs: List<Pack>)

    @Serializable
    private data class Pack(
        val name: String,
        val version: String,
        val asset: String,
        val url: String,
        val sha256: String,
        val bytes: Long,
        val files: List<PackFile>,
    )

    @Serializable
    private data class PackFile(val path: String, val sha256: String)

    @Serializable
    private data class InstalledManifest(
        val animeJaNaiVersion: String,
        val overlaySha256: String,
        val runtimeTag: String,
        val packIndexSha256: String,
        val packs: List<String>,
        val computeCapability: String,
    )

    companion object {
        const val MANIFEST_NAME = "nuviodv-runtime.json"
        const val OVERLAY_VERSION = "3.11.0"

        private val SEVEN_ZIP = Pinned(
            url = "https://github.com/ip7z/7zip/releases/download/26.06/7zr.exe",
            sha256 = "e80db2dad715a6c5adabe292d31ed54e11f4e9b99ca349827b50573b175891bc",
            bytes = 600_064,
            fileName = "7zr.exe",
        )
        private val PACK_INDEX = Pinned(
            url = "https://github.com/the-database/animejanai-inference/releases/download/runtime-2/packs-windows-x64.json",
            sha256 = "5b6f0450ba4eed34d6e25dde072ce1298f083d91d142fd858495483e7711aea1",
            bytes = 15_242,
            fileName = "packs-windows-x64.json",
        )
        private val OVERLAY = Pinned(
            url = "https://github.com/the-database/mpv-upscale-2x_animejanai/releases/download/3.11.0/" +
                "mpv-upscale-2x_animejanai-overlay-3.11.0.7z",
            sha256 = "79c8d88b09e05f50febd6ba2988ab94cf22f4488d9b3cacdad16d7ca6cbbb603",
            bytes = 89_299_197,
            fileName = "overlay.7z",
        )
        private val OVERLAY_FILES = listOf(
            OverlayFile(
                "animejanai\\inference\\aji.dll", "inference/aji.dll",
                "095d18018b2ae7f40d9b7e814a9891867f6028ea94d3e06048d2d40276e63b45",
            ),
            OverlayFile(
                "animejanai\\inference\\aji_trt.dll", "inference/aji_trt.dll",
                "a681d572073dc0c0229a8f93a563aa8210dd283403d83ea175217ac23fde8a92",
            ),
            OverlayFile(
                "animejanai\\onnx\\2x_AnimeJaNai_HD_V3.1_Balanced_SPANF3_b8f64_unshuffle_fp16.onnx",
                "onnx/2x_AnimeJaNai_HD_V3.1_Balanced_SPANF3_b8f64_unshuffle_fp16.onnx",
                "b308e93c3dda3c4f9f968295b2b7b8d1c0583a711bca53a08d51134d9d1cbd5c",
            ),
            OverlayFile(
                "animejanai\\onnx\\2x_AnimeJaNai_SD_V1beta34_Compact_1x3xHxW_dyn-HW_strong_fp16_op21_dynamo.onnx",
                "onnx/2x_AnimeJaNai_SD_V1beta34_Compact_1x3xHxW_dyn-HW_strong_fp16_op21_dynamo.onnx",
                "ded46591ddfaeb22b3daaf84ad7b36838335aa3efca66acfa36177ce4ab21eb5",
            ),
        )

        /** Same content as the qualified configuration: TensorRT, built-in Balanced (slot 1002), empty user slots. */
        private val CONFIG_FILE = buildString {
            append("[global]\nconfig_version=3\nbackend=TensorRT\nlogging=yes\ndefault_slot=1002\n")
            (1..9).forEach { slot -> append("[slot_$slot]\nprofile_name=New Profile\n") }
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
