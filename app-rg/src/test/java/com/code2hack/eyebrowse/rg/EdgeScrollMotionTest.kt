package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

class EdgeScrollMotionTest {
    private val bounds=PointerBounds(8f,8f,472f,632f)
    private fun point(y: Float, x: Float=240f)=PointerPosition(x,y,true)
    @Test fun onlyLiteralFullScreenVerticalContactIncludingCornersScrolls() {
        val m=EdgeScrollMotion()
        for(x in listOf(8f,240f,472f)) {
            assertEquals(-1,m.direction(point(8f,x),bounds,1_000_000_000,1))
            assertEquals(1,m.direction(point(632f,x),bounds,1_000_000_000,1))
            for(y in listOf(8.001f,48f,320f,440f,631.999f))
                assertEquals(0,m.direction(point(y,x),bounds,1_000_000_000,1))
        }
    }
    @Test fun heldFreshEdgeUsesAllDeclaredSpeedsAndDropsAtExactAgeCutoff() {
        for(speed in listOf(120,240,360)) for(direction in listOf(-1,1)) {
            val m=EdgeScrollMotion();val p=point(if(direction<0) 8f else 632f)
            var sum=0
            for(t in 1L..1001L step 10) sum+=m.step(p,bounds,(t+250)*1_000_000,t*1_000_000,speed)
            assertEquals(direction*speed,sum)
            assertEquals(0,m.step(p,bounds,1251_000_000,1251_000_000,speed))
            assertEquals(0,m.step(p,bounds,1501_000_000,1252_000_000,speed))
        }
    }
    @Test fun stalledCallbacksIntegrateAtMost50msAndNeverCatchUp() {
        val m=EdgeScrollMotion();val p=point(632f)
        assertEquals(0,m.step(p,bounds,2_000_000_000,1_000_000,240))
        assertEquals(12,m.step(p,bounds,2_000_000_000,501_000_000,240))
        assertEquals(2,m.step(p,bounds,2_000_000_000,511_000_000,240))
    }
    @Test fun departureUnavailableAndOrdinaryStopDiscardTimeAndFraction() {
        val m=EdgeScrollMotion();val p=point(632f)
        m.step(p,bounds,2_000_000_000,1_000_000,120)
        assertEquals(0,m.step(p,bounds,2_000_000_000,5_000_000,120))
        assertEquals(0,m.step(point(631.99f),bounds,2_000_000_000,6_000_000,120))
        assertEquals(0,m.step(p,bounds,2_000_000_000,100_000_000,120))
        assertEquals(0,m.step(p.copy(available=false),bounds,2_000_000_000,101_000_000,120))
        m.stop()
        assertEquals(0,m.step(p,bounds,2_000_000_000,800_000_000,120))
        assertEquals(1,m.step(p,bounds,2_000_000_000,810_000_000,120))
    }
}
