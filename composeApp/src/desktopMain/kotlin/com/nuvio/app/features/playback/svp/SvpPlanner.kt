package com.nuvio.app.features.playback.svp

import java.nio.file.Path
import kotlin.math.roundToInt

/** Measured stream/display facts a plan is derived from. */
internal data class SvpInput(
    val sourceFps: Double?,
    val displayFps: Double?,
    val width: Int,
    val height: Int,
    /** `video-dec-params/gamma` (pq/hlg = HDR). */
    val gamma: String,
    /** Lowest target (milli-fps) that breached the frame budget for this environment and size class. */
    val breachedTargetMilli: Int? = null,
)

internal sealed interface SvpDecision {
    data class Interpolate(
        val sourceFps: Double,
        val targetMilli: Int,
        val blockWidth: Int,
        val resolutionClass: String,
    ) : SvpDecision {
        val targetFps: Double get() = targetMilli / 1000.0
    }

    data class Skip(val reason: String) : SvpDecision
}

/**
 * Pure SVP planning (spec P5): picks a sustainable output cadence from the actual source FPS, the
 * display refresh, the source resolution and learned machine capability. Targets are integer
 * divisors of the refresh rate (judder-free cadence), never a hardcoded 60, and interpolation is
 * skipped when the source cadence is already adequate.
 */
internal object SvpPlanner {
    /** Sources at or above this rate are already high frame rate. */
    const val HIGH_FRAME_RATE = 47.0

    /** A target must be at least this many times the source rate to be worth interpolating. */
    const val MIN_GAIN = 1.5

    fun resolutionClass(width: Int, height: Int): String {
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        return when {
            longSide <= 1920 && shortSide <= 1080 -> "1080"
            longSide <= 2560 && shortSide <= 1440 -> "1440"
            longSide <= 3840 && shortSide <= 2160 -> "2160"
            else -> "above2160"
        }
    }

    /** Upper bound of the output rate by source size: motion search cost scales with the frame area. */
    fun targetCapFps(resolutionClass: String): Double? = when (resolutionClass) {
        "1080" -> 165.0
        "1440" -> 120.0
        "2160" -> 60.0
        else -> null
    }

    fun plan(input: SvpInput): SvpDecision {
        val source = input.sourceFps?.takeIf { it > 1.0 } ?: return SvpDecision.Skip("Source frame rate is unknown")
        if (source >= HIGH_FRAME_RATE) return SvpDecision.Skip("Source is already high frame rate (%.3f fps)".format(source))
        val gamma = input.gamma.trim().lowercase()
        if (gamma == "pq" || gamma == "hlg") return SvpDecision.Skip("HDR/Dolby Vision is not verified for SVP")
        if (input.width <= 0 || input.height <= 0) return SvpDecision.Skip("Video size is unknown")
        val display = input.displayFps?.takeIf { it > 1.0 } ?: return SvpDecision.Skip("Display refresh rate is unknown")
        if (display < source * MIN_GAIN) {
            return SvpDecision.Skip("Display refresh (%.2f Hz) is not above the source cadence".format(display))
        }
        val resolutionClass = resolutionClass(input.width, input.height)
        val cap = targetCapFps(resolutionClass) ?: return SvpDecision.Skip("Source is larger than 4K")
        val candidates = (1..4)
            .map { divisor -> ((display / divisor) * 1000).roundToInt() }
            .filter { it >= source * MIN_GAIN * 1000 && it <= cap * 1000 + 0.5 }
        if (candidates.isEmpty()) {
            return SvpDecision.Skip("No refresh-rate divisor between %.0f and %.0f fps".format(source * MIN_GAIN, cap))
        }
        val sustainable = candidates.filter { input.breachedTargetMilli == null || it < input.breachedTargetMilli }
        val target = sustainable.maxOrNull()
            ?: return SvpDecision.Skip("Not sustainable on this machine (frame drops at %.2f fps)".format(candidates.min() / 1000.0))
        val blockWidth = if (resolutionClass == "1080") 16 else 32
        return SvpDecision.Interpolate(source, target, blockWidth, resolutionClass)
    }

    /** `concurrent-frames` for vf_vapoursynth: bounded by the CPU and a fixed ceiling. */
    fun concurrentFrames(processors: Int = Runtime.getRuntime().availableProcessors()): Int =
        processors.coerceIn(2, 8)

    const val INITIAL_BUFFERED_FRAMES = 8
    const val MAX_BUFFERED_FRAMES = 24

    /** Next `buffered-frames` after vf_vapoursynth asked for more, or null at the ceiling. */
    fun increasedBufferedFrames(current: Int): Int? =
        (current * 2).coerceAtMost(MAX_BUFFERED_FRAMES).takeIf { it > current }

    /**
     * The VapourSynth script for [decision]. 10-bit sources keep their depth for the rendered
     * frames; motion analysis runs on an 8-bit copy, as SVP's own generator does.
     */
    fun script(decision: SvpDecision.Interpolate, flow1: Path, flow2: Path): String {
        val superParams = "{pel:2,gpu:1}"
        val analyseParams = "{block:{w:${decision.blockWidth},overlap:2},main:{search:{coarse:{distance:-10}}},refine:[{thsad:200}]}"
        val smoothParams = "{rate:{num:${decision.targetMilli},den:1000,abs:true},algo:13,mask:{area:100},scene:{}}"
        val fallbackFps = "%.6f".format(java.util.Locale.ROOT, decision.sourceFps)
        return buildString {
            appendLine("# Generated by NuvioDV for SVP interpolation; do not edit.")
            appendLine("import vapoursynth as vs")
            appendLine("core = vs.core")
            appendLine("if not hasattr(core, 'svp1'):")
            appendLine("    core.std.LoadPlugin(r\"$flow1\")")
            appendLine("if not hasattr(core, 'svp2'):")
            appendLine("    core.std.LoadPlugin(r\"$flow2\")")
            appendLine("clip = video_in")
            appendLine("src_fps = container_fps if container_fps > 0.1 else $fallbackFps")
            appendLine("if clip.format.id not in (vs.YUV420P8, vs.YUV420P10):")
            appendLine("    clip = clip.resize.Bicubic(format=vs.YUV420P8)")
            appendLine("clip8 = clip if clip.format.id == vs.YUV420P8 else clip.resize.Point(format=vs.YUV420P8)")
            appendLine("sup = core.svp1.Super(clip8, \"$superParams\")")
            appendLine("vec = core.svp1.Analyse(sup[\"clip\"], sup[\"data\"], clip8, \"$analyseParams\")")
            appendLine(
                "smooth = core.svp2.SmoothFps(clip, sup[\"clip\"], sup[\"data\"], vec[\"clip\"], vec[\"data\"], " +
                    "\"$smoothParams\", src=clip, fps=src_fps)",
            )
            appendLine("smooth = core.std.AssumeFPS(smooth, fpsnum=smooth.fps_num, fpsden=smooth.fps_den)")
            appendLine("smooth.set_output()")
        }
    }

    /** `vf add` argument for the labelled filter; the path uses mpv's length-prefixed quoting. */
    fun filter(scriptPath: Path, bufferedFrames: Int, concurrentFrames: Int): String {
        val path = scriptPath.toString()
        val length = path.toByteArray(Charsets.UTF_8).size
        return "@${SvpRuntime.FILTER_LABEL}:vapoursynth=file=%$length%$path" +
            ":buffered-frames=$bufferedFrames:concurrent-frames=$concurrentFrames"
    }
}
