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
    data class Preparing(val slot: Int, val width: Int, val height: Int, val fps: Int, val elapsedSeconds: Int) :
        AnimeJaNaiPreparationState
    data class Ready(val slot: Int, val width: Int, val height: Int, val fps: Int) : AnimeJaNaiPreparationState
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

    private val _state = MutableStateFlow<AnimeJaNaiPreparationState>(AnimeJaNaiPreparationState.Idle)
    val state: StateFlow<AnimeJaNaiPreparationState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val activePlaybacks = AtomicInteger(0)

    val isPreparing: Boolean get() = running.get()

    internal fun onPlaybackStarted() {
        activePlaybacks.incrementAndGet()
    }

    internal fun onPlaybackEnded() {
        activePlaybacks.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Starts preparation for [key] from [source]; returns a refusal reason, or null when started. */
    fun prepare(key: EngineKey, source: PreparationSource): String? {
        val availability = runtime.availability()
        if (availability is AnimeJaNaiRuntime.Availability.Unavailable) return availability.reason
        if (activePlaybacks.get() > 0) return "Engine preparation cannot run during playback"
        if (!running.compareAndSet(false, true)) return "Another engine preparation is already running"
        cancelRequested.set(false)
        _state.value = AnimeJaNaiPreparationState.Preparing(key.slot, key.width, key.height, key.fps, 0)
        Thread({
            val result = try {
                runPreparation(key, source)
            } catch (error: Throwable) {
                log.w(error) { "preparation crashed key=${key.storageKey}" }
                "Preparation failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                running.set(false)
            }
            _state.value = if (result == null) {
                AnimeJaNaiPreparationState.Ready(key.slot, key.width, key.height, key.fps)
            } else {
                AnimeJaNaiPreparationState.Failed(key.slot, key.width, key.height, key.fps, result)
            }
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

    internal fun preparationOptions(key: EngineKey, source: PreparationSource): List<String> = buildList {
        add("vo=null")
        add("ao=null")
        add("hwdec=nvdec")
        add("nuvio-dll-dir=${runtime.inferenceDir}")
        add("nuvio-log-filter=${AnimeJaNaiRuntime.STATUS_TOKEN}")
        add("nuvio-log-level=info")
        if (source.headerLines.isNotEmpty()) add("http-header-fields=${escapeHeaderList(source.headerLines)}")
        add("vf=${runtime.filter(key.slot, "prepare")}")
    }

    /** mpv string-list syntax, as the player bridge builds it: ',' separated, '\' and ',' escaped. */
    internal fun escapeHeaderList(lines: List<String>): String =
        lines.joinToString(",") { line -> line.replace("\\", "\\\\").replace(",", "\\,") }
}
