package com.code2hack.eyebrowse.phone;

import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.espresso.action.MotionEvents;
import androidx.test.espresso.action.Press;

/**
 * Test-only one-attempt reproduction of Espresso 3.6.1's FAST linear swipe shape using the
 * instrumentation's existing UiAutomation connection. This keeps privileged system injection on
 * the already-established test client without UiController main-loop pumping after final readiness,
 * without an INJECT_EVENTS grant, and without creating a second automation client or retry path.
 */
final class SingleShotSwipe {
    private static final int MOVE_COUNT = 10;
    private static final long FAST_DURATION_MS = 150;

    private SingleShotSwipe() {}

    static void send(UiAutomation automation, InputSafety.Path path) {
        float[] start = new float[] {path.startX, path.startY};
        float[] end = new float[] {path.endX, path.endY};
        float[] precision = Press.FINGER.describePrecision();
        MotionEvent down = MotionEvents.obtainDownEvent(start, precision);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            inject(automation, down);
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
                    inject(automation, move);
                } finally {
                    move.recycle();
                }
            }
            eventTime += intervalMs;
            sleepUntil(eventTime);
            MotionEvent up = MotionEvents.obtainUpEvent(down, eventTime, end);
            try {
                up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                inject(automation, up);
            } finally {
                up.recycle();
            }
        } finally {
            down.recycle();
        }
    }

    /** One synchronous event attempt on the caller-supplied existing UiAutomation connection. */
    static void inject(UiAutomation automation, MotionEvent event) {
        if (automation == null) {
            throw new IllegalStateException("existing UiAutomation connection unavailable");
        }
        if (!automation.injectInputEvent(event, true)) {
            throw new IllegalStateException("UiAutomation pointer injection returned false");
        }
    }

    private static void sleepUntil(long eventTime) {
        long remaining = eventTime - SystemClock.uptimeMillis();
        if (remaining > 0) {
            SystemClock.sleep(remaining);
        }
    }
}
