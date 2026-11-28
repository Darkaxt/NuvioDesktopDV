package com.nuvio.app.features.plugins.runtime

import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO

@OptIn(ExperimentalCoroutinesApi::class)
internal val pluginDispatcher: CoroutineDispatcher =
    Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_PLUGINS)

internal fun QuickJs.configurePluginRuntime() = Unit

/** Android/iOS keep their existing plugin dispatcher; desktop confines each runtime to one thread. */
internal suspend fun <T> withPluginJsThread(block: suspend () -> T): T = block()
