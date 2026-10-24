package com.nuvio.app.features.trakt

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionMediaType
import com.nuvio.app.features.collection.TraktCollectionSourceType
import com.nuvio.app.features.collection.catalogRouteKey
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.profiles.ProfileRepository
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_trakt_account_not_connected
import org.jetbrains.compose.resources.getString
import kotlin.math.roundToInt

private const val TRAKT_BASE_URL = "https://api.trakt.tv"
private const val ACCOUNT_CATALOG_PAGE_LIMIT = 50
private const val ACCOUNT_SHOW_LIMIT = 50
private const val ACCOUNT_SHOW_CANDIDATE_LIMIT = 70
private const val ACCOUNT_PROGRESS_CONCURRENCY = 8
private const val ACCOUNT_CATALOG_CACHE_TTL_MS = 5L * 60L * 1_000L
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

internal fun interface TraktAccountCatalogHttpEngine {
    suspend fun get(url: String, headers: Map<String, String>): RawHttpResponse
}

private val platformTraktAccountCatalogHttpEngine = TraktAccountCatalogHttpEngine { url, headers ->
    httpRequestRaw(
        method = "GET",
        url = url,
        headers = mapOf("Accept" to "application/json") + headers,
        body = "",
        followRedirects = true,
    )
}

internal class TraktAccountCatalogClient(
    private val engine: TraktAccountCatalogHttpEngine,
    private val nowEpochMs: () -> Long = TraktPlatformClock::nowEpochMs,
    private val localIsoDateAtEpochMs: (Long) -> String? = EpisodeReleaseDatePlatform::localIsoDateAtEpochMs,
    private val parseIsoDateTimeToEpochMs: (String) -> Long? = TraktPlatformClock::parseIsoDateTimeToEpochMs,
) {
    private val log = Logger.withTag("TraktAccountCatalog")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun resolve(
        source: CollectionSource,
        page: Int,
        headers: Map<String, String>,
    ): CatalogPage = withContext(Dispatchers.Default) {
        when (source.resolvedTraktSourceType) {
            TraktCollectionSourceType.RECOMMENDATIONS -> recommendations(source, page, headers)
            TraktCollectionSourceType.WATCHLIST -> watchlist(source, page, headers)
            TraktCollectionSourceType.UP_NEXT -> upNext(page, headers)
            TraktCollectionSourceType.UNWATCHED -> unwatched(page, headers)
            TraktCollectionSourceType.CALENDAR -> calendar(source, page, headers)
            TraktCollectionSourceType.PUBLIC_LIST -> error("Public Trakt lists are resolved by the public-list client")
        }
    }

    private suspend fun recommendations(
        source: CollectionSource,
        page: Int,
        headers: Map<String, String>,
    ): CatalogPage {
        if (page > 1) return emptyPage()
        val mediaType = TmdbCollectionMediaType.fromString(source.mediaType)
        val endpointType = mediaType.endpointType()
        val response = get(
            endpoint = "recommendations/$endpointType",
            query = mapOf(
                "limit" to ACCOUNT_CATALOG_PAGE_LIMIT.toString(),
                "ignore_collected" to "false",
                "ignore_watchlisted" to "false",
                "extended" to "full,images",
            ),
            headers = headers,
        )
        val items = when (mediaType) {
            TmdbCollectionMediaType.MOVIE -> json.decodeFromString<List<TraktCatalogMovieDto>>(response.body)
                .mapNotNull { movie -> movie.toPreview() }

            TmdbCollectionMediaType.TV -> json.decodeFromString<List<TraktCatalogShowDto>>(response.body)
                .mapNotNull { show -> show.toPreview() }
        }
        return singlePage(items, rawItemCount = items.size)
    }

    private suspend fun watchlist(
        source: CollectionSource,
        page: Int,
        headers: Map<String, String>,
    ): CatalogPage {
        val mediaType = TmdbCollectionMediaType.fromString(source.mediaType)
        val response = get(
            endpoint = "sync/watchlist/${mediaType.endpointType()}",
            query = mapOf(
                "extended" to "full,images",
                "page" to page.coerceAtLeast(1).toString(),
                "limit" to ACCOUNT_CATALOG_PAGE_LIMIT.toString(),
            ),
            headers = headers,
        )
        val rawItems = json.decodeFromString<List<TraktCatalogListItemDto>>(response.body)
        val items = rawItems
            .mapNotNull { item -> item.toPreview(mediaType) }
            .distinctBy { item -> "${item.type}:${item.id}" }
        val pageCount = response.headerInt("x-pagination-page-count") ?: page
        return CatalogPage(
            items = items,
            rawItemCount = rawItems.size,
            nextSkip = if (page < pageCount && rawItems.isNotEmpty()) page + 1 else null,
        )
    }

    private suspend fun calendar(
        source: CollectionSource,
        page: Int,
        headers: Map<String, String>,
    ): CatalogPage {
        if (page > 1) return emptyPage()
        val startDate = localIsoDateAtEpochMs(nowEpochMs()) ?: return emptyPage()
        val days = source.calendarDays?.coerceIn(1, 7) ?: 1
        val entries = fetchCalendarEntries(startDate = startDate, days = days, headers = headers)
        val earliestByShow = linkedMapOf<Int, TraktCalendarEntryDto>()
        entries.forEach { entry ->
            val showId = entry.show?.ids?.trakt ?: return@forEach
            val current = earliestByShow[showId]
            if (current == null || entry.firstAired.isBefore(current.firstAired)) {
                earliestByShow[showId] = entry
            }
        }
        val items = earliestByShow.values.mapNotNull { entry ->
            entry.show?.toPreview(
                rawReleaseDate = entry.firstAired,
                contextualDescription = episodeContext("Airs", entry.episode),
            )
        }
        return singlePage(items, rawItemCount = entries.size)
    }

    private suspend fun upNext(page: Int, headers: Map<String, String>): CatalogPage {
        if (page > 1) return emptyPage()
        val snapshots = fetchActiveShowProgress(headers)
        val now = nowEpochMs()
        val items = snapshots
            .filter { snapshot ->
                snapshot.progress.nextEpisode?.firstAired
                    ?.let(parseIsoDateTimeToEpochMs)
                    ?.let { firstAired -> firstAired <= now }
                    ?: false
            }
            .sortedWith(
                compareByDescending<TraktShowProgressSnapshot> { it.watched.lastWatchedAt.orEmpty() }
                    .thenBy { it.watched.show?.title.orEmpty().lowercase() },
            )
            .take(ACCOUNT_SHOW_LIMIT)
            .mapNotNull { snapshot ->
                val episode = snapshot.progress.nextEpisode
                snapshot.watched.show?.toPreview(
                    rawReleaseDate = episode?.firstAired,
                    contextualDescription = episodeContext("Up next", episode),
                )
            }
        return singlePage(items, rawItemCount = items.size)
    }

    private suspend fun unwatched(page: Int, headers: Map<String, String>): CatalogPage {
        if (page > 1) return emptyPage()
        val recentStart = localIsoDateAtEpochMs(nowEpochMs() - 30L * MILLIS_PER_DAY)
        val (snapshots, recentEntries) = coroutineScope {
            val progress = async { fetchActiveShowProgress(headers) }
            val calendar = async {
                recentStart?.let { startDate ->
                    fetchCalendarEntries(startDate = startDate, days = 31, headers = headers)
                }.orEmpty()
            }
            progress.await() to calendar.await()
        }
        val recentAirings = mutableMapOf<Int, String>()
        recentEntries.forEach { entry ->
            val showId = entry.show?.ids?.trakt ?: return@forEach
            val firstAired = entry.firstAired ?: return@forEach
            val previous = recentAirings[showId]
            if (previous == null || firstAired.isAfter(previous)) recentAirings[showId] = firstAired
        }
        val items = snapshots
            .filter { snapshot ->
                val aired = snapshot.progress.aired ?: 0
                val completed = snapshot.progress.completed ?: 0
                aired > completed
            }
            .sortedWith(
                compareByDescending<TraktShowProgressSnapshot> { snapshot ->
                    snapshot.watched.show?.ids?.trakt?.let(recentAirings::get).orEmpty()
                }.thenByDescending { snapshot -> snapshot.watched.lastWatchedAt.orEmpty() },
            )
            .take(ACCOUNT_SHOW_LIMIT)
            .mapNotNull { snapshot ->
                val show = snapshot.watched.show ?: return@mapNotNull null
                val recentAiring = show.ids?.trakt?.let(recentAirings::get)
                    ?: snapshot.progress.nextEpisode?.firstAired
                show.toPreview(
                    rawReleaseDate = recentAiring,
                    contextualDescription = episodeContext("Next unwatched", snapshot.progress.nextEpisode),
                )
            }
        return singlePage(items, rawItemCount = items.size)
    }

    private suspend fun fetchActiveShowProgress(
        headers: Map<String, String>,
    ): List<TraktShowProgressSnapshot> = coroutineScope {
        val watchedDeferred = async { fetchAllWatchedShows(headers) }
        val hiddenDeferred = async { fetchAllDroppedShowIds(headers) }
        val hiddenIds = hiddenDeferred.await()
        val watched = watchedDeferred.await()
            .filter { item -> item.show?.ids?.trakt?.let { it !in hiddenIds } == true }
            .sortedByDescending { item -> item.lastWatchedAt.orEmpty() }
            .take(ACCOUNT_SHOW_CANDIDATE_LIMIT)
        val semaphore = Semaphore(ACCOUNT_PROGRESS_CONCURRENCY)
        watched.map { item ->
            async {
                semaphore.withPermit {
                    val showId = item.show?.ids?.trakt ?: return@withPermit null
                    try {
                        val response = get(
                            endpoint = "shows/$showId/progress/watched",
                            query = mapOf(
                                "specials" to "false",
                                "count_specials" to "false",
                                "extended" to "full",
                            ),
                            headers = headers,
                        )
                        TraktShowProgressSnapshot(
                            watched = item,
                            progress = json.decodeFromString(response.body),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        log.w { "Skipping Trakt show $showId after progress request failed: ${error.message}" }
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchAllWatchedShows(headers: Map<String, String>): List<TraktWatchedCatalogItemDto> =
        fetchAllPages(
            endpoint = "sync/watched/shows",
            query = mapOf("extended" to "full,images"),
            headers = headers,
        )

    private suspend fun fetchAllDroppedShowIds(headers: Map<String, String>): Set<Int> =
        fetchAllPages<TraktHiddenShowDto>(
            endpoint = "users/hidden/dropped",
            query = mapOf("type" to "show"),
            headers = headers,
        ).mapNotNullTo(mutableSetOf()) { item -> item.show?.ids?.trakt }

    private suspend fun fetchCalendarEntries(
        startDate: String,
        days: Int,
        headers: Map<String, String>,
    ): List<TraktCalendarEntryDto> {
        val response = get(
            endpoint = "calendars/my/shows/$startDate/$days",
            query = mapOf("extended" to "full,images"),
            headers = headers,
        )
        return json.decodeFromString(response.body)
    }

    private suspend inline fun <reified T> fetchAllPages(
        endpoint: String,
        query: Map<String, String>,
        headers: Map<String, String>,
    ): List<T> {
        val items = mutableListOf<T>()
        var page = 1
        while (true) {
            val response = get(
                endpoint = endpoint,
                query = query + mapOf(
                    "page" to page.toString(),
                    "limit" to "100",
                ),
                headers = headers,
            )
            val pageItems = json.decodeFromString<List<T>>(response.body)
            items.addAll(pageItems)
            val pageCount = response.headerInt("x-pagination-page-count") ?: page
            if (pageItems.isEmpty() || page >= pageCount) break
            page += 1
        }
        return items
    }

    private suspend fun get(
        endpoint: String,
        query: Map<String, String>,
        headers: Map<String, String>,
    ): RawHttpResponse {
        val url = buildUrl(endpoint, query)
        val response = engine.get(url, headers)
        if (response.status !in 200..299) {
            error("Trakt request failed (${response.status}) for ${endpoint.trim('/')}")
        }
        if (response.body.isBlank()) error("Trakt returned an empty response for ${endpoint.trim('/')}")
        return response
    }

    private fun buildUrl(endpoint: String, query: Map<String, String>): String {
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
        return "$TRAKT_BASE_URL/${endpoint.trim('/')}?$queryString"
    }

    private fun TraktCatalogListItemDto.toPreview(mediaType: TmdbCollectionMediaType): MetaPreview? =
        when (mediaType) {
            TmdbCollectionMediaType.MOVIE -> movie?.toPreview()
            TmdbCollectionMediaType.TV -> show?.toPreview()
        }

    private fun TraktCatalogMovieDto.toPreview(): MetaPreview? {
        val resolvedTitle = title?.takeIf(String::isNotBlank) ?: return null
        val contentId = normalizeTraktContentId(ids, ids?.trakt?.let { "trakt:$it" })
        if (contentId.isBlank()) return null
        return MetaPreview(
            id = contentId,
            type = "movie",
            name = resolvedTitle,
            poster = images.traktBestPosterUrl(),
            banner = images.traktBestBackdropUrl(),
            logo = images.traktBestLogoUrl(),
            posterShape = PosterShape.Poster,
            description = overview?.takeIf(String::isNotBlank),
            releaseInfo = year?.toString() ?: released?.take(4),
            rawReleaseDate = released,
            imdbRating = rating?.formatCatalogRating(),
            genres = genres.orEmpty(),
        )
    }

    private fun TraktCatalogShowDto.toPreview(
        rawReleaseDate: String? = firstAired,
        contextualDescription: String? = null,
    ): MetaPreview? {
        val resolvedTitle = title?.takeIf(String::isNotBlank) ?: return null
        val contentId = normalizeTraktContentId(ids, ids?.trakt?.let { "trakt:$it" })
        if (contentId.isBlank()) return null
        return MetaPreview(
            id = contentId,
            type = "series",
            name = resolvedTitle,
            poster = images.traktBestPosterUrl(),
            banner = images.traktBestBackdropUrl(),
            logo = images.traktBestLogoUrl(),
            posterShape = PosterShape.Poster,
            description = listOfNotNull(
                contextualDescription?.takeIf(String::isNotBlank),
                overview?.takeIf(String::isNotBlank),
            ).joinToString("\n\n").ifBlank { null },
            releaseInfo = year?.toString() ?: firstAired?.take(4),
            rawReleaseDate = rawReleaseDate,
            imdbRating = rating?.formatCatalogRating(),
            genres = genres.orEmpty(),
        )
    }

    private fun episodeContext(prefix: String, episode: TraktCatalogEpisodeDto?): String? {
        episode ?: return null
        val season = episode.season ?: return null
        val number = episode.number ?: return null
        val titleSuffix = episode.title?.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()
        return "$prefix: S${season}E${number}$titleSuffix"
    }

    private fun String?.isBefore(other: String?): Boolean =
        this != null && (other == null || this < other)

    private fun String.isAfter(other: String): Boolean = this > other

    private fun TmdbCollectionMediaType.endpointType(): String =
        if (this == TmdbCollectionMediaType.MOVIE) "movies" else "shows"

    private fun RawHttpResponse.headerInt(name: String): Int? =
        headers.entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.value
            ?.substringBefore(',')
            ?.trim()
            ?.toIntOrNull()

    private fun singlePage(items: List<MetaPreview>, rawItemCount: Int): CatalogPage =
        CatalogPage(items = items, rawItemCount = rawItemCount, nextSkip = null)

    private fun emptyPage(): CatalogPage = singlePage(emptyList(), rawItemCount = 0)
}

object TraktCollectionSourceResolver {
    private data class CacheEntry(
        val fetchedAtEpochMs: Long,
        val page: CatalogPage,
    )

    private val accountClient = TraktAccountCatalogClient(platformTraktAccountCatalogHttpEngine)
    private val cacheMutex = Mutex()
    private val accountCache = mutableMapOf<String, CacheEntry>()

    suspend fun resolve(source: CollectionSource, page: Int = 1): CatalogPage {
        if (source.resolvedTraktSourceType == TraktCollectionSourceType.PUBLIC_LIST) {
            return TraktPublicListSourceResolver.resolve(source = source, page = page)
        }

        val profileId = ProfileRepository.activeProfileId
        val headers = TraktAuthRepository.authorizedHeaders(profileId)
            ?: error(getString(Res.string.collections_trakt_account_not_connected))
        val now = TraktPlatformClock.nowEpochMs()
        val credential = headers.entries
            .firstOrNull { (key, _) -> key.equals("authorization", ignoreCase = true) }
            ?.value
            ?.hashCode()
            ?: profileId
        val key = "$credential:${source.catalogRouteKey()}:$page"
        cacheMutex.withLock {
            accountCache[key]
                ?.takeIf { cached -> now - cached.fetchedAtEpochMs in 0..ACCOUNT_CATALOG_CACHE_TTL_MS }
                ?.let { cached -> return cached.page }
        }

        val resolved = accountClient.resolve(source = source, page = page, headers = headers)
        cacheMutex.withLock {
            accountCache[key] = CacheEntry(fetchedAtEpochMs = now, page = resolved)
        }
        return resolved
    }
}

private fun Double.formatCatalogRating(): String =
    ((this * 10).roundToInt() / 10.0).toString()

@Serializable
private data class TraktCatalogListItemDto(
    val rank: Int? = null,
    @SerialName("listed_at") val listedAt: String? = null,
    val type: String? = null,
    val movie: TraktCatalogMovieDto? = null,
    val show: TraktCatalogShowDto? = null,
)

@Serializable
private data class TraktCatalogMovieDto(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktExternalIds? = null,
    val overview: String? = null,
    val released: String? = null,
    val rating: Double? = null,
    val genres: List<String>? = null,
    val images: TraktImagesDto? = null,
)

@Serializable
private data class TraktCatalogShowDto(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktExternalIds? = null,
    val overview: String? = null,
    @SerialName("first_aired") val firstAired: String? = null,
    val rating: Double? = null,
    val genres: List<String>? = null,
    val images: TraktImagesDto? = null,
)

@Serializable
private data class TraktCatalogEpisodeDto(
    val season: Int? = null,
    val number: Int? = null,
    val title: String? = null,
    val overview: String? = null,
    @SerialName("first_aired") val firstAired: String? = null,
    val ids: TraktExternalIds? = null,
)

@Serializable
private data class TraktCalendarEntryDto(
    @SerialName("first_aired") val firstAired: String? = null,
    val episode: TraktCatalogEpisodeDto? = null,
    val show: TraktCatalogShowDto? = null,
)

@Serializable
private data class TraktWatchedCatalogItemDto(
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    val show: TraktCatalogShowDto? = null,
)

@Serializable
private data class TraktHiddenShowDto(
    val show: TraktCatalogShowDto? = null,
)

@Serializable
private data class TraktShowProgressDto(
    val aired: Int? = null,
    val completed: Int? = null,
    @SerialName("next_episode") val nextEpisode: TraktCatalogEpisodeDto? = null,
)

private data class TraktShowProgressSnapshot(
    val watched: TraktWatchedCatalogItemDto,
    val progress: TraktShowProgressDto,
)
