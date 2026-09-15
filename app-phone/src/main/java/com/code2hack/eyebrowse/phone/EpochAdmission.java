package com.code2hack.eyebrowse.phone;

/**
 * Pure admission decision for one captured image against its bound output epoch (B, JVM-testable).
 *
 * <p>{@code PrivateDisplayHost.onImageAvailableForEpoch} evaluates this BEFORE any throttle or
 * copy work. Decisions:
 * <ul>
 * <li>{@code DISCARD_STALE} — the callback's reader/epoch no longer matches the host's live
 * epoch/reader (superseded lease, replaced reader, retired owner): initialization or orphaned
 * output, never delivered.</li>
 * <li>{@code DISCARD_UNREADY} — the epoch's two-stage current-output readiness has not
 * completed: initialization/preparation output of the display surface, discarded without
 * consuming the throttle budget and without moving the first-delivery clock.</li>
 * <li>{@code THROTTLED} — ready output inside the 5 fps cap window: kept queued (latest-only),
 * not delivered.</li>
 * <li>{@code ADMIT} — ready, current, outside the throttle window: copy and deliver.</li>
 * </ul>
 * No decision input is pixel color: a white document and a colored document follow the same
 * path (the identical-white negative requirement).
 */
final class EpochAdmission {

    enum Decision {
        DISCARD_STALE,
        DISCARD_UNREADY,
        THROTTLED,
        ADMIT
    }

    private EpochAdmission() {
    }

    /**
     * @param captureActive    host capture flag (lease live and armed)
     * @param captureReleased  host release flag (teardown requested)
     * @param liveReader       the host's CURRENT reader identity (may differ from the callback's)
     * @param currentEpoch     the host's CURRENT epoch (null when superseded)
     * @param boundEpoch       the epoch bound at listener registration
     * @param callbackReader   the reader this callback fired for
     * @param deliveredAny     whether an admitted frame was delivered since arm/rearm
     * @param lastDeliveryMs   last admitted delivery time (compatible monotonic clock)
     * @param nowMs            now on the same clock
     */
    static Decision evaluate(boolean captureActive, boolean captureReleased, Object liveReader,
            OutputEpoch currentEpoch, OutputEpoch boundEpoch, Object callbackReader,
            boolean deliveredAny, long lastDeliveryMs, long nowMs) {
        if (!captureActive || captureReleased || currentEpoch == null || boundEpoch == null
                || currentEpoch != boundEpoch || callbackReader != liveReader
                || callbackReader != boundEpoch.readerRef()) {
            return Decision.DISCARD_STALE;
        }
        if (!boundEpoch.isReady()) {
            return Decision.DISCARD_UNREADY;
        }
        if (deliveredAny && HostingPolicy.frameThrottled(nowMs, lastDeliveryMs)) {
            return Decision.THROTTLED;
        }
        return Decision.ADMIT;
    }
}
