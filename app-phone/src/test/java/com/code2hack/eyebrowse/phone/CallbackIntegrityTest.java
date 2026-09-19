package com.code2hack.eyebrowse.phone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * JVM regressions for the callback integrity facts used by the delayed consumer: whole-valid-bitmap
 * fingerprinting independent of color, detection of changes away from the sampled corner, stability
 * of a non-qualifying initialization buffer, and immutable entry facts despite later source reuse.
 */
public class CallbackIntegrityTest {

    private static final int WIDTH = 8;
    private static final int HEIGHT = 6;

    private static int[] pixels(int fill) {
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, fill);
        return pixels;
    }

    private static CallbackIntegrity.Snapshot snapshot(int[] pixels, long entryElapsedMs) {
        return CallbackIntegrity.of(entryElapsedMs, entryElapsedMs, 3, 7L, WIDTH, HEIGHT,
                WIDTH, HEIGHT, "ARGB_8888", pixels, pixels.length);
    }

    @Test
    public void stableInitializationBufferIsStableRegardlessOfColor() {
        int[] initialization = pixels(0xff000000);
        CallbackIntegrity.Snapshot entry = snapshot(initialization, 1_000);
        CallbackIntegrity.Snapshot postHold = snapshot(initialization, 1_000);
        assertTrue("identical borrowed content is stable", entry.sameContent(postHold));
        assertEquals(entry.fingerprint, postHold.fingerprint);
    }

    @Test
    public void changedPixelsAwayFromTheSampledCornerChangeTheFingerprint() {
        int[] original = pixels(0xff123456);
        CallbackIntegrity.Snapshot entry = snapshot(original, 1_000);
        int[] reused = java.util.Arrays.copyOf(original, original.length);
        reused[WIDTH * (HEIGHT / 2) + (WIDTH / 2)] = 0xffabcdef; // centre, far from (10,10)
        CallbackIntegrity.Snapshot postHold = snapshot(reused, 1_000);
        assertNotEquals("a centre change must change the whole-bitmap fingerprint",
                entry.fingerprint, postHold.fingerprint);
        assertFalse(entry.sameContent(postHold));
    }

    @Test
    public void missingPostHoldOrChangedMetadataNeverComparesAsEqual() {
        int[] borrowed = pixels(0xff000000);
        CallbackIntegrity.Snapshot entry = snapshot(borrowed, 1_234);
        assertFalse(entry.sameContent(null));
        assertFalse(entry.sameContent(CallbackIntegrity.of(1_234, 1_234, 4, 7L, WIDTH, HEIGHT,
                WIDTH, HEIGHT, "ARGB_8888", borrowed, borrowed.length)));
        assertFalse(entry.sameContent(CallbackIntegrity.of(1_234, 1_234, 3, 7L, WIDTH, HEIGHT,
                WIDTH, HEIGHT, "RGB_565", borrowed, borrowed.length)));
    }

    @Test
    public void immutableEntryFactsSurviveLaterSourceMutation() {
        int[] borrowed = pixels(0xff000000);
        CallbackIntegrity.Snapshot entry = snapshot(borrowed, 1_234);
        long entryFingerprint = entry.fingerprint;
        borrowed[borrowed.length - 1] = 0xffffffff; // pipeline reuses the borrowed buffer
        assertEquals("retained entry fingerprint is immutable", entryFingerprint,
                entry.fingerprint);
        assertFalse("a later mutation does not retroactively change the entry observation",
                entry.sameContent(snapshot(borrowed, 1_234)));
        assertEquals(1_234, entry.entryElapsedMs);
    }
}
