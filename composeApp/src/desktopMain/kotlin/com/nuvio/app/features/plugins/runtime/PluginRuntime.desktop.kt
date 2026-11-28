package com.nuvio.app.features.plugins.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal val pluginDispatcher: CoroutineDispatcher = Dispatchers.Default

internal fun QuickJs.configurePluginRuntime() = Unit

private val jsThreadCounter = AtomicInteger()

/**
 * NuvioDV: confine each QuickJS runtime to one dedicated thread for its whole lifetime.
 * On the shared multi-threaded Default pool its continuations and async jobs resumed on
 * arbitrary threads, which intermittently produced spurious unhandled rejections or
 * results that never arrived under concurrent scraper execution.
 */
internal suspend fun <T> withPluginJsThread(block: suspend () -> T): T {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "plugin-js-${jsThreadCounter.incrementAndGet()}").apply { isDaemon = true }
    }
    val dispatcher = executor.asCoroutineDispatcher()
    try {
        return withContext(dispatcher) { block() }
    } finally {
        dispatcher.close()
    }
}
