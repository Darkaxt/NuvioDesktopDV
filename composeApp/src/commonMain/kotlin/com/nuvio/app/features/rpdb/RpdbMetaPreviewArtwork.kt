package com.nuvio.app.features.rpdb

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.features.home.MetaPreview

@Composable
fun MetaPreview.rpdbPosterSelection(): RpdbPosterSelection {
    RpdbSettingsRepository.ensureLoaded()
    val settings by RpdbSettingsRepository.uiState.collectAsState()
    return RpdbPosterResolver.resolve(id, type, posterShape, poster, settings)
}
