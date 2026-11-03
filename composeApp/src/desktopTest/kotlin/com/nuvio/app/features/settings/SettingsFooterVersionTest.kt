package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nuvio.app.core.build.AppVersionPolicy
import org.junit.Rule
import kotlin.test.Test

class SettingsFooterVersionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun footerDisplaysTheDesktopVersionSelectedByThePlatformPolicy() {
        compose.setContent {
            MaterialTheme {
                LazyColumn {
                    settingsFooterContent(isTablet = false)
                }
            }
        }

        compose.onNodeWithText(
            "Version ${AppVersionPolicy.displayVersionName} (${AppVersionPolicy.displayVersionCode})",
        ).assertIsDisplayed()
    }
}
