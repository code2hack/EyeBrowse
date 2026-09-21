package com.code2hack.eyebrowse.phone

import kotlin.math.abs

/**
 * Canonicalizes one-pixel layout jitter for the visible Phone browser viewport.
 *
 * Android window/inset reconfiguration can return to the same logical window shape with a
 * one-pixel measurement difference. That must not silently change the authoritative browser
 * viewport used by the private display/capture path. Large changes (rotation, fold/unfold,
 * multi-window) remain authoritative and replace the shape-specific baseline.
 */
internal class PhoneViewportStability {

    internal data class Viewport(val width: Int, val height: Int, val densityDpi: Int)

    private var portrait: Viewport? = null
    private var landscape: Viewport? = null

    internal fun canonicalize(width: Int, height: Int, densityDpi: Int): Viewport {
        val measured = Viewport(width, height, densityDpi)
        if (width <= 0 || height <= 0 || densityDpi <= 0) {
            return measured
        }
        val prior = if (height >= width) portrait else landscape
        val stable = if (prior != null && prior.densityDpi == densityDpi &&
            abs(prior.width - width) <= JITTER_PX &&
            abs(prior.height - height) <= JITTER_PX
        ) {
            prior
        } else {
            measured
        }
        if (height >= width) {
            portrait = stable
        } else {
            landscape = stable
        }
        return stable
    }

    private companion object {
        const val JITTER_PX = 1
    }
}
