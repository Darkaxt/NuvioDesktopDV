package com.nuvio.app.features.playback.animejanai

import com.nuvio.app.features.player.AnimeJaNaiProfile
import com.nuvio.app.features.player.desktop.DesktopHostOs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal actual fun platformAnimeJaNaiRuntimeControls(): AnimeJaNaiRuntimeControls? =
    if (DesktopHostOs.current == DesktopHostOs.WINDOWS) DesktopAnimeJaNaiRuntimeControls else null

private object DesktopAnimeJaNaiRuntimeControls : AnimeJaNaiRuntimeControls {
    private val controller = AnimeJaNaiPreparationController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val state: StateFlow<AnimeJaNaiRuntimeUiState> =
        combine(controller.installer.state, controller.state, controller.pending) { install, preparation, pending ->
            // Availability is a cheap file check; re-read on every state change (e.g. after install).
            val availability = controller.runtime.availability()
            val installActivity = (install as? AnimeJaNaiInstallState.Running)?.let { running ->
                if (running.totalBytes > 0) {
                    "${running.step} ${running.downloadedBytes / MB}/${running.totalBytes / MB} MB"
                } else {
                    running.step
                }
            }
            val preparationActivity = (preparation as? AnimeJaNaiPreparationState.Preparing)?.let {
                "${it.phase} ${it.width}x${it.height} at ${it.fps} fps (${it.elapsedSeconds} s)"
            }
            AnimeJaNaiRuntimeUiState(
                installed = availability is AnimeJaNaiRuntime.Availability.Available,
                unavailableReason = (availability as? AnimeJaNaiRuntime.Availability.Unavailable)?.reason,
                activity = installActivity ?: preparationActivity,
                error = (install as? AnimeJaNaiInstallState.Failed)?.reason
                    ?: (preparation as? AnimeJaNaiPreparationState.Failed)?.reason,
                pendingPreparation = pending?.key?.describe(),
                lastPrepared = (preparation as? AnimeJaNaiPreparationState.Ready)?.let {
                    val verdict = if (it.qualified) "within budget" else "over budget, AnimeJaNai stays off"
                    "${EngineKey(it.slot, it.width, it.height, it.fps).describe()}: ${it.budget}, $verdict"
                },
            )
        }.stateIn(scope, SharingStarted.Eagerly, AnimeJaNaiRuntimeUiState())

    override fun install(): String? = controller.installer.install()

    override fun preparePending(): String? = controller.preparePending()

    override fun cancelPreparation() = controller.cancel()

    private fun EngineKey.describe(): String {
        val profile = AnimeJaNaiProfile.entries.firstOrNull { it.slot == slot }?.label ?: "slot $slot"
        return "${width}x$height at $fps fps ($profile)"
    }

    private const val MB = 1024L * 1024L
}
