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
 * Reviewed AnimeJaNai quality profiles, mapped to the built-in profile slots of the pinned
 * AnimeJaNai inference runtime (`aji_conf.cpp`): 1002 = HD V3.1 Balanced, 1003 = HD V3.1
 * Performance (SD sources use the SD Compact model in both). Slot 1001 "Quality" is not exposed:
 * it uses the same Balanced HD model plus 60 fps chains, so it adds no cheaper or better step.
 *
 * [fallbackSlot] is the cheaper prepared profile automatic selection and the live downgrade may
 * use when this one is over budget or drops frames; null means AnimeJaNai turns off instead.
 */
enum class AnimeJaNaiProfile(val storageValue: String, val slot: Int, val label: String, val fallbackSlot: Int?) {
    Balanced("balanced", 1002, "Balanced (HD V3.1)", fallbackSlot = 1003),
    Performance("performance", 1003, "Performance (HD V3.1)", fallbackSlot = null);

    companion object {
        val Default: AnimeJaNaiProfile = Balanced

        fun fromStorage(value: String?): AnimeJaNaiProfile =
            entries.firstOrNull { it.storageValue == value } ?: Default

        fun ofSlot(slot: Int): AnimeJaNaiProfile? = entries.firstOrNull { it.slot == slot }

        /** [slot] followed by its cheaper fallbacks, in selection order. */
        fun selectionSlots(slot: Int): List<Int> = buildList {
            var next: Int? = slot
            while (next != null && next !in this) {
                add(next)
                next = ofSlot(next)?.fallbackSlot
            }
        }
    }
}

/** Currently playing parent title, published by the player screen for platform enhancement logic. */
object ActivePlaybackTitle {
    data class Value(val parentMetaId: String, val parentMetaType: String)

    @kotlin.concurrent.Volatile
    var current: Value? = null
}
