package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings

/** The one thing the main screen can be showing at any time. */
sealed interface UiState {
    /**
     * The accessibility service is not enabled in system settings: show the permission card.
     * [error] says why the service turned itself off, if it did.
     */
    data class NeedsPermission(val error: String? = null) : UiState

    /** Enabled in system settings but not connected yet (just enabled, or the process is starting). */
    data object Connecting : UiState

    /** The service is running with the overlay switched off. */
    data object ServiceIdle : UiState

    /** The service is running with the overlay on: show the controls. */
    data class OverlayOn(val overlaySettings: OverlaySettings) : UiState
}

/**
 * Collapses the service status into a single [UiState].
 *
 * [serviceEnabledInSystem] is only meaningful while the service isn't running (a running service is
 * enabled by definition).
 */
internal fun deriveUiState(
    serviceRunning: Boolean,
    serviceEnabledInSystem: Boolean,
    overlaySettings: OverlaySettings,
    overlayActive: Boolean,
    overlayError: String?
): UiState = when {
    serviceRunning && overlayActive -> UiState.OverlayOn(overlaySettings)
    serviceRunning -> UiState.ServiceIdle
    overlayError != null -> UiState.NeedsPermission(overlayError)
    serviceEnabledInSystem -> UiState.Connecting
    else -> UiState.NeedsPermission()
}
