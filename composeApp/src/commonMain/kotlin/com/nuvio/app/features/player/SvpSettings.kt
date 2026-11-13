package com.nuvio.app.features.player

/** Global SVP interpolation switch (spec P5). Windows-only; machine-local and never cloud-synced. */
enum class SvpMode(val storageValue: String) {
    Off("off"),
    Automatic("automatic");

    companion object {
        fun fromStorage(value: String?): SvpMode =
            entries.firstOrNull { it.storageValue == value } ?: Off
    }
}

/** Installed SVP summary for the settings screen; null where SVP is not supported. */
internal expect fun platformSvpInstallationSummary(): String?
