package com.nuvio.app.features.rpdb

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object RpdbSettingsStorage {
    private const val enabledKey = "rpdb_enabled"
    private const val apiKeyKey = "rpdb_api_key"
    private val store = DesktopStorage.store("nuvio_rpdb_settings")

    actual fun loadEnabled(): Boolean? = store.getBoolean(ProfileScopedKey.of(enabledKey))

    actual fun saveEnabled(enabled: Boolean) {
        store.putBoolean(ProfileScopedKey.of(enabledKey), enabled)
    }

    actual fun loadApiKey(): String? = store.getString(ProfileScopedKey.of(apiKeyKey))

    actual fun saveApiKey(apiKey: String) {
        store.putString(ProfileScopedKey.of(apiKeyKey), apiKey.trim())
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        store.remove(ProfileScopedKey.of(enabledKey))
        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
    }
}
