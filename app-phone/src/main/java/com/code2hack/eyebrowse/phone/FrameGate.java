package com.code2hack.eyebrowse.phone;

/**
 * Admission authority for captured-frame delivery (JVM-testable, lock-free).
 *
 * <p>Each lease opens the gate with its own opaque token, the hosting generation and an absolute
 * expiry deadline ({@code acceptUntilElapsedMs}). A capture callback is bound to that token at
 * lease acquisition, so an in-flight callback from a superseded lease can never be admitted into
 * a replacement consumer — even within the same hosting generation.
 *
 * <p>Expiry authority (R4): delivery admission and renewal both reject once the authoritative
 * deadline has passed — independently of any later cleanup tick or watchdog. An expired lease
 * cannot deliver a frame or resurrect itself through a late renewal; the watchdog only observes
 * the same deadline to run the bounded stop/wake-lock completion, it is not the revocation
 * authority. Revocation closes the gate; an already admitted frame may finish delivery to its own
 * consumer (documented in-flight borrowed use).
 *
 * <p>All state is volatile: the delivery path never takes the controller monitor, so controller
 * teardown holding that monitor cannot deadlock against a delivery callback. Time is passed in by
 * the caller (compatible monotonic {@code SystemClock.elapsedRealtime()} in production, explicit
 * values in JVM tests), keeping every decision a pure function of its arguments.
 */
final class FrameGate {

    private volatile Object currentToken;
    private volatile int generation;
    private volatile boolean accepting;
    private volatile long acceptUntilElapsedMs;

    /**
     * Opens the gate for {@code token} at {@code hostingGeneration}, admitting only that pair and
     * only until {@code acceptUntilElapsedMs} on the compatible monotonic clock.
     */
    void open(Object token, int hostingGeneration, long acceptUntilElapsedMs) {
        this.generation = hostingGeneration;
        this.currentToken = token;
        this.accepting = true;
        this.acceptUntilElapsedMs = acceptUntilElapsedMs;
    }

    /**
     * Admits a frame only for the current token and generation, while the gate is open and the
     * authoritative expiry deadline has not yet been reached at {@code nowElapsedMs} (R4):
     * authority expires AT the deadline instant, so {@code now == acceptUntil} is expired.
     */
    boolean admit(Object token, int frameGeneration, long nowElapsedMs) {
        return accepting && currentToken == token && generation == frameGeneration
                && nowElapsedMs < acceptUntilElapsedMs;
    }

    /**
     * Attempts a renewal at {@code nowElapsedMs}: accepted only while the gate is open and the
     * current deadline has not yet been reached (expiry is AT the deadline); on success the
     * deadline moves to {@code newAcceptUntilMs}. A renewal at or after expiry is rejected and
     * moves nothing — an expired lease cannot revive its delivery authority (R4).
     */
    boolean renew(long nowElapsedMs, long newAcceptUntilMs) {
        if (!accepting || nowElapsedMs >= acceptUntilElapsedMs) {
            return false;
        }
        acceptUntilElapsedMs = newAcceptUntilMs;
        return true;
    }

    /** Closes the gate: no further admissions or renewals until the next open. */
    void close() {
        accepting = false;
        currentToken = null;
    }

    boolean isOpen() {
        return accepting;
    }

    /** Current expiry deadline (diagnostics/tests). */
    long acceptUntilElapsedMs() {
        return acceptUntilElapsedMs;
    }
}
