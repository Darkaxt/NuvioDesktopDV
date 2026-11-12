package com.nuvio.app.features.playback.classification

import com.nuvio.app.features.player.desktop.DesktopHostOs

/** AnimeJaNai/SVP enhancements are Windows-only, so the toggle is shown only there. */
internal actual fun platformAnimationToggleRepository(): AnimationToggleRepository? =
    if (DesktopHostOs.current == DesktopHostOs.WINDOWS) DesktopAnimationToggles.repository else null
