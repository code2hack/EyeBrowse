package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test

class RendererViewportTest {
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
