package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class HeadPointerModelTest {
    private val ms = 1_000_000L
    private fun pose(timeMs: Long, xDegrees: Double = 0.0, yDegrees: Double = 0.0, zDegrees: Double = 0.0): RotationSample {
        val angle = Math.toRadians(sqrt(xDegrees*xDegrees+yDegrees*yDegrees+zDegrees*zDegrees))
        val length = sqrt(xDegrees*xDegrees+yDegrees*yDegrees+zDegrees*zDegrees)
        val scale = if (length == 0.0) 0.0 else sin(angle/2)/length
        return RotationSample(timeMs*ms,(xDegrees*scale).toFloat(),(yDegrees*scale).toFloat(),
            (zDegrees*scale).toFloat(),cos(angle/2).toFloat())
    }
    private fun model(rotation: Int = 0) = HeadPointerModel().apply {
        resize(PointerBounds(10f,20f,470f,620f),rotation)
        start()
        assertTrue(sample(pose(1),ms))
    }
    private fun settle(model: HeadPointerModel, x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): PointerPosition {
        for (t in 21L..2001L step 20) { model.sample(pose(t,x,y,z),t*ms);model.advance(t*ms) }
        return model.position
    }

    @Test fun firstValidPoseEstablishesNeutralAndDeclaredAxesAimRightAndDown() {
        val model = model()
        assertEquals(PointerPosition(240f,320f,true),model.position)
        val right = settle(model,y=-10.0)
        assertTrue(right.x>240 && abs(right.y-320)<.1)
        val down = settle(model(),x=-10.0)
        assertTrue(down.y>320 && abs(down.x-240)<.1)
    }
    @Test fun relativeQuaternionCrossesWrapAndIgnoresQuaternionSign() {
        val model = model()
        model.sample(pose(21,yDegrees=179.0),21*ms);assertTrue(model.recenter(21*ms))
        for (t in 41L..1001L step 20) { model.sample(pose(t,yDegrees=-179.0),t*ms);model.advance(t*ms) }
        assertTrue(abs(model.position.x-240)<30)
        val prior=model.position
        val p=pose(1021,yDegrees=-179.0)
        assertTrue(model.sample(p.copy(x=-p.x,y=-p.y,z=-p.z,w=-p.w),1021*ms))
        model.advance(1021*ms)
        assertEquals(prior.x,model.position.x,.1f)
    }
    @Test fun displayRotationRemapsAxesAndRollDoesNotBecomeYaw() {
        assertTrue(settle(model(1),y=-10.0).y>320)
        assertTrue(settle(model(2),y=-10.0).x<240)
        assertTrue(settle(model(3),y=-10.0).y<320)
        assertEquals(PointerPosition(240f,320f,true),settle(model(),z=30.0))
    }
    @Test fun deadbandAndHeldPoseConvergeWithoutVelocityDrift() {
        assertEquals(PointerPosition(240f,320f,true),settle(model(),y=-.2))
        val m=model();val held=settle(m,y=-10.0)
        for(t in 2021L..4001L step 20) { m.sample(pose(t,yDegrees=-10.0),t*ms);m.advance(t*ms) }
        assertEquals(held.x,m.position.x,.1f)
    }
    @Test fun smoothingDependsOnElapsedTimeNotCallbackCount() {
        fun at(step:Long):Float {
            val m=model();m.sample(pose(2,yDegrees=-2.0),2*ms)
            for(t in 1+step..201 step step) m.advance(t*ms)
            return m.position.x
        }
        assertEquals(at(10),at(20),.05f)
    }
    @Test fun speedAndRootClippingAreBounded() {
        val m=model();m.sample(pose(21,yDegrees=-90.0),21*ms);m.advance(21*ms)
        assertTrue(m.position.x-240 <= 460*3*.020+.01)
        val edge=settle(m,y=-90.0);assertEquals(470f,edge.x,.1f)
        m.resize(PointerBounds(5f,7f,105f,207f),0)
        assertTrue(m.position.x in 5f..105f && m.position.y in 7f..207f)
    }
    @Test fun recenterIsImmediateAndDoesNotChangeTheBounds() {
        val m=model();settle(m,x=-8.0,y=-10.0)
        assertTrue(m.recenter(2001*ms));assertEquals(PointerPosition(240f,320f,true),m.position)
        m.sample(pose(2021,xDegrees=-8.0,yDegrees=-10.0),2021*ms);m.advance(2021*ms)
        assertEquals(PointerPosition(240f,320f,true),m.position)
    }
    @Test fun rejectsNonFiniteZeroOutOfOrderAndOldSamplesWithoutRefreshingAge() {
        val m=model();val initial=m.position
        assertFalse(m.sample(pose(2).copy(x=Float.NaN),2*ms))
        assertFalse(m.sample(RotationSample(2*ms,0f,0f,0f,0f),2*ms))
        assertFalse(m.sample(pose(1),2*ms))
        assertFalse(m.sample(pose(2),300*ms))
        assertFalse(m.sample(pose(100),3*ms))
        assertEquals(initial,m.position)
        m.advance(251*ms);assertFalse(m.position.available)
    }
    @Test fun silenceExpiresAtExactBoundaryAndRecoveryRebasesInsteadOfJumping() {
        val m=model();m.advance(250*ms);assertTrue(m.position.available)
        m.advance(251*ms);assertFalse(m.position.available);assertFalse(m.recenter(251*ms))
        assertTrue(m.sample(pose(301,yDegrees=-70.0),301*ms))
        assertEquals(PointerPosition(240f,320f,true),m.position)
    }
    @Test fun largeSampleGapRebasesEvenBeforeAnyExpiryTimerRuns() {
        val m=model();assertTrue(m.sample(pose(251,yDegrees=-70.0),251*ms))
        assertEquals(PointerPosition(240f,320f,true),m.position)
    }
    @Test fun stopRejectsSamplesAndRestartRequiresFreshNeutral() {
        val m=model();m.stop();assertFalse(m.sample(pose(21),21*ms));assertFalse(m.position.available)
        m.start();assertTrue(m.sample(pose(41,yDegrees=-70.0),41*ms))
        assertEquals(PointerPosition(240f,320f,true),m.position)
    }
    @Test fun layoutAbsenceAndAxisChangeCannotLeaveAnOutOfBoundsUsableAim() {
        val m=model();m.resize(PointerBounds(0f,0f,0f,0f),0);assertFalse(m.position.available)
        m.resize(PointerBounds(10f,20f,470f,620f),1)
        m.sample(pose(21,yDegrees=-30.0),21*ms)
        assertEquals(PointerPosition(240f,320f,true),m.position)
    }
}
