package com.nuvio.app.features.playback.animejanai

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeJaNaiBudgetsTest {
    private val key = EngineKey(1002, 960, 540, 24)
    private val engines = listOf(EngineFile("aji-a.engine", 100, 1))

    private fun budgets(environment: () -> String = { "env-1" }): AnimeJaNaiBudgets {
        val dir = Files.createTempDirectory("ajn-budgets")
        return AnimeJaNaiBudgets(AnimeJaNaiRuntime(dir), DesktopStorage.Store(dir.resolve("budgets.properties")), environment)
    }

    @Test
    fun budgetIsEightyPercentOfTheFrameInterval() {
        val store = budgets()
        assertTrue(store.recordBenchmark(key, engines, 480, 33.0, 41.7).qualified)
        assertFalse(store.recordBenchmark(key, engines, 480, 34.0, 41.7).qualified)
    }

    @Test
    fun p95GpuTimeMustAlsoStayWithinTheBudget() {
        val store = budgets()
        assertTrue(store.recordBenchmark(key, engines, 480, 20.0, 41.7, p95FrameMillis = 33.0).qualified)
        val spiky = store.recordBenchmark(key, engines, 480, 20.0, 41.7, p95FrameMillis = 34.0)
        assertFalse(spiky.qualified, "mean within budget but p95 over it")
        assertEquals(34.0, store.find(key, engines)!!.p95FrameMillis)
        assertTrue(spiky.describe().contains("p95 GPU 34.0 ms"))
        // Without reported timing (older libmpv) the mean alone decides.
        assertTrue(store.recordBenchmark(key, engines, 480, 20.0, 41.7).qualified)
    }

    @Test
    fun timingLinesParse() {
        val timing = AjnTiming.parse("[vf/aji] AJN_TIMING_V1 240 12.345 12.000 15.500 30.250 1003")!!
        assertEquals(240, timing.frames)
        assertEquals(12.345, timing.meanMillis)
        assertEquals(15.5, timing.p95Millis)
        assertEquals(30.25, timing.maxMillis)
        assertEquals(1003, timing.slot)
        assertNull(AjnTiming.parse("AJN_STREAM_V1 active TensorRT 1002 960 540 1920 1080 0 23.976"))
        assertNull(AjnTiming.parse("AJN_TIMING_V1 0 1 1 1 1 1002"))
        assertNull(AjnTiming.parse("AJN_TIMING_V1 240 1 nan 1 1 1002"))
        assertNull(AjnStreamStatus.parse("AJN_TIMING_V1 240 12.3 12.0 15.5 30.2 1002"))
        assertTrue("AJN_TIMING_V1".contains(AnimeJaNaiRuntime.LOG_TOKEN))
        assertTrue(AnimeJaNaiRuntime.STATUS_TOKEN.contains(AnimeJaNaiRuntime.LOG_TOKEN))
    }

    @Test
    fun environmentOrEngineChangesMissOnlyThatEntry() {
        var environment = "env-1"
        val store = budgets { environment }
        store.recordBenchmark(key, engines, 480, 10.0, 41.7)
        assertNotNull(store.find(key, engines))
        assertNull(store.find(key, listOf(EngineFile("aji-a.engine", 101, 1))), "rebuilt engine must be re-measured")
        assertNull(store.find(key.copy(fps = 30), engines), "other source class is separate")
        environment = "env-2"
        assertNull(store.find(key, engines), "driver/libmpv/runtime change must be re-measured")
    }

    @Test
    fun liveBreachIsPerOutputClassAndClearedByNewMeasurement() {
        val store = budgets()
        store.recordBenchmark(key, engines, 480, 10.0, 41.7)
        store.recordLiveBreach(key, engines, AnimeJaNaiBudgets.outputClass(2160), "12 of 240 frames dropped")
        val entry = store.find(key, engines)!!
        assertEquals("12 of 240 frames dropped", entry.liveBreaches["2160"])
        assertNull(entry.liveBreaches["1080"])
        store.recordBenchmark(key, engines, 480, 10.0, 41.7)
        assertTrue(store.find(key, engines)!!.liveBreaches.isEmpty())
    }

    @Test
    fun outputClasses() {
        assertEquals("1080", AnimeJaNaiBudgets.outputClass(1080))
        assertEquals("1440", AnimeJaNaiBudgets.outputClass(1200))
        assertEquals("2160", AnimeJaNaiBudgets.outputClass(2160))
        assertEquals("above2160", AnimeJaNaiBudgets.outputClass(2880))
    }
}

class LiveBudgetMonitorTest {
    /** Feeds steady frames in steps of 12 with [dropsPerStep] drops each; returns the first breach. */
    private fun run(monitor: LiveBudgetMonitor, fromFrame: Long, toFrame: Long, startDrops: Long, dropsPerStep: Long): Pair<String?, Long> {
        var drops = startDrops
        var frame = fromFrame
        while (frame <= toFrame) {
            monitor.sample(frame, drops, steady = true)?.let { return it to drops }
            frame += 12
            drops += dropsPerStep
        }
        return null to drops
    }

    @Test
    fun noBreachWithoutDrops() {
        assertNull(run(LiveBudgetMonitor(), 0, 5000, 0, 0).first)
    }

    @Test
    fun sustainedDropsBreachAfterTwoWindows() {
        val monitor = LiveBudgetMonitor()
        val (breach, _) = run(monitor, 0, 5000, 0, 1)
        assertNotNull(breach)
    }

    @Test
    fun singleBadWindowDoesNotBreach() {
        val monitor = LiveBudgetMonitor()
        // Warm-up (48) + one clean window, one bad window, then clean again.
        var (breach, drops) = run(monitor, 0, 300, 0, 0)
        assertNull(breach)
        run(monitor, 312, 552, drops, 1).also { breach = it.first; drops = it.second }
        assertNull(breach)
        assertNull(run(monitor, 564, 5000, drops, 0).first)
    }

    @Test
    fun pausesAndSeeksResetTheWindow() {
        val monitor = LiveBudgetMonitor()
        var drops = 0L
        var frame = 0L
        // Drops only while not steady (buffering/seeking) must never count.
        repeat(2000) {
            val steady = it % 20 != 0
            if (!steady) drops += 5
            assertNull(monitor.sample(frame, drops, steady))
            frame += 1
        }
    }
}
