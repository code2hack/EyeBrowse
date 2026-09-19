package com.code2hack.eyebrowse.phone

import android.graphics.Bitmap

/**
 * One captured offscreen frame delivered to the single live in-process consumer.
 *
 * <p>The bitmap is a shared, reused buffer owned by the capture pipeline: the consumer must read or
 * copy it during the callback and must not retain or recycle it. {@code generation} fences frames
 * from a superseded hosting session; {@code captureElapsedMs} is the {@link
 * android.os.SystemClock#elapsedRealtime()} capture stamp used for freshness checks.
 *
 * <p>Migration note: the Java original exposed package-private final FIELDS that the unchanged
 * Java instrumentation reads directly (`frame.generation`, `frame.bitmap`, ...); `@JvmField`
 * preserves that field access shape. Visibility is widened package-private -> public for the
 * separately compiled androidTest consumer (disclosed in the migration ledger).
 */
class HostingFrame(
    @JvmField val bitmap: Bitmap,
    @JvmField val width: Int,
    @JvmField val height: Int,
    @JvmField val generation: Int,
    @JvmField val sequence: Long,
    @JvmField val captureElapsedMs: Long,
    @JvmField val contentHash: Long,
)
