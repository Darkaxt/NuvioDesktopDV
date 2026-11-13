package com.nuvio.app.features.player

import com.nuvio.app.features.playback.svp.SvpRuntime

internal actual fun platformSvpInstallationSummary(): String? {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return null
    return when (val availability = SvpRuntime().availability()) {
        is SvpRuntime.Availability.Unavailable -> availability.reason
        is SvpRuntime.Availability.Available -> {
            val versions = availability.versions
            listOfNotNull(
                versions["core.full"]?.let { "SVP $it" },
                versions["core.flow64vs"]?.let { "SVPflow $it" },
                versions["opt.vps_portable64"]?.let { "VapourSynth package $it" },
            ).joinToString(" · ").ifBlank { "SVP 4 found" }
        }
    }
}
