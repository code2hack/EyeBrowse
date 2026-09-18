package com.code2hack.eyebrowse.phone

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.espresso.InjectEventSecurityException
import androidx.test.espresso.UiController
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.action.Press

/**
 * Test-only one-attempt Espresso-compatible pointer construction on an already-captured UiController.
 * GeneralSwipeAction/MotionEvents retry wrappers are not used. A false controller result is a hard
 * single-attempt failure; checked injection failures keep their original object identity.
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
     * Sends one Espresso-shaped DOWN and gives the main loop the same bounded tap-detection dwell
     * used by MotionEvents.sendDown, but never retries a false/uncertain DOWN.
     */
    fun injectTapDown(controller: UiController, down: MotionEvent) {
        requireController(controller)
        try {
            if (!controller.injectMotionEvent(down)) {
                throw IllegalStateException("Espresso tap DOWN injection returned false")
            }
            val isTapAt = down.downTime + ViewConfiguration.getTapTimeout() / 2L
            while (true) {
                val delay = isTapAt - SystemClock.uptimeMillis()
                if (delay <= 10L) {
                    break
                }
                controller.loopMainThreadForAtLeast(maxOf(1L, delay / 4L))
            }
        } catch (original: InjectEventSecurityException) {
            throw original
        }
    }

    fun injectTapUp(
        controller: UiController,
        down: MotionEvent,
        point: InputSafety.Path,
    ) {
        requireController(controller)
        val up = MotionEvents.obtainUpEvent(
            down,
            floatArrayOf(point.endX, point.endY),
        )
        try {
            try {
                if (!controller.injectMotionEvent(up)) {
                    throw IllegalStateException("Espresso tap UP injection returned false")
                }
            } catch (original: InjectEventSecurityException) {
                throw original
            }
        } finally {
            up.recycle()
        }
    }

    /**
     * Exact pinned FAST shape: DOWN, ten linear MOVE points, UP over 150 ms. Unlike Swipe.FAST,
     * this helper observes injectMotionEventSequence's boolean result instead of silently returning
     * SUCCESS when InputManager reports false.
     */
    fun send(controller: UiController, path: InputSafety.Path) {
        requireController(controller)
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
            try {
                if (!controller.injectMotionEventSequence(events)) {
                    throw IllegalStateException("Espresso swipe injection returned false")
                }
            } catch (original: InjectEventSecurityException) {
                throw original
            }
        } finally {
            events.forEach(MotionEvent::recycle)
        }
    }

    private fun requireController(controller: UiController?) {
        if (controller == null) {
            throw IllegalStateException("captured Espresso UiController unavailable")
        }
    }
}
