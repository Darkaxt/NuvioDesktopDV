package com.nuvio.app.features.playback.animejanai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Enhancement status for the player panel. */
data class EnhancementStatus(
    val kind: Kind,
    val reason: String? = null,
    val slot: Int? = null,
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
    val outputWidth: Int? = null,
    val outputHeight: Int? = null,
) {
    enum class Kind { Off, Pending, Active, Skipped, PreparationRequired, Failed }

    companion object {
        val Off = EnhancementStatus(Kind.Off)
    }
}

/** Status of the currently attached player's enhancement session; null when none is attached. */
object ActivePlaybackEnhancement {
    val status = MutableStateFlow<StateFlow<EnhancementStatus>?>(null)
}
