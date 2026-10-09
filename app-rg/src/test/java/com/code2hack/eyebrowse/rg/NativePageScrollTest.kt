package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class NativePageScrollTest {
    @Test fun finalUpwardTickStopsAtNativeZeroInsteadOfCrossingIt() {
        // View.scrollBy stores this sum before the renderer applies its own bounds.
        assertEquals(0, 1 + nativePageScrollDelta(1, -4))
        assertEquals(0, 60 + nativePageScrollDelta(60, -63))
        assertEquals(0, nativePageScrollDelta(0, -18))
    }

    @Test fun realIntegratorReachesZeroAtEveryPresetAndLeavesImmediatelyWithoutDebt() {
        val bounds=PointerBounds(8f,8f,472f,632f)
        val top=PointerPosition(240f,8f,true)
        val bottom=PointerPosition(240f,632f,true)
        for(speed in listOf(120,240,360)) {
            val motion=EdgeScrollMotion()
            var y=60
            // Same eligibility check, integrator and writer delta as the Activity's callback.
            for(t in 1L..1001L step 16) {
                val now=t*1_000_000
                val pixels=motion.step(top,bounds,now+250_000_000,now,speed)
                if(y>0) y+=nativePageScrollDelta(y,pixels) else motion.stop()
            }
            assertEquals("held native top at speed=$speed",0,y)
            val now=2_000_000_000L
            assertEquals(0,motion.step(bottom,bounds,now+250_000_000,now,speed))
            val pixels=motion.step(bottom,bounds,now+266_000_000,now+16_000_000,speed)
            assertEquals(speed*16/1000,nativePageScrollDelta(y,pixels))
            assertTrue("opposite edge moves immediately",pixels>0)
        }
    }

    @Test fun interiorAndDownwardNativeDeltasKeepTheirDeclaredRate() {
        assertEquals(-4,nativePageScrollDelta(60,-4))
        assertEquals(4,nativePageScrollDelta(0,4))
        assertEquals(18,nativePageScrollDelta(60,18))
        assertEquals(0,nativePageScrollDelta(60,0))
    }
}
