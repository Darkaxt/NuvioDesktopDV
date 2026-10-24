package com.nuvio.app.features.rpdb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RpdbSettingsRepository {
    private val _uiState = MutableStateFlow(RpdbSettings())
    val uiState: StateFlow<RpdbSettings> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var enabled = false
    private var apiKey = ""

    fun ensureLoaded() {
        if (!hasLoaded) loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun snapshot(): RpdbSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        if (enabled == value) return
        enabled = value
        publish()
        RpdbSettingsStorage.saveEnabled(value)
    }

    fun setApiKey(value: String) {
        ensureLoaded()
        val normalized = value.trim()
        if (apiKey == normalized) return
        apiKey = normalized
        publish()
        RpdbSettingsStorage.saveApiKey(normalized)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        enabled = RpdbSettingsStorage.loadEnabled() ?: false
        apiKey = RpdbSettingsStorage.loadApiKey().orEmpty().trim()
        publish()
    }

    private fun publish() {
        _uiState.value = RpdbSettings(enabled = enabled, apiKey = apiKey)
    }
}
