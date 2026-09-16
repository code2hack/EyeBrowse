# I5-T01 R1 final dispatch-readiness correction

Expert: Expert

This source note applies to `work/issue-5-expert` after the incomplete baseline
`28e11a09f48d16fc5446b6d8a09cff157bdbb092`. It supersedes only the historical
"Open scoped review finding" paragraph in `docs/testing/phone-browser.md` for the source ordering
of this correction. It is not host/device clearance, an I5-T01 handback, or ticket acceptance.

## Final tap/swipe admission ordering

The correction keeps all preparation and evidence before the final admission sample. After the
last known focus/evidence/Espresso pre-action boundary, the used tap/swipe paths obtain one final
read-only DOM sample from the intended WebView, immediately revalidate the current native
Activity/view mapping, and then apply the shared test-only `DispatchReadiness` decision. That
decision requires both:

- unchanged Activity/WebView ownership, focus, attachment, display, visible geometry, scroll and
  IME/inset mapping through the existing `InputSafety.State.revalidationReason` path; and
- unchanged exact fixture document marker/location plus target/viewport geometry through
  `DispatchReadiness.DomState`.

Only after that combined decision does `HarnessProtocol.Dispatch` enter its single input attempt.
No blocking milestone/file/HTTP write, focus wait, DOM observation, Espresso `onView` lookup or
input retry is inserted after the combined decision. Tap still dispatches one DOWN/UP sequence.
Swipe still uses Espresso 3.6.1's `Swipe.FAST`, `Press.FINGER` precision and the existing
bottom-center translated by `-0.083f` to top-center providers, but not `GeneralSwipeAction`'s retry
loop.

The JVM regressions in `DispatchReadinessTest` call the same shared admission+dispatch methods used
by instrumentation. With native state held equal, one regression changes only the document marker
and another changes only target geometry; both require `Dispatch.stage == "not-attempted"` and zero
input calls. A stable case verifies one single-shot tap remains admitted. These are source/JVM test
definitions; their actual execution belongs to the released local host gate.

## R2 preserved

The `28e11a` R2 path remains: diagnostic JavaScript completion is admitted through
`Diagnostic.completeIfOwned`, which rechecks the weak intended Activity/WebView at callback time in
addition to the test/deadline/duplicate fences. The existing same-test owner-replacement and
unchanged-owner regressions remain unchanged. Already-dispatched platform JavaScript is still not
claimed to be recalled. R2 remains subject to renewed independent source review on the final head.

## Evidence boundary

This correction changes test/androidTest/testShared source and test documentation only. It does not
change production/core/RG/fixture/dependency code, Option A output qualification, timing/tolerance
requirements, API levels, device authorization or phase sequencing. The exact pushed candidate must
receive the current local host gate and renewed independent source review before any focused/device
phase can be released.
