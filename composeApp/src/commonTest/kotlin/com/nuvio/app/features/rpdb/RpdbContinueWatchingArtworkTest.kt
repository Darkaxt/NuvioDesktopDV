package com.nuvio.app.features.rpdb

import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RpdbContinueWatchingArtworkTest {
    @Test
    fun seriesPortraitUsesRpdbAndPreservesSourceFallback() {
        val selection = item("tt0903747", "series").resolveRpdbPortraitSelection(
            sourcePoster = "source.jpg",
            settings = RpdbSettings(enabled = true),
        )
        assertEquals(
            "https://api.ratingposterdb.com/t0-free-rpdb/imdb/poster-default/tt0903747.jpg",
            selection.primaryUrl,
        )
        assertEquals("source.jpg", selection.fallbackUrl)
    }

    @Test
    fun episodeThumbnailKeepsPriorityOverRpdb() {
        val item = item("tt0903747", "series").copy(episodeThumbnail = "episode.jpg")
        val selection = item.resolveRpdbPortraitSelection("episode.jpg", RpdbSettings(enabled = true))
        assertEquals("episode.jpg", selection.primaryUrl)
        assertNull(selection.fallbackUrl)
    }

    private fun item(id: String, type: String) = ContinueWatchingItem(
        parentMetaId = id,
        parentMetaType = type,
        videoId = id,
        title = "Title",
        subtitle = "",
        imageUrl = "source.jpg",
        poster = "source.jpg",
        resumePositionMs = 1L,
        durationMs = 2L,
        progressFraction = 0.5f,
    )
}
