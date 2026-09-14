package com.code2hack.eyebrowse.phone;

import android.graphics.Bitmap;

/**
 * One captured offscreen frame delivered to the single live in-process consumer.
 *
 * <p>The bitmap is a shared, reused buffer owned by the capture pipeline: the consumer must read or
 * copy it during the callback and must not retain or recycle it. {@code generation} fences frames
 * from a superseded hosting session; {@code captureElapsedMs} is the {@link
 * android.os.SystemClock#elapsedRealtime()} capture stamp used for freshness checks.
 */
final class HostingFrame {

    final Bitmap bitmap;
    final int width;
    final int height;
    final int generation;
    final long sequence;
    final long captureElapsedMs;
    final long contentHash;

    HostingFrame(Bitmap bitmap, int width, int height, int generation, long sequence,
            long captureElapsedMs, long contentHash) {
        this.bitmap = bitmap;
        this.width = width;
        this.height = height;
        this.generation = generation;
        this.sequence = sequence;
        this.captureElapsedMs = captureElapsedMs;
        this.contentHash = contentHash;
    }
}
