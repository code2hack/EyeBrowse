package com.code2hack.eyebrowse.phone;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.espresso.action.MotionEvents;
import androidx.test.espresso.action.Press;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Test-only one-attempt reproduction of Espresso 3.6.1's FAST linear swipe shape using the
 * instrumentation pointer pipeline. This avoids UiController main-loop pumping after the final DOM
 * readiness sample while preserving the pinned provider coordinates, FINGER precision, ten move
 * points and FAST timing. There is deliberately no retry path.
 */
final class SingleShotSwipe {
    private static final int MOVE_COUNT = 10;
    private static final long FAST_DURATION_MS = 150;

    private SingleShotSwipe() {}

    static void send(float[] start, float[] end) {
        float[] precision = Press.FINGER.describePrecision();
        MotionEvent down = MotionEvents.obtainDownEvent(start, precision);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            long intervalMs = FAST_DURATION_MS / MOVE_COUNT;
            long eventTime = down.getDownTime();
            for (int i = 1; i <= MOVE_COUNT; i++) {
                eventTime += intervalMs;
                sleepUntil(eventTime);
                float fraction = i / (MOVE_COUNT + 2.0f);
                float[] point = new float[] {
                        start[0] + (end[0] - start[0]) * fraction,
                        start[1] + (end[1] - start[1]) * fraction
                };
                MotionEvent move = MotionEvents.obtainMovement(down, eventTime, point);
                try {
                    move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                    InstrumentationRegistry.getInstrumentation().sendPointerSync(move);
                } finally {
                    move.recycle();
                }
            }
            eventTime += intervalMs;
            sleepUntil(eventTime);
            MotionEvent up = MotionEvents.obtainUpEvent(down, eventTime, end);
            try {
                up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
            } finally {
                up.recycle();
            }
        } finally {
            down.recycle();
        }
    }

    private static void sleepUntil(long eventTime) {
        long remaining = eventTime - SystemClock.uptimeMillis();
        if (remaining > 0) {
            SystemClock.sleep(remaining);
        }
    }
}
