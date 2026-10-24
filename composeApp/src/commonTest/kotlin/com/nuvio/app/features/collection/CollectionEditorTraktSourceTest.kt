package com.nuvio.app.features.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionEditorTraktSourceTest {
    @Test
    fun recommendationsCanAddMovieAndSeriesWithoutListIds() {
        val sources = buildTraktAccountSources(
            sourceType = TraktCollectionSourceType.RECOMMENDATIONS,
            mediaType = TmdbCollectionMediaType.MOVIE,
            mediaBoth = true,
            title = "Recommended",
            calendarDays = 1,
            moviesSuffix = "Movies",
            seriesSuffix = "Series",
        )

        assertEquals(listOf("movie", "tv"), sources.map { it.mediaType })
        assertEquals(listOf("Recommended Movies", "Recommended Series"), sources.map { it.title })
        assertTrue(sources.all { it.traktSourceType == TraktCollectionSourceType.RECOMMENDATIONS.value })
        assertTrue(sources.all { it.traktListId == null })
        assertTrue(sources.none(CollectionSource::hasInvalidTraktListId))
    }

    @Test
    fun seriesOnlyAccountSourcesIgnoreMovieAndBothSelections() {
        val sources = buildTraktAccountSources(
            sourceType = TraktCollectionSourceType.UP_NEXT,
            mediaType = TmdbCollectionMediaType.MOVIE,
            mediaBoth = true,
            title = "Up Next",
            calendarDays = 1,
            moviesSuffix = "Movies",
            seriesSuffix = "Series",
        )

        assertEquals(1, sources.size)
        assertEquals("tv", sources.single().mediaType)
        assertEquals("Up Next", sources.single().title)
    }

    @Test
    fun calendarEditorSelectionClampsSupportedWindow() {
        val source = buildTraktAccountSources(
            sourceType = TraktCollectionSourceType.CALENDAR,
            mediaType = TmdbCollectionMediaType.TV,
            mediaBoth = false,
            title = "Calendar",
            calendarDays = 30,
            moviesSuffix = "Movies",
            seriesSuffix = "Series",
        ).single()

        assertEquals(7, source.calendarDays)
        assertEquals(7, source.toTraktEditorSelection().calendarDays)
    }

    @Test
    fun editorSelectionDistinguishesAccountCataloguesFromLegacyPublicLists() {
        val account = CollectionSource(
            provider = "trakt",
            traktSourceType = TraktCollectionSourceType.WATCHLIST.value,
            mediaType = "tv",
        ).toTraktEditorSelection()
        val legacyPublicList = CollectionSource(
            provider = "trakt",
            traktListId = 42L,
            mediaType = "movie",
            sortBy = TraktListSort.ADDED.value,
            sortHow = TraktSortHow.DESC.value,
        ).toTraktEditorSelection()

        assertEquals(TraktBuilderMode.ACCOUNT, account.mode)
        assertEquals(TraktCollectionSourceType.WATCHLIST, account.sourceType)
        assertFalse(account.mediaBoth)
        assertEquals(TraktBuilderMode.PUBLIC_LIST, legacyPublicList.mode)
        assertEquals(TraktCollectionSourceType.PUBLIC_LIST, legacyPublicList.sourceType)
        assertEquals(42L, legacyPublicList.listId)
        assertEquals(TraktListSort.ADDED.value, legacyPublicList.sortBy)
        assertEquals(TraktSortHow.DESC.value, legacyPublicList.sortHow)
        assertNull(account.listId)
    }
}
