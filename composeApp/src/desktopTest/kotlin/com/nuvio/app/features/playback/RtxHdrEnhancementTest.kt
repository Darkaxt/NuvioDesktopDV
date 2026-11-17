package com.nuvio.app.features.playback

import com.nuvio.app.features.playback.animejanai.AnimeJaNaiSession
import com.nuvio.app.features.playback.animejanai.EnhancementStatus
import com.nuvio.app.features.playback.svp.SvpSession
import com.nuvio.app.features.player.AnimeJaNaiMode
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.player.SvpMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RtxHdrEnhancementTest {
    @Test
    fun rtxHdrOwnsTheFilterPipelineBeforeAnimeJaNaiRuntimeOrTitleLookup() = runBlocking {
        val session = AnimeJaNaiSession()
        assertNull(session.planStart(PlayerSettingsUiState(
            animeJaNaiMode = AnimeJaNaiMode.Automatic,
            nvidiaRtxHdrEnabled = true,
        )))
        assertEquals(EnhancementStatus.Kind.Skipped, session.status.value.kind)
        assertEquals("RTX Video HDR is on", session.status.value.reason)
    }

    @Test
    fun rtxHdrOwnsTheFilterPipelineBeforeSvpRuntimeOrTitleLookup() = runBlocking {
        val session = SvpSession()
        assertNull(session.planStart(PlayerSettingsUiState(
            svpMode = SvpMode.Automatic,
            nvidiaRtxHdrEnabled = true,
        )))
        assertEquals(EnhancementStatus.Kind.Skipped, session.status.value.kind)
        assertEquals("RTX Video HDR is on", session.status.value.reason)
    }
}
