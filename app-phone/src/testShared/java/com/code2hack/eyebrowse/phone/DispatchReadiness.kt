package com.code2hack.eyebrowse.phone

import java.util.concurrent.atomic.AtomicReference
import java.util.function.LongSupplier

/**
 * Test-only final input admission shared by Android instrumentation and JVM regressions.
 *
 * The caller samples DOM and native state after its last known slow/idle/evidence boundary, then
 * calls one of the dispatch methods below without another wait or observation. A DOM-only
 * document/target change therefore stops before HarnessProtocol.Dispatch begins. When a caller
 * deliberately recomputes the path from that final native snapshot, benign earlier mapping drift
 * does not force reuse of stale coordinates: ownership/display must still match preparation, the
 * final state must itself be safe, and the exact DOM target/document must still match.
 *
 * This class is never part of the app APK.
 */
internal class DispatchReadiness private constructor() {
    class DomState(
        val marker: String?,
        val location: String?,
        val target: String?,
        val geometry: String?,
    ) {
        fun revalidationReason(current: DomState?): String? {
            if (
                current == null ||
                marker == null ||
                location == null ||
                target == null ||
                geometry == null ||
                current.marker == null ||
                current.location == null ||
                current.target == null ||
                current.geometry == null
            ) {
                return "DOM readiness unavailable"
            }
            if (marker != current.marker || location != current.location) {
                return "document changed after preparation"
            }
            if (target != current.target || geometry != current.geometry) {
                return "DOM target/viewport changed after preparation"
            }
            return null
        }

        fun describe(): String =
            "marker=$marker location=$location target=$target geometry=$geometry"
    }

    fun interface CallbackWork {
        @Throws(Exception::class)
        fun run()
    }

    /**
     * Exact callback-to-test-thread failure relay used by the final Android readiness/input path.
     * Checked injection failures are retained by object identity; assertion/runtime failures keep
     * the same behavior. Fatal VM errors are deliberately not converted into ordinary test results.
     */
    class CallbackHandoff {
        private val primary = AtomicReference<Throwable?>()

        fun capture(work: CallbackWork): Boolean =
            try {
                work.run()
                true
            } catch (failure: Exception) {
                primary.compareAndSet(null, failure)
                false
            } catch (failure: AssertionError) {
                primary.compareAndSet(null, failure)
                false
            }

        @Throws(Exception::class)
        fun rethrowIfPresent() {
            when (val failure = primary.get()) {
                null -> return
                is Exception -> throw failure
                is AssertionError -> throw failure
                else -> throw IllegalStateException("unexpected callback failure type", failure)
            }
        }
    }

    companion object {
        fun finalReason(
            preparedNative: InputSafety.State,
            currentNative: InputSafety.State,
            path: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
        ): String? {
            val nativeReason = preparedNative.revalidationReason(currentNative, path)
            if (nativeReason != null) {
                return nativeReason
            }
            return if (preparedDom == null) {
                "DOM readiness unavailable"
            } else {
                preparedDom.revalidationReason(currentDom)
            }
        }

        fun requireReady(
            preparedNative: InputSafety.State,
            currentNative: InputSafety.State,
            path: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
        ) {
            val reason = finalReason(preparedNative, currentNative, path, preparedDom, currentDom)
            if (reason != null) {
                throw IllegalStateException(reason)
            }
        }

        /**
         * Final admission for a path recomputed from currentNative. The old snapshot is used only
         * to fence intended Activity/WebView/display identity; mapping/bounds/insets may legitimately
         * settle before the final callback because the caller is not reusing the old coordinates.
         */
        fun recomputedFinalReason(
            preparedNative: InputSafety.State?,
            currentNative: InputSafety.State?,
            currentPath: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
        ): String? {
            if (preparedNative == null || currentNative == null) {
                return "input readiness unavailable"
            }
            val unsafe = currentNative.unsafeReason(currentPath)
            if (unsafe != null) {
                return unsafe
            }
            if (
                preparedNative.activity !== currentNative.activity ||
                preparedNative.target !== currentNative.target
            ) {
                return "input owner changed after preparation"
            }
            if (preparedNative.displayId != currentNative.displayId) {
                return "input display changed after preparation"
            }
            return if (preparedDom == null) {
                "DOM readiness unavailable"
            } else {
                preparedDom.revalidationReason(currentDom)
            }
        }

        fun requireRecomputedReady(
            preparedNative: InputSafety.State?,
            currentNative: InputSafety.State?,
            currentPath: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
        ) {
            val reason = recomputedFinalReason(
                preparedNative,
                currentNative,
                currentPath,
                preparedDom,
                currentDom,
            )
            if (reason != null) {
                throw IllegalStateException(reason)
            }
        }

        fun actionOnceIfReady(
            preparedNative: InputSafety.State,
            currentNative: InputSafety.State,
            path: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
            dispatch: HarnessProtocol.Dispatch,
            action: Runnable,
            clock: LongSupplier,
        ) {
            requireReady(preparedNative, currentNative, path, preparedDom, currentDom)
            dispatch.actionOnce(action, clock)
        }

        fun tapOnceIfReady(
            preparedNative: InputSafety.State,
            currentNative: InputSafety.State,
            path: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
            dispatch: HarnessProtocol.Dispatch,
            down: Runnable,
            up: Runnable,
            clock: LongSupplier,
        ) {
            requireReady(preparedNative, currentNative, path, preparedDom, currentDom)
            dispatch.tapOnce(down, up, clock)
        }

        fun actionOnceIfRecomputedReady(
            preparedNative: InputSafety.State?,
            currentNative: InputSafety.State?,
            currentPath: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
            dispatch: HarnessProtocol.Dispatch,
            action: Runnable,
            clock: LongSupplier,
        ) {
            requireRecomputedReady(
                preparedNative,
                currentNative,
                currentPath,
                preparedDom,
                currentDom,
            )
            dispatch.actionOnce(action, clock)
        }

        fun tapOnceIfRecomputedReady(
            preparedNative: InputSafety.State?,
            currentNative: InputSafety.State?,
            currentPath: InputSafety.Path,
            preparedDom: DomState?,
            currentDom: DomState?,
            dispatch: HarnessProtocol.Dispatch,
            down: Runnable,
            up: Runnable,
            clock: LongSupplier,
        ) {
            requireRecomputedReady(
                preparedNative,
                currentNative,
                currentPath,
                preparedDom,
                currentDom,
            )
            dispatch.tapOnce(down, up, clock)
        }
    }
}
