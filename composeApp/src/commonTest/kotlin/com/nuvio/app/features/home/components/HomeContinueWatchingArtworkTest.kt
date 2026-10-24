package com.nuvio.app.features.home.components

import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeContinueWatchingArtworkTest {
    @Test
    fun wideArtworkPreservesEpisodeThumbnailPreference() {
        val item = item(progressFraction = 0.5f)

        assertEquals("thumb.jpg", item.continueWatchingWideArtworkUrl(useEpisodeThumbnails = true))
        assertEquals("poster.jpg", item.continueWatchingWideArtworkUrl(useEpisodeThumbnails = false))
        assertEquals(
            "poster.jpg",
            item.copy(episodeThumbnail = null).continueWatchingWideArtworkUrl(useEpisodeThumbnails = true),
        )
    }

    @Test
    fun wideArtworkUnwrapsPosterCacheFallbackAfterSelectingEpisodeThumbnail() {
        val posterCacheUrl =
            "https://meta.remaxku.eu/poster-cache/episode.jpg" +
                "?url=https%3A%2F%2Fexample.invalid%2Fepisode.jpg" +
                "&fallback=https%3A%2F%2Fimage.tmdb.org%2Ft%2Fp%2Fw780%2Fepisode.jpg" +
                "&sig=invalid"
        val item = item(progressFraction = 0.5f).copy(episodeThumbnail = posterCacheUrl)

        assertEquals(
            "https://image.tmdb.org/t/p/w780/episode.jpg",
            item.continueWatchingWideArtworkUrl(useEpisodeThumbnails = true),
        )
        assertEquals(
            "poster.jpg",
            item.continueWatchingWideArtworkUrl(useEpisodeThumbnails = false),
        )
    }

    @Test
    fun unwrappedEpisodeThumbnailRetainsUnwatchedBlurBehavior() {
        val posterCacheUrl =
            "https://meta.remaxku.eu/poster-cache/episode.jpg" +
                "?fallback=https%3A%2F%2Fimage.tmdb.org%2Ft%2Fp%2Fw780%2Fepisode.jpg"
        val item = item(progressFraction = 0.5f).copy(episodeThumbnail = posterCacheUrl)
        val selectedArtwork = item.continueWatchingWideArtworkUrl(useEpisodeThumbnails = true)

        assertTrue(
            item.shouldBlurContinueWatchingArtwork(
                blurUnwatchedEpisodes = true,
                useEpisodeThumbnails = true,
                artworkUrl = selectedArtwork,
            ),
        )
    }

    @Test
    fun inProgressEpisodeThumbnailRemainsBlurredUntilWatched() {
        val item = item(progressFraction = 0.5f)

        assertTrue(
            item.shouldBlurContinueWatchingArtwork(
                blurUnwatchedEpisodes = true,
                useEpisodeThumbnails = true,
                artworkUrl = "thumb.jpg",
            ),
        )
    }

    @Test
    fun completedEpisodeThumbnailIsNotBlurred() {
        val item = item(progressFraction = 0.9f)

        assertFalse(
            item.shouldBlurContinueWatchingArtwork(
                blurUnwatchedEpisodes = true,
                useEpisodeThumbnails = true,
                artworkUrl = "thumb.jpg",
            ),
        )
    }

    @Test
    fun fallbackArtworkIsNotBlurred() {
        val item = item(progressFraction = 0.5f)

        assertFalse(
            item.shouldBlurContinueWatchingArtwork(
                blurUnwatchedEpisodes = true,
                useEpisodeThumbnails = true,
                artworkUrl = "backdrop.jpg",
            ),
        )
    }

    private fun item(progressFraction: Float) = ContinueWatchingItem(
        parentMetaId = "show",
        parentMetaType = "series",
        videoId = "show:1:1",
        title = "Show",
        subtitle = "S1 E1",
        imageUrl = "thumb.jpg",
        poster = "poster.jpg",
        background = "backdrop.jpg",
        seasonNumber = 1,
        episodeNumber = 1,
        episodeThumbnail = "thumb.jpg",
        resumePositionMs = 300_000L,
        durationMs = 600_000L,
        progressFraction = progressFraction,
    )
}
