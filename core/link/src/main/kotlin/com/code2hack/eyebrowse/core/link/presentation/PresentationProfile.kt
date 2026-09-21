package com.code2hack.eyebrowse.core.link.presentation

import kotlinx.serialization.Serializable

/**
 * A measured content rectangle owned by the endpoint that supplied it.
 *
 * The profile is deliberately independent from Phone window callbacks. A receiver may clamp a
 * remote measurement to the existing allocation limits, but it must create a new immutable epoch
 * rather than mutate a profile already used by a presentation or capture reader.
 */
@Serializable
data class PresentationProfile(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
) {
    init {
        require(width in MIN_DIMENSION..MAX_DIMENSION) { "presentation width out of bounds" }
        require(height in MIN_DIMENSION..MAX_DIMENSION) { "presentation height out of bounds" }
        require(width.toLong() * height <= MAX_PIXELS) { "presentation allocation out of bounds" }
        require(densityDpi in MIN_DENSITY..MAX_DENSITY) { "presentation density out of bounds" }
    }

    companion object {
        const val MIN_DIMENSION: Int = 1
        const val MAX_DIMENSION: Int = 4096
        const val MAX_PIXELS: Long = 4_194_304L
        const val MIN_DENSITY: Int = 1
        const val MAX_DENSITY: Int = 640

        /** Host-only fallback; a real RG handoff must replace it with a measured profile. */
        val RG_DESIGN_FALLBACK: PresentationProfile = PresentationProfile(480, 640, 160)

        fun fromMeasured(width: Int, height: Int, densityDpi: Int): PresentationProfile? =
            runCatching { PresentationProfile(width, height, densityDpi) }.getOrNull()

    }
}
