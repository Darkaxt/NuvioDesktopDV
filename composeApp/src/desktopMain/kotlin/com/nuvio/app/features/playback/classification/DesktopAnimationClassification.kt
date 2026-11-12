package com.nuvio.app.features.playback.classification

import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * JSONL ledger at `<root>/playback-decisions/profile-<id>.jsonl`. Writers are serialized; each
 * record is written as one line and `force(true)`-synced before [append] returns.
 */
class DesktopAnimationDecisionLedger(
    private val directory: Path = DesktopStorage.rootDir.resolve("playback-decisions"),
) : AnimationDecisionLedger {
    private val mutex = Mutex()

    fun fileFor(profileId: String): Path {
        require(profileId.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid profile id '$profileId'" }
        return directory.resolve("profile-$profileId.jsonl")
    }

    override suspend fun append(event: AnimationDecisionEvent) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = fileFor(event.profileId)
            Files.createDirectories(directory)
            FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
                // An unterminated tail is an interrupted, never-acknowledged write: drop it so the new
                // record starts on its own line and earlier history stays intact.
                channel.truncate(lastNewlineEnd(channel))
                channel.position(channel.size())
                val bytes = (AnimationDecisionLedgerCodec.encode(event) + "\n").toByteArray(Charsets.UTF_8)
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        }
    }

    override suspend fun replay(profileId: String): LedgerReplay = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = fileFor(profileId)
            if (!Files.exists(file)) LedgerReplay(emptyMap())
            else AnimationDecisionLedgerCodec.replay(Files.readString(file, Charsets.UTF_8), profileId)
        }
    }

    private fun lastNewlineEnd(channel: FileChannel): Long {
        var position = channel.size()
        val one = ByteBuffer.allocate(1)
        while (position > 0) {
            one.clear()
            channel.read(one, position - 1)
            if (one.get(0) == '\n'.code.toByte()) return position
            position--
        }
        return 0
    }
}

/** Observed facts per profile/title in a small properties store (not a choice store). */
internal class DesktopObservedTitleFactsStore(
    private val store: DesktopStorage.Store = DesktopStorage.store("playback_title_facts"),
) : ObservedTitleFactsStore {
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(profileId: String, title: CanonicalTitle) = "$profileId|${title.key}"

    override fun load(profileId: String, title: CanonicalTitle): ObservedTitleFacts? =
        store.getString(key(profileId, title))?.let {
            runCatching { json.decodeFromString(ObservedTitleFacts.serializer(), it) }.getOrNull()
        }

    override fun save(profileId: String, title: CanonicalTitle, observed: ObservedTitleFacts) {
        store.putString(key(profileId, title), json.encodeToString(ObservedTitleFacts.serializer(), observed))
    }
}

/**
 * Loads the packaged rule artifact and an optional user override at
 * `<root>/playback-content-indicators.json`. An invalid override keeps the last validated set.
 */
class DesktopContentIndicatorRuleLoader(
    private val overrideFile: Path = DesktopStorage.rootDir.resolve(OVERRIDE_FILE_NAME),
    private val packagedBytes: () -> ByteArray = ::readPackagedRules,
) {
    private val packaged: ValidatedRuleSet by lazy {
        validatedOrNull(packagedBytes()).getOrThrow()
    }

    @Volatile
    var current: ValidatedRuleSet? = null
        private set

    /** Precise error for the last override load attempt, or null. */
    @Volatile
    var overrideError: String? = null
        private set

    fun get(): ValidatedRuleSet = current ?: reload()

    @Synchronized
    fun reload(): ValidatedRuleSet {
        val lastValid = current ?: packaged
        val result = if (!Files.exists(overrideFile)) {
            overrideError = null
            packaged
        } else {
            validatedOrNull(runCatching { Files.readAllBytes(overrideFile) }.getOrElse { return fail(lastValid, it) })
                .fold(
                    onSuccess = { overrideError = null; it },
                    onFailure = { return fail(lastValid, it) },
                )
        }
        current = result
        return result
    }

    private fun fail(lastValid: ValidatedRuleSet, error: Throwable): ValidatedRuleSet {
        overrideError = "Override $overrideFile rejected: ${error.message}"
        current = lastValid
        return lastValid
    }

    companion object {
        const val OVERRIDE_FILE_NAME = "playback-content-indicators.json"
        const val RESOURCE_PATH = "playback/playback-content-indicators.json"

        fun readPackagedRules(): ByteArray =
            requireNotNull(DesktopContentIndicatorRuleLoader::class.java.classLoader.getResourceAsStream(RESOURCE_PATH)) {
                "Packaged rule artifact $RESOURCE_PATH is missing"
            }.use { it.readBytes() }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun validatedOrNull(bytes: ByteArray): Result<ValidatedRuleSet> =
            ContentIndicatorRuleParser.parse(bytes.toString(Charsets.UTF_8))
                .map { ValidatedRuleSet(it, sha256(bytes)) }
    }
}

/** Desktop singleton wiring. */
object DesktopAnimationToggles {
    val rules: DesktopContentIndicatorRuleLoader by lazy { DesktopContentIndicatorRuleLoader() }
    val repository: AnimationToggleRepository by lazy {
        AnimationToggleRepository(
            ledger = DesktopAnimationDecisionLedger(),
            factsStore = DesktopObservedTitleFactsStore(),
            ruleSet = { rules.get() },
        )
    }
}
