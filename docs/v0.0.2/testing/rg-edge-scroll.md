# RG pointer, full-screen edge scrolling and Settings

Worker: Worker-#31 · issue #31 · I31-T02 correction attempt2/failure1 · assignment revision1.

The implementation baseline is accepted integration
`7b055cbdd848440346e9f873ac44cf2e688af9d8`, under SPEC D2.2, HUD H1.2 and the
[approved HUD-G1 decision](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6075367078).
The external frozen-candidate manifest identifies the pushed commit, Git tree,
source hashes, app/test APK bytes, host evidence and proposed native procedure.
This document records host implementation, two retained native runs, and a
**host-only diagnostic candidate awaiting a fresh native grant**.
It does not complete I31-T02/T03, independent acceptance, HUD-G2 or a release.

## Production behavior and consumers

`HeadPointerModel` integrates relative angular changes into bounded targets.
Outward angular excess is discarded at saturation. A direction reversal drops
filtered travel opposing the new motion, including a first 0.05-degree inward
sample. Interior noise suppression accumulates deliberate small motion rather
than discarding each small sample. Existing elapsed-time smoothing and speed
limits remain. Stop/lost acquisition leaves a visible unavailable cursor at its
last position; the first valid acquisition establishes an automatic reference.
The local UI has no recenter action. Historical RG consumers retain their existing
recenter API; removing that legacy surface belongs to #33.

The H1.2 cursor footprint remains 16 rendered pixels, with its center confined to
`[8,472] × [8,632]` on the 480×640 screen. The keyboard changes the page viewport,
never these full-screen bounds. Exact top/bottom glyph contact supplies upward/
downward scrolling; x-only saturation, near-edge positions, toolbar bottom and
keyboard divider do not trigger it.

An ordinary repeating callback scrolls the currently shown READY WebView through
`NativeRgInputTarget`, or the currently visible utility ScrollView. More/scanner
covering states never scroll the page. There is no retained original page,
generation, stale-tick queue or edge rearm protocol. Departure, stale tracking,
tab change/close, navigation, covering UI, lost focus and pause remove/reset the
callback. A later fresh pose evaluates the current visible surface normally.

`EdgeScrollMotion` integrates at most 50 ms per callback, at the selected constant
native viewport rate. Integer nanosecond arithmetic retains less than one pixel
of rounding fraction. Stop/direction change/endpoints discard that fraction and
unused distance; a stall cannot create catch-up travel. A last sample age at or
beyond 250 ms has no scroll effect. Keyboard position and native editor focus/
text are untouched by the callback.

More's fourth row opens working black/light Settings below the unchanged toolbar.
Pointer sensitivity Low/Standard/High is 0.75×/1.0×/1.25×; speed Slow/Standard/Fast
is 120/240/360 native viewport px/s. Both default Standard. App-local preferences
persist the selections before displaying/applying success. Failed persistence
keeps the previous selection and shows a recoverable error. Changes affect future
motion, clear filtered travel, and do not move/recenter the pointer. Done/Android
Back exits; there is no light-theme switch.

All product changes are within `app-rg`. Pointer/model consumers are RG-local,
legacy RG and debug preparation; no Phone/shared-core source consumes the changed
classes. The recognizer, native DPAD/OEM admission, tab model, session navigation,
bookmark implementation, main launcher and shared browser policy are unchanged.
Phone evidence is unavailable/unreserved; no Phone PASS or simultaneous-use claim
is made. #32 full keyboard and #33 QR/launcher/final integration remain separate.

## Host evidence

The mission-private evidence root is
`/home/code2hack/.local/state/eyebrowse/v0.0.2/runs/run-001/issue-31/evidence`.
All Gradle invocations serialize with the run's host-build flock, capture memory
before/at lock, use JDK17 and the installed SDK, and apply `--no-daemon
--max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`.

The original `red-pointer` log/XML records three production-model controls failing
against baseline code: boundary slow reversal, opposing filtered travel and
unavailable-cursor freeze. `green-pointer` records the same three control methods
plus 12 existing pointer tests passing. Their original tracked patches, logs and
XML are preserved unchanged. The original red patch is empty because the new
test was untracked. Its source is **reconstructed after the run from the exact
heredoc in this native session's persisted tool call**, not a contemporaneous
filesystem hash. `reconstructed-source.json` discloses that provenance and hashes;
the green model reconstructs from baseline plus its original tracked diff. The
recorded execution sequence contains no intervening test-source edit before
green; case-name equality alone is not the basis for the source binding.

Subsequent runs copy/hash every changed and untracked source before execution.
The original `frozen-source-host` package records these explicit affected gates:

```text
:app-rg:testDebugUnitTest
:app-rg:assembleDebug
:app-rg:assembleDebugAndroidTest
:app-rg:lintDebug
```

The host suite includes all existing RG tests and controls for every edge/corner,
three overshoot amounts and three hold durations, equal first slow inward return,
filter reversal, all gains, persistence failure/recreation, literal edges,
held-edge rates, exact freshness cutoff, cancellation and no catch-up. The new
Android tests compiled; compilation is not device execution. Lint's XML reports
22 warnings and no reported errors; Kotlin metadata-reader diagnostics limit
its analysis. The external task ledger distinguishes actual execution,
UP-TO-DATE, NO-SOURCE and SKIPPED results. It makes no clean-build/cache-free claim.

Simplification retained one motion integrator and the existing native writer,
used ordinary View/lifecycle cancellation, and added no dependency, callback
identity protocol, action queue, second recognizer or target snapshot. Affected
host checks follow that final source pass. Documentation/manifest packaging does
not change compiled source.

## I31-R1 procedure correction and retained negative evidence

The original candidate `b9ce1c80b8d97a6dc647afe279a9f1d6c3439b14` ran once on
the exclusively granted RG under grant13. Its actual terminal was three methods,
one pass and two failures, native duration 92.526 s, outer exit0/runner code-1.
The boundary method passed; both page methods failed in native URL entry because
the helper did not select the symbol/letter keyboard layer. Neither reached page
navigation, so those failures do not diagnose product scrolling. Independent
review I31-R1 and the actual exception agree. The affected #30 invocation was
not started. Grant13 cleanup and RG/39030 return were verified and accepted.

Three original full480×640 capture pairs (top, corner, tracking unavailable),
raw streams, source/APKs and all negative evidence remain unchanged in
`native-rg-grant13`. The common observation file was overwritten by a later
method during host copying; the hash/size check rejected that copy, and its final
original is `[]`. No 72-entry timing table survived. The boundary method's status
and three captures remain evidence, without reconstructed measurements.

The correction changes only the new Android test and this document. Before each
character it observes the rendered key map and, when necessary, activates the
actual SYMBOLS/ABC control. Each tap resolves the current shown, enabled, laid-out
target; no address text or DOM value/selection/scroll outcome is assigned. Existing
native page, rate, gain, timing, RGB, dispatch and cleanup assertions remain.

Each method now writes its own `edge-scroll-<exact-method-name>-observations.json`
after all cleanup attempts, and flushes the file with `fd.sync()`. It retains the
actual observations, partial failure data, method/class, PID/UID, monotonic start/
finish, body completion, cleanup results and failure class. The required
`evidenceRunId` and `candidateHead` instrumentation arguments are external binding
labels; the host must independently verify source/APKs/installed bytes and match
them with these labels and timestamps. They do not prove their own authenticity.
Methods cannot overwrite each other's evidence. The old common file is untouched.

The correction's `r1-procedure-correction-02` packet records only affected
`:app-rg:assembleDebugAndroidTest` and `:app-rg:lintDebug` host gates. The original
119-case JVM suite and product build evidence are historical unchanged-source
evidence, not another execution. The product APK's actual byte reuse and the new
test APK's hashes/task states/analysis limits are recorded in that packet.

## I31-R3 native failures and diagnostic candidate

Candidate `c653682aff84efe5ff5fe736ed980808736edc79` ran once under grant14:
three methods, one pass and two failures, native113.227 s, host116.174130 s,
outer exit0/runner code-1. Its three durable method files contain72/2/5 actual
observations. All72 boundary measurements survived; maximum first inward draw
was19.005417 ms after sample receipt and34.995729 ms after input. One Slow-rate
row measured117.316006 px/s against120. These are bounded observations from
that candidate, not complete qualification of the later diagnostic candidate.
Five original full480×640 capture pairs and all logs/reports remain immutable in
`native-rg-grant14`. Cleanup/metadata/trust and RG/39030 return were verified;
the affected #30 invocation did not start.

The keyboard journey failed the stop helper's elapsed-observation assertion
following More→New Tab. The preceding poll observed the callback stopped, but
its exact failing duration was lost because the observation append followed the
assertion. The Settings journey failed a composite top-endpoint wait: pointerY
must equal8, the actual page must not scroll upward, and native scrollY must
be0. The old report does not identify the failed conjunct or its cause. Required
240/360 rates, gain/persistence and later keyboard/tab/navigation/endpoint checks
remain unqualified. The original terminal and negative evidence are retained.

Host tracing finds synchronous ordinary cancellation in tab/cover paths. The
installed androidx.test:core1.6.1 bytecode also shows that off-main
`ActivityScenario.onActivity()` waits for main-thread idle before dispatching its
observer. Neither fact supplies the missing native timing/state or establishes
the cause of either old failure. No product correction is inferred.

The diagnostic change adds only test observations and this guide. It records the
actual More/New Tab native dispatch/return times and current callback state;
stop-observer call, main sample and return times, including failed measurements;
and first/last actual top-endpoint state with preset, freshness, focus, page and
lifecycle context. It captures no field text, URL or DOM contents. Partial records
are appended before the unchanged failing assertions and retained in the same
three durable method files after ordinary cleanup. The original100 ms stop,
10 s endpoint wait, rates/gains, native page/editor effects and all other oracles
remain unchanged. Observation adds execution cost; it is not an independent
cancellation timestamp or a product probe. Compilation cannot diagnose the
native failures or establish a pass.

## Proposed exact-candidate native diagnosis and qualification — pending grant

No device operation has run for this diagnostic candidate. RG/port39030 are
Manager-held; grants13/14 and all old #30 grants are consumed. The frozen
source/test APK needs a fresh exclusive exact-candidate grant.

The smallest proposed diagnostic invocation selects only the two previously
failing methods, once, with a300 s outer bound and new persisted invocation/source
labels. It retains their complete original journeys and strict assertions. Stop
on any failure, missing/current-binding uncertainty or cleanup problem, preserve
all reached originals and return resources. The unchanged boundary method still
requires renewed exact-head qualification if requested for final acceptance.
Full qualification additionally requires that boundary method and the affected
#30 methods below; none is automatically replayed or started by this proposal.
The external manifest gives exact filters and separates diagnostic execution
from any later explicitly granted qualification.

The external proposal binds the frozen app/test APKs and one owned unchanged
#28 fixture listener on a newly reserved/verified RG. The app APK is unchanged;
verify its installed bytes and stop on mismatch rather than reinstall it. Only
the new test APK needs one explicitly granted install. Proposed fixed fixture routes are keyboard,
history, author-light and media, with the existing delayed load route; fixture
source/pages are unchanged. Its imported accepted artifact is separately hashed
and staged, not rebuilt or started here.

The three new `EdgeScrollInstrumentedTest` methods are:

| Method | Required actual observations |
| --- | --- |
| `allBoundariesDiscardOvershootWithPromptSlowInwardDraw` | 8 edges/corners × 3 overshoot amounts × 3 holds; automatic acquisition; a 0.05° inward step; actual draw position/receipt/elapsed clock; ≤100 ms; honest unavailable cursor. |
| `settingsPersistAndActualPageUsesEveryDeclaredRateAndGain` | More→Settings native controls, selected rows/reopen/recreation, real page motion at 120/240/360, declared ±12% +4 px/s observation tolerance, native/DOM scroll agreement, real 0.75/1/1.25 gain ratios. |
| `fullScreenKeyboardEdgesAndOrdinaryCancellationPreserveNativeEditing` | Real field activation/keys, fixed `(0,440,480,200)` keyboard and full-screen edge, no divider trigger, current focus/`ab`/zero submit, departure≤100 ms, freshness, More/Settings isolation, pause/resume, tab/close/navigation, real endpoints/no debt and lateral-only no-scroll. |

A separately granted, conditional affected invocation rechecks the existing production methods
`approvedGeometryAndAddressEditingStayBlackAndRecoverable`,
`fourLiveTabsAndPadGesturesKeepCurrentNativeEffects`, and
`nativeDpadAdmissionPreservesScopeAndTabEffects`: exact menu/toolbar geometry,
address/history/navigation, one short/zero double, OEM and assigned native DPAD
metadata, no-wrap, tab state/focus and native caret/text. Replay with an observed
pad device identity does not establish a new physical wearer-facing result.

The suite uses generated raw quaternions through the original HeadPoseSource →
production model/overlay path. Positive actions use native touch/key dispatch and
actual built-in controls; JS only reads owned fixture values, selection, counters,
identity and geometry. No DOM value/selection/scroll outcome is assigned. Native
scrollY is checked against the independent actual DOM geometry after departure;
rate and draw clocks remain explicit. Draw/frame/PixelCopy timing is software
window evidence, not compositor/optical latency or ergonomics.

Predeclared new captures are `edge-scroll-{pointer-top,pointer-corner,
tracking-unavailable,settings,keyboard-bottom,scrolling-top}.{png,json}`, plus
three `edge-scroll-<exact-method-name>-observations.json` files for the methods
listed above. Existing scoped methods can reach seven named
`local-browser-*.png`/sidecars and `local-browser-native-editor-observation.json`.
The proposal enumerates all 30 exact potential names. Harvest only the selected methods' distinct
current files after the terminal; do not rely on copying a common file between
methods. Verify each method identity, invocation/source labels, PID/UID, current
monotonic interval, cleanup outcome and actual records, including all72 boundary
measurements only when that method actually ran successfully under the new grant. Only files actually produced in the granted
run count; stale/hypothetical files never supply evidence. PNGs are full native
480×640 window copies following public frame-commit/visual-state waits, with
unscaled origin/bounds asserted. Settings background regions are predeclared.

Before mutation, Manager's fresh grant must bind physical serial/model, access/
route/display/provider, source/APKs, canonical opaque trust digest and safe entry/
cleanup state. No trust bytes, credentials, unrelated UI or broad logcat enter
artifacts. The tests snapshot/restore only `local.tabs` and the two setting keys
inside the exclusively reserved app; they never clear app/site/bookmark data.
A previously absent Settings preferences file may remain with its owned keys
removed. Restoration/close failures remain failures. Captures are collected then
only exact owned files are removed after ownership verification. External cleanup
settles owned instrumentation/app/sensors/fixture and verifies the Manager's
required RG state without borrowing a Phone screen policy or issuing compensating
input. Unexpected data/trust/identity/access/ownership stops affected operations.

Replay and native window evidence do not establish physical head movement,
optics or comfort. No extra universal physical gate or Owner observation is
inferred. Manager owns the fresh resource grant, independent review, PR and
acceptance.
