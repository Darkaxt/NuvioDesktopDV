package com.nuvio.app.features.servers

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.WatchProgressSource
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUserStateProjectionTest {
    private val itemId = "server-user-state-projection-test"
    private val id = ServerItemRef("cfake", itemId).encode()

    @AfterTest
    fun tearDown() {
        WatchProgressRepository.clearProgress(id)
        removeFakeServer()
        WatchProgressRepository.clearLocalState()
    }

    @Test
    fun importsServerProgressOnlyWhenEnabled() {
        val connection = installFakeServer()
        WatchProgressRepository.activateSource(WatchProgressSource.NUVIO_SYNC)
        WatchProgressRepository.clearProgress(id)
        val details = ServerItemDetails(
            meta = MetaDetails(id = id, type = "movie", name = "Projection test item"),
            externalIds = TrackingExternalIds(),
            userStates = listOf(
                ServerUserState(id, positionMs = 30_000L, durationMs = 60_000L, played = false, lastPlayedEpochMs = 5_000L),
            ),
        )

        ServerUserStateProjection.apply(details)
        assertNull(WatchProgressRepository.progressForVideo(id))

        ServerRepository.setImportWatchState(connection.id, true)
        ServerUserStateProjection.apply(details)
        assertEquals(30_000L, WatchProgressRepository.progressForVideo(id)?.lastPositionMs)
    }
}
