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

    /** Retain whatever evidence exists even when the body/cleanup fails; keep the first failure. */
    static void withFinalEvidence(Capture body, Capture evidence) throws Exception {
        Throwable primary = null;
        try {
            body.run();
        } catch (Exception | AssertionError failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary == null) evidence.run();
            else preserveFailure(primary, evidence);
        }
    }

    /** The existing Android main Handler supplies this queue; JVM tests control that same path. */
    interface MainQueue {
        void post(Runnable work);
        void remove(Runnable work);
    }

    interface DiagnosticWork { void start(Diagnostic operation); }

    /** One test instance owns its queued observations, never a static/later test owner. */
    static final class DiagnosticOwner {
        private boolean open = true;
        private final java.util.Set<Diagnostic> pending = new java.util.HashSet<>();

        synchronized boolean attach(Diagnostic operation) {
            if (!open) return false;
            pending.add(operation);
            return true;
        }

        synchronized boolean isOpen() { return open; }
        synchronized void detach(Diagnostic operation) { pending.remove(operation); }
        synchronized int pendingCount() { return pending.size(); }

        void close() {
            Diagnostic[] operations;
            synchronized (this) {
                open = false;
                operations = pending.toArray(new Diagnostic[0]);
            }
            for (Diagnostic operation : operations) operation.cancel();
        }
    }

    /**
     * Small test-owned asynchronous observation, not a worker/executor. The queued runnable holds
     * only this token; cancellation clears its work closure (and hence any intended references).
     * Platform JS already dispatched cannot be recalled, but its callback holds only this token
     * and cannot publish after expiry/teardown. Every UI/JS access also checks active() at the call
     * site. No scenario lookup, blocking main dispatch or per-capture thread is involved.
     */
    static final class Diagnostic implements Runnable {
        private final DiagnosticOwner owner;
        private final FailureBudget budget;
        private final MainQueue queue;
        private final java.util.concurrent.CountDownLatch done =
                new java.util.concurrent.CountDownLatch(1);
        private volatile DiagnosticWork work;
        private volatile boolean cancelled;
        private volatile String result;

        Diagnostic(DiagnosticOwner owner, FailureBudget budget, MainQueue queue,
                DiagnosticWork work) {
            this.owner = owner;
            this.budget = budget;
            this.queue = queue;
            this.work = work;
        }

        void schedule() {
            if (!owner.attach(this) || !active()) {
                cancel();
                return;
            }
            try {
                queue.post(this);
                if (!active()) cancel();
            } catch (RuntimeException | AssertionError failure) {
                cancel();
                throw failure;
            }
        }

        boolean active() {
            return !cancelled && owner.isOpen() && !budget.expired();
        }

        @Override public void run() {
            DiagnosticWork current = work;
            work = null;
            if (current == null || !active()) return;
            try {
                current.start(this);
            } catch (RuntimeException | AssertionError unavailable) {
                complete("unavailable(" + unavailable.getClass().getSimpleName() + ")");
            }
        }

        /** Admission rechecks the actual weak UI owner at callback time, not only test lifetime. */
        void completeIfOwned(String value, java.util.function.BooleanSupplier stillOwned) {
            if (!active()) return;
            try {
                if (!stillOwned.getAsBoolean()) {
                    cancel();
                    return;
                }
            } catch (RuntimeException | AssertionError unavailable) {
                cancel();
                return;
            }
            complete(value);
        }

        synchronized void complete(String value) {
            if (!active() || done.getCount() == 0) return;
            result = value;
            done.countDown();
        }

        String await() {
            try {
                long remaining = budget.stepBudgetMs();
                if (remaining <= 0 || !done.await(remaining,
                        java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    budget.cancel();
                    return null;
                }
                return active() ? result : null;
            } catch (InterruptedException interrupted) {
                budget.cancel();
                Thread.currentThread().interrupt();
                return null;
            } finally {
                cancel();
            }
        }

        void cancel() {
            synchronized (this) {
                cancelled = true;
                work = null;
                result = null;
                done.countDown();
            }
            owner.detach(this);
            queue.remove(this);
        }
    }

    /**
     * One absolute diagnostic deadline shared by every failure-time capture step (main dispatch,
     * JavaScript observation and waiting). Results that arrive after the deadline are discarded
     * rather than recorded. Pure logic so the budget/expiry paths have JVM regressions.
     */
    static final class FailureBudget {
        /** The plan's bounded failure-capture budget. */
        static final long DEFAULT_BUDGET_MS = 2_000;

        private final LongSupplier clock;
        private final long startedMs;
        private final long deadlineMs;
        private volatile boolean cancelled;

        FailureBudget(LongSupplier clock, long budgetMs) {
            this.clock = clock;
            this.startedMs = clock.getAsLong();
            this.deadlineMs = startedMs + Math.max(1, budgetMs);
        }

        long elapsedMs() {
            return Math.max(0, clock.getAsLong() - startedMs);
        }

        long stepBudgetMs() {
            return cancelled ? 0 : Math.max(0, deadlineMs - clock.getAsLong());
        }

        void cancel() { cancelled = true; }

        boolean expired() {
            return cancelled || clock.getAsLong() >= deadlineMs;
        }

    }
}
