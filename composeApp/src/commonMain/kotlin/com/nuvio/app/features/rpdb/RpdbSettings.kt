package com.nuvio.app.features.rpdb

data class RpdbSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
) {
    val effectiveApiKey: String
        get() = apiKey.trim().ifBlank { RPDB_PUBLIC_API_KEY }

    val usesPublicApiKey: Boolean
        get() = apiKey.isBlank()
}

internal const val RPDB_PUBLIC_API_KEY = "t0-free-rpdb"
