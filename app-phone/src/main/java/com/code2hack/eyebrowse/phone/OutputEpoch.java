package com.code2hack.eyebrowse.phone;

/**
 * Immutable identity of one hosting output cycle (B, JVM-testable) with its supported
 * visual-state -> window-submission stages, plus replacement semantics.
 *
 * <p>Identity: the exact WebView, presenting container, ImageReader, geometry, hosting
 * generation, the PhoneBrowserSession output-state version at creation (document/attachment
 * epoch), and the ORIGINAL eligibility instant of the demand that created it. The full live
 * tuple is revalidated (not mere object identity) at every production callback/admission.
 *
 * <p>Stages (production-only completion, package-private mutators):
 * <ol>
 * <li>{@code visualStateCompleted} — the WebView's {@code postVisualStateCallback} completed
 * with the full live tuple revalidated: the next draw reflects DOM state through the request
 * point (official guarantee; visibility/attachment conditions apply; video excluded). This is a
 * document-readiness signal, NOT rendered output.</li>
 * <li>{@code windowSubmitted} — {@code ViewTreeObserver.registerFrameCommitCallback} (API 29+)
 * fired for a traversal of the same live hardware-rendered hierarchy registered after stage 1:
 * hardware rendering has rendered a frame and SUBMITTED it to the window swap chain. The frame
 * need not yet be visible, and this says NOTHING about any ImageReader buffer's content.</li>
 * </ol>
 * The recorded terminal state is therefore WINDOW_SUBMITTED, explicitly NOT output-ready: the
 * window-submission -> VirtualDisplay/ImageReader buffer correspondence (step 5) is unresolved,
 * and the production consumer admission gate stays CLOSED while it is unresolved.
 *
 * <p>Replacement, not reset: a relevant output-state change (document commit — including a
 * same-URL replacement — attachment change, view replacement) supersedes the epoch with a NEW
 * one. A pending or window-submitted-but-never-delivered epoch is thereby invalidated as a
 * whole; its stages never complete retroactively for the replacement. The ORIGINAL eligibility
 * anchor is carried across same-lease replacements and is never re-anchored by any stage.
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

    private volatile boolean visualStateCompleted;
    private volatile long visualStateElapsedMs;
    private volatile boolean windowSubmitted;
    private volatile long windowSubmittedElapsedMs;

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

    /** Full live-identity revalidation: every dimension must match the CURRENT output cycle. */
    boolean matchesLive(Object view, Object presentation, Object reader,
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

    long outputStateVersion() {
        return outputStateVersion;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /** The ORIGINAL eligibility instant; never re-anchored by any stage or replacement. */
    long eligibleElapsedMs() {
        return eligibleElapsedMs;
    }

    long createdElapsedMs() {
        return createdElapsedMs;
    }

    /** Stage 1 completion (production path only, main thread, full tuple pre-validated). */
    void markVisualStateCompleted(long nowElapsedMs) {
        if (!visualStateCompleted) {
            visualStateCompleted = true;
            visualStateElapsedMs = nowElapsedMs;
        }
    }

    boolean isVisualStateCompleted() {
        return visualStateCompleted;
    }

    long visualStateElapsedMs() {
        return visualStateElapsedMs;
    }

    /**
     * Stage 2 completion (production path only, main thread): requires stage 1; records
     * WINDOW_SUBMITTED — the frame was submitted to the window swap chain, not delivered to any
     * reader, and not output-ready.
     */
    void markWindowSubmitted(long nowElapsedMs) {
        if (visualStateCompleted && !windowSubmitted) {
            windowSubmitted = true;
            windowSubmittedElapsedMs = nowElapsedMs;
        }
    }

    boolean isWindowSubmitted() {
        return windowSubmitted;
    }

    long windowSubmittedElapsedMs() {
        return windowSubmittedElapsedMs;
    }
}
