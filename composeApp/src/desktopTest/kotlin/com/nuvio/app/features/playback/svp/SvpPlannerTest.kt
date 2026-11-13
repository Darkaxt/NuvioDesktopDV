package com.nuvio.app.features.playback.svp

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.formatFps
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SvpPlannerTest {
    private fun input(
        source: Double? = 23.976,
        display: Double? = 60.0,
        width: Int = 1920,
        height: Int = 1080,
        gamma: String = "bt.1886",
        breached: Int? = null,
    ) = SvpInput(source, display, width, height, gamma, breached)

    @Test
    fun filmOnSixtyHertzTargetsTheRefreshRate() {
        val decision = assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input()))
        assertEquals(60_000, decision.targetMilli)
        assertEquals(16, decision.blockWidth)
        assertEquals("1080", decision.resolutionClass)
    }

    @Test
    fun highRefreshDisplaysUseTheHighestDivisorUnderTheResolutionCap() {
        // 1080p: 165 Hz is within the cap.
        assertEquals(165_000, assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(display = 165.0))).targetMilli)
        // A measured 165.059 Hz panel is still the 165 cap, not half of it.
        assertEquals(165_059, assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(display = 165.059))).targetMilli)
        // 240 Hz is above the 1080p cap: half the refresh.
        assertEquals(120_000, assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(display = 240.0))).targetMilli)
        // 1440p is capped at 120: 144 Hz falls back to 72 (144/2), never a non-divisor.
        val qhd = assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(display = 144.0, width = 2560, height = 1440)))
        assertEquals(72_000, qhd.targetMilli)
        assertEquals(32, qhd.blockWidth)
        // 4K is capped at 60: 120 Hz gives 60.
        assertEquals(60_000, assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(display = 120.0, width = 3840, height = 2160))).targetMilli)
    }

    @Test
    fun learnedBreachesPickALowerDivisorOrSkip() {
        val lowered = assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(display = 144.0, breached = 144_000)))
        assertEquals(72_000, lowered.targetMilli)
        val skipped = assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(display = 60.0, breached = 60_000)))
        assertTrue(skipped.reason.startsWith("Not sustainable"))
    }

    @Test
    fun adequateOrUnknownCadencesAreSkipped() {
        assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(source = 50.0)))
        assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(source = null)))
        assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(display = null)))
        // 30 fps on a 30 Hz display: nothing to gain.
        assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(source = 29.97, display = 30.0)))
        // 25 fps on 60 Hz: 60 is >= 1.5x.
        assertEquals(60_000, assertIs<SvpDecision.Interpolate>(SvpPlanner.plan(input(source = 25.0))).targetMilli)
    }

    @Test
    fun hdrAndOversizedSourcesAreSkipped() {
        assertEquals("HDR/Dolby Vision is not verified for SVP", assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(gamma = "pq"))).reason)
        assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(gamma = "hlg")))
        assertIs<SvpDecision.Skip>(SvpPlanner.plan(input(width = 7680, height = 4320)))
    }

    @Test
    fun bufferedFramesGrowToABoundedCeiling() {
        assertEquals(16, SvpPlanner.increasedBufferedFrames(8))
        assertEquals(24, SvpPlanner.increasedBufferedFrames(16))
        assertNull(SvpPlanner.increasedBufferedFrames(24))
        assertEquals(8, SvpPlanner.concurrentFrames(32))
        assertEquals(2, SvpPlanner.concurrentFrames(1))
    }

    @Test
    fun scriptAndFilterAreDeterministic() {
        val decision = SvpDecision.Interpolate(23.976, 59_940, 16, "1080")
        val flow1 = Paths.get("""C:\Program Files\SVP 4\plugins64\svpflow1_vs.dll""")
        val flow2 = Paths.get("""C:\Program Files\SVP 4\plugins64\svpflow2_vs.dll""")
        val script = SvpPlanner.script(decision, flow1, flow2)
        assertEquals(script, SvpPlanner.script(decision, flow1, flow2))
        assertTrue("core.std.LoadPlugin(r\"$flow1\")" in script)
        assertTrue("rate:{num:59940,den:1000,abs:true}" in script)
        assertTrue("block:{w:16,overlap:2}" in script)
        assertTrue("else 23.976000" in script)
        assertTrue("smooth.set_output()" in script)

        val path = Paths.get("""C:\Users\Ünïcode\svp-1.vpy""")
        val filter = SvpPlanner.filter(path, 8, 4)
        val bytes = path.toString().toByteArray(Charsets.UTF_8).size
        assertEquals("@nuviosvp:vapoursynth=file=%$bytes%$path:buffered-frames=8:concurrent-frames=4:skip-seek-pre-target=no", filter)
    }

    @Test
    fun budgetWindowsAreTimeBasedAndSeeksRestartThem() {
        // 165 fps: three seconds of warmup, then two three-second windows over 2% drops.
        val monitor = SvpPlanner.budgetMonitor(165.0)
        var drops = 0L
        val breachFrame = (1L..4 * 495L).firstOrNull { frame ->
            if (frame > 495 && frame % 20 == 0L) drops++ // 5% drops after warmup
            monitor.sample(frame, drops, steady = true) != null
        }
        // Warmup (495) + first window opens at 496, then two full 495-frame windows.
        assertEquals(496L + 2 * 495, breachFrame)
        // A short burst right after a (re)start stays within the warmup and is not judged.
        val fresh = SvpPlanner.budgetMonitor(165.0)
        assertNull((1L..490L).firstNotNullOfOrNull { fresh.sample(it, it, steady = true) })

        assertTrue(SvpPlanner.isDiscontinuity(1000, 900, 23.976), "backward seek")
        assertTrue(SvpPlanner.isDiscontinuity(1000, 1100, 23.976), "forward seek beyond 3 s")
        assertTrue(!SvpPlanner.isDiscontinuity(1000, 1006, 23.976), "normal playback between ticks")
        assertTrue(!SvpPlanner.isDiscontinuity(-1, 5000, 23.976), "first sample")
    }

    @Test
    fun componentVersionsAreParsed() {
        val xml = """
            <Packages><Package><Name>core.flow64vs</Name><Version>4.6.0.274</Version></Package>
            <Package><Name>opt.vps_portable64</Name><Title>x</Title><Version>70</Version></Package></Packages>
        """.trimIndent()
        assertEquals(mapOf("core.flow64vs" to "4.6.0.274", "opt.vps_portable64" to "70"), SvpRuntime.parseComponentVersions(xml))
    }

    @Test
    fun capabilityKeepsTheLowestBreachPerEnvironment() {
        val dir = Files.createTempDirectory("svp-capability")
        var environment = "env-1"
        val capability = SvpCapability(DesktopStorage.Store(dir.resolve("svp.properties"))) { environment }
        assertNull(capability.breachedTargetMilli("1080"))
        capability.recordBreach("1080", 120_000)
        capability.recordBreach("1080", 144_000)
        assertEquals(120_000, capability.breachedTargetMilli("1080"))
        assertNull(capability.breachedTargetMilli("1440"))
        environment = "env-2"
        assertNull(capability.breachedTargetMilli("1080"))
    }

    @Test
    fun bothPortableVapourSynthLayoutsAreDetected() {
        fun layout(vararg runtimeFiles: String): SvpRuntime.Availability {
            val root = Files.createTempDirectory("svp-root")
            Files.createDirectories(root.resolve("mpv64"))
            Files.createDirectories(root.resolve("plugins64"))
            runtimeFiles.forEach { Files.createFile(root.resolve("mpv64").resolve(it)) }
            listOf("svpflow1_vs.dll", "svpflow2_vs.dll").forEach { Files.createFile(root.resolve("plugins64").resolve(it)) }
            return SvpRuntime(root).availability()
        }
        // SVP's VapourSynth package 70: portable.vs and a versioned Python.
        assertIs<SvpRuntime.Availability.Available>(
            layout("VSScript.dll", "vapoursynth.dll", "python3.dll", "python312.dll", "portable.vs"),
        )
        // Package 81 (VapourSynth R79): shim DLLs, no portable.vs.
        assertIs<SvpRuntime.Availability.Available>(layout("VSScript.dll", "vapoursynth.dll", "python3.dll"))
        assertIs<SvpRuntime.Availability.Unavailable>(layout("vapoursynth.dll", "python3.dll"))
    }

    @Test
    fun startOptionsRespectTheDecoderPolicy() {
        val runtime = SvpRuntime(Paths.get("""C:\Program Files\SVP 4"""))
        assertTrue("hwdec=auto-copy" in runtime.playerStartOptions(0))
        assertTrue(runtime.playerStartOptions(SvpRuntime.SOFTWARE_DECODING).none { it.startsWith("hwdec=") })
        assertTrue("nuvio-dll-dir=${runtime.vapourSynthDir}" in runtime.playerStartOptions(1))
    }

    @Test
    fun fpsLabelsDropTrailingZeros() {
        assertEquals("23.976", formatFps(23.976))
        assertEquals("60", formatFps(60.0))
        assertEquals("59.94", formatFps(59.94))
    }
}
