package com.nuvio.app.features.playback.animejanai

import co.touchlab.kermit.Logger
import com.nuvio.app.features.playback.classification.AnimationToggleState
import com.nuvio.app.features.playback.classification.CanonicalTitle
import com.nuvio.app.features.playback.classification.DesktopAnimationToggles
import com.nuvio.app.features.player.ActivePlaybackTitle
import com.nuvio.app.features.player.AnimeJaNaiMode
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Start-time decision; [startOptions] is empty when mpv must start exactly as before. */
internal data class AnimeJaNaiStartPlan(
    val startOptions: List<String>,
    val slot: Int,
    val toggleState: StateFlow<AnimationToggleState>?,
)

/**
 * Per-player AnimeJaNai integration (spec P2/P4). One instance per native player controller; each
 * stream start calls [planStart] then [beginStream]. The filter is inserted only after the first
 * decoded video size is known and a prepared engine exists for exactly that slot/size/rate, because
 * `stream-mode=required` ends playback when its engine is missing. The stream is never reloaded.
 */
internal class AnimeJaNaiSession(
    private val runtime: AnimeJaNaiRuntime = AnimeJaNaiPreparationController.runtime,
    private val engineIndex: AnimeJaNaiEngineIndex = AnimeJaNaiPreparationController.engineIndex,
    private val budgets: AnimeJaNaiBudgets = AnimeJaNaiPreparationController.budgets,
) {
    private val log = Logger.withTag("AnimeJaNai")
    // Single worker thread that exits when idle, so idle players keep no thread alive.
    private val worker = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
        Thread(runnable, "nuvio-animejanai").apply { isDaemon = true }
    }
    private val evaluating = AtomicBoolean(false)
    private val playbackRegistered = AtomicBoolean(false)

    private val _status = MutableStateFlow(EnhancementStatus.Off)
    val status: StateFlow<EnhancementStatus> = _status.asStateFlow()

    /** Engine the current stream needs but that is not prepared yet (for a later preparation). */
    @Volatile
    var preparationRequiredKey: EngineKey? = null
        private set

    /** Stream of the current playback, reused by a later preparation for [preparationRequiredKey]. */
    @Volatile
    var preparationSource: PreparationSource? = null
        private set

    fun setPreparationSource(source: PreparationSource) {
        preparationSource = source
    }

    // Mutated on the worker thread only.
    @Volatile private var plan: AnimeJaNaiStartPlan? = null
    /** Live native handle; every native call holds [nativeLock] and re-checks it (dispose safety). */
    private val nativeLock = Any()
    private var handle: Long = 0L
    @Volatile private var decidedKey: EngineKey? = null
    /** Profile selection for [decidedKey]'s stream (preferred profile first). */
    @Volatile private var selectionKeys: List<EngineKey> = emptyList()
    /** Profile of the inserted filter; changes when the live downgrade switches profiles. */
    @Volatile private var activeKey: EngineKey? = null
    @Volatile private var filterInserted = false
    @Volatile private var skippedForOutput = false
    @Volatile private var animationEnabled = true
    /** Engines of the inserted filter, for recording a live budget breach. */
    @Volatile private var insertedEngines: List<EngineFile>? = null
    /** Set once a sustained budget breach disabled AnimeJaNai; it stays off for this stream. */
    @Volatile private var budgetDisabled = false
    private val liveMonitor = LiveBudgetMonitor()

    /**
     * Toggle state of a stream that started without AnimeJaNai only because the title was not marked
     * Animation; observing it reports a later On as pending for the next stream start.
     */
    @Volatile var deferredToggleState: StateFlow<AnimationToggleState>? = null
        private set

    suspend fun planStart(settings: PlayerSettingsUiState): AnimeJaNaiStartPlan? {
        deferredToggleState = null
        if (settings.animeJaNaiMode != AnimeJaNaiMode.Automatic) {
            _status.value = EnhancementStatus.Off
            return null
        }
        fun skip(reason: String): AnimeJaNaiStartPlan? {
            _status.value = EnhancementStatus(EnhancementStatus.Kind.Skipped, reason)
            return null
        }
        val title = ActivePlaybackTitle.current
        AnimeJaNaiPlanner.startSkipReason(
            AjnStartFacts(
                rtxSuperResolution = settings.nvidiaRtxSuperResolutionEnabled,
                softwareDecoding = settings.decoderPriority == 2,
                unavailableReason = (runtime.availability() as? AnimeJaNaiRuntime.Availability.Unavailable)?.reason,
                preparing = AnimeJaNaiPreparationController.isPreparing,
                installing = AnimeJaNaiPreparationController.installer.isInstalling,
                titleKnown = title != null,
            ),
        )?.let { return skip(it) }
        title!!
        val profileId = ProfileRepository.activeProfileId.toString()
        val toggles = DesktopAnimationToggles.repository
        val toggleState = runCatching {
            toggles.load(profileId)
            toggles.state(profileId, CanonicalTitle.of(title.parentMetaId, title.parentMetaType))
        }.getOrElse { error -> return skip("Animation toggle unavailable: ${error.message}") }
        if (!toggleState.value.eligibility.animeJaNai) {
            deferredToggleState = toggleState
            return skip("Title is not marked Animation")
        }
        animationEnabled = true
        return AnimeJaNaiStartPlan(runtime.playerStartOptions(), settings.animeJaNaiProfile.slot, toggleState)
    }

    /** Called with the plan used for the native create (null = ordinary start). */
    fun beginStream(startPlan: AnimeJaNaiStartPlan?) {
        worker.execute {
            plan = startPlan
            decidedKey = null
            activeKey = null
            selectionKeys = emptyList()
            filterInserted = false
            preparationRequiredKey = null
            insertedEngines = null
            budgetDisabled = false
            liveMonitor.reset()
            if (startPlan != null) {
                _status.value = EnhancementStatus(EnhancementStatus.Kind.Pending, "Waiting for video", slot = startPlan.slot)
            }
        }
    }

    /**
     * The controller accepted a created native player. Any live player (AnimeJaNai or not) blocks
     * engine preparation until it is detached.
     */
    fun attachHandle(nativeHandle: Long) {
        synchronized(nativeLock) { handle = nativeHandle }
        if (playbackRegistered.compareAndSet(false, true)) AnimeJaNaiPreparationController.onPlaybackStarted()
    }

    /**
     * Called before the native player is disposed (off the UI thread); waits for any in-flight
     * native call so no call can reach a freed handle.
     */
    fun detachHandle(nativeHandle: Long) {
        val detached = synchronized(nativeLock) {
            if (handle == nativeHandle) {
                handle = 0L
                filterInserted = false
                true
            } else {
                false
            }
        }
        if (detached) {
            // Offer the engine this stream needed for preparation once the player is closed.
            val key = preparationRequiredKey
            val source = preparationSource
            if (key != null && source != null) AnimeJaNaiPreparationController.rememberPending(key, source)
        }
        if (detached && playbackRegistered.compareAndSet(true, false)) {
            AnimeJaNaiPreparationController.onPlaybackEnded()
        }
    }

    /** Periodic hook; evaluates video reconfigs off the UI thread. */
    fun onTick() {
        if (plan == null) return
        if (!evaluating.compareAndSet(false, true)) return
        worker.execute {
            try {
                evaluate()
                monitorBudget()
            } catch (error: Throwable) {
                log.w(error) { "evaluation failed" }
            } finally {
                evaluating.set(false)
            }
        }
    }

    /** `mpvLog:` lines forwarded by the native bridge. */
    fun onLogLine(line: String) {
        AjnTiming.parse(line)?.let { timing ->
            log.i {
                "timing slot=${timing.slot} frames=${timing.frames} mean=${timing.meanMillis} " +
                    "p95=${timing.p95Millis} max=${timing.maxMillis} ms"
            }
            return
        }
        val parsed = AjnStreamStatus.parse(line) ?: return
        worker.execute { applyStreamStatus(parsed) }
    }

    /**
     * Live Animation toggle change (spec P2): switches the already-inserted, prepared filter with
     * `vf-command aji slot 0|n`; otherwise the change is pending until the next stream start.
     */
    fun applyAnimationToggle(enabled: Boolean) {
        worker.execute {
            val current = plan
            if (current == null) {
                // This stream did not start on the AnimeJaNai path (vulkan/nvdec); never reload it.
                _status.value = EnhancementStatus(
                    EnhancementStatus.Kind.Pending,
                    "Animation ${if (enabled) "On" else "Off"} applies at the next stream start",
                )
                return@execute
            }
            if (budgetDisabled) {
                _status.value = _status.value.copy(reason = "Off for this stream after sustained frame drops")
                return@execute
            }
            if (!filterInserted) {
                animationEnabled = enabled
                if (enabled) {
                    // Re-evaluate on the next tick: inserts live only if a prepared engine exists.
                    decidedKey = null
                } else {
                    _status.value = _status.value.copy(kind = EnhancementStatus.Kind.Skipped, reason = "Animation is Off")
                }
                return@execute
            }
            val slot = if (enabled) activeKey?.slot ?: current.slot else 0
            val result = command("vf-command", AnimeJaNaiRuntime.FILTER_LABEL, "slot", slot.toString())
            if (result < 0) {
                _status.value = _status.value.copy(reason = "Live switch failed; applies at the next stream start")
                return@execute
            }
            animationEnabled = enabled
            liveMonitor.reset()
            if (!enabled) {
                _status.value = _status.value.copy(kind = EnhancementStatus.Kind.Skipped, reason = "Animation is Off")
            } else {
                _status.value = _status.value.copy(kind = EnhancementStatus.Kind.Pending, reason = "Re-enabling")
            }
        }
    }

    /** Observes the title's Animation toggle and applies changes live (never reloads the stream). */
    suspend fun observeToggle(toggleState: StateFlow<AnimationToggleState>) {
        var last: Boolean? = null
        toggleState.collect { state ->
            val enabled = state.eligibility.animeJaNai
            if (last != null && last != enabled) applyAnimationToggle(enabled)
            last = enabled
        }
    }

    private fun evaluate() {
        val current = plan ?: return
        val width = intProperty("video-dec-params/w") ?: return
        val height = intProperty("video-dec-params/h") ?: return
        if (width <= 0 || height <= 0) return
        val fps = doubleProperty("container-fps") ?: doubleProperty("estimated-vf-fps") ?: 0.0
        val key = EngineKey.of(current.slot, width, height, fps)
        if (key == decidedKey) {
            // Only an output-size skip is re-evaluated for the same stream (e.g. leaving a small window).
            if (!skippedForOutput || !outputLargerThan(width, height)) return
        }
        if (filterInserted) {
            // Actual stream change: never keep a required-mode filter for an unverified size.
            command("vf", "remove", "@${AnimeJaNaiRuntime.FILTER_LABEL}")
            filterInserted = false
            insertedEngines = null
            activeKey = null
        }
        // A new stream size is a new budget decision.
        budgetDisabled = false
        liveMonitor.reset()
        decidedKey = key
        selectionKeys = AnimeJaNaiPreparationController.selectionKeys(key)
        preparationRequiredKey = null
        skippedForOutput = false

        fun skip(reason: String) {
            _status.value = EnhancementStatus(
                EnhancementStatus.Kind.Skipped, reason, current.slot, width, height,
            )
        }

        val facts = AjnStreamFacts(
            width = width,
            height = height,
            gamma = stringProperty("video-dec-params/gamma"),
            outputWidth = intProperty("osd-width") ?: 0,
            outputHeight = intProperty("osd-height") ?: 0,
            animationEnabled = animationEnabled,
        )
        val chosen = when (val decision = AnimeJaNaiPlanner.decide(selectionKeys, facts, engineIndex::find, budgets::find)) {
            is AjnStreamDecision.Skip -> {
                skippedForOutput = decision.forOutput
                return skip(decision.reason)
            }
            AjnStreamDecision.AwaitOutput -> {
                decidedKey = null // retry once the output size is known
                return
            }
            is AjnStreamDecision.PreparationRequired -> {
                // Preparation covers the whole selection of the preferred profile.
                preparationRequiredKey = key
                _status.value = EnhancementStatus(
                    EnhancementStatus.Kind.PreparationRequired, decision.reason, current.slot, width, height,
                )
                return
            }
            is AjnStreamDecision.Insert -> decision
        }
        val result = command("vf", "add", runtime.filter(chosen.key.slot, "required"))
        if (result < 0) {
            _status.value = EnhancementStatus(
                EnhancementStatus.Kind.Failed, "Filter insertion failed ($result)", chosen.key.slot, width, height,
            )
            return
        }
        filterInserted = true
        insertedEngines = chosen.engines
        activeKey = chosen.key
        _status.value = EnhancementStatus(
            EnhancementStatus.Kind.Pending, "Starting", chosen.key.slot, width, height,
            width * AnimeJaNaiRuntime.SCALE, height * AnimeJaNaiRuntime.SCALE,
        )
    }

    /**
     * Live budget check of an active filter. A sustained breach is recorded for the active profile at
     * this output size; playback then switches live to the next cheaper prepared, qualified profile,
     * or bypasses AnimeJaNai for the rest of the stream when there is none.
     */
    private fun monitorBudget() {
        if (!filterInserted || budgetDisabled || !animationEnabled) return
        if (_status.value.kind != EnhancementStatus.Kind.Active) return
        val frame = intProperty("estimated-frame-number")?.toLong() ?: return
        val drops = (intProperty("frame-drop-count") ?: 0) + (intProperty("decoder-frame-drop-count") ?: 0)
        val steady = stringProperty("core-idle").trim() == "no" && stringProperty("seeking").trim() == "no"
        val breach = liveMonitor.sample(frame, drops.toLong(), steady) ?: return
        val key = activeKey ?: return
        val outputClass = AnimeJaNaiBudgets.outputClass(intProperty("osd-height") ?: 0)
        log.w { "budget breach key=${key.storageKey} output=$outputClass: $breach" }
        insertedEngines?.let { budgets.recordLiveBreach(key, it, outputClass, breach) }
        val fallback = AnimeJaNaiPlanner.downgrade(selectionKeys, key, outputClass, engineIndex::find, budgets::find)
        if (fallback != null &&
            command("vf-command", AnimeJaNaiRuntime.FILTER_LABEL, "slot", fallback.key.slot.toString()) >= 0
        ) {
            log.i { "live downgrade ${key.slot} -> ${fallback.key.slot}" }
            activeKey = fallback.key
            insertedEngines = fallback.engines
            liveMonitor.reset()
            _status.value = _status.value.copy(
                kind = EnhancementStatus.Kind.Pending,
                reason = "Switched to a lighter profile after frame drops",
                slot = fallback.key.slot,
            )
            return
        }
        command("vf-command", AnimeJaNaiRuntime.FILTER_LABEL, "slot", "0")
        budgetDisabled = true
        _status.value = _status.value.copy(
            kind = EnhancementStatus.Kind.Skipped,
            reason = "Turned off for this stream: $breach",
        )
    }

    private fun applyStreamStatus(status: AjnStreamStatus) {
        if (plan == null) return
        val base = _status.value
        val kind: EnhancementStatus.Kind
        val reason: String?
        when (status.state) {
            "active" -> { kind = EnhancementStatus.Kind.Active; reason = "${status.backend} active" }
            "bypass" -> {
                kind = EnhancementStatus.Kind.Skipped
                // Keep the reason of our own bypass (budget breach or Animation Off).
                reason = when {
                    budgetDisabled -> base.reason
                    !animationEnabled -> "Animation is Off"
                    else -> "Bypassed"
                }
            }
            "building" -> { kind = EnhancementStatus.Kind.Pending; reason = "Building" }
            else -> {
                kind = EnhancementStatus.Kind.Failed
                reason = "AnimeJaNai ${status.state}"
                if (status.state == "engineMissing" || status.state == "engineIncompatible") {
                    (activeKey ?: decidedKey)?.let(engineIndex::remove)
                }
            }
        }
        _status.value = base.copy(
            kind = kind,
            reason = reason,
            slot = status.slot ?: base.slot,
            inputWidth = status.inWidth ?: base.inputWidth,
            inputHeight = status.inHeight ?: base.inputHeight,
            outputWidth = status.outWidth ?: base.outputWidth,
            outputHeight = status.outHeight ?: base.outputHeight,
        )
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

    private fun outputLargerThan(width: Int, height: Int): Boolean {
        val outputWidth = intProperty("osd-width") ?: return false
        val outputHeight = intProperty("osd-height") ?: return false
        return minOf(outputWidth.toDouble() / width, outputHeight.toDouble() / height) > 1.0
    }

    private fun intProperty(name: String): Int? =
        stringProperty(name).trim().toDoubleOrNull()?.toInt()

    private fun doubleProperty(name: String): Double? =
        stringProperty(name).trim().toDoubleOrNull()?.takeIf { it > 0 }

}
