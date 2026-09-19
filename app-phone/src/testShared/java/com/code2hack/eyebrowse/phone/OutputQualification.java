package com.code2hack.eyebrowse.phone;

import java.util.List;

/**
 * Test-only temporal qualification for raw capture callbacks (Owner Option A).
 *
 * <p>Initial raw capture callbacks may be non-qualifying initialization output (for example a
 * white startup frame before the document is drawn). The contract is that the FIRST VALID
 * current-document/current-geometry callback reaches the consumer within
 * {@link #VALIDITY_BOUND_MS} of the ORIGINAL eligibility instant. That clock is never restarted
 * at the first raw callback, a renewal or a readiness wait. Raw callbacks are retained and
 * classified, never hidden, and perpetual initialization output fails. Once the current
 * document/geometry is qualified this is not an excuse for later stale or wrong output.
 *
 * <p>Each {@link Observation} copies the sampled pixel value inside the callback itself; the
 * borrowed pipeline bitmap is neither retained nor re-read, so a later reuse of that buffer can
 * never retroactively qualify an earlier callback. This class is test-only (shared by JVM
 * regressions and Android instrumentation), has no Android dependencies, and is never part of
 * the app APK.
 */
final class OutputQualification {

    private OutputQualification() {}

    /** The unchanged eligibility-to-first-valid-delivery bound for #5. */
    static final long VALIDITY_BOUND_MS = 2_000L;

    /** Channel-wise tolerance used by the unchanged fixture color checks. */
    static final int CHANNEL_TOLERANCE = 8;

    /** Immutable per-callback facts, captured at callback time. */
    static final class Observation {

        final long deliveryUptimeMs;
        final long deliveryElapsedMs;
        final int width;
        final int height;
        final int generation;
        final long sequence;
        final long contentHash;
        final int sampledColor;
        final boolean geometryMatches;
        final boolean contentMatches;
        /** Callback-time qualification outcome; computed only from the facts above. */
        final boolean qualified;

        private Observation(long deliveryUptimeMs, long deliveryElapsedMs, int width, int height,
                int generation, long sequence, long contentHash, int sampledColor,
                boolean geometryMatches, boolean contentMatches, boolean qualified) {
            this.deliveryUptimeMs = deliveryUptimeMs;
            this.deliveryElapsedMs = deliveryElapsedMs;
            this.width = width;
            this.height = height;
            this.generation = generation;
            this.sequence = sequence;
            this.contentHash = contentHash;
            this.sampledColor = sampledColor;
            this.geometryMatches = geometryMatches;
            this.contentMatches = contentMatches;
            this.qualified = qualified;
        }

        /** Delivery delay measured from the ORIGINAL eligibility instant. */
        long delayFrom(long eligibleUptimeMs) {
            return deliveryUptimeMs - eligibleUptimeMs;
        }
    }

    /**
     * Records one callback against the expected current document/geometry. {@code sampledColor}
     * must be read from the delivered bitmap inside the callback. An expected width/height of
     * zero means "not evaluated", so the observation can never qualify.
     */
    static Observation observe(long deliveryUptimeMs, long deliveryElapsedMs, int width,
            int height, int generation, long sequence, long contentHash, int sampledColor,
            int expectedWidth, int expectedHeight, int expectedColor) {
        boolean geometryMatches = expectedWidth > 0 && expectedHeight > 0
                && width == expectedWidth && height == expectedHeight;
        boolean contentMatches = nearColor(sampledColor, expectedColor);
        return new Observation(deliveryUptimeMs, deliveryElapsedMs, width, height, generation,
                sequence, contentHash, sampledColor, geometryMatches, contentMatches,
                geometryMatches && contentMatches);
    }

    /** Channel-wise comparison with tolerance; robust to renderer color-management drift. */
    static boolean nearColor(int actual, int expected) {
        return Math.abs(red(actual) - red(expected)) <= CHANNEL_TOLERANCE
                && Math.abs(green(actual) - green(expected)) <= CHANNEL_TOLERANCE
                && Math.abs(blue(actual) - blue(expected)) <= CHANNEL_TOLERANCE;
    }

    /** True when a delay measured from the ORIGINAL eligibility is inside the unchanged bound. */
    static boolean validWithinBound(long deliveryDelayMs) {
        return deliveryDelayMs >= 0 && deliveryDelayMs <= VALIDITY_BOUND_MS;
    }

    /** Index of the earliest qualifying observation at or after {@code fromIndex}, or -1. */
    static int earliestQualifyingIndex(List<Observation> observations, int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < observations.size(); i++) {
            if (observations.get(i).qualified) {
                return i;
            }
        }
        return -1;
    }

    /** Delay from the original eligibility to that earliest qualifying callback, or -1. */
    static long earliestQualifyingDelayMs(List<Observation> observations, int fromIndex,
            long eligibleUptimeMs) {
        int index = earliestQualifyingIndex(observations, fromIndex);
        return index < 0 ? -1 : observations.get(index).delayFrom(eligibleUptimeMs);
    }

    /** Number of retained non-qualifying observations before the first qualifying one. */
    static int nonQualifyingBefore(List<Observation> observations, int fromIndex, int index) {
        int count = 0;
        for (int i = Math.max(0, fromIndex); i < index; i++) {
            if (!observations.get(i).qualified) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sparse retention summary for durable evidence: first valid delivery, how many earlier
     * non-qualifying callbacks were retained, the delay from the original eligibility when one
     * is supplied, and each retained observation's immutable geometry/sample/verdict (capped).
     */
    static String summary(List<Observation> observations, int fromIndex, long eligibleUptimeMs) {
        int from = Math.max(0, fromIndex);
        int firstValid = earliestQualifyingIndex(observations, from);
        StringBuilder text = new StringBuilder("observed=").append(observations.size() - from)
                .append(" fromIndex=").append(from)
                .append(" firstValid=").append(firstValid);
        if (firstValid >= 0) {
            text.append(" nonQualifyingBefore=")
                    .append(nonQualifyingBefore(observations, from, firstValid));
            if (eligibleUptimeMs > 0) {
                text.append(" firstValidDelay=")
                        .append(observations.get(firstValid).delayFrom(eligibleUptimeMs))
                        .append("ms");
            }
        } else {
            text.append(" nonQualifyingBefore=none-valid totalNonQualifying=")
                    .append(observations.size() - from);
        }
        text.append(" facts=[");
        int shown = 0;
        for (int i = from; i < observations.size() && shown < 10; i++, shown++) {
            if (i > from) {
                text.append("; ");
            }
            Observation observation = observations.get(i);
            text.append(i).append(':').append(observation.width).append('x')
                    .append(observation.height)
                    .append(" #").append(Long.toHexString(observation.contentHash))
                    .append(" sample=#").append(String.format("%08x", observation.sampledColor))
                    .append(" q=").append(observation.qualified ? "Y" : "N");
        }
        if (observations.size() - from > shown) {
            text.append("; +").append(observations.size() - from - shown).append(" more");
        }
        return text.append(']').toString();
    }

    private static int red(int color) {
        return (color >> 16) & 0xff;
    }

    private static int green(int color) {
        return (color >> 8) & 0xff;
    }

    private static int blue(int color) {
        return color & 0xff;
    }
}
