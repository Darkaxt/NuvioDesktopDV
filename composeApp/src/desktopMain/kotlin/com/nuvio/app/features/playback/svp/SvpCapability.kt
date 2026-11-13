package com.nuvio.app.features.playback.svp

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.playback.animejanai.AnimeJaNaiBudgets
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import java.security.MessageDigest

/**
 * Learned SVP capability of this machine (spec P5 "measured machine capability"). Machine-specific
 * and never synced. Keyed by environment (GPU and driver, loaded libmpv build, SVP component
 * versions) and source resolution class; stores the lowest output rate that breached the live frame
 * budget, so later plans pick a lower refresh divisor. Any environment change starts fresh.
 */
internal class SvpCapability(
    private val store: DesktopStorage.Store = DesktopStorage.store("svp_capability"),
    private val environment: () -> String,
) {
    fun breachedTargetMilli(resolutionClass: String): Int? = store.getInt(key(resolutionClass))

    fun recordBreach(resolutionClass: String, targetMilli: Int) {
        val key = key(resolutionClass)
        val previous = store.getInt(key)
        if (previous == null || targetMilli < previous) store.putInt(key, targetMilli)
    }

    private fun key(resolutionClass: String): String = "${sha256(environment()).take(24)}|$resolutionClass"

    companion object {
        fun defaultEnvironment(runtime: SvpRuntime): String {
            val versions = runtime.componentVersions().toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }
            return "${AnimeJaNaiBudgets.gpuIdentity}|${NativePlayerBridge.libmpvIdentity()}|$versions"
        }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
