#!/usr/bin/env python3
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
CONTINUE_ROW = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/features/home/components/HomeContinueWatchingSection.kt"
CONTINUE_SHEET = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/ContinueWatchingActionSheet.kt"
MAIN_APP = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt"
FLOATING_PROMPT = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/FloatingPrompt.kt"
SETTINGS = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsScreen.kt"
SYNC = ROOT / "composeApp/src/commonMain/kotlin/com/nuvio/app/core/sync/ProfileSettingsSync.kt"


class RpdbPortraitCoverageTest(unittest.TestCase):
    def test_continue_watching_portrait_row_uses_rpdb_with_fallback(self):
        source = CONTINUE_ROW.read_text(encoding="utf-8")
        poster_card = source[source.index("private fun ContinueWatchingPosterCard(") :]
        self.assertIn("rpdbPortraitSelection", poster_card)
        self.assertIn("cwFallbackUrl = rpdbPoster.fallbackUrl", poster_card)

    def test_continue_watching_wide_strip_preserves_thumbnail_priority(self):
        source = CONTINUE_ROW.read_text(encoding="utf-8")
        wide_card = source[
            source.index("private fun ContinueWatchingWideCard(") :
            source.index("private fun ContinueWatchingPosterCard(")
        ]
        self.assertIn("continueWatchingWideArtworkUrl", wide_card)
        self.assertNotIn("continueWatchingPosterArtworkUrl", wide_card)
        self.assertIn("rpdbPortraitSelection", wide_card)
        self.assertIn("fallbackUrl = rpdbPoster.fallbackUrl", wide_card)

    def test_action_sheet_and_resume_prompt_use_rpdb_fallbacks(self):
        sheet = CONTINUE_SHEET.read_text(encoding="utf-8")
        main = MAIN_APP.read_text(encoding="utf-8")
        prompt = FLOATING_PROMPT.read_text(encoding="utf-8")
        self.assertIn("rpdbPortraitSelection", sheet)
        self.assertIn("rpdbPoster.fallbackUrl", sheet)
        self.assertIn("rpdbPortraitSelection", main)
        self.assertIn("fallbackImageUrl = resumePromptPoster", main)
        self.assertIn("fallbackImageUrl: String?", prompt)

    def test_settings_and_sync_expose_enabled_state_without_cloud_key(self):
        settings = SETTINGS.read_text(encoding="utf-8")
        sync = SYNC.read_text(encoding="utf-8")
        self.assertIn("SettingsPage.Rpdb", settings)
        self.assertIn("RpdbSettingsStorage.exportToSyncPayload()", sync)
        self.assertIn("PROFILE_RPDB_SETTINGS_FEATURE", sync)


if __name__ == "__main__":
    unittest.main()
