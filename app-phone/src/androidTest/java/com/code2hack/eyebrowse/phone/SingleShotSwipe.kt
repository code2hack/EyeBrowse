package com.code2hack.eyebrowse.phone

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.Press
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Test-only single-submission pointer injection through Instrumentation.
 *
 * Event construction retains the pinned Espresso tap/FAST gesture shape, but the actual system
 * submission does not enter Espresso's retrying InputManagerEventInjectionStrategy. Every generated
 * event is submitted at most once and is never resubmitted. Android 12 sendPointerSync is void:
 * thrown failures are visible to this harness, while a false system injection result (and a
 * RemoteException swallowed inside Instrumentation) is not observable at submission time.
 */
internal object SingleShotSwipe {
    private const val SWIPE_EVENT_COUNT = 10
    private const val FAST_DURATION_MS = 150

    fun obtainTapDown(point: InputSafety.Path): MotionEvent =
        MotionEvents.obtainDownEvent(
            floatArrayOf(point.startX, point.startY),
            Press.FINGER.describePrecision(),
        )

    /**
     * One DOWN submission. The following sleep is only the retained tap-detection gesture cadence,
     * not readiness observation, retry, or rejection recovery.
     */
    fun injectTapDown(down: MotionEvent) {
        instrumentation().sendPointerSync(down)
        sleepUntil(down.downTime + ViewConfiguration.getTapTimeout() / 2L)
    }

    fun injectTapUp(
        down: MotionEvent,
        point: InputSafety.Path,
    ) {
        val eventTime = maxOf(
            SystemClock.uptimeMillis(),
            down.downTime + ViewConfiguration.getTapTimeout() / 2L,
        )
        val up = MotionEvents.obtainUpEvent(
            down,
            eventTime,
            floatArrayOf(point.endX, point.endY),
        )
        try {
            instrumentation().sendPointerSync(up)
        } finally {
            up.recycle()
        }
    }

    /**
     * Exact pinned FAST shape: DOWN, ten linear MOVE points, UP over 150 ms. Each event is
     * submitted once in order through Instrumentation.sendPointerSync and is never resubmitted.
     * A thrown failure aborts immediately; silent platform-side failure is not observable here and
     * is therefore detected only by the caller's downstream scroll/effect assertions.
     */
    fun send(path: InputSafety.Path) {
        val start = floatArrayOf(path.startX, path.startY)
        val end = floatArrayOf(path.endX, path.endY)
        val precision = Press.FINGER.describePrecision()
        val events = mutableListOf<MotionEvent>()
        val down = MotionEvents.obtainDownEvent(start, precision)
        events += down
        try {
            val intervalMs = FAST_DURATION_MS / SWIPE_EVENT_COUNT
            var eventTime = down.downTime
            for (i in 1..SWIPE_EVENT_COUNT) {
                eventTime += intervalMs.toLong()
                val fraction = i / (SWIPE_EVENT_COUNT + 2.0f)
                val point = floatArrayOf(
                    start[0] + (end[0] - start[0]) * fraction,
                    start[1] + (end[1] - start[1]) * fraction,
                )
                events += MotionEvents.obtainMovement(down, eventTime, point)
            }
            eventTime += intervalMs.toLong()
            events += MotionEvents.obtainUpEvent(down, eventTime, end)

            for (event in events) {
                sleepUntil(event.eventTime)
                instrumentation().sendPointerSync(event)
            }
        } finally {
            events.forEach(MotionEvent::recycle)
        }
    }

    private fun instrumentation() = InstrumentationRegistry.getInstrumentation()

    private fun sleepUntil(eventTime: Long) {
        val remaining = eventTime - SystemClock.uptimeMillis()
        if (remaining > 0) {
            SystemClock.sleep(remaining)
        }
    }
}
