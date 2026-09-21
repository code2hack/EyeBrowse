package com.code2hack.eyebrowse.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneViewportStabilityTest {

    @Test
    fun `one pixel return jitter snaps to prior same-shape viewport`() {
        val stability = PhoneViewportStability()
        assertEquals(
            PhoneViewportStability.Viewport(1036, 1571, 420),
            stability.canonicalize(1036, 1571, 420),
        )
        assertEquals(
            PhoneViewportStability.Viewport(1036, 1571, 420),
            stability.canonicalize(1036, 1570, 420),
        )
    }

    @Test
    fun `orientation round trip retains independent portrait baseline`() {
        val stability = PhoneViewportStability()
        val portrait = PhoneViewportStability.Viewport(1036, 1571, 420)
        val landscape = PhoneViewportStability.Viewport(2028, 513, 420)
        assertEquals(portrait, stability.canonicalize(1036, 1571, 420))
        assertEquals(landscape, stability.canonicalize(2028, 513, 420))
        assertEquals(portrait, stability.canonicalize(1036, 1570, 420))
    }

    @Test
    fun `material same-orientation resize replaces baseline`() {
        val stability = PhoneViewportStability()
        stability.canonicalize(1036, 1571, 420)
        assertEquals(
            PhoneViewportStability.Viewport(1760, 1680, 420),
            stability.canonicalize(1760, 1680, 420),
        )
    }

    @Test
    fun `density change is never normalized to old baseline`() {
        val stability = PhoneViewportStability()
        stability.canonicalize(1036, 1571, 420)
        assertEquals(
            PhoneViewportStability.Viewport(1036, 1570, 480),
            stability.canonicalize(1036, 1570, 480),
        )
    }
}
