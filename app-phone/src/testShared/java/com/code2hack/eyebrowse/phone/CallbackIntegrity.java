package com.code2hack.eyebrowse.phone;

/**
 * Test-only immutable callback-entry / post-hold integrity facts for one borrowed frame buffer.
 *
 * <p>The fingerprint covers the WHOLE valid bitmap (not only {@code frame.contentHash} or the
 * sampled cream corner), so changed pixels anywhere fail independently of any document-color
 * classification. A stable initialization frame therefore satisfies integrity even when it is not
 * qualified as current-document output. Values are copied at observation time and the borrowed
 * bitmap is never retained beyond its callback. Pure JVM logic shared by the JVM regressions and
 * the instrumented delayed consumer; never part of the app APK.
 */
final class CallbackIntegrity {

    private CallbackIntegrity() {}

    /** One immutable in-callback observation of the borrowed buffer. */
    static final class Snapshot {
        final long entryUptimeMs;
        final long entryElapsedMs;
        final int generation;
        final long sequence;
        final int declaredWidth;
        final int declaredHeight;
        final int actualWidth;
        final int actualHeight;
        final String config;
        final int pixelCount;
        final long fingerprint;

        private Snapshot(long entryUptimeMs, long entryElapsedMs, int generation, long sequence,
                int declaredWidth, int declaredHeight, int actualWidth, int actualHeight,
                String config, int pixelCount, long fingerprint) {
            this.entryUptimeMs = entryUptimeMs;
            this.entryElapsedMs = entryElapsedMs;
            this.generation = generation;
            this.sequence = sequence;
            this.declaredWidth = declaredWidth;
            this.declaredHeight = declaredHeight;
            this.actualWidth = actualWidth;
            this.actualHeight = actualHeight;
            this.config = config;
            this.pixelCount = pixelCount;
            this.fingerprint = fingerprint;
        }

        /** True when two observations from the same callback describe identical buffer content. */
        boolean sameContent(Snapshot other) {
            return other != null
                    && generation == other.generation
                    && sequence == other.sequence
                    && declaredWidth == other.declaredWidth
                    && declaredHeight == other.declaredHeight
                    && actualWidth == other.actualWidth
                    && actualHeight == other.actualHeight
                    && pixelCount == other.pixelCount
                    && java.util.Objects.equals(config, other.config)
                    && fingerprint == other.fingerprint;
        }

        String summary() {
            return "entryUptime=" + entryUptimeMs + " entryElapsed=" + entryElapsedMs
                    + " gen=" + generation + " seq=" + sequence
                    + " declared=" + declaredWidth + "x" + declaredHeight
                    + " actual=" + actualWidth + "x" + actualHeight
                    + " config=" + config + " pixels=" + pixelCount
                    + " fingerprint=" + Long.toHexString(fingerprint);
        }
    }

    /**
     * FNV-1a over every pixel of the valid bitmap copy. Identical buffer content yields an
     * identical fingerprint; a change anywhere (including outside the sampled corner) changes it.
     */
    static long fingerprint(int[] pixels, int count) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < count; i++) {
            int value = pixels[i];
            for (int shift = 0; shift < 32; shift += 8) {
                hash ^= (value >>> shift) & 0xff;
                hash *= 0x100000001b3L;
            }
        }
        return hash;
    }

    static Snapshot of(long entryUptimeMs, long entryElapsedMs, int generation, long sequence,
            int declaredWidth, int declaredHeight, int actualWidth, int actualHeight,
            String config, int[] pixels, int pixelCount) {
        return new Snapshot(entryUptimeMs, entryElapsedMs, generation, sequence, declaredWidth,
                declaredHeight, actualWidth, actualHeight, config, pixelCount,
                fingerprint(pixels, pixelCount));
    }
}
