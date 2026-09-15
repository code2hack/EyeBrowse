package com.code2hack.eyebrowse.phone;

/**
 * Immutable identity of one hosting output cycle (B correction, JVM-testable) plus its staged
 * current-output readiness.
 *
 * <p>Identity: the exact WebView, presenting container, ImageReader, geometry, hosting
 * generation, the PhoneBrowserSession output-state version at creation (document/attachment
 * epoch), and the ORIGINAL eligibility instant of the demand that created it.
 *
 * <p>Readiness is a TWO-STAGE, production-only sequence; neither stage alone admits delivery
 * (addendum step 4: a draw/visual callback alone does not prove ImageReader buffer content):
 * <ol>
 * <li>{@code drawCompleted} — a COMPLETED draw pass of the delivered view tree into the
 * presenting surface, observed via {@code ViewTreeObserver.OnDrawListener} (fired during the
 * draw pass after the hierarchy draw, before surface commit). {@code OnPreDraw} is NOT used: it
 * fires before drawing occurs and proves nothing about any buffer.</li>
 * <li>{@code frameRenderedAfterDraw} — a frame-rendered event on the ImageReader's own surface
 * ({@code Surface.setOnFrameRenderedListener}, API 29+), observed strictly after the completed
 * draw. This reports the compositor WROTE a frame into the capture buffer queue after the draw
 * committed; virtual-display compositions are input-driven, so with no input change between the
 * show-time composition and the draw commit, the first composition rendered after the completed
 * draw carries the drawn document.</li>
 * </ol>
 * {@code markReady()} is package-private and reachable only from that production sequence, so a
 * "fake ready" epoch cannot be constructed by callers/tests. Images acquired before readiness
 * are initialization/preparation output: discarded without delivery, without consuming the
 * throttle budget, and without moving the first-delivery clock, which stays anchored to the
 * ORIGINAL eligibility instant.
 *
 * <p>Pending (not-yet-ready) readiness is invalidated by a session output-state change
 * (document commit, attachment change, view replacement): {@code resetReadiness()} returns the
 * epoch to the unready state for re-observation, so a stale draw of a replaced document cannot
 * complete readiness. Already-delivering (ready) epochs are unaffected — B governs the FIRST
 * delivered frame.
 */
final class OutputEpoch {

    private final Object viewRef;
    private final Object presentationRef;
    private final Object readerRef;
    private final int width;
    private final int height;
    private final int densityDpi;
    private final int hostingGeneration;
    private final long outputStateVersion;
    private final long eligibleElapsedMs; // ORIGINAL eligibility anchor; carried across rearms.
    private final long createdElapsedMs;

    private volatile boolean drawCompleted;
    private volatile long drawCompletedElapsedMs;
    private volatile boolean ready; // drawCompleted && frameRenderedAfterDraw.
    private volatile long readyElapsedMs;

    OutputEpoch(Object viewRef, Object presentationRef, Object readerRef,
            int width, int height, int densityDpi, int hostingGeneration, long outputStateVersion,
            long eligibleElapsedMs, long createdElapsedMs) {
        this.viewRef = viewRef;
        this.presentationRef = presentationRef;
        this.readerRef = readerRef;
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
        this.hostingGeneration = hostingGeneration;
        this.outputStateVersion = outputStateVersion;
        this.eligibleElapsedMs = eligibleElapsedMs;
        this.createdElapsedMs = createdElapsedMs;
    }

    /** True when every identity dimension of the epoch still matches the live output cycle. */
    boolean matches(Object view, Object presentation, Object reader,
            int w, int h, int dpi, int generation, long sessionOutputVersion) {
        return viewRef == view && presentationRef == presentation && readerRef == reader
                && width == w && height == h && densityDpi == dpi
                && hostingGeneration == generation && outputStateVersion == sessionOutputVersion;
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

    int hostingGeneration() {
        return hostingGeneration;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    long outputStateVersion() {
        return outputStateVersion;
    }

    /** The ORIGINAL eligibility instant; never re-anchored by readiness or rearm. */
    long eligibleElapsedMs() {
        return eligibleElapsedMs;
    }

    long createdElapsedMs() {
        return createdElapsedMs;
    }

    /** Stage 1: a completed draw pass of the delivered view tree (main-thread caller). */
    void markDrawCompleted(long nowElapsedMs) {
        if (!drawCompleted) {
            drawCompleted = true;
            drawCompletedElapsedMs = nowElapsedMs;
        }
    }

    boolean isDrawCompleted() {
        return drawCompleted;
    }

    long drawCompletedElapsedMs() {
        return drawCompletedElapsedMs;
    }

    /**
     * Stage 2 completion, production-only: a frame rendered into the capture buffer surface
     * strictly after the completed draw. Package-private by design.
     */
    void markReady(long nowElapsedMs) {
        if (drawCompleted && !ready) {
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

    /**
     * Invalidates PENDING readiness after a session output-state change (document/attachment):
     * the observation re-runs against the new output. Never called on a ready epoch.
     */
    void resetReadiness() {
        if (!ready) {
            drawCompleted = false;
            drawCompletedElapsedMs = 0;
        }
    }
}
