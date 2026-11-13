package com.nuvio.app.features.playback.animejanai

import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.math.roundToInt

/**
 * App-owned AnimeJaNai runtime at `%LOCALAPPDATA%\NuvioDV\AnimeJaNai` (inference/, onnx/,
 * animejanai.conf). Installation/download is not handled here; a missing or incomplete runtime
 * simply makes AnimeJaNai unavailable with a reason.
 */
internal class AnimeJaNaiRuntime(val rootDir: Path = defaultRootDir()) {
    val inferenceDir: Path get() = rootDir.resolve("inference")
    val modelDir: Path get() = rootDir.resolve("onnx")
    val configFile: Path get() = rootDir.resolve("animejanai.conf")

    sealed interface Availability {
        data object Available : Availability
        data class Unavailable(val reason: String) : Availability
    }

    fun availability(): Availability {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) {
            return Availability.Unavailable("AnimeJaNai is only supported on Windows")
        }
        if (!rootDir.isDirectory()) return Availability.Unavailable("AnimeJaNai runtime is not installed ($rootDir)")
        val missing = buildList {
            if (!configFile.isRegularFile()) add("animejanai.conf")
            REQUIRED_INFERENCE_FILES.filterNot { inferenceDir.resolve(it).isRegularFile() }.forEach { add("inference/$it") }
            REQUIRED_MODEL_FILES.filterNot { modelDir.resolve(it).isRegularFile() }.forEach { add("onnx/$it") }
        }
        return if (missing.isEmpty()) {
            Availability.Available
        } else {
            Availability.Unavailable("AnimeJaNai runtime is incomplete; missing ${missing.joinToString()}")
        }
    }

    /** `@aji:animejanai:...` filter string; paths use mpv's `[...]` quoting. */
    fun filter(slot: Int, streamMode: String): String =
        "@$FILTER_LABEL:animejanai" +
            ":lib=[${inferenceDir.resolve("aji.dll")}]" +
            ":conf=[$configFile]" +
            ":model-dir=[$modelDir]" +
            ":trtexec=[${inferenceDir.resolve("trtexec.exe")}]" +
            ":slot=$slot" +
            ":stream-mode=$streamMode"

    /** Start options for an mpv instance that may host the filter (vulkan + nvdec, DLL path, status logs). */
    fun playerStartOptions(): List<String> = listOf(
        "gpu-api=vulkan",
        "hwdec=nvdec",
        "nuvio-dll-dir=$inferenceDir",
        "nuvio-log-filter=$LOG_TOKEN",
        "nuvio-log-level=info",
    )

    /** Engine files the filter wrote into the model directory, by file name. */
    fun engineFiles(): Map<String, EngineFile> =
        if (!modelDir.isDirectory()) {
            emptyMap()
        } else {
            modelDir.listDirectoryEntries("aji-*.engine")
                .filter { it.isRegularFile() }
                .associate { path ->
                    path.name to EngineFile(path.name, Files.size(path), Files.getLastModifiedTime(path).toMillis())
                }
        }

    companion object {
        const val FILTER_LABEL = "aji"
        const val STATUS_TOKEN = "AJN_STREAM_V1"
        const val TIMING_TOKEN = "AJN_TIMING_V1"

        /**
         * Bridge log filter (a substring match): admits the versioned filter signals
         * [STATUS_TOKEN] and [TIMING_TOKEN]; other `AJN_` lines are ignored by the parsers.
         */
        const val LOG_TOKEN = "AJN_"

        /** Required files of the pinned `nuviodv-windows-ajn.1` TensorRT runtime (GPU-specific builder resources excluded). */
        val REQUIRED_INFERENCE_FILES = listOf(
            "aji.dll",
            "aji_trt.dll",
            "trtexec.exe",
            "cudart64_13.dll",
            "nvinfer_11.dll",
            "nvinfer_plugin_11.dll",
            "nvonnxparser_11.dll",
        )

        /**
         * Models of the exposed built-in profiles: HD V3.1 Balanced (1002) and Performance (1003) for
         * HD sources, SD Compact for SD sources in both.
         */
        val REQUIRED_MODEL_FILES = listOf(
            "2x_AnimeJaNai_HD_V3.1_Balanced_SPANF3_b8f64_unshuffle_fp16.onnx",
            "2x_AnimeJaNai_HD_V3.1_Performance_SPANF3_b5f48_unshuffle_fp16.onnx",
            "2x_AnimeJaNai_SD_V1beta34_Compact_1x3xHxW_dyn-HW_strong_fp16_op21_dynamo.onnx",
        )

        /** AnimeJaNai models upscale 2x. */
        const val SCALE = 2

        fun defaultRootDir(): Path {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            return (localAppData?.let(Paths::get)
                ?: Paths.get(System.getProperty("user.home").orEmpty()).resolve("AppData/Local"))
                .resolve("NuvioDV")
                .resolve("AnimeJaNai")
        }
    }
}

@Serializable
internal data class EngineFile(val name: String, val size: Long, val modifiedMillis: Long)

/** Engine identity used by Nuvio: profile slot plus decoded stream size and rounded frame rate. */
internal data class EngineKey(val slot: Int, val width: Int, val height: Int, val fps: Int) {
    val storageKey: String get() = "$slot|${width}x$height|$fps"

    companion object {
        fun of(slot: Int, width: Int, height: Int, fps: Double): EngineKey =
            EngineKey(slot, width, height, fps.takeIf { it.isFinite() && it > 0 }?.roundToInt() ?: 24)
    }
}

/**
 * Persisted map of prepared engines. An entry is valid only while every recorded engine file still
 * exists with its recorded size; a stale entry is dropped on lookup. The filter itself still rejects
 * incompatible engines at runtime (`engineIncompatible`).
 */
internal class AnimeJaNaiEngineIndex(
    private val runtime: AnimeJaNaiRuntime,
    private val store: DesktopStorage.Store = DesktopStorage.store("animejanai_engines"),
) {
    @Serializable
    private data class Entry(val files: List<EngineFile>, val preparedAtMillis: Long)

    private val json = Json { ignoreUnknownKeys = true }

    fun find(key: EngineKey): List<EngineFile>? {
        val entry = store.getString(key.storageKey)
            ?.let { runCatching { json.decodeFromString(Entry.serializer(), it) }.getOrNull() }
            ?: return null
        val valid = entry.files.isNotEmpty() && entry.files.all { file ->
            val path = runtime.modelDir.resolve(file.name)
            path.isRegularFile() && runCatching { Files.size(path) }.getOrNull() == file.size
        }
        if (!valid) {
            remove(key)
            return null
        }
        return entry.files
    }

    fun record(key: EngineKey, files: List<EngineFile>) {
        store.putString(key.storageKey, json.encodeToString(Entry.serializer(), Entry(files, System.currentTimeMillis())))
    }

    fun remove(key: EngineKey) {
        store.remove(key.storageKey)
    }
}

/**
 * One parsed `AJN_TIMING_V1 <frames> <meanMs> <p50Ms> <p95Ms> <maxMs> <slot>` line: GPU-completed
 * per-frame inference time over a window of frames (owned libmpv `nuviodv-windows-ajn.3` and later).
 */
internal data class AjnTiming(
    val frames: Int,
    val meanMillis: Double,
    val p50Millis: Double,
    val p95Millis: Double,
    val maxMillis: Double,
    val slot: Int?,
) {
    companion object {
        fun parse(line: String): AjnTiming? {
            val tokens = line.substringAfter(AnimeJaNaiRuntime.TIMING_TOKEN, missingDelimiterValue = "")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            val frames = tokens.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val values = (1..4).map { tokens.getOrNull(it)?.toDoubleOrNull()?.takeIf { v -> v.isFinite() && v >= 0 } ?: return null }
            return AjnTiming(frames, values[0], values[1], values[2], values[3], tokens.getOrNull(5)?.toIntOrNull())
        }
    }
}

/** One parsed `AJN_STREAM_V1 <state> <backend> <slot> <inW> <inH> <outW> <outH> <rife> <fps>` line. */
internal data class AjnStreamStatus(
    val state: String,
    val backend: String,
    val slot: Int?,
    val inWidth: Int?,
    val inHeight: Int?,
    val outWidth: Int?,
    val outHeight: Int?,
    val fps: Double?,
) {
    val isBuilding: Boolean get() = state == "building"
    val isActive: Boolean get() = state == "active"
    val isBypass: Boolean get() = state == "bypass"

    companion object {
        fun parse(line: String): AjnStreamStatus? {
            val tokens = line.substringAfter(AnimeJaNaiRuntime.STATUS_TOKEN, missingDelimiterValue = "")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return AjnStreamStatus(
                state = tokens[0],
                backend = tokens.getOrNull(1).orEmpty(),
                slot = tokens.getOrNull(2)?.toIntOrNull(),
                inWidth = tokens.getOrNull(3)?.toIntOrNull(),
                inHeight = tokens.getOrNull(4)?.toIntOrNull(),
                outWidth = tokens.getOrNull(5)?.toIntOrNull(),
                outHeight = tokens.getOrNull(6)?.toIntOrNull(),
                fps = tokens.getOrNull(8)?.toDoubleOrNull(),
            )
        }
    }
}
