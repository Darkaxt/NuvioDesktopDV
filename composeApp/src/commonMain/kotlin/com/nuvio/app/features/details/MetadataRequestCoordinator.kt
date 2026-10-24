package com.nuvio.app.features.details

import io.ktor.util.date.GMTDate
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

internal class MetadataRequestRateLimiter(
    private val minimumStartIntervalMs: Long,
    private val currentTimeMillis: () -> Long = { GMTDate().timestamp },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val queueMutex = Mutex()
    private var nextStartEpochMs: Long? = null

    suspend fun awaitPermit() {
        queueMutex.lock()
        try {
            while (true) {
                val now = currentTimeMillis()
                val nextStart = nextStartEpochMs
                val waitMs = if (nextStart == null) 0L else nextStart - now
                if (waitMs > 0L) {
                    pause(waitMs)
                    continue
                }

                nextStartEpochMs = saturatedAdd(now, minimumStartIntervalMs.coerceAtLeast(0L))
                return
            }
        } finally {
            queueMutex.unlock()
        }
    }
}

internal class MetadataFetchInFlightCoordinator<T> {
    private val stateLock = SynchronizedObject()
    private val inFlightByKey = mutableMapOf<String, CompletableDeferred<T?>>()

    fun acquire(key: String): MetadataFetchLease<T> = synchronized(stateLock) {
        val existing = inFlightByKey[key]
        if (existing != null) {
            MetadataFetchLease(
                key = key,
                deferred = existing,
                coordinator = this,
                isOwner = false,
            )
        } else {
            val created = CompletableDeferred<T?>()
            inFlightByKey[key] = created
            MetadataFetchLease(
                key = key,
                deferred = created,
                coordinator = this,
                isOwner = true,
            )
        }
    }

    internal fun complete(key: String, deferred: CompletableDeferred<T?>, value: T?) {
        deferred.complete(value)
        synchronized(stateLock) {
            if (inFlightByKey[key] === deferred) {
                inFlightByKey.remove(key)
            }
        }
    }

    internal fun fail(key: String, deferred: CompletableDeferred<T?>, error: Throwable) {
        deferred.completeExceptionally(error)
        synchronized(stateLock) {
            if (inFlightByKey[key] === deferred) {
                inFlightByKey.remove(key)
            }
        }
    }
}

internal class MetadataFetchLease<T> internal constructor(
    private val key: String,
    private val deferred: CompletableDeferred<T?>,
    private val coordinator: MetadataFetchInFlightCoordinator<T>,
    val isOwner: Boolean,
) {
    suspend fun await(): T? = deferred.await()

    fun complete(value: T?) {
        check(isOwner) { "Only the in-flight request owner can complete the result" }
        coordinator.complete(key, deferred, value)
    }

    fun fail(error: Throwable) {
        check(isOwner) { "Only the in-flight request owner can fail the result" }
        coordinator.fail(key, deferred, error)
    }
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
