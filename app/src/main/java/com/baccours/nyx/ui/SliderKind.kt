package com.baccours.nyx.ui

import com.baccours.nyx.data.OverlaySettings
import com.baccours.nyx.util.SliderMapping

/** The settings sliders, and how each maps its progress (0..[SliderMapping.STEPS]) to [OverlaySettings]. */
enum class SliderKind(
    val progressOf: (OverlaySettings) -> Int,
    val withProgress: (OverlaySettings, Int) -> OverlaySettings
) {
    DIMMING(
        { SliderMapping.fractionToProgress(it.dimIntensity) },
        { s, p -> s.copy(dimIntensity = SliderMapping.progressToFraction(p)) }
    ),
    BLUE_LIGHT(
        { SliderMapping.fractionToProgress(it.blueLightIntensity) },
        { s, p -> s.copy(blueLightIntensity = SliderMapping.progressToFraction(p)) }
    ),
    TEMPERATURE(
        { SliderMapping.kelvinToProgress(it.colorTemperature) },
        { s, p -> s.copy(colorTemperature = SliderMapping.progressToKelvin(p)) }
    ),
    BLACK_LEVEL(
        { SliderMapping.fractionToProgress(it.blackLevel) },
        { s, p -> s.copy(blackLevel = SliderMapping.progressToFraction(p)) }
    )
}
