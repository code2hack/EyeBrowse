# RG pointer, full-screen edge scrolling and Settings

Worker: Worker-#31 · issue #31 · I31-T02 correction attempt3/failure2 · assignment revision1.

The implementation baseline is accepted integration
`7b055cbdd848440346e9f873ac44cf2e688af9d8`, under SPEC D2.2, HUD H1.2 and the
[approved HUD-G1 decision](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6075367078).
The external frozen-candidate manifest identifies the pushed commit, Git tree,
source hashes, app/test APK bytes, host evidence and proposed native procedure.
This document records implementation and the four retained native grants13–16,
followed by a **host-only affected aiming-procedure correction awaiting fresh qualification**.
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
beyond 250 ms has no scroll effect. The existing native writer bounds an upward
tick by the remaining nonnegative View.scrollY, so a final tick cannot cross the
physical top while the renderer clamps a separate scroll offset. Interior and
downward deltas are unchanged. Keyboard position and native editor focus/
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

## Native15 diagnosis and current host correction

The immutable diagnostic candidate `9072b029f0680173508ea5c0f015789cee914d03`
ran only the two previously failing methods under grant15: two run, keyboard PASS,
Settings FAIL, native34.411 s/host record interval37.467518 s, outer0/runner-1.
The reports retain14 keyboard and4 Settings observations, with independent
installed APK/PID/UID/time bindings. All three reached480×640 capture pairs,
raw failures, restoration and resource return remain in `native-rg-grant15`.
No boundary or affected #30 invocation ran. The partial Slow rate was
119.73251934776488 native px/s; later rates/gains were not reached.

At current Standard240's top-endpoint failure the last sample is fresh, READY,
shown and window-focused, pointerY8, canScrollUpfalse, nativeY/checkedY-3. This
identifies the current failed zeroY conjunct. It does not reconstruct c653's
missing failed values. The keyboard method passed its reached checks; its old
elapsed failure was not reproduced. Both current More/New Tab dispatch-return
snapshots were interior, with edgeScrollRunningfalse, before completed click
effects. They cannot establish cancellation of an already running edge scroll.

Host tracing supports a narrow product writer gap. The Activity admits a full
upward tick when canScrollVertically(-1) is true, without bounding it by the
remaining native distance. [Android12 View.scrollBy](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/view/View.java)
stores the unbounded sum. WebView forwards its scroll-change notification, while
[Chromium95 AwScrollOffsetManager](https://chromium.googlesource.com/chromium/src/+/95.0.4638.74/android_webview/java/src/org/chromium/android_webview/AwScrollOffsetManager.java)
clamps the renderer offset separately in that notification path. These primary
sources match the recorded platform/provider version; they are not an
authenticated disassembly of the installed OEM provider or a trace of the exact
failing native tick. The current production correction bounds only the upward
native delta in the existing writer. No test assigns a native/DOM scroll outcome.

`NativePageScrollTest` calls that same production delta function, including with
the actual EdgeScrollMotion at all three presets. The red control extracts the
old unchanged delta into an identity function used by the writer: three tests,
two failures/one pass (final crossing-3 and held endpoint-1). It is a semantically
unchanged old-behavior control, not execution of an unmodified9072 APK. The
corrected function passes all three controls. Exact test source and compiled
class bytes match red/green; every tracked/untracked pre-run source is copied and
hashed. Logs/XML and the provenance record remain in
`r3-native15-host-continuation-02`. These are host controls; fresh real WebView
qualification remains required.

The native procedure now waits for actual page movement at the literal bottom
edge and asserts a fresh running callback/current READY shown page before timed
departure, More, tab switch, close and Refresh. It retains the existing native
pose/touch writer and current selectTab product path. More, close and New Tab
also require their actual utility/count/index effects; New Tab follows More's
cancellation and is checked for staying stopped, without claiming a second
running cancellation. Pause follows an observed running scroll; its stopped
check follows the actual lifecycle transition. The affected #30 tests supply
the unchanged DPAD/OEM native dispatch regression.

A timed trigger runs on main after Activity acquisition. The clock starts before
the actual action; subsequent read-only main samples use runOnMainSync directly,
without ActivityScenario.onActivity's pre-sample idle wait. The original100 ms
poll and elapsed≤100,000,000 ns assertion remain, including dispatch and observer
return cost. Before/first/last state and dispatch-return/elapsed records survive
failed assertions. This improves measurement preconditions without diagnosing
the historical elapsed failure or claiming exact callback/optical latency. The
top predicate still requires pointerY8, canScrollUpfalse and nativeY0 within10 s;
all three are read on the same main-thread observation now. Rates, gains,
freshness, editor/focus, fixed keyboard, geometry, RGB and cleanup criteria stay
strict. No dependency, second writer, queue or generation protocol is added.

## Proposed exact-candidate native qualification — pending grant

No device operation has run for the current correction. All grants13/14/15 are
consumed/released; RG/39030 are Manager-held. The new production and test APKs
both need fresh exact-candidate install authority and independent installed-byte
verification. The external immutable manifest binds the actual new source/APKs
and separately retains every historical packet and result.

The smallest complete affected qualification is one EdgeScrollInstrumentedTest
three-method invocation, ≤900 s, with fresh persisted invocation/source labels.
Any assertion, report/binding or cleanup uncertainty stops later invocation; no
replay or repair is proposed. Only all three passing current reports, all72
boundary rows, strict native effects, and successful scene/raw-source/Activity
cleanup permit one conditional affected #30 three-method invocation, ≤300 s.
The external proposal supplies exact filters and all30 potential artifact names.

The proposal uses one owned unchanged #28 fixture with fixed keyboard/history/
author-light/media/delayed-load routes, independently hashed source/JAR/pages,
fresh interface/freeport and exact PID/UID/start/argv/listener ownership. It is
staged evidence only; no fixture is built or started by host preparation.

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


## Native16 results and affected aiming correction03

Exact `4bc843c3e04e0cff3ea428811071ddea8263277c` first returned3PASS in
122.328s. The independently bound original reports retain72/10/14 actual rows,
all120/240/360 rates, gain ratios/recreation, three native zero top endpoints,
and actual running-edge cancellation effects. Boundary maxima were19.122083ms
from receipt/31.566666ms from input; largest timed running-stop observation was
42.090625ms. These are software observer measurements, not exact callback/optical
timestamps. Six reached original480×640 captures and all scene cleanup passed.

The conditionally authorized original affected #30 invocation returned2PASS/
1FAIL in34.428s. Geometry/address and nativeDPAD admission passed. The four-tab
journey failed `raw quaternion reaches current native target` at oldaim270 via558.
Its exact failed coordinates/state/cause were not emitted. This prevents complete
affected qualification. Twelve current original images across both invocations,
raw failures, three reports and all122 indexed native members remain immutable.
Recorded cleanup20:13:40.580625Z preserved exact three keys/unrelated preferences/
opaque trust, settled only independently qualified own processes/fixture, and
returned RG+39030. This records a past state, not current device access authority.

A host trace of the actual production model and original replay formula at the
recorded480×640/density204/160, with declared neutral reference/settled fresh
stream, demonstrates an obsolete procedure assumption: Standard More target
(456,24) settles at(458.0679,21.897995), then Add target(368,72) settles at
(373.86542,61.72976), outside the original strict eight-pixel criterion. These
host values are not reconstructed historical device observations. The helper
used launch-relative absolute geometry, a density-derived radius and fixed gain;
the current pointer integrates movement and discards boundary/reversal debt.

Only the local-browser test aiming path now generates one relative raw movement
from its current observed cursor, actual glyph motion bounds, adopted/current
quaternion and current gain. It retains the original HeadPoseSource/model/overlay
route, held fresh samples, strict `<8` criterion and10s bound. It does not retry,
recenter, assign a cursor/result/DOM value, modify production gain/filter, or bypass
the subsequent native pad action. The legacy absolute helper and its other callers
are unchanged. No product source, writer, dependency or framework changed.

`RawPoseAimTest` invokes the same test-only generator as the native path and the
real production model. Three controls cover More→Add, corner reversal and interior
target sequences at all three gains. The old formula's semantic extraction at
recorded RG geometry fails3/3; the relative generator passes3/3. Exact test source
and compiled test bytes match red/green. This is a host control, not an unmodified
4bc APK run or proof of the unknown native cause.

Minimal field-free aiming reports record target/before/first/last bounds, actual
pointer, gain, freshness/source registration, layout/window focus, utility/tab
state, elapsed samples and original failure class before returning/throwing.
Each method owns `local-browser-<exact-method-name>-aim-observations.json`; the
same method's array retains both aim calls and flushes with `fd.sync()`. Source/
run/PID labels need independent installed/time binding. These are **aim-only**
reports; original JUnit outcomes and external scene/raw-source/metadata cleanup
remain necessary. File-write failures preserve the original primary exception.
No field text, URL or private payload is recorded. All original `@Test` bodies,
native effects, short/double/tab/editor/history/no-wrap assertions are unchanged.

The smallest proposed fresh native action is one explicit original
`fourLiveTabsAndPadGesturesKeepCurrentNativeEffects` invocation,≤180s total,
with current source/run labels, unchanged installed app verification and one new
test APK install, one owned unchanged fixture, strict original effects and normal
finally. Exactly three potential outputs are its aim report and four-tabs PNG/
sidecar. No additional method/replay/repair is proposed. A fresh Manager grant is
required; this candidate contains no device execution or changed-head native PASS.
Earlier positive tests retain their exact4bc binding and do not automatically
qualify changed test bytes. Independent changed-head review and affected native
qualification remain pending.
