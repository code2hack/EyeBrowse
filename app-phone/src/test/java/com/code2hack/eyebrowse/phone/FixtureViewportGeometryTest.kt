package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test

class FixtureViewportGeometryTest {
    @Test fun measuredWebViewScaleExplainsNative160VersusCss75() {
        val geometry=FixtureViewportGeometry(2.1473214626312256,480,405)
        assertEquals(75,geometry.cssDelta(160))
        assertEquals(-75,geometry.cssDelta(-160))
        assertNotEquals(geometry.cssDelta(160),kotlin.math.round(160/(204/160.0)).toInt())
    }
    @Test fun clickUsesObservedRectInViewportPixels() {
        val geometry=FixtureViewportGeometry(2.1473214626312256,480,405)
        val (x,y)=geometry.center(16.0,72.0,140.0,40.0)
        assertEquals(184.6696,x,0.001);assertEquals(197.5536,y,0.001)
    }
    @Test fun invalidScaleAndOffscreenTargetFailInsteadOfClamping() {
        assertThrows(IllegalArgumentException::class.java) { FixtureViewportGeometry(Double.NaN,480,405) }
        assertThrows(IllegalArgumentException::class.java) { FixtureViewportGeometry(0.0,480,405) }
        assertThrows(IllegalArgumentException::class.java) { FixtureViewportGeometry(2.0,480,405).center(0.0,300.0,10.0,10.0) }
    }
}
