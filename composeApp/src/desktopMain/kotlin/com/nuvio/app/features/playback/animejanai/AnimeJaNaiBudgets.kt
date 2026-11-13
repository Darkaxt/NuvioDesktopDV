package com.nuvio.app.features.playback.animejanai

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile

/**
 * Locally cached AnimeJaNai performance budgets (spec P4). Machine-specific and never synced.
 *
 * One entry per prepared engine key and measurement environment. The environment fingerprint covers
 * the GPU name and driver, the loaded libmpv build, the installed runtime manifest (AnimeJaNai,
 * TensorRT and model versions) and the engine files themselves (their names encode model hash,
 * TensorRT version and GPU), so a change to any of them misses the old entry and only that engine
 * needs re-measuring. Live budget breaches are stored per output-size class inside the entry, and a
 * new measurement replaces the entry (clearing old breaches).
 */
internal class AnimeJaNaiBudgets(
    private val runtime: AnimeJaNaiRuntime,
    private val store: DesktopStorage.Store = DesktopStorage.store("animejanai_budgets"),
    private val environment: () -> String = { defaultEnvironment(runtime) },
) {
    @Serializable
    data class Entry(
        /** Video frames processed by the isolated benchmark sample. */
        val frames: Int,
        /** Mean wall time per processed frame (decode + inference, no rendering). */
        val meanFrameMillis: Double,
        /** Source frame interval the budget applies to. */
        val frameIntervalMillis: Double,
        val measuredAtMillis: Long,
        /** Output class to reason, for classes where playback breached the budget. */
        val liveBreaches: Map<String, String> = emptyMap(),
        /**
         * 95th percentile of the GPU-completed per-frame inference time reported by the filter
         * (`AJN_TIMING_V1`, the highest window p95 of the sample); null when the libmpv build does not
         * report timing.
         */
        val p95FrameMillis: Double? = null,
    ) {
        /**
         * Acceptance budget: both the mean processing cost (decode + inference) and, when reported,
         * the p95 GPU inference time stay below 80% of the output-frame interval.
         */
        val qualified: Boolean get() {
            val budget = BUDGET_FRACTION * frameIntervalMillis
            return meanFrameMillis < budget && (p95FrameMillis == null || p95FrameMillis < budget)
        }

        fun describe(): String = buildString {
            append("%.1f ms".format(meanFrameMillis))
            p95FrameMillis?.let { append(", p95 GPU %.1f ms".format(it)) }
            append(" of %.1f ms per frame".format(frameIntervalMillis))
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun find(key: EngineKey, engines: List<EngineFile>): Entry? =
        store.getString(storageKey(key, engines))
            ?.let { runCatching { json.decodeFromString(Entry.serializer(), it) }.getOrNull() }

    fun recordBenchmark(
        key: EngineKey,
        engines: List<EngineFile>,
        frames: Int,
        meanFrameMillis: Double,
        frameIntervalMillis: Double,
        p95FrameMillis: Double? = null,
    ): Entry {
        val entry = Entry(
            frames, meanFrameMillis, frameIntervalMillis, System.currentTimeMillis(),
            p95FrameMillis = p95FrameMillis,
        )
        store.putString(storageKey(key, engines), json.encodeToString(Entry.serializer(), entry))
        return entry
    }

    fun recordLiveBreach(key: EngineKey, engines: List<EngineFile>, outputClass: String, reason: String) {
        val storageKey = storageKey(key, engines)
        val entry = find(key, engines) ?: return
        val updated = entry.copy(liveBreaches = entry.liveBreaches + (outputClass to reason))
        store.putString(storageKey, json.encodeToString(Entry.serializer(), updated))
    }

    private fun storageKey(key: EngineKey, engines: List<EngineFile>): String {
        val engineIdentity = engines.sortedBy { it.name }.joinToString(";") { "${it.name}:${it.size}" }
        return "${key.storageKey}|${sha256(environment() + "|" + engineIdentity).take(24)}"
    }

    companion object {
        const val BUDGET_FRACTION = 0.8

        /** Output-size class by drawable height: the render cost of the 2x output grows with it. */
        fun outputClass(outputHeight: Int): String = when {
            outputHeight <= 1080 -> "1080"
            outputHeight <= 1440 -> "1440"
            outputHeight <= 2160 -> "2160"
            else -> "above2160"
        }

        internal val gpuIdentity: String by lazy { queryGpuIdentity() }

        fun defaultEnvironment(runtime: AnimeJaNaiRuntime): String {
            val manifest = runtime.rootDir.resolve(AnimeJaNaiRuntimeInstaller.MANIFEST_NAME)
            val manifestHash = if (manifest.isRegularFile()) sha256(Files.readString(manifest)) else "no-manifest"
            return "$gpuIdentity|${NativePlayerBridge.libmpvIdentity()}|$manifestHash"
        }

        /** `name, driver` of the NVIDIA GPU (the only AnimeJaNai backend here); stable for the app run. */
        private fun queryGpuIdentity(): String = runCatching {
            val windir = System.getenv("WINDIR") ?: "C:\\Windows"
            val tool = Path.of(windir, "System32", "nvidia-smi.exe")
            val process = ProcessBuilder(tool.toString(), "--query-gpu=name,driver_version", "--format=csv,noheader")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                "gpu-unknown"
            } else {
                output.lineSequence().firstOrNull().orEmpty().ifBlank { "gpu-unknown" }
            }
        }.getOrDefault("gpu-unknown")

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Steady-state frame-drop watch for an active AnimeJaNai stream (spec P4 live downgrade). Windows
 * are counted in completed frames; pauses, seeks and buffering reset the window and are followed by
 * a warm-up, so only steady rendering is judged. A breach needs [breachWindows] consecutive windows
 * whose drops exceed [maxDropFraction]; the caller then switches to the next cheaper prepared,
 * qualified profile, or disables AnimeJaNai for the rest of the stream when there is none; a
 * breached profile is never re-enabled live.
 */
internal class LiveBudgetMonitor(
    private val windowFrames: Long = 240,
    private val warmupFrames: Long = 48,
    private val maxDropFraction: Double = 0.02,
    private val breachWindows: Int = 2,
) {
    private var warmupEndFrame: Long? = null
    private var windowStartFrame = 0L
    private var windowStartDrops = 0L
    private var windowOpen = false
    private var consecutiveBreaches = 0

    fun reset() {
        warmupEndFrame = null
        windowOpen = false
        consecutiveBreaches = 0
    }

    /** Returns the breach description once the budget is breached in a sustained way, else null. */
    fun sample(frame: Long, drops: Long, steady: Boolean): String? {
        if (!steady || frame < 0 || drops < 0) {
            reset()
            return null
        }
        val warmupEnd = warmupEndFrame ?: (frame + warmupFrames).also { warmupEndFrame = it }
        if (frame < warmupEnd) return null
        if (!windowOpen || frame < windowStartFrame || drops < windowStartDrops) {
            windowOpen = true
            windowStartFrame = frame
            windowStartDrops = drops
            return null
        }
        val frames = frame - windowStartFrame
        if (frames < windowFrames) return null
        val dropped = drops - windowStartDrops
        windowStartFrame = frame
        windowStartDrops = drops
        if (dropped > frames * maxDropFraction) {
            consecutiveBreaches++
            if (consecutiveBreaches >= breachWindows) {
                return "$dropped of $frames frames dropped in steady playback"
            }
        } else {
            consecutiveBreaches = 0
        }
        return null
    }
}
