package com.nuvio.app.features.player.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec P6: the bundled Windows libmpv runtime matches `runtime-pins.sha256`, the pin list the
 * release workflow and `scripts/install_windows_libmpv.ps1` also enforce.
 */
class WindowsRuntimePinTest {
    private val nativeDir: Path = Paths.get("src/desktopMain/native/windows")

    private fun pins(): Map<String, String> =
        Files.readAllLines(nativeDir.resolve("runtime-pins.sha256"))
            .filter { it.isNotBlank() }
            .associate { line ->
                val (hash, name) = line.trim().split(Regex("\\s+"), limit = 2)
                name.trimStart('*') to hash
            }

    @Test
    fun pinListCoversTheOwnedRuntime() {
        val pins = pins()
        assertEquals(setOf("libmpv-2.dll", "ajn-mvtools.dll"), pins.keys)
        pins.values.forEach { assertTrue(it.matches(Regex("[0-9a-f]{64}")), it) }
        // Every pinned DLL is the only runtime content: nothing unpinned is bundled.
        val bundled = Files.list(nativeDir.resolve("runtime")).use { files ->
            files.filter { it.toString().endsWith(".dll") }.map { it.fileName.toString() }.toList()
        }
        assertEquals(pins.keys, bundled.toSet())
    }

    @Test
    fun bundledFilesMatchTheirPins() {
        pins().forEach { (name, expected) ->
            val file = nativeDir.resolve("runtime").resolve(name)
            assertTrue(file.isRegularFile(), "$name is missing")
            assertTrue(Files.size(file) > 100_000, "$name is a Git LFS pointer; run git lfs pull")
            assertEquals(expected, sha256(file), name)
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
