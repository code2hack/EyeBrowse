package com.code2hack.eyebrowse.phone;

import android.view.MotionEvent;

import androidx.test.espresso.InjectEventSecurityException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.action.Press;
import androidx.test.espresso.action.Swipe;
import androidx.test.espresso.action.Swiper;

/**
 * Test-only one-attempt use of Espresso 3.6.1's FAST swipe on an already-captured UiController.
 * The caller performs final DOM/native/path admission in the WebView result callback before calling
 * this helper, so there is no separate Espresso pre-action or JavaScript-wait idle boundary between
 * admission and this one gesture attempt. GeneralSwipeAction's retry wrapper is never used.
 */
final class SingleShotSwipe {
    private SingleShotSwipe() {}

    static void send(UiController controller, InputSafety.Path path) {
        if (controller == null) {
            throw new IllegalStateException("captured Espresso UiController unavailable");
        }
        float[] start = new float[] {path.startX, path.startY};
        float[] end = new float[] {path.endX, path.endY};
        Swiper.Status result = Swipe.FAST.sendSwipe(
                controller, start, end, Press.FINGER.describePrecision());
        if (result != Swiper.Status.SUCCESS) {
            throw new IllegalStateException("single Espresso swipe failed; no replay");
        }
    }

    /** Exactly one Espresso motion-event injection attempt; false/exception stops the tap. */
    static void inject(UiController controller, MotionEvent event) {
        if (controller == null) {
            throw new IllegalStateException("captured Espresso UiController unavailable");
        }
        try {
            if (!controller.injectMotionEvent(event)) {
                throw new IllegalStateException("Espresso pointer injection returned false");
            }
        } catch (InjectEventSecurityException original) {
            SingleShotSwipe.<RuntimeException>sneakyThrow(original);
        }
    }

    /** Preserve the exact checked Espresso injection throwable through Runnable-based dispatch. */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable original) throws T {
        throw (T) original;
    }
}
