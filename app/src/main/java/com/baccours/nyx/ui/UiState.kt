package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings

/** The one thing the main screen can be showing at any time. */
sealed interface UiState {
    /** The accessibility service is not enabled in system settings: show the permission card. */
    data object NeedsPermission : UiState

    /** Enabled in system settings but not connected yet (just enabled, or the process is starting). */
    data object Connecting : UiState

    /** The service is running with the filter switched off. */
    data object ServiceIdle : UiState

    /** The service is running with the filter on: show the controls. */
    data class FilterOn(val settings: OverlaySettings) : UiState
}

/**
 * Collapses the service status into a single [UiState].
 *
 * [enabledInSystem] is only meaningful while the service isn't running (a running service is
 * enabled by definition).
 */
internal fun deriveUiState(
    serviceRunning: Boolean,
    filterEnabled: Boolean,
    settings: OverlaySettings,
    enabledInSystem: Boolean
): UiState = when {
    serviceRunning && filterEnabled -> UiState.FilterOn(settings)
    serviceRunning -> UiState.ServiceIdle
    enabledInSystem -> UiState.Connecting
    else -> UiState.NeedsPermission
}
