package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test

class RendererViewportTest {
    @Test fun recordedWebView99ViewportUsesBothUpwardConversions() {
        val recorded = RendererViewport(171.022216796875, 123.02222442626953, 1.0, 2.8125, true)
        assertTrue(recorded.matches(480, 344, 2.8125f))
        assertFalse(recorded.matches(480, 240, 2.8125f))
        assertFalse(recorded.matches(480, 344, 1.275f)) // Private display DPI is not this scale.
        assertFalse(recorded.copy(pageFocused = false).matches(480, 344, 2.8125f))
    }
    @Test fun fractionalDipBoundaryRequiresExactQuantizedExtentNotPixelTolerance() {
        fun viewport(pixelWidth: Int) = RendererViewport(pixelWidth / 2.8125, 346.0 / 2.8125, 1.0, 2.8125, true)
        assertTrue(viewport(481).matches(480, 344, 2.8125f))
        assertFalse(viewport(480).matches(480, 344, 2.8125f))
        assertFalse(viewport(482).matches(480, 344, 2.8125f))
        assertFalse(viewport(481).matches(482, 344, 2.8125f))
        assertTrue(viewport(484).matches(482, 344, 2.8125f))
        assertTrue(RendererViewport(241.0, 172.0, 1.0, 2.0, true).matches(481, 344, 2f))
    }
    @Test fun sharedLogicalBucketCannotDistinguishNativeSizeOrEstablishFreshness() {
        val viewport = RendererViewport(481.0 / 2.8125, 346.0 / 2.8125, 1.0, 2.8125, true)
        // Exact native/image dimensions and independent context/reader fences remain mandatory.
        assertTrue(viewport.matches(480, 344, 2.8125f))
        assertTrue(viewport.matches(479, 345, 2.8125f))
        assertFalse(viewport.matches(482, 346, 2.8125f))
    }
    @Test fun nativeFloatConversionDoesNotInventAnExtraBoundaryPixel() {
        val nativeScale = 1.2f
        val viewport = RendererViewport(10.0, 20.0, 1.0, nativeScale.toDouble(), true)
        assertTrue(viewport.matches(12, 24, nativeScale))
        // Doing the second conversion in double precision would incorrectly ceil to 13/25.
        assertEquals(13.0, kotlin.math.ceil(10 * nativeScale.toDouble()), 0.0)
        assertFalse(viewport.copy(width = 13.0 / nativeScale).matches(12, 24, nativeScale))
    }
    @Test fun invalidNativeDimensionsAndNonFiniteConversionsCannotQualify() {
        val valid = RendererViewport(480.0, 240.0, 1.0, 1.0, true)
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(valid.copy(width = invalid).matches(480, 240, 1f))
            assertFalse(valid.copy(height = invalid).matches(480, 240, 1f))
            assertFalse(valid.copy(scale = invalid).matches(480, 240, 1f))
            assertFalse(valid.copy(devicePixelRatio = invalid).matches(480, 240, 1f))
            assertFalse(valid.matches(480, 240, invalid.toFloat()))
        }
        assertFalse(valid.matches(0, 240, 1f))
        assertFalse(valid.matches(480, -1, 1f))
        assertFalse(valid.copy(width = Double.MAX_VALUE).matches(Int.MAX_VALUE, 240, 2f))
        assertFalse(valid.copy(devicePixelRatio = Double.MIN_VALUE).matches(480, 240, 1f))
    }
    @Test fun mapsMeasuredCssWithActualScaleRatherThanDeviceDensity() {
        val v = RendererViewport(480.0/1.275, 240.0/1.275, 1.0, 2.0, true)
        assertTrue(v.matches(480, 240, 1.275f))
        assertFalse(v.matches(480, 240, 2f))
    }
    @Test fun oldViewportCannotQualifyNewImageAllocation() {
        val old = RendererViewport(480.0, 344.0, 1.0, 1.0, true)
        assertFalse(old.matches(480, 240, 1f))
        assertTrue(old.matches(480, 344, 1f)) // Epoch/reader identity is an additional fence.
    }
    @Test fun absentFocusOrInvalidMeasurementsNeverQualify() {
        val valid = RendererViewport(480.0, 240.0, 1.0, 1.0, true)
        assertFalse(valid.copy(pageFocused=false).matches(480,240,1f))
        assertFalse(valid.copy(height=Double.NaN).matches(480,240,1f))
        assertFalse(valid.copy(width=Double.POSITIVE_INFINITY).matches(480,240,1f))
        assertFalse(valid.matches(480,240,0f))
    }
}
