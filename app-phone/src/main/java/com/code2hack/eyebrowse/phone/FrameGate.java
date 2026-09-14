package com.code2hack.eyebrowse.phone;

/**
 * Admission authority for captured-frame delivery (JVM-testable, lock-free).
 *
 * <p>Each lease opens the gate with its own opaque token and the hosting generation; a capture
 * callback is bound to that token at lease acquisition, so an in-flight callback from a
 * superseded lease can never be admitted into a replacement consumer — even within the same
 * hosting generation. Revocation closes the gate, which prevents new admissions; an already
 * admitted frame may finish delivery to its own consumer (documented in-flight borrowed use).
 * All state is volatile: the delivery path never takes the controller monitor, so controller
 * teardown holding that monitor cannot deadlock against a delivery callback.
 */
final class FrameGate {

    private volatile Object currentToken;
    private volatile int generation;
    private volatile boolean accepting;

    /** Opens the gate for {@code token} at {@code hostingGeneration}, admitting only that pair. */
    void open(Object token, int hostingGeneration) {
        this.generation = hostingGeneration;
        this.currentToken = token;
        this.accepting = true;
    }

    /** Admits a frame only for the current token, generation, while the gate is open. */
    boolean admit(Object token, int frameGeneration) {
        return accepting && currentToken == token && generation == frameGeneration;
    }

    /** Closes the gate: no further admissions until the next open. */
    void close() {
        accepting = false;
        currentToken = null;
    }

    boolean isOpen() {
        return accepting;
    }
}
