package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class HeadScrollModelTest {
    private var at=1_000_000_000L
    private fun pose(pitch: Double): RotationSample {
        val half=Math.toRadians(-pitch)/2
        return RotationSample(at,sin(half).toFloat(),0f,0f,cos(half).toFloat())
    }
    private fun feed(m:HeadScrollModel,pitch:Double,count:Int=1) { repeat(count) { at+=20_000_000;m.sample(pose(pitch),at,0) } }
    @Test fun neutralDirectionsAndBoundedSpeed() {
        val m=HeadScrollModel();m.start();feed(m,0.0,17);assertTrue(m.armed)
        feed(m,1.0,10);assertEquals(0.0,m.speed(at),0.0)
        feed(m,12.0,20);assertTrue(m.speed(at)>0);assertTrue(m.speed(at)<=600)
        feed(m,-12.0,40);assertTrue(m.speed(at)<0);assertTrue(m.speed(at)>=-600)
        feed(m,0.0);assertEquals(0.0,m.speed(at),0.0)
    }
    @Test fun swipeAndLivenessRequireReturningToNeutral() {
        val m=HeadScrollModel();m.start();feed(m,0.0,17);feed(m,10.0,20);assertTrue(m.speed(at)>0)
        m.suspend();feed(m,10.0,30);assertFalse(m.armed);assertEquals(0.0,m.speed(at),0.0)
        feed(m,0.0,17);assertTrue(m.armed);feed(m,10.0,10);assertTrue(m.speed(at)>0)
        assertEquals(0.0,m.speed(at+250_000_000),0.0)
        at+=300_000_000;feed(m,10.0,30);assertFalse(m.armed)
        feed(m,0.0,17);assertTrue(m.armed)
    }
    @Test fun invalidSamplesAndRestartCannotResumeHeldInput() {
        val m=HeadScrollModel();m.start();feed(m,0.0,17);feed(m,10.0,20)
        at+=20_000_000;assertFalse(m.sample(pose(0.0).copy(x=Float.NaN),at,0));assertFalse(m.armed)
        m.stop();assertEquals(0.0,m.speed(at),0.0)
        m.start();feed(m,10.0,17);assertTrue(m.armed);assertEquals(0.0,m.speed(at),0.0)
    }
    @Test fun driftTracksOnlyNeutralAndRotationReacquires() {
        val m=HeadScrollModel();m.start();feed(m,0.0,17)
        // A slow neutral offset is tracked; motion outside the dead zone never moves the reference.
        feed(m,1.0,1_100);assertEquals(0.0,m.speed(at),0.0)
        feed(m,2.5,20);assertEquals(0.0,m.speed(at),0.0)
        feed(m,15.0,1_000);assertTrue(m.speed(at)>500)
        m.suspend();feed(m,15.0,100);assertFalse(m.armed)
        at+=20_000_000;assertFalse(m.sample(pose(0.0),at,4));assertFalse(m.armed)
        at+=20_000_000;assertTrue(m.sample(pose(0.0),at,1));assertFalse(m.armed)
    }
}
