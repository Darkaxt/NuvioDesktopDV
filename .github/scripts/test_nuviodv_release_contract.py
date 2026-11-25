#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/desktop-release.yml"
VERSION = ROOT / "composeApp/Configuration/DesktopVersion.properties"
UPDATER = ROOT / "composeApp/src/desktopMain/kotlin/com/nuvio/app/features/updater/AppUpdaterPlatform.desktop.kt"
BUILD = ROOT / "composeApp/build.gradle.kts"


class NuvioDvReleaseContractTest(unittest.TestCase):
    def test_every_tracked_submodule_has_a_url_mapping(self):
        entries = subprocess.check_output(['git', 'ls-files', '--stage'], cwd=ROOT, text=True).splitlines()
        gitlinks = {entry.split('\t', 1)[1] for entry in entries if entry.startswith('160000 ')}
        paths = subprocess.check_output(['git', 'config', '--file', '.gitmodules', '--get-regexp',
            r'^submodule\..*\.path$'], cwd=ROOT, text=True).splitlines()
        configured = {}
        for line in paths:
            key, path = line.split(' ', 1)
            configured[path] = subprocess.check_output(['git', 'config', '--file', '.gitmodules',
                '--get', key.removesuffix('.path') + '.url'], cwd=ROOT, text=True).strip()
        self.assertEqual(set(), gitlinks - {path for path, url in configured.items() if url})

    def test_release_jobs_require_simkl_client_id(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        required_blocks = re.findall(r"required_properties=\(\s*(.*?)\s*\)", workflow, re.DOTALL)
        self.assertGreaterEqual(len(required_blocks), 2)
        self.assertTrue(all("SIMKL_CLIENT_ID" in block for block in required_blocks))

    def test_simkl_client_id_is_generated_from_release_properties(self):
        build = BUILD.read_text(encoding="utf-8")
        self.assertIn('props.getProperty("SIMKL_CLIENT_ID", "")', build)

    def test_version_preserves_upstream_base_and_fork_revision(self):
        properties = VERSION.read_text(encoding="utf-8")
        self.assertRegex(properties, r"(?m)^VERSION_NAME=0.1.39-nuviodv\.\d+$")
        self.assertRegex(properties, r"(?m)^VERSION_CODE=\d+$")

    def test_updater_targets_renamed_fork_and_normal_releases(self):
        updater = UPDATER.read_text(encoding="utf-8")
        self.assertIn('owner = "Darkaxt"', updater)
        self.assertIn('repo = "NuvioDesktopDV"', updater)
        self.assertIn("includePrereleases = false", updater)

    def test_windows_package_keeps_distinct_fork_identity(self):
        build = BUILD.read_text(encoding="utf-8")
        self.assertIn('packageName = "NuvioDV"', build)
        self.assertIn('menuGroup = "NuvioDV"', build)


if __name__ == "__main__":
    unittest.main()
