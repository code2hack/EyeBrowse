package com.code2hack.eyebrowse.projector.directrg

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Math/gesture evidence only; synthetic poses and keys do not prove hardware ergonomics. */
class ProjectorInputTest {
    private fun pose(ms: Long, yaw: Double = 0.0, pitch: Double = 0.0): RotationSample {
        val y = Math.toRadians(yaw) / 2
        val p = Math.toRadians(pitch) / 2
        return RotationSample(ms * 1_000_000, sin(p).toFloat(), sin(y).toFloat(), 0f, (cos(y) * cos(p)).toFloat())
    }
    private fun pointer(rotation: Int = 0, gain: Double = 2.0) = HeadPointerModel().apply {
        resize(PointerBounds(8f, 8f, 472f, 632f), rotation, 32f)
        sensitivity(gain, 0); start(); sample(pose(1), 1_000_000)
    }
    private fun settle(model: HeadPointerModel, yaw: Double, pitch: Double = 0.0) {
        for (t in 21L..1001L step 20) { model.sample(pose(t, yaw, pitch), t * 1_000_000); model.advance(t * 1_000_000) }
    }
    @Test fun gainAndRotationRetainDonorSemantics() {
        val standard = pointer(gain = 1.0); settle(standard, -5.0)
        val twice = pointer(); settle(twice, -5.0)
        assertEquals((standard.position.x - 240) * 2, twice.position.x - 240, .1f)
        val rotated = pointer(rotation = 1); settle(rotated, -5.0)
        assertTrue(rotated.position.y > 320)
        assertEquals(240f, rotated.position.x, .1f)
    }
    @Test fun stripsStayBoundedAndInwardTravelImmediatelyReturns() {
        val model = pointer(); settle(model, 0.0, -80.0)
        assertEquals(664f, model.position.y, .1f)
        for (t in 1021L..1221L step 20) { model.sample(pose(t, 0.0, -76.0), t * 1_000_000); model.advance(t * 1_000_000) }
        assertTrue(model.position.y < 640)
        assertEquals(8f, model.motionBounds.left, 0f)
        assertEquals(-24f, model.motionBounds.top, 0f)
    }
    @Test fun sensorLossStopAndRestartDiscardOldAim() {
        val model = pointer(); settle(model, -12.0)
        model.advance(1_251_000_000); assertFalse(model.position.available)
        assertTrue(model.sample(pose(1300, -70.0), 1_300_000_000))
        assertEquals(240f, model.position.x, .1f)
        model.stop(); assertFalse(model.sample(pose(1400), 1_400_000_000))
        model.start(); assertFalse(model.position.available)
        assertFalse(model.sample(pose(1500).copy(x = Float.NaN), 1_500_000_000))
    }
    @Test fun scrollStopsAtVisibleBoundaryExpiryAndPauseWithoutCatchup() {
        val motion = EdgeScrollMotion(); val bounds = PointerBounds(0f, 0f, 480f, 640f)
        val outside = PointerPosition(240f, 650f, true)
        assertEquals(0, motion.step(outside, bounds, Long.MAX_VALUE, 1_000_000, 240))
        assertEquals(12, motion.step(outside, bounds, Long.MAX_VALUE, 51_000_000, 240))
        assertEquals(0, motion.step(outside.copy(y = 639f), bounds, Long.MAX_VALUE, 71_000_000, 240))
        assertEquals(0, motion.step(outside, bounds, Long.MAX_VALUE, 91_000_000, 240))
        assertEquals(12, motion.step(outside, bounds, Long.MAX_VALUE, 5_000_000_000, 240))
        assertEquals(0, motion.step(outside, bounds, 5_000_000_000, 5_000_000_000, 240))
        motion.stop()
        assertEquals(0, motion.step(outside, bounds, Long.MAX_VALUE, 6_000_000_000, 240))
    }
    @Test fun oneShortTapDoubleSuppressionSwipeAndCancel() {
        var taps = 0; val scrolls = mutableListOf<Int>()
        val gestures = PadGestureRecognizer(300, 500, { taps++ }, { scrolls.add(it) })
        fun press(key: PadGestureRecognizer.Key, start: Long, duration: Long = 30) {
            gestures.accept(PadGestureRecognizer.Event(key, PadGestureRecognizer.Phase.DOWN, start, start), start)
            gestures.accept(PadGestureRecognizer.Event(key, PadGestureRecognizer.Phase.UP, start + duration, start), start + duration)
        }
        press(PadGestureRecognizer.Key.TAP, 1000)
        gestures.confirm(1330); assertEquals(1, taps)
        press(PadGestureRecognizer.Key.TAP, 1500); press(PadGestureRecognizer.Key.TAP, 1600)
        gestures.confirm(2000); assertEquals(1, taps)
        press(PadGestureRecognizer.Key.FORWARD, 2100); press(PadGestureRecognizer.Key.BACKWARD, 2500)
        assertEquals(listOf(160, -160), scrolls)
        press(PadGestureRecognizer.Key.TAP, 3000); gestures.cancel(); gestures.confirm(4000)
        assertEquals(1, taps)
        press(PadGestureRecognizer.Key.TAP, 5000, 600); gestures.confirm(6000)
        assertEquals(1, taps)
    }
    @Test fun settingsUseOwnNumericValuesAndRetainValueOnSaveFailure() {
        val saved = mutableMapOf<String, String>(); var writable = true
        val settings = LocalInputSettings(saved::get) { k, v -> if (writable) { saved[k] = v; true } else false }
        assertEquals(2.0, settings.pointerGain, 0.0)
        settings.stepPointer(1); settings.stepScroll(-1)
        assertEquals(2.1, settings.pointerGain, 0.0); assertEquals(230, settings.scrollSpeed)
        writable = false
        assertFalse(settings.stepPointer(1)); assertEquals(2.1, settings.pointerGain, 0.0)
        assertNotNull(settings.error)
        assertEquals(2.1, LocalInputSettings(saved::get) { _, _ -> false }.pointerGain, 0.0)
    }
}
