package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/** Raw quaternion controls of the production model, never assigned cursor coordinates. */
class PointerBoundaryRegressionTest {
    private fun pose(ms: Long, yaw: Double, pitch: Double): RotationSample {
        val sy = sin(Math.toRadians(-yaw) / 2); val cy = cos(Math.toRadians(-yaw) / 2)
        val sx = sin(Math.toRadians(-pitch) / 2); val cx = cos(Math.toRadians(-pitch) / 2)
        return RotationSample(ms * 1_000_000, (cy*sx).toFloat(), (sy*cx).toFloat(),
            (-sy*sx).toFloat(), (cy*cx).toFloat())
    }
    @Test fun everyEdgeAndCornerDiscardsAllOvershootAndReversesOnFirstSlowSample() {
        val returns=mutableMapOf<Pair<Int,Int>,Pair<Float,Float>>()
        for (horizontal in -1..1) for (vertical in -1..1) {
            if (horizontal == 0 && vertical == 0) continue
            for (extra in listOf(5.0, 20.0, 45.0)) for (hold in listOf(100L, 1000L, 3000L)) {
                val m = HeadPointerModel().apply { resize(PointerBounds(8f,8f,472f,632f),0); start() }
                var t = 1L
                fun emit(yaw: Double, pitch: Double) { assertTrue(m.sample(pose(t,yaw,pitch),t*1_000_000)); m.advance(t*1_000_000); t += 20 }
                emit(0.0,0.0)
                val yaw = horizontal * (30.3 + extra); val pitch = vertical * (22.3 + extra / 2)
                repeat((hold / 20).toInt() + 100) { emit(yaw,pitch) }
                val edge=m.position
                val label="h=$horizontal v=$vertical excess=$extra hold=$hold"
                if(horizontal != 0) assertEquals(label, if(horizontal<0) 8f else 472f,edge.x,.001f)
                if(vertical != 0) assertEquals(label, if(vertical<0) 8f else 632f,edge.y,.001f)
                emit(yaw-horizontal*.05,pitch-vertical*.05)
                if(horizontal != 0) assertTrue("first slow inward x $label",horizontal*(m.position.x-edge.x)<0)
                if(vertical != 0) assertTrue("first slow inward y $label",vertical*(m.position.y-edge.y)<0)
                val movement=abs(m.position.x-edge.x) to abs(m.position.y-edge.y)
                val first=returns.getOrPut(horizontal to vertical) { movement }
                assertEquals("equal inward x $label",first.first,movement.first,.002f)
                assertEquals("equal inward y $label",first.second,movement.second,.002f)
                assertTrue(m.position.x in 8f..472f && m.position.y in 8f..632f)
            }
        }
    }
    @Test fun earlyReversalDoesNotContinueTowardAnOldFilteredTarget() {
        val m=HeadPointerModel().apply { resize(PointerBounds(8f,8f,472f,632f),0);start() }
        m.sample(pose(1,0.0,0.0),1_000_000)
        m.sample(pose(21,70.0,0.0),21_000_000);m.advance(21_000_000)
        val before=m.position.x
        m.sample(pose(41,69.95,0.0),41_000_000);m.advance(41_000_000)
        assertTrue("inward sample must discard outward filtered travel",m.position.x < before)
    }
    @Test fun sensitivityChangesFutureGainWithoutMovingOrReplayingFilteredTravel() {
        val distances=mutableListOf<Float>()
        for(gain in listOf(.75,1.0,1.25)) {
            val m=HeadPointerModel().apply { resize(PointerBounds(8f,8f,472f,632f),0);start() }
            m.sample(pose(1,0.0,0.0),1_000_000)
            val before=m.position;m.sensitivity(gain,1_000_000)
            assertEquals(before,m.position)
            for(t in 21L..1001L step 20) {m.sample(pose(t,10.0,0.0),t*1_000_000);m.advance(t*1_000_000)}
            distances+=m.position.x-240
            m.sample(pose(1021,20.0,0.0),1021_000_000);m.advance(1021_000_000)
            val moving=m.position;m.sensitivity(1.0,1022_000_000)
            assertEquals(moving,m.position)
            for(t in 1041L..1201L step 20) {m.sample(pose(t,20.0,0.0),t*1_000_000);m.advance(t*1_000_000)}
            assertEquals("no pre-setting filter travel",moving.x,m.position.x,.001f)
        }
        assertEquals(.75,distances[0]/distances[1].toDouble(),.001)
        assertEquals(1.25,distances[2]/distances[1].toDouble(),.001)
    }
    @Test fun stoppingTrackingFreezesTheVisibleUnavailableCursor() {
        val m=HeadPointerModel().apply { resize(PointerBounds(8f,8f,472f,632f),0);start() }
        for(t in 1L..1001L step 20) {m.sample(pose(t,if(t==1L) 0.0 else 10.0,0.0),t*1_000_000);m.advance(t*1_000_000)}
        val before=m.position;m.stop()
        assertEquals(before.x,m.position.x,0f);assertEquals(before.y,m.position.y,0f);assertFalse(m.position.available)
    }
}
