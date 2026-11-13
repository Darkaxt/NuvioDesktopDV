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

    data class PreparationRequired(val reason: String) : AjnStreamDecision

    data class Insert(val engines: List<EngineFile>) : AjnStreamDecision
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

    fun decide(
        key: EngineKey,
        facts: AjnStreamFacts,
        engines: (EngineKey) -> List<EngineFile>?,
        budget: (EngineKey, List<EngineFile>) -> AnimeJaNaiBudgets.Entry?,
    ): AjnStreamDecision {
        val longSide = maxOf(facts.width, facts.height)
        val shortSide = minOf(facts.width, facts.height)
        if (longSide >= 3840 || shortSide >= 2160) return AjnStreamDecision.Skip("Source is 4K or larger")
        val gamma = facts.gamma.trim().lowercase()
        if (gamma == "pq" || gamma == "hlg") return AjnStreamDecision.Skip("HDR/Dolby Vision is not verified for AnimeJaNai")
        if (facts.outputWidth <= 0 || facts.outputHeight <= 0) return AjnStreamDecision.AwaitOutput
        val scale = minOf(facts.outputWidth.toDouble() / facts.width, facts.outputHeight.toDouble() / facts.height)
        if (scale <= 1.0) return AjnStreamDecision.Skip("Output is not larger than the source", forOutput = true)
        if (!facts.animationEnabled) return AjnStreamDecision.Skip("Animation is Off")
        val prepared = engines(key)
            ?: return AjnStreamDecision.PreparationRequired(
                "No prepared engine for ${facts.width}x${facts.height} at ${key.fps} fps",
            )
        val entry = budget(key, prepared)
            ?: return AjnStreamDecision.PreparationRequired(
                "Performance for ${facts.width}x${facts.height} at ${key.fps} fps is not measured yet",
            )
        if (!entry.qualified) return AjnStreamDecision.Skip("Over the performance budget (${entry.describe()})")
        entry.liveBreaches[AnimeJaNaiBudgets.outputClass(facts.outputHeight)]?.let { breach ->
            return AjnStreamDecision.Skip("Off at this output size after frame drops ($breach)")
        }
        return AjnStreamDecision.Insert(prepared)
    }
}
