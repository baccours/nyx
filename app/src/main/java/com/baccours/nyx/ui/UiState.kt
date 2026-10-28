package com.baccours.nyx.ui

/** The one thing the main screen can be showing at any time. Slider values live in a separate flow. */
sealed interface UiState {
    /**
     * The accessibility service is not enabled in system settings: show the permission card.
     * [error] says why the service turned itself off, if it did.
     */
    data class NeedsPermission(val error: String? = null) : UiState

    /** Enabled in system settings but not connected yet (just enabled, or the process is starting). */
    data object Connecting : UiState

    /** Enabled in system settings but still not connected after a timeout: likely killed or stuck. */
    data object NotResponding : UiState

    /** The service is running with the overlay switched off. */
    data object ServiceIdle : UiState

    /** The service is running with the overlay on: show the controls. */
    data object OverlayOn : UiState
}

/** One-shot effects for the activity. */
sealed interface UiEvent {
    data object OpenAccessibilitySettings : UiEvent
}

/**
 * Collapses the service status into a single [UiState].
 *
 * [serviceEnabledInSystem] is only meaningful while the service isn't running (a running service is
 * enabled by definition). [connectTimedOut] only matters while enabled but not connected.
 */
internal fun deriveUiState(
    serviceRunning: Boolean,
    serviceEnabledInSystem: Boolean,
    overlayActive: Boolean,
    overlayError: String?,
    connectTimedOut: Boolean
): UiState = when {
    serviceRunning && overlayActive -> UiState.OverlayOn
    serviceRunning -> UiState.ServiceIdle
    overlayError != null -> UiState.NeedsPermission(overlayError)
    serviceEnabledInSystem && connectTimedOut -> UiState.NotResponding
    serviceEnabledInSystem -> UiState.Connecting
    else -> UiState.NeedsPermission()
}
