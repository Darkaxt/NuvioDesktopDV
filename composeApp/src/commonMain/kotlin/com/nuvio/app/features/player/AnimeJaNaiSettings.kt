package com.nuvio.app.features.player

/** Global AnimeJaNai switch (spec P4). Windows-only; machine-local and never cloud-synced. */
enum class AnimeJaNaiMode(val storageValue: String) {
    Off("off"),
    Automatic("automatic");

    companion object {
        fun fromStorage(value: String?): AnimeJaNaiMode =
            entries.firstOrNull { it.storageValue == value } ?: Off
    }
}

/**
 * Reviewed AnimeJaNai quality profiles, mapped to the upstream built-in profile slots of the
 * owned filter. Only the upstream default slot (1002, HD V3.1 Balanced) is mapped: the other
 * built-in slot numbers are not documented in the pinned runtime package, so no guessed slot
 * is exposed.
 */
enum class AnimeJaNaiProfile(val storageValue: String, val slot: Int, val label: String) {
    Balanced("balanced", 1002, "Balanced (HD V3.1)");

    companion object {
        val Default: AnimeJaNaiProfile = Balanced

        fun fromStorage(value: String?): AnimeJaNaiProfile =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

/** Currently playing parent title, published by the player screen for platform enhancement logic. */
object ActivePlaybackTitle {
    data class Value(val parentMetaId: String, val parentMetaType: String)

    @kotlin.concurrent.Volatile
    var current: Value? = null
}
