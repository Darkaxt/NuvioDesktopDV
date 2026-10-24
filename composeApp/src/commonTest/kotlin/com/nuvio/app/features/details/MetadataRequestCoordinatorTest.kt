package com.nuvio.app.features.details

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetadataRequestCoordinatorTest {
    @Test
    fun `shared limiter spaces concurrent request starts at two per second`() = runBlocking {
        var nowMs = 0L
        val waits = mutableListOf<Long>()
        val limiter = MetadataRequestRateLimiter(
            minimumStartIntervalMs = 500L,
            currentTimeMillis = { nowMs },
            pause = { waitMs ->
                waits += waitMs
                nowMs += waitMs
            },
        )

        coroutineScope {
            List(4) {
                async {
                    limiter.awaitPermit()
                }
            }.awaitAll()
        }

        assertEquals(listOf(500L, 500L, 500L), waits)
        assertEquals(1_500L, nowMs)
    }

    @Test
    fun `cancelled waiter releases limiter for the next request`() = runBlocking {
        var nowMs = 0L
        val pauseEntered = CompletableDeferred<Unit>()
        val holdPause = CompletableDeferred<Unit>()
        val limiter = MetadataRequestRateLimiter(
            minimumStartIntervalMs = 500L,
            currentTimeMillis = { nowMs },
            pause = {
                pauseEntered.complete(Unit)
                holdPause.await()
            },
        )

        limiter.awaitPermit()
        val cancelledWaiter = launch { limiter.awaitPermit() }
        pauseEntered.await()
        cancelledWaiter.cancelAndJoin()

        nowMs = 500L
        limiter.awaitPermit()
    }

    @Test
    fun `same key fetches share one in flight result`() = runBlocking {
        val coordinator = MetadataFetchInFlightCoordinator<String>()

        val owner = coordinator.acquire("series:tt123")
        val follower = coordinator.acquire("series:tt123")

        assertTrue(owner.isOwner)
        assertFalse(follower.isOwner)
        owner.complete("resolved")
        assertEquals("resolved", follower.await())

        assertTrue(coordinator.acquire("series:tt123").isOwner)
    }
}
