package com.nuvio.app.features.plugins

import com.nuvio.app.features.plugins.runtime.PluginRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * NuvioDV regression guard for the intermittent concurrent QuickJS failure
 * (spurious unhandled rejection / result never delivered) seen in single-round
 * PluginRuntimeDesktopTest runs. Repeats the concurrent scenario so the race
 * shows up deterministically enough to qualify a fix.
 */
class PluginRuntimeConcurrencyStressTest {
    @Test
    fun `desktop runtime survives repeated concurrent scraper executions`() = runBlocking {
        repeat(ROUNDS) { round ->
            val results = withTimeout(ROUND_TIMEOUT_MS) {
                coroutineScope {
                    (0 until PARALLEL).map { index ->
                        async(Dispatchers.Default) {
                            PluginRuntime.executePlugin(
                                code = """
                                    module.exports.getStreams = async function(tmdbId, mediaType) {
                                        await Promise.resolve();
                                        await new Promise(function(resolve) { resolve(); });
                                        return [{
                                            title: "Stress " + tmdbId,
                                            url: "https://example.test/" + tmdbId + ".mp4",
                                            provider: "Desktop Stress Test"
                                        }];
                                    };
                                """.trimIndent(),
                                tmdbId = "$round-$index",
                                mediaType = "movie",
                                season = null,
                                episode = null,
                                scraperId = "desktop-runtime-stress-$round-$index",
                            )
                        }
                    }.awaitAll()
                }
            }
            assertEquals(PARALLEL, results.size, "round $round")
            results.forEachIndexed { index, streams ->
                assertEquals(1, streams.size, "round $round index $index")
                assertEquals("https://example.test/$round-$index.mp4", streams.single().url, "round $round index $index")
            }
        }
    }

    private companion object {
        const val ROUNDS = 40
        const val PARALLEL = 32
        const val ROUND_TIMEOUT_MS = 30_000L
    }
}
