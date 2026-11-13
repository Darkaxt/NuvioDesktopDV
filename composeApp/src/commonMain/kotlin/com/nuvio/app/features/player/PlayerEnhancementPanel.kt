package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.playback.animejanai.ActivePlaybackEnhancement
import com.nuvio.app.features.playback.animejanai.EnhancementStatus
import com.nuvio.app.features.playback.classification.AnimationToggleSurface
import com.nuvio.app.features.playback.classification.platformAnimationToggleRepository
import com.nuvio.app.features.playback.classification.rememberAnimationToggle
import com.nuvio.app.features.playback.classification.toTitleFacts
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.animation_toggle_explicit_desc
import nuvio.composeapp.generated.resources.animation_toggle_suggested_desc
import nuvio.composeapp.generated.resources.animation_toggle_title
import nuvio.composeapp.generated.resources.player_enhancement_active
import nuvio.composeapp.generated.resources.player_enhancement_failed
import nuvio.composeapp.generated.resources.player_enhancement_off
import nuvio.composeapp.generated.resources.player_enhancement_pending
import nuvio.composeapp.generated.resources.player_enhancement_prepare_note
import nuvio.composeapp.generated.resources.player_enhancement_preparation_required
import nuvio.composeapp.generated.resources.player_enhancement_skipped
import nuvio.composeapp.generated.resources.player_enhancement_svp_active
import nuvio.composeapp.generated.resources.player_enhancement_svp_failed
import nuvio.composeapp.generated.resources.player_enhancement_svp_off
import nuvio.composeapp.generated.resources.player_enhancement_svp_pending
import nuvio.composeapp.generated.resources.player_enhancement_svp_skipped
import nuvio.composeapp.generated.resources.player_enhancements_title
import org.jetbrains.compose.resources.stringResource

/** True where the per-title Animation toggle and enhancement status exist (Windows desktop). */
internal val enhancementPanelSupported: Boolean
    get() = platformAnimationToggleRepository() != null

/** The enhancement panel's model plus the action that flips the title's Animation choice. */
internal class PlayerEnhancementControls(
    val state: PlayerControlEnhancementState,
    val toggleAnimation: (() -> Unit)?,
)

/**
 * Builds the player enhancement panel (spec P2): status plus the title's Animation toggle. It is
 * rendered by the native controls layer, which sits above the video surface on Windows; a Compose
 * panel would be drawn beneath that surface. A change is saved immediately; the platform session
 * applies it live or reports it as pending.
 */
@Composable
internal fun rememberPlayerEnhancementControls(
    visible: Boolean,
    parentMetaId: String,
    parentMetaType: String,
    title: String,
    meta: MetaDetails?,
): PlayerEnhancementControls {
    val facts = remember(meta, parentMetaId) { meta?.takeIf { it.id == parentMetaId }?.toTitleFacts() }
    val toggle = rememberAnimationToggle(
        metaId = parentMetaId,
        metaType = parentMetaType,
        displayTitle = meta?.takeIf { it.id == parentMetaId }?.name ?: title,
        facts = facts,
        surface = AnimationToggleSurface.Player,
    )
    val statusFlow by ActivePlaybackEnhancement.status.collectAsState()
    val status = statusFlow?.collectAsState()?.value
    val svpStatusFlow by ActivePlaybackEnhancement.svpStatus.collectAsState()
    val svpStatus = svpStatusFlow?.collectAsState()?.value
    val state = PlayerControlEnhancementState(
        visible = visible,
        panelTitle = stringResource(Res.string.player_enhancements_title),
        statusHeadline = status?.headline().orEmpty(),
        statusReason = status?.reason?.takeIf { it.isNotBlank() }.orEmpty(),
        statusNote = if (status?.kind == EnhancementStatus.Kind.PreparationRequired) {
            stringResource(Res.string.player_enhancement_prepare_note)
        } else {
            ""
        },
        // SVP is shown once it is enabled (Off means the global switch is off).
        svpHeadline = svpStatus?.takeIf { it.kind != EnhancementStatus.Kind.Off }?.svpHeadline().orEmpty(),
        svpReason = svpStatus?.takeIf { it.kind != EnhancementStatus.Kind.Off }?.reason.orEmpty(),
        animationAvailable = toggle != null,
        animationLabel = stringResource(Res.string.animation_toggle_title),
        animationDescription = if (toggle == null) {
            ""
        } else {
            stringResource(
                if (toggle.isExplicit) {
                    Res.string.animation_toggle_explicit_desc
                } else {
                    Res.string.animation_toggle_suggested_desc
                },
            )
        },
        animationChecked = toggle?.enabled == true,
        animationError = toggle?.state?.error.orEmpty(),
    )
    return PlayerEnhancementControls(state, toggle?.let { binding -> { binding.set(!binding.enabled) } })
}

@Composable
private fun EnhancementStatus.headline(): String {
    val base = stringResource(
        when (kind) {
            EnhancementStatus.Kind.Off -> Res.string.player_enhancement_off
            EnhancementStatus.Kind.Pending -> Res.string.player_enhancement_pending
            EnhancementStatus.Kind.Active -> Res.string.player_enhancement_active
            EnhancementStatus.Kind.Skipped -> Res.string.player_enhancement_skipped
            EnhancementStatus.Kind.PreparationRequired -> Res.string.player_enhancement_preparation_required
            EnhancementStatus.Kind.Failed -> Res.string.player_enhancement_failed
        },
    )
    val sizes = if (inputWidth != null && inputHeight != null && outputWidth != null && outputHeight != null) {
        " ${inputWidth}x$inputHeight → ${outputWidth}x$outputHeight"
    } else if (inputWidth != null && inputHeight != null) {
        " ${inputWidth}x$inputHeight"
    } else {
        ""
    }
    return base + sizes
}

@Composable
private fun EnhancementStatus.svpHeadline(): String {
    val base = stringResource(
        when (kind) {
            EnhancementStatus.Kind.Off -> Res.string.player_enhancement_svp_off
            EnhancementStatus.Kind.Pending, EnhancementStatus.Kind.PreparationRequired -> Res.string.player_enhancement_svp_pending
            EnhancementStatus.Kind.Active -> Res.string.player_enhancement_svp_active
            EnhancementStatus.Kind.Skipped -> Res.string.player_enhancement_svp_skipped
            EnhancementStatus.Kind.Failed -> Res.string.player_enhancement_svp_failed
        },
    )
    val source = sourceFps
    val output = outputFps
    val rates = when {
        source != null && output != null -> " ${formatFps(source)} → ${formatFps(output)} fps"
        source != null -> " ${formatFps(source)} fps"
        else -> ""
    }
    val size = if (inputWidth != null && inputHeight != null) " · ${inputWidth}x$inputHeight" else ""
    return base + rates + size
}

/** Up to three decimals without trailing zeros (23.976, 60, 59.94). */
internal fun formatFps(fps: Double): String {
    val milli = kotlin.math.round(fps * 1000).toLong()
    val whole = milli / 1000
    val fraction = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
