package com.nuvio.app.features.playback.svp

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * The user's licensed SVP 4 installation (spec P5). NuvioDV uses only its VapourSynth runtime
 * (`mpv64`: portable VapourSynth + Python) and the SVPflow VapourSynth plugins (`plugins64`), loaded
 * into the owned libmpv through `vf_vapoursynth`. SVP's own libmpv is never substituted, and SVP
 * Manager is not involved: the app owns its filter chain (no concurrent Manager control).
 */
internal class SvpRuntime(val rootDir: Path = defaultRootDir()) {
    /** Portable VapourSynth runtime directory; `VSScript.dll` is resolved from here. */
    val vapourSynthDir: Path get() = rootDir.resolve("mpv64")
    val pluginDir: Path get() = rootDir.resolve("plugins64")
    val flow1Plugin: Path get() = pluginDir.resolve("svpflow1_vs.dll")
    val flow2Plugin: Path get() = pluginDir.resolve("svpflow2_vs.dll")

    sealed interface Availability {
        data class Available(val versions: Map<String, String>) : Availability
        data class Unavailable(val reason: String) : Availability
    }

    fun availability(): Availability {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
            return Availability.Unavailable("SVP is only supported on Windows")
        }
        if (!rootDir.isDirectory()) return Availability.Unavailable("SVP 4 is not installed ($rootDir)")
        val missing = buildList {
            REQUIRED_RUNTIME_FILES.filterNot { vapourSynthDir.resolve(it).isRegularFile() }.forEach { add("mpv64/$it") }
            if (!flow1Plugin.isRegularFile()) add("plugins64/svpflow1_vs.dll")
            if (!flow2Plugin.isRegularFile()) add("plugins64/svpflow2_vs.dll")
        }
        if (missing.isNotEmpty()) {
            return Availability.Unavailable(
                "SVP's VapourSynth components are missing (${missing.joinToString()}); " +
                    "install the VapourSynth options in SVP 4",
            )
        }
        return Availability.Available(componentVersions())
    }

    /** Installed versions of the SVP packages this integration depends on (from components.xml). */
    fun componentVersions(): Map<String, String> {
        val manifest = rootDir.resolve("components.xml")
        if (!manifest.isRegularFile()) return emptyMap()
        val text = runCatching { Files.readString(manifest) }.getOrDefault("")
        return parseComponentVersions(text).filterKeys { it in RELEVANT_PACKAGES }
    }

    /** Start options for an mpv instance that may host the SVP filter. */
    fun playerStartOptions(decoderPriority: Int): List<String> = buildList {
        // vf_vapoursynth needs frames in system memory. Copy-back keeps hardware decoding; software
        // decoding (an explicit user choice) already delivers system-memory frames and is kept.
        if (decoderPriority != SOFTWARE_DECODING) add("hwdec=auto-copy")
        add("nuvio-dll-dir=$vapourSynthDir")
        add("nuvio-log-filter=$BUFFER_WARNING_TOKEN")
        add("nuvio-log-level=warn")
    }

    companion object {
        const val FILTER_LABEL = "nuviosvp"
        const val SOFTWARE_DECODING = 2

        /** vf_vapoursynth's warning when the script needs more buffered input frames. */
        const val BUFFER_WARNING_TOKEN = "buffered-frames"

        private val REQUIRED_RUNTIME_FILES = listOf("VSScript.dll", "vapoursynth.dll", "python312.dll", "portable.vs")
        private val RELEVANT_PACKAGES = setOf("core.full", "core.flow64vs", "opt.vps_portable64", "deps.python64")

        fun defaultRootDir(): Path {
            val programFiles = System.getenv("ProgramFiles")?.takeIf { it.isNotBlank() } ?: "C:\\Program Files"
            return Paths.get(programFiles, "SVP 4")
        }

        internal fun parseComponentVersions(text: String): Map<String, String> =
            Regex("<Package>(.*?)</Package>", RegexOption.DOT_MATCHES_ALL).findAll(text).mapNotNull { match ->
                val block = match.groupValues[1]
                val name = Regex("<Name>(.*?)</Name>").find(block)?.groupValues?.get(1)
                val version = Regex("<Version>(.*?)</Version>").find(block)?.groupValues?.get(1)
                if (name != null && version != null) name to version else null
            }.toMap()
    }
}
