package com.baccours.nyx.data

/** The three overlay values, always read, written and persisted together as a single unit. */
data class OverlaySettings(
    val dimIntensity: Float = 0.3f,
    val blueLightIntensity: Float = 0.0f,
    val colorTemperature: Float = 3400f
) {
    /** Returns a copy with every value clamped to its valid range (e.g. after a restore). */
    fun sanitized() = OverlaySettings(
        dimIntensity = dimIntensity.finiteOr(DEFAULT.dimIntensity).coerceIn(0f, 1f),
        blueLightIntensity = blueLightIntensity.finiteOr(DEFAULT.blueLightIntensity).coerceIn(0f, 1f),
        colorTemperature = colorTemperature.finiteOr(DEFAULT.colorTemperature).coerceIn(MIN_KELVIN, MAX_KELVIN)
    )

    private fun Float.finiteOr(fallback: Float) = if (isFinite()) this else fallback

    companion object {
        const val MIN_KELVIN = 1000f
        const val MAX_KELVIN = 7000f

        /** Single source of truth for default values. */
        val DEFAULT = OverlaySettings()
    }
}
