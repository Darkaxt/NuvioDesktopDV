package com.nuvio.app.features.playback.animejanai

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Live end-to-end check of the pinned runtime installer (about 410 MB of downloads). Runs only when
 * `NUVIO_AJN_INSTALL_TEST_ROOT` names an empty scratch directory; otherwise it is a no-op.
 */
class AnimeJaNaiRuntimeInstallerLiveTest {
    @Test
    fun installsAndVerifiesPinnedRuntime() {
        val root = System.getenv("NUVIO_AJN_INSTALL_TEST_ROOT")?.takeIf { it.isNotBlank() } ?: return
        val runtime = AnimeJaNaiRuntime(Path.of(root))
        val installer = AnimeJaNaiRuntimeInstaller(runtime)
        assertNull(installer.install())
        val deadline = System.currentTimeMillis() + 45 * 60 * 1000L
        while (installer.isInstalling && System.currentTimeMillis() < deadline) Thread.sleep(1000)
        val state = installer.state.value
        assertEquals(AnimeJaNaiInstallState.Installed, state, "installer ended with $state")
        assertEquals(AnimeJaNaiRuntime.Availability.Available, runtime.availability())
    }
}
