package com.nuvio.app.features.playback.animejanai

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface AnimeJaNaiPreparationState {
    data object Idle : AnimeJaNaiPreparationState
    data class Preparing(
        val slot: Int,
        val width: Int,
        val height: Int,
        val fps: Int,
        val elapsedSeconds: Int,
        val phase: String = "Preparing engine",
    ) : AnimeJaNaiPreparationState
    /** Engine prepared and measured; [qualified] says whether playback may use it. */
    data class Ready(val slot: Int, val width: Int, val height: Int, val fps: Int, val budget: String, val qualified: Boolean) :
        AnimeJaNaiPreparationState
    data class Failed(val slot: Int, val width: Int, val height: Int, val fps: Int, val reason: String) :
        AnimeJaNaiPreparationState
}

/** The stream to prepare from: the same URL and HTTP header lines the player used. */
internal data class PreparationSource(val url: String, val headerLines: List<String>)

/**
 * Supervised engine preparation (spec P4): a hidden, windowless (`vo=null`) libmpv instance decodes
 * the title's own stream with nvdec and runs the filter in `stream-mode=prepare`, wakes it every
 * ~2 s with `vf-command aji poll ""` and finishes on the first non-`building` status
 * (`active` = ready). It never runs while a playback is active, and only one runs at a time.
 *
 * A successful build is followed, in the same supervised operation, by an isolated benchmark: a
 * second windowless instance plays [BENCHMARK_FRAMES] frames of the same stream `untimed` with the
 * filter in `stream-mode=required`. The time from the filter's `active` signal to the end of that
 * frame sample is decode + inference cost (no rendering), recorded in [AnimeJaNaiBudgets].
 *
 * The real stream is required: the filter only accepts CUDA hardware frames, so software sources
 * such as `av://lavfi:testsrc2` fail with "Unknown hw_subfmt" (verified 2026-11-12), and the
 * stream's own size/rate is exactly the engine identity playback will request. Verified with the
 * owned build: real 960x540 23.976 clip, vo=null, building -> active in 44.5 s, then a
 * `stream-mode=required` playback of the same clip reached `active` in 1.75 s.
 */
internal object AnimeJaNaiPreparationController {
    private val log = Logger.withTag("AnimeJaNaiPreparation")
    private const val POLL_SECONDS = 2.0
    private const val TIMEOUT_MILLIS = 20 * 60 * 1000L

    val runtime: AnimeJaNaiRuntime by lazy { AnimeJaNaiRuntime() }
    val engineIndex: AnimeJaNaiEngineIndex by lazy { AnimeJaNaiEngineIndex(runtime) }
    val installer: AnimeJaNaiRuntimeInstaller by lazy { AnimeJaNaiRuntimeInstaller(runtime) }
    val budgets: AnimeJaNaiBudgets by lazy { AnimeJaNaiBudgets(runtime) }

    /** Processed-frame sample of the benchmark (20 s at 24 fps), started 10% into the stream. */
    internal const val BENCHMARK_FRAMES = 480
    private const val BENCHMARK_START = "10%"
    private const val BENCHMARK_TIMEOUT_MILLIS = 5 * 60 * 1000L

    private val _state = MutableStateFlow<AnimeJaNaiPreparationState>(AnimeJaNaiPreparationState.Idle)
    val state: StateFlow<AnimeJaNaiPreparationState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val activePlaybacks = AtomicInteger(0)

    val isPreparing: Boolean get() = running.get()
    val hasActivePlayback: Boolean get() = activePlaybacks.get() > 0

    internal fun onPlaybackStarted() {
        activePlaybacks.incrementAndGet()
    }

    internal fun onPlaybackEnded() {
        activePlaybacks.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Engine a closed playback needed but that is not prepared yet, with the stream to prepare from. */
    data class PendingPreparation(val key: EngineKey, val source: PreparationSource)

    private val _pending = MutableStateFlow<PendingPreparation?>(null)
    val pending: StateFlow<PendingPreparation?> = _pending.asStateFlow()

    /** Remembers [key] when its engine is missing or not yet measured. */
    internal fun rememberPending(key: EngineKey, source: PreparationSource) {
        val engines = engineIndex.find(key)
        if (engines == null || budgets.find(key, engines) == null) _pending.value = PendingPreparation(key, source)
    }

    /** Prepares the remembered pending engine; returns a refusal reason, or null when started. */
    fun preparePending(): String? {
        val request = _pending.value ?: return "No engine preparation is needed"
        return prepare(request.key, request.source)
    }

    /** Starts preparation for [key] from [source]; returns a refusal reason, or null when started. */
    fun prepare(key: EngineKey, source: PreparationSource): String? {
        val availability = runtime.availability()
        if (availability is AnimeJaNaiRuntime.Availability.Unavailable) return availability.reason
        if (activePlaybacks.get() > 0) return "Engine preparation cannot run during playback"
        if (installer.isInstalling) return "The AnimeJaNai runtime is being installed"
        if (!running.compareAndSet(false, true)) return "Another engine preparation is already running"
        cancelRequested.set(false)
        _state.value = AnimeJaNaiPreparationState.Preparing(key.slot, key.width, key.height, key.fps, 0)
        Thread({
            val result: Result<AnimeJaNaiBudgets.Entry> = try {
                runPreparation(key, source)?.let { failure -> Result.failure(PreparationFailure(failure)) }
                    ?: runBenchmark(key, source)
            } catch (error: Throwable) {
                log.w(error) { "preparation crashed key=${key.storageKey}" }
                Result.failure(PreparationFailure("Preparation failed: ${error.message ?: error.javaClass.simpleName}"))
            } finally {
                running.set(false)
            }
            result.onSuccess { _pending.compareAndSet(PendingPreparation(key, source), null) }
            _state.value = result.fold(
                onSuccess = { entry ->
                    AnimeJaNaiPreparationState.Ready(key.slot, key.width, key.height, key.fps, entry.describe(), entry.qualified)
                },
                onFailure = { error ->
                    AnimeJaNaiPreparationState.Failed(key.slot, key.width, key.height, key.fps, error.message.orEmpty())
                },
            )
        }, "nuvio-animejanai-prepare").apply {
            isDaemon = true
            start()
        }
        return null
    }

    fun cancel() {
        cancelRequested.set(true)
    }

    /** Returns null on success or the failure reason. */
    private fun runPreparation(key: EngineKey, source: PreparationSource): String? {
        NativePlayerBridge.ensureLoaded()
        val before = runtime.engineFiles()
        val handle = NativePlayerBridge.headlessCreate(preparationOptions(key, source).toTypedArray())
        if (handle == 0L) return "Unable to start the preparation player"
        try {
            if (NativePlayerBridge.headlessCommand(handle, arrayOf("loadfile", source.url)) < 0) {
                return "Unable to open the stream for preparation"
            }
            val startedAt = System.currentTimeMillis()
            while (true) {
                if (cancelRequested.get()) return "Preparation cancelled"
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed > TIMEOUT_MILLIS) return "Preparation timed out"
                _state.value = AnimeJaNaiPreparationState.Preparing(
                    key.slot, key.width, key.height, key.fps, (elapsed / 1000).toInt(),
                )
                NativePlayerBridge.headlessCommand(handle, arrayOf("vf-command", AnimeJaNaiRuntime.FILTER_LABEL, "poll", ""))
                val line = NativePlayerBridge.headlessNextLog(handle, POLL_SECONDS)
                when {
                    line.isEmpty() -> continue
                    line.startsWith("event:") -> return "Preparation player stopped unexpectedly ($line)"
                }
                val status = AjnStreamStatus.parse(line) ?: continue
                if (status.isBuilding) continue
                if (!status.isActive) return "Engine preparation ended with '${status.state}'"
                if (status.inWidth != key.width || status.inHeight != key.height) {
                    return "Stream decoded at ${status.inWidth}x${status.inHeight}, expected ${key.width}x${key.height}"
                }
                val after = runtime.engineFiles()
                val created = after.values.filter { before[it.name] != it }
                // Engine already existed (nothing new written): bind to the current engine set so a
                // later deletion invalidates the entry and forces re-preparation.
                val recorded = created.ifEmpty { after.values.toList() }
                if (recorded.isEmpty()) return "Preparation reported ready but no engine file was found"
                engineIndex.record(key, recorded)
                log.i { "prepared key=${key.storageKey} files=${recorded.map(EngineFile::name)}" }
                return null
            }
        } finally {
            NativePlayerBridge.headlessDestroy(handle)
        }
    }

    private class PreparationFailure(message: String) : Exception(message)

    /** Measures the prepared engine on [source]; the engine must already be recorded in [engineIndex]. */
    private fun runBenchmark(key: EngineKey, source: PreparationSource): Result<AnimeJaNaiBudgets.Entry> {
        fun fail(reason: String) = Result.failure<AnimeJaNaiBudgets.Entry>(PreparationFailure(reason))
        val engines = engineIndex.find(key) ?: return fail("Prepared engine was not recorded")
        val handle = NativePlayerBridge.headlessCreate(benchmarkOptions(key, source).toTypedArray())
        if (handle == 0L) return fail("Unable to start the benchmark player")
        try {
            if (NativePlayerBridge.headlessCommand(handle, arrayOf("loadfile", source.url)) < 0) {
                return fail("Unable to open the stream for the benchmark")
            }
            val startedAt = System.currentTimeMillis()
            var activeAt = 0L
            var sourceFps = 0.0
            while (true) {
                if (cancelRequested.get()) return fail("Preparation cancelled")
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed > BENCHMARK_TIMEOUT_MILLIS) return fail("Benchmark timed out")
                _state.value = AnimeJaNaiPreparationState.Preparing(
                    key.slot, key.width, key.height, key.fps, (elapsed / 1000).toInt(), "Measuring performance",
                )
                val line = NativePlayerBridge.headlessNextLog(handle, POLL_SECONDS)
                if (line.isEmpty()) continue
                if (line.startsWith("event:")) {
                    if (activeAt == 0L || line != "event:end-file") {
                        return fail("Benchmark player stopped before measuring ($line)")
                    }
                    val measuredMillis = (System.currentTimeMillis() - activeAt).toDouble()
                    val fps = sourceFps.takeIf { it > 0 } ?: key.fps.toDouble()
                    val entry = budgets.recordBenchmark(
                        key, engines, BENCHMARK_FRAMES, measuredMillis / BENCHMARK_FRAMES, 1000.0 / fps,
                    )
                    log.i { "benchmark key=${key.storageKey} ${entry.describe()} qualified=${entry.qualified}" }
                    return Result.success(entry)
                }
                val status = AjnStreamStatus.parse(line) ?: continue
                when {
                    status.isActive && activeAt == 0L -> {
                        activeAt = System.currentTimeMillis()
                        sourceFps = status.fps ?: 0.0
                    }
                    status.isBuilding || status.isActive -> Unit
                    else -> return fail("Benchmark ended with '${status.state}'")
                }
            }
        } finally {
            NativePlayerBridge.headlessDestroy(handle)
        }
    }

    internal fun preparationOptions(key: EngineKey, source: PreparationSource): List<String> =
        headlessOptions(source) + "vf=${runtime.filter(key.slot, "prepare")}"

    /** Untimed playback of a fixed frame sample with the prepared engine (never builds). */
    internal fun benchmarkOptions(key: EngineKey, source: PreparationSource): List<String> =
        headlessOptions(source) + listOf(
            "untimed=yes",
            "frames=$BENCHMARK_FRAMES",
            "start=$BENCHMARK_START",
            "vf=${runtime.filter(key.slot, "required")}",
        )

    private fun headlessOptions(source: PreparationSource): List<String> = buildList {
        add("vo=null")
        add("ao=null")
        add("hwdec=nvdec")
        add("nuvio-dll-dir=${runtime.inferenceDir}")
        add("nuvio-log-filter=${AnimeJaNaiRuntime.STATUS_TOKEN}")
        add("nuvio-log-level=info")
        if (source.headerLines.isNotEmpty()) add("http-header-fields=${escapeHeaderList(source.headerLines)}")
    }

    /** mpv string-list syntax, as the player bridge builds it: ',' separated, '\' and ',' escaped. */
    internal fun escapeHeaderList(lines: List<String>): String =
        lines.joinToString(",") { line -> line.replace("\\", "\\\\").replace(",", "\\,") }
}
