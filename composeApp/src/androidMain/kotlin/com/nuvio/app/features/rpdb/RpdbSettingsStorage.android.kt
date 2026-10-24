package com.nuvio.app.features.rpdb

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object RpdbSettingsStorage {
    private const val preferencesName = "nuvio_rpdb_settings"
    private const val enabledKey = "rpdb_enabled"
    private const val apiKeyKey = "rpdb_api_key"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadEnabled(): Boolean? = preferences?.let { values ->
        val key = ProfileScopedKey.of(enabledKey)
        if (values.contains(key)) values.getBoolean(key, false) else null
    }

    actual fun saveEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(ProfileScopedKey.of(enabledKey), enabled)?.apply()
    }

    actual fun loadApiKey(): String? =
        preferences?.getString(ProfileScopedKey.of(apiKeyKey), null)

    actual fun saveApiKey(apiKey: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(apiKeyKey), apiKey.trim())?.apply()
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadEnabled()?.let { put(enabledKey, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.remove(ProfileScopedKey.of(enabledKey))?.apply()
        payload.decodeSyncBoolean(enabledKey)?.let(::saveEnabled)
    }
}
