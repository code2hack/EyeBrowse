package com.code2hack.eyebrowse.phone;

/**
 * Immutable identity of one hosting output cycle (B correction, JVM-testable): the exact
 * WebView, presenting container, ImageReader, geometry and hosting generation whose composed
 * output may be delivered, plus the ORIGINAL eligibility instant of the demand that created it.
 *
 * <p>Delivery admission requires the epoch's current-output readiness to have completed: the
 * bound view recorded a draw into the CURRENT presentation while this epoch was the live one
 * (one-shot pre-draw observation registered at epoch creation). Images acquired before that
 * completion are initialization/preparation output of the display surface and are discarded
 * without delivery, without consuming the throttle budget and without moving the first-delivery
 * clock — the first admissible frame is measured from the unchanged eligibility instant (B:
 * first delivered frame carries the current document at current geometry within 2s of
 * eligibility; initialization buffers stay internal).
 *
 * <p>Identity fields are immutable; only the readiness flag/timestamp transition (once, on the
 * main thread). A superseded epoch never becomes ready retroactively: callers compare epoch
 * identity against the host's current epoch before acting on ready completions or delivering.
 *
 * <p>What the readiness completion guarantees is documented where it is established
 * (PrivateDisplayHost): the view's draw is recorded into the presenting surface; combined with
 * latest-only acquisition strictly after completion and the bounded drain of queued
 * initialization buffers, the first admitted buffer is the first composition produced after
 * that draw. The exact native fence/compositor ordering beyond that is an implementation
 * question the device verification observes, not a claimed guarantee.
 */
final class OutputEpoch {

    private final Object viewRef;
    private final Object presentationRef;
    private final Object readerRef;
    private final int width;
    private final int height;
    private final int densityDpi;
    private final int hostingGeneration;
    private final long eligibleElapsedMs; // ORIGINAL eligibility anchor; carried across rearms.
    private final long createdElapsedMs;

    private volatile boolean ready;
    private volatile long readyElapsedMs;

    OutputEpoch(Object viewRef, Object presentationRef, Object readerRef,
            int width, int height, int densityDpi, int hostingGeneration,
            long eligibleElapsedMs, long createdElapsedMs) {
        this.viewRef = viewRef;
        this.presentationRef = presentationRef;
        this.readerRef = readerRef;
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
        this.hostingGeneration = hostingGeneration;
        this.eligibleElapsedMs = eligibleElapsedMs;
        this.createdElapsedMs = createdElapsedMs;
    }

    /** True when every identity dimension of the epoch still matches the live output cycle. */
    boolean matches(Object view, Object presentation, Object reader,
            int w, int h, int dpi, int generation) {
        return viewRef == view && presentationRef == presentation && readerRef == reader
                && width == w && height == h && densityDpi == dpi
                && hostingGeneration == generation;
    }

    Object viewRef() {
        return viewRef;
    }

    Object presentationRef() {
        return presentationRef;
    }

    Object readerRef() {
        return readerRef;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int hostingGeneration() {
        return hostingGeneration;
    }

    /** The ORIGINAL eligibility instant; never re-anchored by readiness or rearm. */
    long eligibleElapsedMs() {
        return eligibleElapsedMs;
    }

    long createdElapsedMs() {
        return createdElapsedMs;
    }

    /** Marks current-output readiness complete; first completion wins (main-thread caller). */
    void markReady(long nowElapsedMs) {
        if (!ready) {
            ready = true;
            readyElapsedMs = nowElapsedMs;
        }
    }

    boolean isReady() {
        return ready;
    }

    long readyElapsedMs() {
        return readyElapsedMs;
    }
}
