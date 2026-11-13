package com.nuvio.app.features.playback.animejanai

/** Machine/session facts checked before a stream starts (spec P4). */
internal data class AjnStartFacts(
    val rtxSuperResolution: Boolean,
    val softwareDecoding: Boolean,
    val unavailableReason: String?,
    val preparing: Boolean,
    val installing: Boolean,
    val titleKnown: Boolean,
)

/** Measured stream facts the per-stream decision is derived from. */
internal data class AjnStreamFacts(
    val width: Int,
    val height: Int,
    val gamma: String,
    /** Drawable output size; zero while unknown. */
    val outputWidth: Int,
    val outputHeight: Int,
    val animationEnabled: Boolean,
)

internal sealed interface AjnStreamDecision {
    data class Skip(val reason: String, val forOutput: Boolean = false) : AjnStreamDecision

    /** The output size is not known yet; decide again later. */
    data object AwaitOutput : AjnStreamDecision

    /** [key] is the first profile of the selection that still needs preparing or measuring. */
    data class PreparationRequired(val reason: String, val key: EngineKey) : AjnStreamDecision

    /** Insert the filter with [key]'s profile, the first prepared and qualified one of the selection. */
    data class Insert(val key: EngineKey, val engines: List<EngineFile>) : AjnStreamDecision
}

/** Pure AnimeJaNai decisions, kept separate from the native session for regression tests (spec P6). */
internal object AnimeJaNaiPlanner {
    /** Reason the stream must start without AnimeJaNai before any title check, or null. */
    fun startSkipReason(facts: AjnStartFacts): String? = when {
        // Never stacked with the upstream RTX VSR setting.
        facts.rtxSuperResolution -> "RTX Video Super Resolution is on"
        facts.softwareDecoding -> "Software decoding is selected"
        facts.unavailableReason != null -> facts.unavailableReason
        // Ordinary playback continues while a preparation or install owns the GPU.
        facts.preparing -> "An engine preparation is running"
        facts.installing -> "The AnimeJaNai runtime is being installed"
        !facts.titleKnown -> "Title is unknown"
        else -> null
    }

    /**
     * Chooses among the prepared, qualified profiles of [keys] (the preferred profile first, then its
     * cheaper fallbacks, all for the same stream). A profile is usable when its engine is prepared,
     * its benchmark is within budget and playback has not breached the budget at this output size.
     * When none is usable, the first unprepared or unmeasured profile is reported for preparation;
     * otherwise the stream is skipped with the preferred profile's reason.
     */
    fun decide(
        keys: List<EngineKey>,
        facts: AjnStreamFacts,
        engines: (EngineKey) -> List<EngineFile>?,
        budget: (EngineKey, List<EngineFile>) -> AnimeJaNaiBudgets.Entry?,
    ): AjnStreamDecision {
        require(keys.isNotEmpty()) { "No AnimeJaNai profile to select" }
        val longSide = maxOf(facts.width, facts.height)
        val shortSide = minOf(facts.width, facts.height)
        if (longSide >= 3840 || shortSide >= 2160) return AjnStreamDecision.Skip("Source is 4K or larger")
        val gamma = facts.gamma.trim().lowercase()
        if (gamma == "pq" || gamma == "hlg") return AjnStreamDecision.Skip("HDR/Dolby Vision is not verified for AnimeJaNai")
        if (facts.outputWidth <= 0 || facts.outputHeight <= 0) return AjnStreamDecision.AwaitOutput
        val scale = minOf(facts.outputWidth.toDouble() / facts.width, facts.outputHeight.toDouble() / facts.height)
        if (scale <= 1.0) return AjnStreamDecision.Skip("Output is not larger than the source", forOutput = true)
        if (!facts.animationEnabled) return AjnStreamDecision.Skip("Animation is Off")
        val outputClass = AnimeJaNaiBudgets.outputClass(facts.outputHeight)
        var preparation: AjnStreamDecision.PreparationRequired? = null
        var skipReason: String? = null
        for (key in keys) {
            val prepared = engines(key)
            if (prepared == null) {
                preparation = preparation ?: AjnStreamDecision.PreparationRequired(
                    "No prepared engine for ${facts.width}x${facts.height} at ${key.fps} fps", key,
                )
                continue
            }
            val entry = budget(key, prepared)
            if (entry == null) {
                preparation = preparation ?: AjnStreamDecision.PreparationRequired(
                    "Performance for ${facts.width}x${facts.height} at ${key.fps} fps is not measured yet", key,
                )
                continue
            }
            if (!entry.qualified) {
                skipReason = skipReason ?: "Over the performance budget (${entry.describe()})"
                continue
            }
            val breach = entry.liveBreaches[outputClass]
            if (breach != null) {
                skipReason = skipReason ?: "Off at this output size after frame drops ($breach)"
                continue
            }
            return AjnStreamDecision.Insert(key, prepared)
        }
        return preparation ?: AjnStreamDecision.Skip(skipReason ?: "No usable AnimeJaNai profile")
    }

    /**
     * Live downgrade target after a budget breach of [active] at [outputClass]: the next profile of
     * [keys] after [active] that is prepared, qualified and not breached at that output size.
     */
    fun downgrade(
        keys: List<EngineKey>,
        active: EngineKey,
        outputClass: String,
        engines: (EngineKey) -> List<EngineFile>?,
        budget: (EngineKey, List<EngineFile>) -> AnimeJaNaiBudgets.Entry?,
    ): AjnStreamDecision.Insert? {
        val index = keys.indexOf(active)
        if (index < 0) return null
        for (key in keys.drop(index + 1)) {
            val prepared = engines(key) ?: continue
            val entry = budget(key, prepared) ?: continue
            if (entry.qualified && outputClass !in entry.liveBreaches) return AjnStreamDecision.Insert(key, prepared)
        }
        return null
    }
}
