package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerTechnicalDetailsTest {
    @Test
    fun `formats loaded video facts in display order`() {
        val snapshot = PlayerPlaybackSnapshot(
            videoWidth = 3840,
            videoHeight = 2160,
            videoCodec = "hevc",
            videoDynamicRange = VideoDynamicRange.DolbyVision,
            videoFrameRate = 23.976f,
        )

        assertEquals("4K • HEVC • DV • 23.976 FPS", snapshot.technicalDetailsLine())
    }

    @Test
    fun `normalizes common codec and resolution labels`() {
        assertEquals(
            "1080p • H.264 • SDR • 59.94 FPS",
            PlayerPlaybackSnapshot(
                videoWidth = 1920,
                videoHeight = 1080,
                videoCodec = "avc1.640028",
                videoDynamicRange = VideoDynamicRange.Sdr,
                videoFrameRate = 59.94f,
            ).technicalDetailsLine(),
        )
        assertEquals(
            "1440p • AV1 • HLG • 25 FPS",
            PlayerPlaybackSnapshot(
                videoWidth = 2560,
                videoHeight = 1440,
                videoCodec = "av01",
                videoDynamicRange = VideoDynamicRange.Hlg,
                videoFrameRate = 25f,
            ).technicalDetailsLine(),
        )
    }

    @Test
    fun `omits unavailable or invalid facts`() {
        assertEquals(
            "720p • HDR",
            PlayerPlaybackSnapshot(
                videoWidth = 1280,
                videoHeight = 720,
                videoDynamicRange = VideoDynamicRange.Hdr,
                videoFrameRate = Float.NaN,
            ).technicalDetailsLine(),
        )
        assertNull(PlayerPlaybackSnapshot().technicalDetailsLine())
    }

    @Test
    fun `maps native media HDR labels to the inline dynamic range`() {
        assertEquals(
            VideoDynamicRange.DolbyVision,
            PlayerMediaInfo(videoCodec = "HEVC", hdr = listOf("DV", "HDR10")).videoDynamicRange(),
        )
        assertEquals(
            VideoDynamicRange.Hlg,
            PlayerMediaInfo(videoCodec = "HEVC", hdr = listOf("HLG")).videoDynamicRange(),
        )
        assertEquals(
            VideoDynamicRange.Sdr,
            PlayerMediaInfo(videoCodec = "AVC").videoDynamicRange(),
        )
        assertNull(PlayerMediaInfo().videoDynamicRange())
    }
}
