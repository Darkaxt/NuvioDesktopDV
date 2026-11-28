package com.nuvio.app.core.storage

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** NuvioDV: desktop tests must never touch the user's real NuvioDV profile. */
class DesktopTestStorageIsolationTest {
    @Test
    fun `desktop tests use the isolated storage root`() {
        val override = assertNotNull(System.getProperty(DesktopStorage.STORAGE_ROOT_PROPERTY))
        val root = Paths.get(override).toAbsolutePath().normalize()
        assertTrue(DesktopStorage.rootDir.toAbsolutePath().normalize().startsWith(root), "data dir ${DesktopStorage.rootDir}")
        assertTrue(DesktopStorage.cacheDir.toAbsolutePath().normalize().startsWith(root), "cache dir ${DesktopStorage.cacheDir}")
    }
}
