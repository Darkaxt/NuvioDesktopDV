package com.nuvio.app.features.rpdb

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

internal actual object RpdbSettingsStorage {
    private const val enabledKey = "rpdb_enabled"
    private const val apiKeyKey = "rpdb_api_key"

    actual fun loadEnabled(): Boolean? {
        val key = ProfileScopedKey.of(enabledKey)
        return if (NSUserDefaults.standardUserDefaults.objectForKey(key) != null) {
            NSUserDefaults.standardUserDefaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(enabledKey))
    }

    actual fun loadApiKey(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(apiKeyKey))

    actual fun saveApiKey(apiKey: String) {
        NSUserDefaults.standardUserDefaults.setObject(apiKey.trim(), forKey = ProfileScopedKey.of(apiKeyKey))
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(enabledKey))
        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
    }
}
