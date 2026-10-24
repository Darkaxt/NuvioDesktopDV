package com.nuvio.app.features.rpdb

import com.nuvio.app.features.home.PosterShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RpdbPosterResolverTest {
    @Test
    fun disabledIntegrationKeepsSourcePoster() {
        val selection = RpdbPosterResolver.resolve(
            "tt0133093", "movie", PosterShape.Poster, "source.jpg", RpdbSettings(enabled = false),
        )
        assertEquals("source.jpg", selection.primaryUrl)
        assertNull(selection.fallbackUrl)
    }

    @Test
    fun blankCustomKeyUsesPublicKeyAndKeepsFallback() {
        val selection = RpdbPosterResolver.resolve(
            "tt0133093", "movie", PosterShape.Poster, "source.jpg", RpdbSettings(enabled = true),
        )
        assertEquals(
            "https://api.ratingposterdb.com/t0-free-rpdb/imdb/poster-default/tt0133093.jpg",
            selection.primaryUrl,
        )
        assertEquals("source.jpg", selection.fallbackUrl)
    }

    @Test
    fun customKeyUsesTmdbSeriesEndpoint() {
        val selection = RpdbPosterResolver.resolve(
            "tmdb:1399", "series", PosterShape.Poster, "source.jpg",
            RpdbSettings(enabled = true, apiKey = "custom-key"),
        )
        assertEquals(
            "https://api.ratingposterdb.com/custom-key/tmdb/poster-default/series-1399.jpg",
            selection.primaryUrl,
        )
        assertEquals("source.jpg", selection.fallbackUrl)
    }

    @Test
    fun landscapeAndUnsupportedIdsRemainUnchanged() {
        val settings = RpdbSettings(enabled = true)
        val landscape = RpdbPosterResolver.resolve(
            "tt0133093", "movie", PosterShape.Landscape, "landscape.jpg", settings,
        )
        val unsupported = RpdbPosterResolver.resolve(
            "kitsu:1", "series", PosterShape.Poster, "anime.jpg", settings,
        )
        assertEquals("landscape.jpg", landscape.primaryUrl)
        assertNull(landscape.fallbackUrl)
        assertEquals("anime.jpg", unsupported.primaryUrl)
        assertNull(unsupported.fallbackUrl)
    }
}
