package com.code2hack.eyebrowse.rg

import org.junit.Assert.*
import org.junit.Test

/** Actual production model plus the exact raw generator used by the native journey. */
class RawPoseAimTest {
    private val bounds=PointerBounds(8f,8f,472f,632f)
    private val neutral=RotationSample(0,0f,0f,0f,1f)
    private fun journey(gain: Double, points: List<InputPoint>) {
        val model=HeadPointerModel().apply { resize(bounds,0);start() }
        var raw=neutral;var time=1_000_000L
        assertTrue(model.sample(raw.copy(timestampNs=time),time));model.sensitivity(gain,time)
        for(point in points) {
            raw=rawPoseToward(neutral,raw,model.position,bounds,point,gain)
            // Deterministic held fresh raw stream and display frames, not assigned cursor values.
            repeat(100) { time+=20_000_000;assertTrue(model.sample(raw.copy(timestampNs=time),time));model.advance(time) }
            val actual=model.position
            assertTrue("gain=$gain target=$point actual=$actual",actual.available &&
                kotlin.math.abs(actual.x-point.x)<8 && kotlin.math.abs(actual.y-point.y)<8)
        }
    }
    @Test fun moreThenAddUsesCurrentGeometryAndRelativeMotionAtEveryGain() {
        for(gain in listOf(.75,1.0,1.25)) journey(gain,listOf(InputPoint(456f,24f),InputPoint(368f,72f)))
    }
    @Test fun reversalFromAFullScreenCornerStillReachesTheNativeTarget() {
        for(gain in listOf(.75,1.0,1.25)) journey(gain,listOf(InputPoint(472f,632f),InputPoint(368f,72f)))
    }
    @Test fun repeatedInteriorTargetsKeepTheOriginalEightPixelCriterion() {
        for(gain in listOf(.75,1.0,1.25)) journey(gain,listOf(InputPoint(100f,200f),InputPoint(360f,100f),InputPoint(240f,320f)))
    }
}
