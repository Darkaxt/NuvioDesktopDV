package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.playback.animejanai.ActivePlaybackEnhancement
import com.nuvio.app.features.playback.animejanai.EnhancementStatus
import com.nuvio.app.features.playback.classification.AnimationToggleSurface
import com.nuvio.app.features.playback.classification.platformAnimationToggleRepository
import com.nuvio.app.features.playback.classification.rememberAnimationToggle
import com.nuvio.app.features.playback.classification.toTitleFacts
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
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
import nuvio.composeapp.generated.resources.player_enhancements_title
import org.jetbrains.compose.resources.stringResource

/** True where the per-title Animation toggle and enhancement status exist (Windows desktop). */
internal val enhancementPanelSupported: Boolean
    get() = platformAnimationToggleRepository() != null

/**
 * Player side panel with the enhancement status and the title's Animation toggle (spec P2). A change
 * is saved immediately; the platform session applies it live or reports it as pending.
 */
@Composable
internal fun PlayerEnhancementPanel(
    visible: Boolean,
    parentMetaId: String,
    parentMetaType: String,
    title: String,
    meta: MetaDetails?,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
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

    PlayerSidePanel(visible = visible, onDismiss = onDismiss, width = 420.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayerPanelHeader(title = stringResource(Res.string.player_enhancements_title)) {
                PlayerDialogButton(label = stringResource(Res.string.action_close), onClick = onDismiss)
            }

            if (status != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = status.headline(),
                        color = tokens.colors.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    status.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(text = reason, color = tokens.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (status.kind == EnhancementStatus.Kind.PreparationRequired) {
                        Text(
                            text = stringResource(Res.string.player_enhancement_prepare_note),
                            color = tokens.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (toggle != null) {
                val label = stringResource(Res.string.animation_toggle_title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = toggle.enabled,
                            role = Role.Switch,
                            onValueChange = toggle::set,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = label, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = stringResource(
                                if (toggle.isExplicit) {
                                    Res.string.animation_toggle_explicit_desc
                                } else {
                                    Res.string.animation_toggle_suggested_desc
                                },
                            ),
                            color = tokens.colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        toggle.state.error?.let { error ->
                            Spacer(Modifier.height(4.dp))
                            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    // The row owns the toggle semantics and focus; the switch is visual only.
                    Switch(checked = toggle.enabled, onCheckedChange = null)
                }
            }
        }
    }
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
