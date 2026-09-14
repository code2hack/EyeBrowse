package com.code2hack.eyebrowse.phone;

import java.net.URI;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Test-only protocol shared by JVM regressions and Android instrumentation, never the app APK. */
final class HarnessProtocol {
    private HarnessProtocol() {}

    /** All DOM fields come from one evaluation; timestamps bound that evaluation, not its exact instant. */
    static final class Snapshot {
        final String marker;
        final String title;
        final String location;
        final String readyState;
        final long sampleStartMs;
        final long sampleEndMs;

        Snapshot(String marker, String title, String location, String readyState,
                 long sampleStartMs, long sampleEndMs) {
            this.marker = marker;
            this.title = title;
            this.location = location;
            this.readyState = readyState;
            this.sampleStartMs = sampleStartMs;
            this.sampleEndMs = sampleEndMs;
        }

        boolean ready(String expectedTitle, String expectedLocation) {
            return marker != null && marker.matches("L\\d+") && "complete".equals(readyState)
                    && expectedTitle.equals(title) && expectedLocation.equals(location);
        }
    }

    /** Construct BEFORE requesting navigation. Reload may never discard an existing document ID. */
    static final class Baseline {
        private final String previousMarker;
        private final String expectedTitle;
        private final String expectedLocation;

        private Baseline(Snapshot before, String title, String location, boolean reload) {
            if (reload && (before == null || !before.ready(title, location))) {
                throw new IllegalStateException("reload requires a settled outgoing fixture snapshot");
            }
            previousMarker = before == null ? null : before.marker;
            expectedTitle = title;
            expectedLocation = location;
        }

        static Baseline opening(Snapshot before, String title, String location) {
            return new Baseline(before, title, location, false);
        }

        static Baseline reloading(Snapshot before, String title, String location) {
            return new Baseline(before, title, location, true);
        }

        boolean accepts(Snapshot current) {
            return current != null && current.ready(expectedTitle, expectedLocation)
                    && !Objects.equals(previousMarker, current.marker);
        }
    }

    /** Tracks API-call progress, NOT proof of website delivery. There is deliberately no retry loop. */
    static final class Dispatch {
        String stage = "not-attempted";
        long startMs = -1;
        long endMs = -1;

        void tapOnce(Runnable down, Runnable up, LongSupplier clock) {
            begin(clock);
            try {
                stage = "down-attempted";
                down.run();
                stage = "up-attempted";
                up.run();
                stage = "returned";
            } finally {
                endMs = clock.getAsLong();
            }
        }

        void actionOnce(Runnable action, LongSupplier clock) {
            begin(clock);
            try {
                stage = "action-attempted";
                action.run();
                stage = "returned";
            } finally {
                endMs = clock.getAsLong();
            }
        }

        private void begin(LongSupplier clock) {
            if (!"not-attempted".equals(stage)) {
                throw new IllegalStateException("an uncertain action must not be replayed");
            }
            startMs = clock.getAsLong();
        }
    }

    /** Redacted diagnostic representation only; actual-location assertions use the original value. */
    static String traceLocation(String actualLocation, String expectedFixtureLocation) {
        try {
            URI actual = new URI(actualLocation);
            URI expected = new URI(expectedFixtureLocation);
            if (!("http".equals(expected.getScheme()) || "https".equals(expected.getScheme()))
                    || expected.getHost() == null || expected.getRawUserInfo() != null
                    || expected.getRawQuery() != null || expected.getRawFragment() != null
                    || actual.getRawUserInfo() != null
                    || !expected.getScheme().equals(actual.getScheme())
                    || !expected.getHost().equalsIgnoreCase(actual.getHost())
                    || port(expected) != port(actual)
                    || !Objects.equals(expected.getRawPath(), actual.getRawPath())) {
                return "(other)";
            }
            return expected.toASCIIString();
        } catch (Exception invalid) {
            return "(other)";
        }
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
    }

    static void requireRecorded(boolean recorded) {
        if (!recorded) throw new IllegalStateException("fixture did not acknowledge case evidence");
    }

    interface Capture { void run() throws Exception; }

    static void preserveFailure(Throwable original, Capture capture) {
        try {
            capture.run();
        } catch (Exception | AssertionError captureFailure) {
            if (captureFailure != original) original.addSuppressed(captureFailure);
        }
    }
}
