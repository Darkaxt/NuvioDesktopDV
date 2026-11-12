package com.nuvio.app.features.playback.animejanai

import kotlinx.coroutines.flow.StateFlow

/** Settings view of the AnimeJaNai runtime and engine preparation (Windows only). */
data class AnimeJaNaiRuntimeUiState(
    /** Runtime is installed and complete. */
    val installed: Boolean = false,
    /** Why the runtime is unusable (null when installed). */
    val unavailableReason: String? = null,
    /** Running install/preparation step, e.g. "Downloading TensorRT trt-runtime 120/167 MB". */
    val activity: String? = null,
    /** Last install/preparation failure. */
    val error: String? = null,
    /** A closed playback needed an engine; e.g. "960x540 at 24 fps (Balanced) for Example". */
    val pendingPreparation: String? = null,
    /** Last successfully prepared engine, for confirmation. */
    val lastPrepared: String? = null,
)

interface AnimeJaNaiRuntimeControls {
    val state: StateFlow<AnimeJaNaiRuntimeUiState>

    /** Starts the pinned runtime download/installation; returns a refusal reason or null. */
    fun install(): String?

    /** Prepares the engine the last closed playback needed, from that stream; refusal reason or null. */
    fun preparePending(): String?

    fun cancelPreparation()
}

/** Null where the platform has no AnimeJaNai path. */
internal expect fun platformAnimeJaNaiRuntimeControls(): AnimeJaNaiRuntimeControls?
