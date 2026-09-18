# I5-T01 input-readiness correction history

Expert: Expert

This note records the source correction history on `work/issue-5-expert`. It supersedes the older
input-sequencing description in `docs/testing/phone-browser.md` when the two differ. It is not a
host/device clearance, an I5-T01 handback, or ticket acceptance.

## Round2 result retained

The independent review of `cc1eb56f03a486048a483f87467b79ec4c772bfe` was
`CHANGES_REQUESTED`. Its shared `DispatchReadiness` classifier rejected supplied unequal DOM/native
snapshots, but the used Android sequencing still had a callback-to-input gap:

- swipe sampled DOM through `UiController.loopMainThreadForAtLeast()`, whose pinned Espresso 3.6.1
  implementation subsequently drains the main loop until idle; and
- tap sampled DOM before a later `ActivityScenario.onActivity` native-observation turn.

A DOM-only document/target change in either interval could therefore leave stale DOM A authorizing
input. R2 was source-cleared in that review and remains unchanged: diagnostic JavaScript completion
still uses `Diagnostic.completeIfOwned` with weak actual Activity/WebView ownership plus the
existing test/deadline/duplicate fences.

## Round3 source/host clearance and device result

The round3 candidate `8e3fde9db9b4361a56efae34f0ab9cbc8aaf0e4e` removed those known
callback-to-idle / callback-to-ActivityScenario gaps. Its scoped source/host review passed, but that
was explicitly pre-device evidence. The subsequent Manager-released focused-three device invocation
on the API31 S20+ completed all three methods with **one pass and two failures**:

- `HostingInstrumentedTest#delayedConsumerReleaseReacquireIsolatesBorrowedFrames` passed.
- `BrowserInstrumentedTest#contentDestinationIsAnEngineNoOpWithoutAFabricatedNotice` stopped before
  dispatch with `IllegalStateException: input context changed after preparation`. This proves the
  old byte-for-byte prepared/native mapping equality was too strict for that live path; the retained
  evidence does not by itself identify which individual native field changed.
- `BrowserInstrumentedTest#realSwipeScrollsLongDocument` reached its single swipe attempt but direct
  `Instrumentation.sendPointerSync` was rejected by Android 12 with the cross-application
  `INJECT_EVENTS` SecurityException. No privilege grant, root/system signing, second automation
  client, or replay is an allowed remedy.

The source/host PASS on `8e3fde9` remains valid historical evidence for that exact head; it is not a
runtime PASS and is not carried forward as verification of a changed head.

## Current correction: recompute final path, then inject on the existing Espresso controller

The current source keeps the round3 DOM sequencing fence and changes only the used input admission
and injection seam.

Preparation still records the intended Activity/WebView/display, document marker/location, exact DOM
target/viewport geometry, and an intended provider path for evidence. Any Espresso controller
capture, blocking evidence write, focus wait, pre-boundary DOM observation, seam regression mutation,
and explicit idle drain occur **before** the decisive sample.

`captureFinalReadiness` then runs one WebView JavaScript evaluation. In its result callback, before
returning to the main looper, it now:

1. decodes the exact current document/target/viewport facts;
2. captures complete current native Activity/view/focus/attachment/display/bounds/IME state;
3. recomputes the intended input path from that **current** native mapping (tap from the exact DOM
   CSS center/viewport; swipe from the pinned Espresso 3.6.1 `swipeUp` providers); and
4. performs `DispatchReadiness` admission and the one input attempt in that same callback.

For a recomputed path, admission no longer requires every non-coordinate native diagnostic string to
be byte-identical to preparation. It instead requires the current state itself to pass the complete
`InputSafety.unsafeReason` guard, the exact prepared Activity/WebView owner and display to remain the
same, and the prepared/current document marker/location and target/viewport geometry to match. Thus
a changed mapping cannot reuse stale screen coordinates: it is either safely recomputed from the
final sample or rejected. Owner/display changes and every unsafe final bounds/focus/IME condition
still stop before `HarnessProtocol.Dispatch` begins.

Tap sends exactly one DOWN and, only after a successful DOWN, one UP through the **already-captured
Espresso `UiController`**. Swipe calls pinned `Swipe.FAST` exactly once on that same captured
controller, preserving `Press.FINGER`, the exact bottom-center translated by `-0.083f` to top-center
providers, ten interpolated move points, and the 150 ms FAST shape. `GeneralSwipeAction`'s retry loop
is not used. No direct `UiAutomation.injectInputEvent`, new UiAutomation client, `INJECT_EVENTS`
grant, root/system signing, or input replay exists in the final tree. A provisional forward commit
that explored the existing UiAutomation connection was superseded before candidate freeze; history
was not rewritten.

The important sequencing distinction is that the controller is captured in an earlier no-op
Espresso action. The decisive WebView callback does **not** enter another `onView(...).perform(...)`
or pre-action idle synchronization. Admission begins the dispatch before the captured controller's
injection machinery can perform any injection-time waiting. The known harness-created pre-dispatch
idle gap identified in round2 therefore stays removed.

## Regression coverage

The two Android used-seam regressions remain on the exact production-style test paths:

- `queuedDomOnlyTargetMoveBeforeFinalTapSampleDispatchesZeroInput` changes only target DOM geometry
  after the pre-boundary sample and requires `Dispatch.stage == "not-attempted"` plus zero click
  effect.
- `queuedDomOnlyDocumentChangeBeforeFinalSwipeSampleDispatchesZeroInput` changes only the fixture
  marker after the pre-boundary sample and requires `Dispatch.stage == "not-attempted"` plus zero
  scroll effect.

The ordinary content-destination tap and `realSwipeScrollsLongDocument` remain the positive used
paths. `DispatchReadinessTest` now also covers final mapping drift with a freshly recomputed safe path
and proves that owner or display changes still produce zero dispatch, alongside the retained
DOM-only negative and positive single-shot cases.

These are source definitions on the changed head. No cloud/local host build, fresh JVM execution,
Android instrumentation, fixture, install, or device result is claimed for that changed head by this
note. The failed focused-three result belongs to `8e3fde9`; a changed candidate requires a fresh host
gate, renewed relevant independent review, and a separately released focused-three invocation.

## Round4 checked-primary handoff correction

The independent review of `bb8b3574eabee5256f0ba7103b45ceb1586a16ee` credited the
recomputed final path and Espresso-controller route, but withheld pre-device clearance for one
error-preservation defect. A tap-side `UiController.injectMotionEvent` rejection is Espresso's
checked `InjectEventSecurityException`. `SingleShotSwipe.inject` already retained that exact
object, but the WebView callback handoff and outer tap evidence handler caught only runtime
exceptions/assertions, so the checked primary could bypass the intended callback-to-test-thread
relay.

The correction uses one shared test-only `DispatchReadiness.CallbackHandoff` on the actual Android
path. It captures `Exception | AssertionError` from the final WebView callback, always releases the
callback latch in `finally`, and rethrows the same captured object on the instrumentation test
thread. The outer tap path now records bounded supplementary failure evidence for checked or
runtime/assertion primaries and rethrows the same primary unchanged. No input retry, privilege,
UiAutomation client, permission grant, or new injection route is added.

The JVM regression
`checkedCallbackPrimaryCrossesHandoffByIdentityWithStageAndCleanup` exercises that same handoff
primitive with a checked DOWN failure. It requires one DOWN attempt and zero UP attempts,
`Dispatch.stage == "down-attempted"`, both dispatch/callback cleanup finally-blocks, exact
same-object propagation to the caller, and retention of a deliberately failing supplementary
evidence capture only as a suppressed exception.

This source correction is not a host/device result. The current shared/JVM source changed, so a fresh
exact-head host/JVM gate and renewed relevant independent review are required before another focused
device release.

## Focused3 #2 device evidence and event-delivery correction

The focused3 run on `f6d9a7a58f2eea4754e83cbaec020f56f3e0b731` completed all three
selected methods in 29.005 s on the authorized API31 S20+ and returned one PASS / two FAIL:

- delayed consumer release/reacquire remained PASS;
- the content-destination tap passed final recomputed readiness, then Espresso
  `UiController.injectMotionEvent` returned `false`; and
- the swipe no longer hit the earlier INJECT_EVENTS SecurityException, but the pinned
  `Swipe.FAST` path timed out waiting for document scroll.

These are new runtime modes, not passes. They retain the earlier evidence that the stale mapping
comparison and direct `sendPointerSync` permission route were no longer the active failures.

Pinned Espresso source matters to the correction. `UiControllerImpl` requires motion injection on
the main thread. `Swipe.FAST` constructs one DOWN + ten linear MOVE events + one UP over 150 ms,
but ignores the boolean returned by `injectMotionEventSequence` and reports SUCCESS unless an
exception escapes. Thus a later scroll timeout does not establish that InputManager accepted the
sequence.

The current source keeps final DOM/native/path sampling and fail-closed admission inside the WebView
value callback, but no longer injects while that callback is still executing. Only after admission
passes, the callback posts exactly one input task with `Handler.postAtFrontOfQueue`; that task runs
on the next main-loop iteration before ordinary queued UI work from this harness. A failed admission
posts no input task. The existing `CallbackHandoff` spans both callback and front task, so checked,
runtime, or assertion primaries still return to the instrumentation thread with the original
`Dispatch.stage`.

Tap now uses Espresso's own `MotionEvents.obtainDownEvent` / `obtainUpEvent` construction and
`Press.FINGER` precision. It performs one application-level DOWN/UP attempt and the same bounded
tap-detection dwell used by Espresso, without MotionEvents' outer retry wrapper. Swipe constructs
the exact pinned FAST sequence itself and calls `injectMotionEventSequence` once; a `false`
result is now an explicit single-attempt failure rather than a fabricated SUCCESS followed by a
scroll timeout. No second automation client, UiAutomation injection, permission grant, new product
path, tolerance change, sleep-based readiness fence, or input replay is introduced.

This is source-only until a fresh exact-head host gate and renewed relevant review. Runtime success
of the new queue-front/event-shape path remains a future device fact.

## Scope and evidence boundary

This correction changes testShared/JVM/androidTest and necessary test documentation only. It does not
change production/core/RG/fixture/dependency code, Option A output qualification, original
eligibility, content tolerance, API levels, diagnostic 2 s budget, resource/timing assertions,
device authorization, or phase sequencing. R2 remains source-cleared and unchanged.
