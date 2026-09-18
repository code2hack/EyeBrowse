package com.code2hack.eyebrowse.phone;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.espresso.InjectEventSecurityException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.action.MotionEvents;
import androidx.test.espresso.action.Press;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only one-attempt Espresso-compatible pointer construction on an already-captured UiController.
 * GeneralSwipeAction/MotionEvents retry wrappers are not used. A false controller result is a hard
 * single-attempt failure; checked injection failures keep their original object identity.
 */
final class SingleShotSwipe {
    private static final int SWIPE_EVENT_COUNT = 10;
    private static final int FAST_DURATION_MS = 150;

    private SingleShotSwipe() {}

    static MotionEvent obtainTapDown(InputSafety.Path point) {
        return MotionEvents.obtainDownEvent(
                new float[] {point.startX, point.startY}, Press.FINGER.describePrecision());
    }

    /**
     * Sends one Espresso-shaped DOWN and gives the main loop the same bounded tap-detection dwell
     * used by MotionEvents.sendDown, but never retries a false/uncertain DOWN.
     */
    static void injectTapDown(UiController controller, MotionEvent down) {
        requireController(controller);
        try {
            if (!controller.injectMotionEvent(down)) {
                throw new IllegalStateException("Espresso tap DOWN injection returned false");
            }
            long isTapAt = down.getDownTime() + (ViewConfiguration.getTapTimeout() / 2L);
            while (true) {
                long delay = isTapAt - SystemClock.uptimeMillis();
                if (delay <= 10) {
                    break;
                }
                controller.loopMainThreadForAtLeast(Math.max(1L, delay / 4L));
            }
        } catch (InjectEventSecurityException original) {
            SingleShotSwipe.<RuntimeException>sneakyThrow(original);
        }
    }

    static void injectTapUp(UiController controller, MotionEvent down, InputSafety.Path point) {
        requireController(controller);
        MotionEvent up = MotionEvents.obtainUpEvent(
                down, new float[] {point.endX, point.endY});
        try {
            try {
                if (!controller.injectMotionEvent(up)) {
                    throw new IllegalStateException("Espresso tap UP injection returned false");
                }
            } catch (InjectEventSecurityException original) {
                SingleShotSwipe.<RuntimeException>sneakyThrow(original);
            }
        } finally {
            up.recycle();
        }
    }

    /**
     * Exact pinned FAST shape: DOWN, ten linear MOVE points, UP over 150 ms. Unlike Swipe.FAST,
     * this helper observes injectMotionEventSequence's boolean result instead of silently returning
     * SUCCESS when InputManager reports false.
     */
    static void send(UiController controller, InputSafety.Path path) {
        requireController(controller);
        float[] start = new float[] {path.startX, path.startY};
        float[] end = new float[] {path.endX, path.endY};
        float[] precision = Press.FINGER.describePrecision();
        List<MotionEvent> events = new ArrayList<>();
        MotionEvent down = MotionEvents.obtainDownEvent(start, precision);
        events.add(down);
        try {
            long intervalMs = FAST_DURATION_MS / SWIPE_EVENT_COUNT;
            long eventTime = down.getDownTime();
            for (int i = 1; i <= SWIPE_EVENT_COUNT; i++) {
                eventTime += intervalMs;
                float fraction = i / (SWIPE_EVENT_COUNT + 2.0f);
                float[] point = new float[] {
                        start[0] + (end[0] - start[0]) * fraction,
                        start[1] + (end[1] - start[1]) * fraction
                };
                events.add(MotionEvents.obtainMovement(down, eventTime, point));
            }
            eventTime += intervalMs;
            events.add(MotionEvents.obtainUpEvent(down, eventTime, end));
            try {
                if (!controller.injectMotionEventSequence(events)) {
                    throw new IllegalStateException("Espresso swipe injection returned false");
                }
            } catch (InjectEventSecurityException original) {
                SingleShotSwipe.<RuntimeException>sneakyThrow(original);
            }
        } finally {
            for (MotionEvent event : events) {
                event.recycle();
            }
        }
    }

    private static void requireController(UiController controller) {
        if (controller == null) {
            throw new IllegalStateException("captured Espresso UiController unavailable");
        }
    }

    /** Preserve the exact checked Espresso injection throwable through Runnable-based dispatch. */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable original) throws T {
        throw (T) original;
    }
}
