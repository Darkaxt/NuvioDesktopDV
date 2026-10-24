package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.rpdb.RPDB_PUBLIC_API_KEY
import com.nuvio.app.features.rpdb.RpdbSettings
import com.nuvio.app.features.rpdb.RpdbSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.settings_rpdb_api_key_description
import nuvio.composeapp.generated.resources.settings_rpdb_api_key_label
import nuvio.composeapp.generated.resources.settings_rpdb_api_key_title
import nuvio.composeapp.generated.resources.settings_rpdb_custom_key_active
import nuvio.composeapp.generated.resources.settings_rpdb_enable
import nuvio.composeapp.generated.resources.settings_rpdb_enable_description
import nuvio.composeapp.generated.resources.settings_rpdb_public_key_active
import nuvio.composeapp.generated.resources.settings_rpdb_section_api_key
import nuvio.composeapp.generated.resources.settings_rpdb_section_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.rpdbSettingsContent(isTablet: Boolean, settings: RpdbSettings) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_rpdb_section_title),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_rpdb_enable),
                    description = stringResource(Res.string.settings_rpdb_enable_description),
                    checked = settings.enabled,
                    isTablet = isTablet,
                    onCheckedChange = RpdbSettingsRepository::setEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                RpdbInfoRow(
                    isTablet = isTablet,
                    text = if (settings.usesPublicApiKey) {
                        stringResource(Res.string.settings_rpdb_public_key_active, RPDB_PUBLIC_API_KEY)
                    } else {
                        stringResource(Res.string.settings_rpdb_custom_key_active)
                    },
                )
            }
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_rpdb_section_api_key),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                RpdbApiKeyRow(
                    isTablet = isTablet,
                    value = settings.apiKey,
                    onApiKeyCommitted = RpdbSettingsRepository::setApiKey,
                )
            }
        }
    }
}

@Composable
private fun RpdbApiKeyRow(
    isTablet: Boolean,
    value: String,
    onApiKeyCommitted: (String) -> Unit,
) {
    val horizontalPadding = if (isTablet) 20.dp else 16.dp
    val verticalPadding = if (isTablet) 16.dp else 14.dp
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    val normalizedDraft = draft.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.settings_rpdb_api_key_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(Res.string.settings_rpdb_api_key_description, RPDB_PUBLIC_API_KEY),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSecretTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.settings_rpdb_api_key_label),
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    draft = normalizedDraft
                    onApiKeyCommitted(normalizedDraft)
                },
                enabled = normalizedDraft != value,
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }
}

@Composable
private fun RpdbInfoRow(isTablet: Boolean, text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 14.dp else 12.dp,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
