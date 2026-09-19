package com.code2hack.eyebrowse.phone

/**
 * Single-owner capture lifecycle phase machine (R2), JVM-testable and single-threaded by
 * contract: every transition is executed by the owning main-thread path of
 * {@code PrivateDisplayHost}.
 *
 * <p>Phases: {@code IDLE} (no capture owner), {@code ACTIVE} (a live capture owner with an armed
 * reader/thread), {@code RETIRING} (a teardown has been requested; the retiring owner's resources
 * are still retained until its completion, and no replacement capture may start), and
 * {@code QUIESCENT} (the previous owner fully retired; a replacement is safe).
 *
 * <p>The invariant this machine enforces: while a previous capture owner is retiring, neither
 * reacquisition nor restart may begin — a replacement can only be armed from {@code IDLE} or
 * {@code QUIESCENT}. Combined with snapshot teardown (the retiring task closes only its own
 * captured references), this prevents a stale teardown from closing replacement resources or
 * overlapping two consumers' borrowed-buffer lifetimes.
 */
internal class CaptureOwnerPhase {

    enum class Phase {
        IDLE,
        ACTIVE,
        RETIRING,
        QUIESCENT
    }

    private var phase: Phase = Phase.IDLE

    fun phase(): Phase {
        return phase
    }

    /** True when a teardown has been requested but has not completed. */
    fun isRetiring(): Boolean {
        return phase == Phase.RETIRING
    }

    /** True when the previous owner has fully retired (or none ever existed). */
    fun isQuiescent(): Boolean {
        return phase == Phase.IDLE || phase == Phase.QUIESCENT
    }

    /** True while a capture owner is live (frame production possible). */
    fun isActive(): Boolean {
        return phase == Phase.ACTIVE
    }

    /** Starts a capture owner; legal only from a quiescent state. */
    fun beginActive(): Boolean {
        if (phase != Phase.IDLE && phase != Phase.QUIESCENT) {
            return false
        }
        phase = Phase.ACTIVE
        return true
    }

    /** Requests retirement of the live owner; legal only from {@code ACTIVE}. */
    fun beginRetiring(): Boolean {
        if (phase != Phase.ACTIVE) {
            return false
        }
        phase = Phase.RETIRING
        return true
    }

    /** Completes the retiring teardown; legal only from {@code RETIRING}. */
    fun completeRetirement(): Boolean {
        if (phase != Phase.RETIRING) {
            return false
        }
        phase = Phase.QUIESCENT
        return true
    }
}
