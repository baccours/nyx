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
 * A running service is necessarily enabled in system settings, so [isEnabledInSystem] (a binder
 * call) is only consulted when the service is not running.
 */
internal fun deriveUiState(
    serviceRunning: Boolean,
    filterEnabled: Boolean,
    settings: OverlaySettings,
    isEnabledInSystem: () -> Boolean
): UiState = when {
    serviceRunning && filterEnabled -> UiState.FilterOn(settings)
    serviceRunning -> UiState.ServiceIdle
    isEnabledInSystem() -> UiState.Connecting
    else -> UiState.NeedsPermission
}
