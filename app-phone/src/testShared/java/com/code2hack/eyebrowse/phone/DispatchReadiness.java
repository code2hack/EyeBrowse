package com.code2hack.eyebrowse.phone;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Test-only final input admission shared by Android instrumentation and JVM regressions.
 *
 * <p>The caller samples DOM and native state after its last known slow/idle/evidence boundary,
 * then calls one of the dispatch methods below without another wait or observation. A DOM-only
 * document/target change therefore stops before {@link HarnessProtocol.Dispatch} begins, even when
 * the Activity/WebView/native mapping remains equal. This class is never part of the app APK.
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
}
