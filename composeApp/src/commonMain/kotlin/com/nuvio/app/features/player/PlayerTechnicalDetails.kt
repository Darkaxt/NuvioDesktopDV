package com.nuvio.app.features.player

import kotlin.math.abs
import kotlin.math.round

internal fun PlayerPlaybackSnapshot.technicalDetailsLine(): String? =
    listOfNotNull(
        technicalResolutionLabel(videoWidth, videoHeight),
        technicalCodecLabel(videoCodec),
        videoDynamicRange?.label,
        technicalFrameRateLabel(videoFrameRate),
    ).takeIf { it.isNotEmpty() }?.joinToString(" • ")

internal fun technicalCodecLabel(codec: String?): String? {
    val normalized = codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        normalized.contains("hevc") || normalized.contains("h265") || normalized.startsWith("hev1") ||
            normalized.startsWith("hvc1") -> "HEVC"
        normalized.contains("h264") || normalized.contains("avc") -> "H.264"
        normalized.contains("av1") || normalized.startsWith("av01") -> "AV1"
        normalized.contains("vp9") || normalized.startsWith("vp09") -> "VP9"
        normalized.contains("mpeg2") -> "MPEG-2"
        else -> codec.trim().uppercase()
    }
}

private fun technicalResolutionLabel(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    return when {
        width >= 3800 || height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 576 -> "576p"
        height >= 480 -> "480p"
        else -> "${width}×${height}"
    }
}

private fun technicalFrameRateLabel(frameRate: Float?): String? {
    val fps = frameRate?.toDouble()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val rounded = round(fps * 1000.0) / 1000.0
    val value = if (abs(rounded - round(rounded)) < 0.0005) {
        round(rounded).toInt().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
    return "$value FPS"
}
