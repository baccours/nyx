package com.baccours.nyx.data

/** How the warm / blue-light part of the filter is rendered. */
enum class FilterEngine {
    /** A translucent overlay window. Works everywhere, but warming it lifts black levels. */
    OVERLAY,

    /**
     * The system's own Night Light color transform (needs the WRITE_SECURE_SETTINGS permission).
     * It is a real per-channel multiply in the display pipeline, so black stays exactly black.
     * Dimming still uses a pure black overlay, which is also an exact multiply.
     */
    SYSTEM
}
