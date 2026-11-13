package com.nuvio.app.features.playback.svp

import co.touchlab.kermit.Logger
import com.nuvio.app.features.playback.animejanai.EnhancementStatus
import com.nuvio.app.features.playback.animejanai.LiveBudgetMonitor
import com.nuvio.app.features.playback.classification.AnimationToggleState
import com.nuvio.app.features.playback.classification.CanonicalTitle
import com.nuvio.app.features.playback.classification.DesktopAnimationToggles
import com.nuvio.app.features.player.ActivePlaybackTitle
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.player.SvpMode
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Start-time SVP decision; the options make the stream able to host the filter. */
internal data class SvpStartPlan(
    val startOptions: List<String>,
    val toggleState: StateFlow<AnimationToggleState>,
)

/**
 * Per-player SVP integration (spec P5). One instance per native player controller. The app owns
 * the whole filter: it generates the VapourSynth script, inserts it under its own label after the
 * decoded video and display refresh are known, and only ever removes that label (other filters are
 * untouched). SVP Manager is not involved. The stream is never reloaded.
 */
internal class SvpSession(
    private val runtime: SvpRuntime = sharedRuntime,
    private val capability: SvpCapability = sharedCapability,
    private val scriptDir: Path = defaultScriptDir(),
) {
    private val log = Logger.withTag("SVP")
    private val worker = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
        Thread(runnable, "nuvio-svp").apply { isDaemon = true }
    }
    private val evaluating = AtomicBoolean(false)

    private val _status = MutableStateFlow(EnhancementStatus.Off)
    val status: StateFlow<EnhancementStatus> = _status.asStateFlow()

    // Mutated on the worker thread only.
    @Volatile private var plan: SvpStartPlan? = null
    private val nativeLock = Any()
    private var handle: Long = 0L
    @Volatile private var decidedKey: String? = null
    @Volatile private var inserted: SvpDecision.Interpolate? = null
    @Volatile private var insertedBufferedFrames = 0
    /** Consecutive steady ticks in which the filtered rate stayed below the target. */
    @Volatile private var lowFpsTicks = 0
    @Volatile private var svpEligible = true
    @Volatile private var bufferedFrames = SvpPlanner.INITIAL_BUFFERED_FRAMES
    @Volatile private var bufferIncreaseRequested = false
    @Volatile private var displayWaitStartMillis = 0L
    /** Off for the rest of this stream (failure or no sustainable target left). */
    @Volatile private var disabledForStream = false
    private val liveMonitor = LiveBudgetMonitor()

    /** Toggle state of a stream that started without SVP only because of its classification. */
    @Volatile var deferredToggleState: StateFlow<AnimationToggleState>? = null
        private set

    suspend fun planStart(settings: PlayerSettingsUiState): SvpStartPlan? {
        deferredToggleState = null
        if (settings.svpMode != SvpMode.Automatic) {
            _status.value = EnhancementStatus.Off
            return null
        }
        fun skip(reason: String): SvpStartPlan? {
            _status.value = EnhancementStatus(EnhancementStatus.Kind.Skipped, reason)
            return null
        }
        if (settings.nvidiaRtxSuperResolutionEnabled) return skip("RTX Video Super Resolution is on")
        val availability = runtime.availability()
        if (availability is SvpRuntime.Availability.Unavailable) return skip(availability.reason)
        val title = ActivePlaybackTitle.current ?: return skip("Title is unknown")
        val profileId = ProfileRepository.activeProfileId.toString()
        val toggles = DesktopAnimationToggles.repository
        val toggleState = runCatching {
            toggles.load(profileId)
            toggles.state(profileId, CanonicalTitle.of(title.parentMetaId, title.parentMetaType))
        }.getOrElse { error -> return skip("Animation toggle unavailable: ${error.message}") }
        val state = toggleState.value
        if (!state.eligibility.svp) {
            deferredToggleState = toggleState
            return skip(ineligibleReason(state))
        }
        svpEligible = true
        return SvpStartPlan(runtime.playerStartOptions(settings.decoderPriority), toggleState)
    }

    fun beginStream(startPlan: SvpStartPlan?) {
        worker.execute {
            plan = startPlan
            decidedKey = null
            inserted = null
            bufferedFrames = SvpPlanner.INITIAL_BUFFERED_FRAMES
            bufferIncreaseRequested = false
            displayWaitStartMillis = 0L
            disabledForStream = false
            liveMonitor.reset()
            if (startPlan != null) {
                _status.value = EnhancementStatus(EnhancementStatus.Kind.Pending, "Waiting for video")
            }
        }
    }

    fun attachHandle(nativeHandle: Long) {
        synchronized(nativeLock) { handle = nativeHandle }
    }

    /** Called before the native player is disposed; waits for any in-flight native call. */
    fun detachHandle(nativeHandle: Long) {
        synchronized(nativeLock) {
            if (handle == nativeHandle) {
                handle = 0L
                inserted = null
            }
        }
    }

    fun onTick() {
        if (plan == null) return
        if (!evaluating.compareAndSet(false, true)) return
        worker.execute {
            try {
                evaluate()
                monitor()
            } catch (error: Throwable) {
                log.w(error) { "evaluation failed" }
            } finally {
                evaluating.set(false)
            }
        }
    }

    /** `mpvLog:` lines forwarded by the native bridge (filtered on [SvpRuntime.BUFFER_WARNING_TOKEN]). */
    fun onLogLine(line: String) {
        if (!line.contains(SvpRuntime.BUFFER_WARNING_TOKEN)) return
        bufferIncreaseRequested = true
    }

    /** Live Animation toggle change: Animation On removes SVP; Off inserts it on an SVP-capable stream. */
    fun applyAnimationToggle(eligible: Boolean) {
        worker.execute {
            if (plan == null) {
                _status.value = EnhancementStatus(
                    EnhancementStatus.Kind.Pending,
                    if (eligible) "SVP applies at the next stream start" else "Animation is On",
                )
                return@execute
            }
            svpEligible = eligible
            if (!eligible) {
                removeFilter()
                _status.value = _status.value.copy(kind = EnhancementStatus.Kind.Skipped, reason = "Animation is On", outputFps = null)
            } else {
                decidedKey = null // re-evaluated on the next tick
            }
        }
    }

    suspend fun observeToggle(toggleState: StateFlow<AnimationToggleState>) {
        var last: Boolean? = null
        toggleState.collect { state ->
            val eligible = state.eligibility.svp
            if (last != null && last != eligible) applyAnimationToggle(eligible)
            last = eligible
        }
    }

    private fun evaluate() {
        plan ?: return
        if (!svpEligible || disabledForStream) return
        val width = intProperty("video-dec-params/w") ?: return
        val height = intProperty("video-dec-params/h") ?: return
        if (width <= 0 || height <= 0) return
        val sourceFps = doubleProperty("container-fps")
        // display-fps is the detected refresh; the estimate (display-sync only) jitters, so round it.
        val displayFps = doubleProperty("display-fps")
            ?: doubleProperty("estimated-display-fps")?.let { Math.round(it * 10) / 10.0 }
        if (displayFps == null) {
            // The refresh rate is known once the window is shown; give it a bounded wait.
            val now = System.currentTimeMillis()
            if (displayWaitStartMillis == 0L) displayWaitStartMillis = now
            if (now - displayWaitStartMillis < DISPLAY_WAIT_MILLIS) return
        }
        val gamma = stringProperty("video-dec-params/gamma")
        val resolutionClass = SvpPlanner.resolutionClass(width, height)
        val key = "${width}x$height|$sourceFps|$displayFps|$gamma|$bufferedFrames|${capability.breachedTargetMilli(resolutionClass)}"
        if (key == decidedKey && !bufferIncreaseRequested) return
        decidedKey = key

        if (bufferIncreaseRequested) {
            bufferIncreaseRequested = false
            val increased = SvpPlanner.increasedBufferedFrames(bufferedFrames)
            if (increased == null) {
                failForStream("VapourSynth frame buffer exhausted at $bufferedFrames frames")
                return
            }
            log.i { "buffered-frames $bufferedFrames -> $increased" }
            bufferedFrames = increased
        }

        val decision = SvpPlanner.plan(
            SvpInput(sourceFps, displayFps, width, height, gamma, capability.breachedTargetMilli(resolutionClass)),
        )
        val current = inserted
        if (decision is SvpDecision.Skip) {
            removeFilter()
            _status.value = EnhancementStatus(
                EnhancementStatus.Kind.Skipped, decision.reason,
                inputWidth = width, inputHeight = height, sourceFps = sourceFps,
            )
            return
        }
        decision as SvpDecision.Interpolate
        if (current == decision && insertedBufferedFrames == bufferedFrames && filterPresent()) return
        removeFilter()
        val script = runCatching { writeScript(decision) }.getOrElse { error ->
            disabledForStream = true
            _status.value = EnhancementStatus(EnhancementStatus.Kind.Failed, "Script write failed: ${error.message}")
            return
        }
        val filter = SvpPlanner.filter(script, bufferedFrames, SvpPlanner.concurrentFrames())
        val result = command("vf", "add", filter)
        if (result < 0) {
            disabledForStream = true
            _status.value = EnhancementStatus(
                EnhancementStatus.Kind.Failed, "Filter insertion failed ($result)",
                inputWidth = width, inputHeight = height, sourceFps = decision.sourceFps,
            )
            return
        }
        inserted = decision
        insertedBufferedFrames = bufferedFrames
        lowFpsTicks = 0
        liveMonitor.reset()
        _status.value = EnhancementStatus(
            EnhancementStatus.Kind.Pending, "Starting",
            inputWidth = width, inputHeight = height,
            sourceFps = decision.sourceFps, outputFps = decision.targetFps,
        )
    }

    /** Activation check and live budget watch of the inserted filter. */
    private fun monitor() {
        val active = inserted ?: return
        if (bufferIncreaseRequested) return // the next evaluation re-inserts with more buffering
        if (!steady()) {
            // Pause, seek and buffering are not judged.
            lowFpsTicks = 0
            liveMonitor.reset()
            return
        }
        val vfFps = doubleProperty("estimated-vf-fps")
        val reached = vfFps != null && vfFps >= active.targetFps * 0.9
        lowFpsTicks = if (reached) 0 else lowFpsTicks + 1
        val status = _status.value
        if (status.kind != EnhancementStatus.Kind.Active) {
            if (reached) {
                _status.value = status.copy(kind = EnhancementStatus.Kind.Active, reason = "Interpolating")
            } else if (lowFpsTicks >= ACTIVATION_TICKS) {
                // vf_vapoursynth failed (mpv bypasses a failed filter) or never produced frames.
                failForStream("Interpolation did not start (output %.2f fps)".format(vfFps ?: 0.0))
            }
            return
        }
        if (lowFpsTicks >= STOPPED_TICKS) {
            // mpv disables a failed filter and passes the source frames through.
            failForStream("Interpolation stopped (output %.2f fps)".format(vfFps ?: 0.0))
            return
        }
        val sourceFrame = intProperty("estimated-frame-number")?.toLong() ?: return
        val outputFrame = (sourceFrame * active.targetFps / active.sourceFps).toLong()
        val drops = (intProperty("frame-drop-count") ?: 0) + (intProperty("decoder-frame-drop-count") ?: 0)
        val breach = liveMonitor.sample(outputFrame, drops.toLong(), steady = true) ?: return
        log.w { "budget breach class=${active.resolutionClass} target=${active.targetMilli}: $breach" }
        capability.recordBreach(active.resolutionClass, active.targetMilli)
        // The next evaluation sees the recorded breach and picks a lower divisor, or skips.
        decidedKey = null
        _status.value = status.copy(kind = EnhancementStatus.Kind.Pending, reason = "Lowering the target after frame drops ($breach)")
    }

    private fun failForStream(reason: String) {
        removeFilter()
        disabledForStream = true
        _status.value = _status.value.copy(kind = EnhancementStatus.Kind.Failed, reason = reason, outputFps = null)
    }

    private fun steady(): Boolean =
        stringProperty("core-idle").trim() == "no" && stringProperty("seeking").trim() == "no"

    private fun filterPresent(): Boolean = stringProperty("vf").contains(SvpRuntime.FILTER_LABEL)

    private fun removeFilter() {
        if (inserted == null && !filterPresent()) return
        command("vf", "remove", "@${SvpRuntime.FILTER_LABEL}")
        inserted = null
    }

    private fun writeScript(decision: SvpDecision.Interpolate): Path {
        val text = SvpPlanner.script(decision, runtime.flow1Plugin, runtime.flow2Plugin)
        val name = "svp-" + sha256(text).take(16) + ".vpy"
        val path = scriptDir.resolve(name)
        if (!Files.isRegularFile(path) || Files.readString(path) != text) {
            Files.createDirectories(scriptDir)
            val temp = Files.createTempFile(scriptDir, "svp-", ".tmp")
            Files.writeString(temp, text)
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }
        return path
    }

    private fun command(vararg args: String): Int = synchronized(nativeLock) {
        val current = handle
        if (current == 0L) return -1
        runCatching { NativePlayerBridge.command(current, arrayOf(*args)) }.getOrElse { error ->
            log.w(error) { "mpv command failed ${args.firstOrNull()}" }
            -1
        }
    }

    private fun stringProperty(name: String): String = synchronized(nativeLock) {
        val current = handle
        if (current == 0L) return ""
        runCatching { NativePlayerBridge.stringProperty(current, name) }.getOrDefault("")
    }

    private fun intProperty(name: String): Int? = stringProperty(name).trim().toDoubleOrNull()?.toInt()

    private fun doubleProperty(name: String): Double? =
        stringProperty(name).trim().toDoubleOrNull()?.takeIf { it > 0 }

    companion object {
        private const val DISPLAY_WAIT_MILLIS = 5_000L
        /** Steady ticks (250 ms) allowed to reach the target after insertion, and to lose it later. */
        private const val ACTIVATION_TICKS = 32
        private const val STOPPED_TICKS = 16

        private val sharedRuntime by lazy { SvpRuntime() }
        private val sharedCapability by lazy { SvpCapability(environment = { SvpCapability.defaultEnvironment(sharedRuntime) }) }

        fun defaultScriptDir(): Path {
            val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: Paths.get(System.getProperty("user.home"), "AppData", "Local").toString()
            return Paths.get(base, "NuvioDV", "SVP", "scripts")
        }

        internal fun ineligibleReason(state: AnimationToggleState): String = when {
            state.explicitValue == true -> "Animation is On"
            state.classification.animationEvidence == true -> "Title is classified as animation"
            else -> "Content type is unknown"
        }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
