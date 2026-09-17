# I5-T01 R1 dispatch-readiness correction

Expert: Expert

This note records the source correction history on `work/issue-5-expert`. It is not host/device
clearance, an I5-T01 handback, or ticket acceptance.

## Round2 review result retained

The independent review of `cc1eb56f03a486048a483f87467b79ec4c772bfe` was
`CHANGES_REQUESTED`. Its shared `DispatchReadiness` classifier correctly rejected supplied unequal
DOM/native snapshots, but the used Android sequencing still had a callback-to-input gap:

- swipe sampled DOM through an Espresso `UiController.loopMainThreadForAtLeast()` path whose pinned
  3.6.1 implementation subsequently drains the main loop until idle; and
- tap sampled DOM before a later `ActivityScenario.onActivity` native-observation turn.

A DOM-only document/target change in either interval could therefore leave native facts equal while
stale DOM A still authorized input. The round2 JVM classifier regressions did not execute those
Android sequencing seams. The earlier wording that described `cc1eb56` as having a final coherent
sample is superseded by that review, not preserved as acceptance evidence.

R2 was source-cleared in that same review and remains unchanged in round3: diagnostic JavaScript
completion still uses `Diagnostic.completeIfOwned` with the weak actual Activity/WebView owner,
test/deadline and duplicate fences. Already-dispatched platform JavaScript is still not claimed to
be recalled.

## Round3 used-path ordering

Round3 keeps all blocking evidence and preparation before the decisive sample. Both used paths:

1. retain their prepared document/target/native facts and the existing geometry/IME guards;
2. perform a read-only pre-boundary DOM check;
3. finish any known focus/main-idle boundary before the decisive sample; and
4. call `captureFinalReadiness`, where the intended WebView's JavaScript result callback captures
   the returned DOM value and the complete native Activity/view state in that same callback.

After that callback, the test thread performs no `ActivityScenario.onActivity`, Espresso
`UiController` main-loop pump, blocking milestone/file/HTTP write, focus wait, or other deliberate
main-idle operation before the shared `DispatchReadiness` decision and the one input attempt.
`DispatchReadiness` still requires unchanged Activity/WebView identity, focus, attachment, display,
visible geometry, scroll and IME/inset mapping plus unchanged document marker/location and
DOM target/viewport geometry.

The tap remains one instrumentation `DOWN`/`UP` attempt. The swipe no longer calls Espresso's
`UiController` after the decisive sample because that was the reviewed sequencing gap. It still
uses Espresso 3.6.1's exact `swipeUp` coordinate providers (bottom-center translated by `-0.083f`
of view height to top-center), `Press.FINGER` precision, ten interpolated move points and the
150 ms FAST timing shape. `SingleShotSwipe` sends that one sequence through the same target-scoped
instrumentation pointer API already used by the tap; there is no GeneralSwipeAction retry loop,
second automation client, UiAutomation connection, privilege, or input replay.

This is a test-harness sequencing correction, not a claim that arbitrary page JavaScript can never
change spontaneously after observation. It removes the known harness-created callback-to-idle /
callback-to-ActivityScenario intervals identified by the independent review and revalidates after
them immediately before the single attempt.

## Used-seam regressions

`BrowserInstrumentedTest` now contains two Android instrumentation regressions that run the same
used tap/swipe paths:

- `queuedDomOnlyTargetMoveBeforeFinalTapSampleDispatchesZeroInput` changes only target DOM geometry
  after the pre-boundary sample while keeping the native Activity/WebView mapping intact, then
  requires `Dispatch.stage == "not-attempted"` and an unchanged click count.
- `queuedDomOnlyDocumentChangeBeforeFinalSwipeSampleDispatchesZeroInput` changes only the fixture
  document marker after the pre-boundary sample, then requires `Dispatch.stage == "not-attempted"`
  and `window.scrollY == 0`.

The ordinary click and `realSwipeScrollsLongDocument` cases remain the positive used paths. The
existing JVM `DispatchReadinessTest` classifier cases are retained as lower-level guard coverage;
they are not presented as substitutes for the new Android seam regressions.

These new Android regressions are source definitions only at this source-authoring stage and have
**not been executed in cloud**. Their compile/build inventory belongs to the released local host
gate; actual instrumentation execution remains separately device-gated by the Manager.

## Scope and evidence boundary

Round3 changes test/androidTest and necessary test documentation only. It does not change
production/core/RG/fixture/dependency code, Option A output qualification, original eligibility,
content tolerance, API levels, diagnostic 2 s budget, resource/timing assertions, device
authorization or phase sequencing. The exact pushed candidate must receive fresh local host
verification and renewed independent exact-head source review before any focused/device phase can
be released.
