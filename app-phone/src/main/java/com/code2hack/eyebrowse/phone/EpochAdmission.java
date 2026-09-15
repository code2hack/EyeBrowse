package com.code2hack.eyebrowse.phone;

/**
 * Pure admission/consumption decision for one captured-image callback against its bound output
 * epoch (B, JVM-testable). No decision input is pixel color: identical-white data before and
 * after any valid authority follows the same path.
 *
 * <ul>
 * <li>{@code DISCARD_STALE} — the callback's bound epoch/reader no longer matches the host's
 * live epoch/reader, or capture is inactive/released: orphaned output of a retired cycle. The
 * reader is NOT touched by this decision (it may already belong to a successor).</li>
 * <li>{@code CONSUME_UNREADY} — the epoch is live/current but the supported output-readiness
 * chain has not reached its recorded state: acquire and close the newest image on the owning
 * path (bounded queue progress; initialization/preparation output stays internal). Delivery is
 * NOT opened by consumption.</li>
 * <li>{@code RECORD_CANDIDATE} — live, recorded chain complete, consumer gate OPEN, inside the
 * 5 fps throttle window: acquire/copy the newest image as the single bounded latest candidate
 * and schedule one continuation so the LAST update is eventually delivered (no FIFO).</li>
 * <li>{@code DELIVER} — live, recorded chain complete, consumer gate OPEN, outside the throttle
 * window: copy and deliver now.</li>
 * </ul>
 * The consumer gate is a production input: while the step-5 window-submission ->
 * ImageReader-buffer correspondence is unresolved, production keeps it CLOSED, so the decision
 * can only be CONSUME_UNREADY for live epochs — an explicitly incomplete, non-delivering state.
 */
final class EpochAdmission {

    enum Decision {
        DISCARD_STALE,
        CONSUME_UNREADY,
        RECORD_CANDIDATE,
        DELIVER
    }

    private EpochAdmission() {
    }

    static Decision evaluate(boolean captureActive, boolean captureReleased, Object liveReader,
            OutputEpoch currentEpoch, OutputEpoch boundEpoch, Object callbackReader,
            boolean consumerGateOpen, boolean deliveredAny, long lastDeliveryMs, long nowMs) {
        if (!captureActive || captureReleased || currentEpoch == null || boundEpoch == null
                || currentEpoch != boundEpoch || callbackReader != liveReader
                || callbackReader != boundEpoch.readerRef()) {
            return Decision.DISCARD_STALE;
        }
        if (!consumerGateOpen) {
            return Decision.CONSUME_UNREADY;
        }
        if (deliveredAny && HostingPolicy.frameThrottled(nowMs, lastDeliveryMs)) {
            return Decision.RECORD_CANDIDATE;
        }
        return Decision.DELIVER;
    }
}
