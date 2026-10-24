package com.nuvio.app.features.rpdb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.watchprogress.ContinueWatchingItem

internal fun ContinueWatchingItem.resolveRpdbPortraitSelection(
    sourcePoster: String?,
    settings: RpdbSettings,
): RpdbPosterSelection {
    val normalizedSource = sourcePoster?.trim()?.takeIf(String::isNotBlank)
    val normalizedEpisodeThumbnail = episodeThumbnail?.trim()?.takeIf(String::isNotBlank)
    if (normalizedSource != null && normalizedSource == normalizedEpisodeThumbnail) {
        return RpdbPosterSelection(primaryUrl = sourcePoster)
    }
    return RpdbPosterResolver.resolve(parentMetaId, parentMetaType, PosterShape.Poster, sourcePoster, settings)
}

@Composable
internal fun ContinueWatchingItem.rpdbPortraitSelection(sourcePoster: String?): RpdbPosterSelection {
    RpdbSettingsRepository.ensureLoaded()
    val settings by RpdbSettingsRepository.uiState.collectAsState()
    return resolveRpdbPortraitSelection(sourcePoster, settings)
}
