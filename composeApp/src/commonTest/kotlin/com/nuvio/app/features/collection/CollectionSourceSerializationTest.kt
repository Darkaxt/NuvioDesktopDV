package com.nuvio.app.features.collection

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectionSourceSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun traktSourceRoundTripsWithPublicListShape() {
        val collection = Collection(
            id = "collection-1",
            title = "Favorites",
            folders = listOf(
                CollectionFolder(
                    id = "folder-1",
                    title = "Lists",
                    sources = listOf(
                        CollectionSource(
                            provider = "trakt",
                            title = "Criterion Movies",
                            traktListId = 123456L,
                            mediaType = TmdbCollectionMediaType.MOVIE.name,
                            sortBy = TraktListSort.ADDED.value,
                            sortHow = TraktSortHow.DESC.value,
                        ),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(listOf(collection))
        assertTrue(encoded.contains(""""provider":"trakt""""))
        assertTrue(encoded.contains(""""traktListId":123456"""))
        assertTrue(encoded.contains(""""sortHow":"desc""""))

        val decoded = json.decodeFromString<List<Collection>>(encoded)
        val source = decoded.single().folders.single().resolvedSources.single()
        assertTrue(source.isTrakt)
        assertEquals(123456L, source.traktListId)
        assertEquals(TmdbCollectionMediaType.MOVIE.name, source.mediaType)
        assertEquals(TraktListSort.ADDED.value, source.sortBy)
        assertEquals(TraktSortHow.DESC.value, source.sortHow)
        assertEquals(TraktCollectionSourceType.PUBLIC_LIST, source.resolvedTraktSourceType)
    }

    @Test
    fun authenticatedTraktSourceTypesRoundTripWithoutListIds() {
        val sources = listOf(
            CollectionSource(
                provider = "trakt",
                title = "Recommended Movies",
                traktSourceType = TraktCollectionSourceType.RECOMMENDATIONS.value,
                mediaType = TmdbCollectionMediaType.MOVIE.value,
            ),
            CollectionSource(
                provider = "trakt",
                title = "Movie Watchlist",
                traktSourceType = TraktCollectionSourceType.WATCHLIST.value,
                mediaType = TmdbCollectionMediaType.MOVIE.value,
            ),
            CollectionSource(
                provider = "trakt",
                title = "Recommended Shows",
                traktSourceType = TraktCollectionSourceType.RECOMMENDATIONS.value,
                mediaType = TmdbCollectionMediaType.TV.value,
            ),
            CollectionSource(
                provider = "trakt",
                title = "Up Next",
                traktSourceType = TraktCollectionSourceType.UP_NEXT.value,
                mediaType = TmdbCollectionMediaType.TV.value,
            ),
            CollectionSource(
                provider = "trakt",
                title = "Recently Aired",
                traktSourceType = TraktCollectionSourceType.UNWATCHED.value,
                mediaType = TmdbCollectionMediaType.TV.value,
            ),
            CollectionSource(
                provider = "trakt",
                title = "Calendar",
                traktSourceType = TraktCollectionSourceType.CALENDAR.value,
                mediaType = TmdbCollectionMediaType.TV.value,
                calendarDays = 1,
            ),
            CollectionSource(
                provider = "trakt",
                title = "Show Watchlist",
                traktSourceType = TraktCollectionSourceType.WATCHLIST.value,
                mediaType = TmdbCollectionMediaType.TV.value,
            ),
        )
        val collection = Collection(
            id = "collection-1",
            title = "For You",
            folders = listOf(CollectionFolder(id = "folder-1", title = "Trakt", sources = sources)),
        )

        val decoded = json.decodeFromString<List<Collection>>(json.encodeToString(listOf(collection)))
            .single()
            .folders
            .single()
            .resolvedSources

        assertEquals(sources, decoded)
        assertTrue(decoded.none(CollectionSource::hasInvalidTraktListId))
        assertEquals(
            sources.map { it.traktSourceType },
            decoded.map { it.resolvedTraktSourceType.value },
        )
    }

    @Test
    fun authenticatedTraktSourcesExportWithWebsiteCompatibilityListId() {
        val collection = Collection(
            id = "collection-1",
            title = "Discover",
            folders = listOf(
                CollectionFolder(
                    id = "folder-1",
                    title = "For You",
                    sources = listOf(
                        CollectionSource(
                            provider = "trakt",
                            title = "Recommended Movies",
                            traktSourceType = TraktCollectionSourceType.RECOMMENDATIONS.value,
                            mediaType = TmdbCollectionMediaType.MOVIE.value,
                        ),
                    ),
                ),
            ),
        )

        val merged = CollectionJsonPreserver.merge(
            json = json,
            rawCollectionsJson = json.parseToJsonElement("[]"),
            collections = listOf(collection),
        )
        val sourceObject = merged.single()
            .jsonObject["folders"]!!
            .jsonArray.single()
            .jsonObject["sources"]!!
            .jsonArray.single()
            .jsonObject

        assertEquals(2_147_483_647L, sourceObject["traktListId"]!!.jsonPrimitive.long)
        assertEquals(
            TraktCollectionSourceType.RECOMMENDATIONS.value,
            sourceObject["traktSourceType"]!!.jsonPrimitive.content,
        )
        val decoded = json.decodeFromJsonElement(CollectionSource.serializer(), sourceObject)
        assertEquals(TraktCollectionSourceType.RECOMMENDATIONS, decoded.resolvedTraktSourceType)
        assertFalse(decoded.hasInvalidTraktListId())
    }

    @Test
    fun legacyTraktPublicListInfersTypeAndStillRequiresListId() {
        val validLegacy = CollectionSource(
            provider = "trakt",
            traktListId = 42L,
            mediaType = "MOVIE",
        )
        val invalidExplicit = CollectionSource(
            provider = "trakt",
            traktSourceType = TraktCollectionSourceType.PUBLIC_LIST.value,
            mediaType = "MOVIE",
        )

        assertEquals(TraktCollectionSourceType.PUBLIC_LIST, validLegacy.resolvedTraktSourceType)
        assertFalse(validLegacy.hasInvalidTraktListId())
        assertTrue(invalidExplicit.hasInvalidTraktListId())
    }

    @Test
    fun traktRouteKeysDistinguishAccountSourceTypeAndMedia() {
        fun source(type: TraktCollectionSourceType, mediaType: String) = CollectionSource(
            provider = "trakt",
            traktSourceType = type.value,
            mediaType = mediaType,
        )

        val keys = listOf(
            source(TraktCollectionSourceType.RECOMMENDATIONS, "movie"),
            source(TraktCollectionSourceType.RECOMMENDATIONS, "series"),
            source(TraktCollectionSourceType.WATCHLIST, "movie"),
            source(TraktCollectionSourceType.WATCHLIST, "series"),
            source(TraktCollectionSourceType.UP_NEXT, "series"),
            source(TraktCollectionSourceType.UNWATCHED, "series"),
            source(TraktCollectionSourceType.CALENDAR, "series").copy(calendarDays = 1),
        ).map(CollectionSource::catalogRouteKey)

        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("trakt_") })
    }

    @Test
    fun tmdbExclusionFiltersRoundTripAndBuildDiscoverQuery() {
        val filters = TmdbCollectionFilters(
            withoutGenres = "16",
            withoutKeywords = "9715|818",
            withoutCompanies = "420",
            withoutWatchProviders = "8|337",
        )
        val collection = Collection(
            id = "collection-1",
            title = "Live Action",
            folders = listOf(
                CollectionFolder(
                    id = "folder-1",
                    title = "Movies",
                    sources = listOf(
                        CollectionSource(
                            provider = "tmdb",
                            tmdbSourceType = TmdbCollectionSourceType.DISCOVER.name,
                            title = "Without Animation",
                            mediaType = TmdbCollectionMediaType.MOVIE.name,
                            filters = filters,
                        ),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(listOf(collection))
        val decodedSource = json.decodeFromString<List<Collection>>(encoded)
            .single()
            .folders
            .single()
            .resolvedSources
            .single()
        assertEquals(filters, decodedSource.filters)

        val query = TmdbCollectionSourceResolver.buildDiscoverQuery(
            source = decodedSource,
            sourceType = TmdbCollectionSourceType.DISCOVER,
            mediaType = TmdbCollectionMediaType.MOVIE,
            language = "en-US",
            page = 1,
            filters = decodedSource.filters ?: TmdbCollectionFilters(),
        )
        assertEquals("16", query["without_genres"])
        assertEquals("9715|818", query["without_keywords"])
        assertEquals("420", query["without_companies"])
        assertEquals("8|337", query["without_watch_providers"])
        assertEquals("US", query["watch_region"])
        assertFalse("with_watch_monetization_types" in query)
    }

    @Test
    fun importedTraktSourceWithoutListIdIsRejected() {
        val payload = """
            [
              {
                "id": "collection-1",
                "title": "Favorites",
                "folders": [
                  {
                    "id": "folder-1",
                    "title": "Lists",
                    "sources": [
                      {
                        "provider": "trakt",
                        "title": "Missing List",
                        "mediaType": "MOVIE",
                        "sortBy": "rank",
                        "sortHow": "asc"
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val source = json.decodeFromString<List<Collection>>(payload)
            .single()
            .folders
            .single()
            .resolvedSources
            .single()

        assertTrue(source.hasInvalidTraktListId())
    }

    @Test
    fun importModelRejectsDuplicateCollectionIds() {
        val collections = listOf(
            Collection(id = "collection-1", title = "One"),
            Collection(id = "collection-1", title = "Two"),
        )

        assertEquals(
            CollectionImportModelError.DuplicateCollectionId("collection-1"),
            validateImportModel(collections),
        )
    }

    @Test
    fun importModelRejectsDuplicateFolderIdsWithinCollection() {
        val collections = listOf(
            Collection(
                id = "collection-1",
                title = "Favorites",
                folders = listOf(
                    CollectionFolder(id = "folder-1", title = "One"),
                    CollectionFolder(id = "folder-1", title = "Two"),
                ),
            ),
        )

        assertEquals(
            CollectionImportModelError.DuplicateFolderId(
                folderId = "folder-1",
                collectionTitle = "Favorites",
            ),
            validateImportModel(collections),
        )
    }

    @Test
    fun legacyAddonCatalogSourcesRemainCompatible() {
        val payload = """
            [
              {
                "id": "collection-1",
                "title": "Favorites",
                "folders": [
                  {
                    "id": "folder-1",
                    "title": "Movies",
                    "catalogSources": [
                      {
                        "addonId": "addon-1",
                        "type": "movie",
                        "catalogId": "top",
                        "genre": "Action"
                      }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val collection = json.decodeFromString<List<Collection>>(payload).single()
        val source = collection.folders.single().resolvedSources.single()
        val addonSource = source.addonCatalogSource()

        assertNotNull(addonSource)
        assertEquals("addon-1", addonSource.addonId)
        assertEquals("movie", addonSource.type)
        assertEquals("top", addonSource.catalogId)
        assertEquals("Action", addonSource.genre)
    }

    @Test
    fun sourceKeyPreservationKeepsUnknownTraktFields() {
        val raw = json.parseToJsonElement(
            """
                [
                  {
                    "id": "collection-1",
                    "title": "Favorites",
                    "folders": [
                      {
                        "id": "folder-1",
                        "title": "Lists",
                        "sources": [
                          {
                            "provider": "trakt",
                            "title": "Criterion Movies",
                            "traktListId": 123456,
                            "mediaType": "MOVIE",
                            "sortBy": "rank",
                            "sortHow": "asc",
                            "customField": "keep-me"
                          }
                        ]
                      }
                    ]
                  }
                ]
            """.trimIndent(),
        )
        val collection = Collection(
            id = "collection-1",
            title = "Favorites",
            folders = listOf(
                CollectionFolder(
                    id = "folder-1",
                    title = "Lists",
                    sources = listOf(
                        CollectionSource(
                            provider = "trakt",
                            title = "Criterion Movies",
                            traktListId = 123456L,
                            mediaType = TmdbCollectionMediaType.MOVIE.name,
                            sortBy = TraktListSort.RANK.value,
                            sortHow = TraktSortHow.ASC.value,
                        ),
                    ),
                ),
            ),
        )

        val merged = CollectionJsonPreserver.merge(json, raw, listOf(collection)).toString()
        assertTrue(merged.contains(""""customField":"keep-me""""))
        assertTrue(merged.contains(""""traktListId":123456"""))
    }

    @Test
    fun accountSourcePreservationKeysDoNotCollide() {
        val raw = json.parseToJsonElement(
            """
                [
                  {
                    "id": "collection-1",
                    "title": "Discover",
                    "folders": [
                      {
                        "id": "folder-1",
                        "title": "For You",
                        "sources": [
                          {
                            "provider": "trakt",
                            "traktSourceType": "recommendations",
                            "traktListId": 0,
                            "mediaType": "movie",
                            "customField": "recommendations-marker"
                          },
                          {
                            "provider": "trakt",
                            "traktSourceType": "watchlist",
                            "traktListId": 0,
                            "mediaType": "movie",
                            "customField": "watchlist-marker"
                          },
                          {
                            "provider": "trakt",
                            "traktSourceType": "calendar",
                            "traktListId": 0,
                            "mediaType": "tv",
                            "calendarDays": 1,
                            "customField": "calendar-marker"
                          }
                        ]
                      }
                    ]
                  }
                ]
            """.trimIndent(),
        )
        val collection = json.decodeFromString<List<Collection>>(raw.toString()).single()

        val mergedSources = CollectionJsonPreserver.merge(json, raw, listOf(collection))
            .single()
            .jsonObject["folders"]!!
            .jsonArray.single()
            .jsonObject["sources"]!!
            .jsonArray

        assertEquals(
            listOf("recommendations-marker", "watchlist-marker", "calendar-marker"),
            mergedSources.map { it.jsonObject["customField"]!!.jsonPrimitive.content },
        )
        assertTrue(
            mergedSources.all {
                it.jsonObject["traktListId"]!!.jsonPrimitive.long == 2_147_483_647L
            },
        )
    }

    @Test
    fun mobileGifToggleDoesNotEnterCollectionJsonOrOverwriteTvGifToggle() {
        val raw = json.parseToJsonElement(
            """
                [
                  {
                    "id": "collection-1",
                    "title": "Favorites",
                    "folders": [
                      {
                        "id": "folder-1",
                        "title": "Movies",
                        "coverImageUrl": "https://example.com/poster.jpg",
                        "focusGifUrl": "https://example.com/focus.gif",
                        "focusGifEnabled": true
                      }
                    ]
                  }
                ]
            """.trimIndent(),
        )
        val collection = json.decodeFromString<List<Collection>>(raw.toString()).single()
        val mobileDisabled = collection.copy(
            folders = collection.folders.map { folder ->
                folder.copy(mobileFocusGifEnabled = false)
            },
        )

        val merged = CollectionJsonPreserver.merge(json, raw, listOf(mobileDisabled))
        val mergedFolder = merged
            .single()
            .jsonObject["folders"]!!
            .jsonArray
            .single()
            .jsonObject

        assertTrue(mergedFolder["focusGifEnabled"]!!.jsonPrimitive.boolean)
        assertTrue(mergedFolder["mobileFocusGifEnabled"] == null)
    }

    @Test
    fun mobileGifToggleDefaultsIndependentOfTvGifToggle() {
        val payload = """
            [
              {
                "id": "collection-1",
                "title": "Favorites",
                "folders": [
                  {
                    "id": "folder-1",
                    "title": "Movies",
                    "focusGifUrl": "https://example.com/focus.gif",
                    "focusGifEnabled": false
                  }
                ]
              }
            ]
        """.trimIndent()

        val folder = json.decodeFromString<List<Collection>>(payload).single().folders.single()

        assertFalse(folder.focusGifEnabled)
        assertTrue(folder.mobileFocusGifEnabled)
    }
}
