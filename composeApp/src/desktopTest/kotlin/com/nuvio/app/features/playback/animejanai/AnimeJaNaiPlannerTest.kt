package com.nuvio.app.features.playback.animejanai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeJaNaiPlannerTest {
    private val start = AjnStartFacts(
        rtxSuperResolution = false,
        softwareDecoding = false,
        unavailableReason = null,
        preparing = false,
        installing = false,
        titleKnown = true,
    )

    @Test
    fun startSkipsWhileRtxVsrPreparationOrInstallOwnTheGpu() {
        assertNull(AnimeJaNaiPlanner.startSkipReason(start))
        assertEquals("RTX Video Super Resolution is on", AnimeJaNaiPlanner.startSkipReason(start.copy(rtxSuperResolution = true)))
        // Ordinary playback starts (null plan) while a preparation is pending or running.
        assertEquals("An engine preparation is running", AnimeJaNaiPlanner.startSkipReason(start.copy(preparing = true)))
        assertEquals("The AnimeJaNai runtime is being installed", AnimeJaNaiPlanner.startSkipReason(start.copy(installing = true)))
        assertEquals("Software decoding is selected", AnimeJaNaiPlanner.startSkipReason(start.copy(softwareDecoding = true)))
        assertEquals("TensorRT missing", AnimeJaNaiPlanner.startSkipReason(start.copy(unavailableReason = "TensorRT missing")))
        assertEquals("Title is unknown", AnimeJaNaiPlanner.startSkipReason(start.copy(titleKnown = false)))
        // RTX VSR wins over every other reason: AnimeJaNai never stacks with it.
        assertEquals(
            "RTX Video Super Resolution is on",
            AnimeJaNaiPlanner.startSkipReason(start.copy(rtxSuperResolution = true, preparing = true, titleKnown = false)),
        )
    }

    private val engines = listOf(EngineFile("aji.engine", 100, 1))
    private val qualified = AnimeJaNaiBudgets.Entry(480, 10.0, 41.7, 0)

    private fun decide(
        width: Int = 960,
        height: Int = 540,
        gamma: String = "bt.1886",
        output: Pair<Int, Int> = 2560 to 1440,
        animation: Boolean = true,
        prepared: List<EngineFile>? = engines,
        budget: AnimeJaNaiBudgets.Entry? = qualified,
    ): AjnStreamDecision = AnimeJaNaiPlanner.decide(
        EngineKey.of(1002, width, height, 23.976),
        AjnStreamFacts(width, height, gamma, output.first, output.second, animation),
        { prepared },
        { _, _ -> budget },
    )

    @Test
    fun subFourKUpscalesNativeFourKAndHdrAreSkipped() {
        assertIs<AjnStreamDecision.Insert>(decide())
        assertIs<AjnStreamDecision.Insert>(decide(width = 1920, height = 1080, output = 3840 to 2160))
        assertEquals("Source is 4K or larger", assertIs<AjnStreamDecision.Skip>(decide(width = 3840, height = 2160, output = 3840 to 2160)).reason)
        assertEquals("HDR/Dolby Vision is not verified for AnimeJaNai", assertIs<AjnStreamDecision.Skip>(decide(gamma = "PQ")).reason)
    }

    @Test
    fun noUpscaleSkipIsMarkedForReevaluationAndUnknownOutputWaits() {
        val small = assertIs<AjnStreamDecision.Skip>(decide(width = 1920, height = 1080, output = 1280 to 720))
        assertTrue(small.forOutput)
        assertIs<AjnStreamDecision.AwaitOutput>(decide(output = 0 to 0))
        assertEquals("Animation is Off", assertIs<AjnStreamDecision.Skip>(decide(animation = false)).reason)
    }

    @Test
    fun missingOrRejectedEngineAndUnmeasuredBudgetRequirePreparation() {
        // A stale/incompatible engine is dropped by the index lookup, which then reports none.
        assertTrue(assertIs<AjnStreamDecision.PreparationRequired>(decide(prepared = null)).reason.startsWith("No prepared engine"))
        assertTrue(assertIs<AjnStreamDecision.PreparationRequired>(decide(budget = null)).reason.contains("not measured"))
    }

    @Test
    fun overBudgetAndLiveBreachesSkip() {
        val slow = qualified.copy(meanFrameMillis = 40.0)
        assertTrue(assertIs<AjnStreamDecision.Skip>(decide(budget = slow)).reason.startsWith("Over the performance budget"))
        val breached = qualified.copy(liveBreaches = mapOf(AnimeJaNaiBudgets.outputClass(1440) to "12 dropped"))
        assertEquals("Off at this output size after frame drops (12 dropped)", assertIs<AjnStreamDecision.Skip>(decide(budget = breached)).reason)
        // Another output class is unaffected.
        assertIs<AjnStreamDecision.Insert>(decide(budget = breached, output = 1920 to 1080))
    }
}
