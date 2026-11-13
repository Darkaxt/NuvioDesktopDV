package com.nuvio.app.features.playback.animejanai

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Spec P6: runtime/model pins and no independent (floating) updates. */
class AnimeJaNaiRuntimePinTest {
    private val sha256 = Regex("[0-9a-f]{64}")

    @Test
    fun everyDownloadIsAnImmutableHashPinnedReleaseAsset() {
        val downloads = AnimeJaNaiRuntimeInstaller.PINNED_DOWNLOADS
        assertEquals(3, downloads.size)
        downloads.forEach { pinned ->
            assertTrue(pinned.sha256.matches(sha256), "${pinned.fileName} hash")
            assertTrue(pinned.bytes > 0, "${pinned.fileName} size")
            // A fixed release tag, never a floating "latest" or branch URL.
            assertTrue(
                Regex("""https://github\.com/[^/]+/[^/]+/releases/download/[^/]+/[^/]+""").matches(pinned.url),
                pinned.url,
            )
            assertTrue("/latest" !in pinned.url, pinned.url)
        }
        AnimeJaNaiRuntimeInstaller.OVERLAY_FILES.forEach { file ->
            assertTrue(file.sha256.matches(sha256), "${file.destination} hash")
        }
        assertEquals(
            AnimeJaNaiRuntimeInstaller.OVERLAY_FILES.size,
            AnimeJaNaiRuntimeInstaller.OVERLAY_FILES.map { it.destination }.toSet().size,
        )
    }

    @Test
    fun verificationRejectsAnyOtherContent() {
        val file = Files.createTempFile("ajn-pin", ".bin")
        Files.writeString(file, "abc")
        // SHA-256("abc")
        AnimeJaNaiRuntimeInstaller.verify(file, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertFailsWith<IllegalStateException> {
            AnimeJaNaiRuntimeInstaller.verify(file, "0".repeat(64))
        }
        assertFailsWith<IllegalStateException> {
            AnimeJaNaiRuntimeInstaller.verify(file.resolveSibling("missing-ajn-pin.bin"), "0".repeat(64))
        }
    }
}
