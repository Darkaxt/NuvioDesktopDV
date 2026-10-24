package com.nuvio.app.features.rpdb

import com.nuvio.app.features.home.PosterShape

data class RpdbPosterSelection(
    val primaryUrl: String?,
    val fallbackUrl: String? = null,
)

object RpdbPosterResolver {
    private const val API_BASE_URL = "https://api.ratingposterdb.com"
    private val imdbIdPattern = Regex("^tt\\d+$", RegexOption.IGNORE_CASE)
    private val tmdbIdPattern = Regex("^tmdb:(\\d+)$", RegexOption.IGNORE_CASE)

    fun resolve(
        id: String,
        type: String,
        posterShape: PosterShape,
        sourcePoster: String?,
        settings: RpdbSettings,
    ): RpdbPosterSelection {
        if (!settings.enabled || posterShape != PosterShape.Poster) return sourceSelection(sourcePoster)

        val mediaType = type.toRpdbMediaType() ?: return sourceSelection(sourcePoster)
        val normalizedId = id.trim()
        val relativePath = when {
            imdbIdPattern.matches(normalizedId) ->
                "imdb/poster-default/${normalizedId.lowercase()}.jpg"

            normalizedId.startsWith("imdb:", ignoreCase = true) &&
                imdbIdPattern.matches(normalizedId.substringAfter(':')) ->
                "imdb/poster-default/${normalizedId.substringAfter(':').lowercase()}.jpg"

            else -> tmdbIdPattern.matchEntire(normalizedId)?.groupValues?.getOrNull(1)?.let { tmdbId ->
                "tmdb/poster-default/$mediaType-$tmdbId.jpg"
            }
        } ?: return sourceSelection(sourcePoster)

        val rpdbUrl = "$API_BASE_URL/${settings.effectiveApiKey}/$relativePath"
        return RpdbPosterSelection(
            primaryUrl = rpdbUrl,
            fallbackUrl = sourcePoster?.takeIf { it.isNotBlank() && it != rpdbUrl },
        )
    }

    private fun sourceSelection(sourcePoster: String?) = RpdbPosterSelection(primaryUrl = sourcePoster)

    private fun String.toRpdbMediaType(): String? = when (trim().lowercase()) {
        "movie", "film" -> "movie"
        "series", "show", "tv" -> "series"
        else -> null
    }
}
