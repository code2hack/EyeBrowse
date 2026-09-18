package com.code2hack.eyebrowse.phone;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Test-only final input admission shared by Android instrumentation and JVM regressions.
 *
 * <p>The caller samples DOM and native state after its last known slow/idle/evidence boundary,
 * then calls one of the dispatch methods below without another wait or observation. A DOM-only
 * document/target change therefore stops before {@link HarnessProtocol.Dispatch} begins. When a
 * caller deliberately recomputes the path from that final native snapshot, benign earlier mapping
 * drift does not force reuse of stale coordinates: ownership/display must still match preparation,
 * the final state must itself be safe, and the exact DOM target/document must still match.
 * This class is never part of the app APK.
 */
final class DispatchReadiness {
    private DispatchReadiness() {}

    /** Exact current-document and target/viewport facts from one coherent JavaScript evaluation. */
    static final class DomState {
        final String marker;
        final String location;
        final String target;
        final String geometry;

        DomState(String marker, String location, String target, String geometry) {
            this.marker = marker;
            this.location = location;
            this.target = target;
            this.geometry = geometry;
        }

        String revalidationReason(DomState current) {
            if (current == null || marker == null || location == null || target == null
                    || geometry == null || current.marker == null || current.location == null
                    || current.target == null || current.geometry == null) {
                return "DOM readiness unavailable";
            }
            if (!Objects.equals(marker, current.marker)
                    || !Objects.equals(location, current.location)) {
                return "document changed after preparation";
            }
            if (!Objects.equals(target, current.target)
                    || !Objects.equals(geometry, current.geometry)) {
                return "DOM target/viewport changed after preparation";
            }
            return null;
        }

        String describe() {
            return "marker=" + marker + " location=" + location + " target=" + target
                    + " geometry=" + geometry;
        }
    }

    static String finalReason(InputSafety.State preparedNative, InputSafety.State currentNative,
            InputSafety.Path path, DomState preparedDom, DomState currentDom) {
        String nativeReason = preparedNative.revalidationReason(currentNative, path);
        if (nativeReason != null) {
            return nativeReason;
        }
        return preparedDom == null ? "DOM readiness unavailable"
                : preparedDom.revalidationReason(currentDom);
    }

    static void requireReady(InputSafety.State preparedNative, InputSafety.State currentNative,
            InputSafety.Path path, DomState preparedDom, DomState currentDom) {
        String reason = finalReason(preparedNative, currentNative, path, preparedDom, currentDom);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
    }

    /**
     * Final admission for a path recomputed from {@code currentNative}. The old snapshot is used only
     * to fence the intended Activity/WebView/display identity; mapping/bounds/insets may legitimately
     * settle before the final callback because the caller is not reusing the old coordinates.
     */
    static String recomputedFinalReason(InputSafety.State preparedNative,
            InputSafety.State currentNative, InputSafety.Path currentPath,
            DomState preparedDom, DomState currentDom) {
        if (preparedNative == null || currentNative == null) {
            return "input readiness unavailable";
        }
        String unsafe = currentNative.unsafeReason(currentPath);
        if (unsafe != null) {
            return unsafe;
        }
        if (preparedNative.activity != currentNative.activity
                || preparedNative.target != currentNative.target) {
            return "input owner changed after preparation";
        }
        if (preparedNative.displayId != currentNative.displayId) {
            return "input display changed after preparation";
        }
        return preparedDom == null ? "DOM readiness unavailable"
                : preparedDom.revalidationReason(currentDom);
    }

    static void requireRecomputedReady(InputSafety.State preparedNative,
            InputSafety.State currentNative, InputSafety.Path currentPath,
            DomState preparedDom, DomState currentDom) {
        String reason = recomputedFinalReason(preparedNative, currentNative, currentPath,
                preparedDom, currentDom);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
    }

    @FunctionalInterface
    interface CallbackWork {
        void run() throws Exception;
    }

    /**
     * Exact callback-to-test-thread failure relay used by the final Android readiness/input path.
     * Checked injection failures are retained by object identity; assertion/runtime failures keep
     * the same behavior. Fatal VM errors are deliberately not converted into ordinary test results.
     */
    static final class CallbackHandoff {
        private final AtomicReference<Throwable> primary = new AtomicReference<>();

        void capture(CallbackWork work) {
            try {
                work.run();
            } catch (Exception | AssertionError failure) {
                primary.compareAndSet(null, failure);
            }
        }

        void rethrowIfPresent() throws Exception {
            Throwable failure = primary.get();
            if (failure == null) {
                return;
            }
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw (AssertionError) failure;
        }
    }

    static void actionOnceIfReady(InputSafety.State preparedNative, InputSafety.State currentNative,
            InputSafety.Path path, DomState preparedDom, DomState currentDom,
            HarnessProtocol.Dispatch dispatch, Runnable action, LongSupplier clock) {
        requireReady(preparedNative, currentNative, path, preparedDom, currentDom);
        dispatch.actionOnce(action, clock);
    }

    static void tapOnceIfReady(InputSafety.State preparedNative, InputSafety.State currentNative,
            InputSafety.Path path, DomState preparedDom, DomState currentDom,
            HarnessProtocol.Dispatch dispatch, Runnable down, Runnable up, LongSupplier clock) {
        requireReady(preparedNative, currentNative, path, preparedDom, currentDom);
        dispatch.tapOnce(down, up, clock);
    }

    static void actionOnceIfRecomputedReady(InputSafety.State preparedNative,
            InputSafety.State currentNative, InputSafety.Path currentPath,
            DomState preparedDom, DomState currentDom, HarnessProtocol.Dispatch dispatch,
            Runnable action, LongSupplier clock) {
        requireRecomputedReady(preparedNative, currentNative, currentPath, preparedDom, currentDom);
        dispatch.actionOnce(action, clock);
    }

    static void tapOnceIfRecomputedReady(InputSafety.State preparedNative,
            InputSafety.State currentNative, InputSafety.Path currentPath,
            DomState preparedDom, DomState currentDom, HarnessProtocol.Dispatch dispatch,
            Runnable down, Runnable up, LongSupplier clock) {
        requireRecomputedReady(preparedNative, currentNative, currentPath, preparedDom, currentDom);
        dispatch.tapOnce(down, up, clock);
    }
}
