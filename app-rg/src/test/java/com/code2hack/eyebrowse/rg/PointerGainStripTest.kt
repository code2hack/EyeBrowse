package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PointerGainStripTest {
    private fun pose(ms: Long, yaw: Double, pitch: Double): RotationSample {
        val sy=sin(Math.toRadians(-yaw)/2);val cy=cos(Math.toRadians(-yaw)/2)
        val sx=sin(Math.toRadians(-pitch)/2);val cx=cos(Math.toRadians(-pitch)/2)
        return RotationSample(ms*1_000_000,(cy*sx).toFloat(),(sy*cx).toFloat(),(-sy*sx).toFloat(),(cy*cx).toFloat())
    }
    private fun model(gain: Double, padding: Float=32f) = HeadPointerModel().apply {
        resize(PointerBounds(8f,8f,472f,632f),0,padding);start();sample(pose(1,0.0,0.0),1_000_000)
        sensitivity(gain,1_000_000)
    }
    @Test fun comparableModerateTracesDoubleBothAxesWithoutUsingPaddedHeight() {
        println("trace,axis,gain,timeMs,yawDegrees,pitchDegrees,x,y")
        for(vertical in listOf(false,true)) {
            val old=model(1.0,0f);val current=model(2.0)
            for(t in 21L..401L step 20) {
                val degrees=min(4.0,t/100.0)
                val raw=pose(t,if(vertical) 0.0 else degrees,if(vertical) degrees else 0.0)
                for(m in listOf(old,current)) {
                    assertTrue(m.sample(raw,t*1_000_000));m.advance(t*1_000_000)
                    println("moderate,${if(vertical) "y" else "x"},${m.sensitivity},$t,${if(vertical) 0.0 else degrees},${if(vertical) degrees else 0.0},${m.position.x},${m.position.y}")
                }
                val baseline=if(vertical) old.position.y-320 else old.position.x-240
                val actual=if(vertical) current.position.y-320 else current.position.x-240
                if(abs(baseline)>.01) assertEquals("both axes retain the same visible-span reference",2.0,actual/baseline.toDouble(),.001)
            }
        }
    }
    @Test fun fastTraceScalesTheVelocityCapAndDoesNotCatchUpAfterAStall() {
        val old=model(1.0);val current=model(2.0)
        val firstDisplacements=mutableListOf<Float>()
        for(m in listOf(old,current)) {
            assertTrue(m.sample(pose(21,90.0,0.0),21_000_000));m.advance(21_000_000)
            println("fast,x,${m.sensitivity},21,90.0,0.0,${m.position.x},${m.position.y}")
            assertEquals(464*3*m.sensitivity*.02,m.position.x-240.0,.001)
            firstDisplacements+=m.position.x-240
            assertTrue(m.sample(pose(221,90.0,0.0),221_000_000))
            val before=m.position.x;m.advance(221_000_000)
            assertTrue(m.position.x-before<=464*3*m.sensitivity*.05+.001)
        }
        assertEquals(2.0,firstDisplacements[1]/firstDisplacements[0].toDouble(),.001)
    }
    @Test fun smallInwardMotionStillScrollsUntilVisibleReentry() {
        val visible=PointerBounds(0f,0f,480f,640f)
        for(direction in listOf(-1,1)) {
            val m=model(2.0);val scroll=EdgeScrollMotion()
            for(t in 21L..1001L step 20) {m.sample(pose(t,0.0,direction*50.0),t*1_000_000);m.advance(t*1_000_000)}
            assertEquals(if(direction<0) -24f else 664f,m.position.y,.001f)
            var time=1021L
            m.sample(pose(time,0.0,direction*49.95),time*1_000_000);m.advance(time*1_000_000)
            assertTrue(m.position.available)
            assertEquals(direction,scroll.direction(m.position,visible,m.expiresAtNs,time*1_000_000))
            val before=m.position.y
            m.sample(pose(1041,0.0,direction*48.5),1041_000_000)
            for(t in 1041L..1121L step 20) {time=t;m.sample(pose(t,0.0,direction*48.5),t*1_000_000);m.advance(t*1_000_000)}
            assertTrue("visible return is bounded",m.position.y in 0f..<640f)
            assertEquals(0,scroll.direction(m.position,visible,m.expiresAtNs,time*1_000_000))
            assertTrue(abs(m.position.y-before)<64)
        }
    }
}
