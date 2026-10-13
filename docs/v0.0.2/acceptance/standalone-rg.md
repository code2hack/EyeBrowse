# Standalone RG integration evidence — issue #33

Worker: Worker-#33 · batch005 · I33-T03 correction02 attempt2/failure1/lastConcluded1.
I33-T01/T02/T04 retain attempt1/failure0/lastConcluded0.

The RG launcher now opens the local browser and More contains four working
utilities. QR uses the real RG camera, previews a validated address, and navigates
the selected tab only after Open. This is a Worker candidate record.
Phone checks are removed from this mission and ongoing RG development by the
[Owner RG-only direction](https://github.com/code2hack/EyeBrowse/issues/33#issuecomment-6098004889).
Historical Phone results retain their original outcomes; removed checks are not
marked PASS. Renewed exact-candidate independent HUD-G2 review remains required.

## Authority and source

- [Canonical #33](https://github.com/code2hack/EyeBrowse/issues/33), assigned baseline
  `20233136fe5c6d0af954bb00cf203b3d7d33edfb`, branch `work/v0.0.2-issue-33`.
- SPEC D2.2 blob `1acb8ad6d7a0d77d660a5137010efe17ad39ff0a` and HUD H1.2 blob
  `070ef8b84d321974af2f35fc68dc1ed2febb91cc` are unchanged.
- [HUD-G1 approval](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6075367078)
  and [accepted native temporary password echo](https://github.com/code2hack/EyeBrowse/issues/29#issuecomment-6072154841)
  remain effective. No new design/echo/approach gate applies.
- [Owner unattended QR direction](https://github.com/code2hack/EyeBrowse/issues/33#issuecomment-6097193856)
  permits real camera checks plus controlled inputs through the production
  decoder/UI. Human target presentation/alignment is not a prerequisite. No
  optical QR capture or wearer observation is claimed by the software tests.
- #30/#31/#32 were accepted and merged before this assignment (PR36/37/38).
  #30's [accepted physical touchpad observations](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6085820799)
  confirm both directions, one tab per swipe and safe boundaries. They retain
  their original source; this candidate reruns the unchanged native admission
  and tab effects using replay, without relabeling replay as a new human swipe.

Current product and executable/test checkpoint:
`e1c675695a9a200e1dca526502b48298e4882d78`. Correction02 changes only the
RG page-session callback handling and focused native tests. The final ledger/image
commit adds no compiled source; its exact pushed SHA is supplied in CANDIDATE_READY.

The original reviewed candidate is `fbf8e0f56c0f3f052925e38bbc9b840ee1802168`
(tree `6a1035e64698e363b8bffe59dbc812f419f805af`), product9570384 and
executable/test14a878dc. Earlier logs/images retain those original bindings.
NativeInput at08986992 and its unchanged fixture remain historical evidence.

## Runtime and build binding

| Item | Observed value |
| --- | --- |
| Host/toolchain | spark, OpenJDK17.0.19 arm64, Android SDK35, Gradle8.11.1 |
| Build constraints | Existing host-build.lock; `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g` |
| RG | Personal RG-glasses, physical serial1906092617103125, configured `100.87.122.122:5555` |
| OS/provider | Android12/API32, SKQ1.240613.001; com.android.webview95.0.4638.74 |
| Display | Native480×640 portrait, rotation0; density240/override204; actual root480×640, toolbar480×48 |
| Identity | com.code2hack.eyebrowse.rg; existing signing/versionCode1/versionName0.0.1 retained; install-r only |
| Current product APK | 10,330,536 bytes; SHA256 `3c908bced99e7c0bad14d22af4f9e8b114800a9fa03346573859ba444ec7ff27` |
| Current test APK | 1,745,738 bytes; SHA256 `5092f8bf5fc47fc05cbd843a23066ba109688fd1b164613b12b5feaf10131574` |
| Own fixture | `http://100.92.81.33:39030`, existing rg-local-probe JVM fixture, dummy pages only |
| Phone | Removed from scope by Owner; historical results retained, no new host/device checks |

Ordinary local evidence root on spark:
`/home/code2hack/.local/state/eyebrowse/v0.0.2/runs/run-001/issue-33/evidence`.
`build-binding-01…05.json` bind the original round. Correction02 uses the
`correction02-rg-state-evidence` subdirectory, `build-binding03.json`, normal
install-r receipts, JUnit logs, original PNGs and current-state observations.
The original APKs (product `eb5620c0`, test `f55a1f17`) are retained unchanged. Camera room imagery remains in this
private evidence directory; public images below contain dummy app/fixture UI.

## What changed and what was retained

The main manifest has one production Activity: portrait LocalBrowserActivity
with MAIN/LAUNCHER. MainActivity, RG pairing Activity, preparation Activity,
RG link/pairing stores, streamed presentation/remote commands, old Reading/
recenter/swipe-scroll/remote keyboard plumbing and their exclusive tests were
removed. RG drops its core:link dependency. Old stored pairing/remote preferences
are neither read nor migrated into active state. Their data is not cleared.
The existing debug NativeInputTestActivity remains a test fixture, not a launcher.

`retained-consumer-trace.txt` records that Phone/core/renderer-editor import no
removed RG classes. A retained core:link KDoc mention of RgLinkClient is historical,
not a dependency. Those modules have no source changes; Phone's own MainActivity,
shared protocol/renderer consumers and Phone data paths remain intact. No installed
Phone behavior is inferred from this source trace or from host tests.

CameraQrScanner reuses installed CameraX and ZXing dependencies, one analyzer,
KEEP_ONLY_LATEST and `ImageProxy.close()` in finally. It unbinds only its own two
use cases on decode, Cancel, utility exit, pause/error or destruction. PreviewView
uses TextureView so the existing single cursor stays above camera media. Permission
is requested only by scanner entry/Retry. Denial/unavailability gives a recoverable
black panel. No clipboard, pairing, auto-navigation or new action-identity framework
was added. Open uses the same strict AddressPolicy as normal entry.

Local WebView DOM storage is enabled; cookies flush on normal stop. Live tabs keep
their WebViews/JS/form state across normal hiding and pause. Cold recovery restores
locations as EMPTY and waits for explicit Open; it does not promise JS/form memory
survives process death or automatically replay a submission.

Simplification removed the obsolete RG implementation instead of preserving two
active runtimes. The accepted local keyboard layout, pointer math, direct input,
edge-scroll logic, tabs, Bookmarks, Settings and Phone/shared source remain.
The retained keyboard single-character validator preserves the old printable
boundary without depending on the remote protocol. No new dependency or general
injection/test framework was introduced.

## Executed checks

Correction02 current RG results appear below. The following original-round
results, including Phone host checks performed before the scope change, remain
historical. No Phone check was run for correction02.

| Evidence | Source / result | Scope |
| --- | --- | --- |
| host-build-05.log; host-test-summary.json | Product9570384; RG63 and Phone194 JVM tests; browser14 executed in build02 on unchanged shared source; zero failures/errors/skips | RG unit tests, real decoder malformed/unsupported payloads, Phone/shared host regression |
| host-build-05.log | SUCCESS,138 tasks:75 executed/63 up-to-date | RG/Phone debug builds, RG test APK, RG and Phone lint |
| host-build-10.log | Test14a878dc; SUCCESS,55 tasks:7 executed/48 up-to-date | Final instrumentation compiled/packaged; product tasks and unchanged lint analysis cached |
| device-standalone04.log | 14a878dc;8 passed,37.141s | Default launcher/four menu items; real camera/preview/release; bind error; controlled QR; recreation; screen off/resume; transitions |
| device-permission03.log | 14a878dc;1 passed,12.025s | Real OEM Deny → Retry → Allow → actual frame → Cancel |
| device-coldprepare02.log + device-cold-install-02.log + device-coldrecovery02.log | 14a878dc;2 passed,2.989s/3.027s; same product APK | Separate process, force-stop/install-r, bookmark/settings/cookie/localStorage preserved; EMPTY/no automatic load; explicit Open/zero submits |
| device-network-02.log | 14a878dc;1 passed,5.288s | Stop/restart only own listener; real load error; other live tab/JS survives; local utilities work; explicit Refresh recovers |
| device-localbrowser04.log | 14a878dc;8 passed,66.884s | All existing navigation/keyboard/tabs/bookmarks/darkness requirements, strict unchanged oracles |
| device-edge05.log | 14a878dc;3 passed,120.745s | All three existing overshoot/rate/gain/keyboard/lifecycle/Refresh tests |
| device-native-input-03.log | 08986992;2 passed,18.419s; these classes unchanged at14a878dc | Native one-tap/double suppression, direct keys/current focus/Done/scroll and native password captures |

Original-round RG lint reports 0 errors/6 warnings; historical Phone lint
0 errors/10 warnings. Current RG lint reports 0 errors/7 warnings. The installed
lint Kotlin frontend emits2.2.0-versus2.0.0 metadata diagnostics while Gradle tasks
succeed. This limits Kotlin semantic lint coverage; successful task status is not
claimed to repair that toolchain mismatch. No toolchain/provider upgrade was made.
Unchanged tasks reported UP-TO-DATE were not rerun as fresh analyses.

Useful failures are retained. Initial clean QR patterns, including valid
`javascript:alert(1)` QR contents, sometimes made ZXing's detector throw
FormatException. Production decoding now falls back to ZXing PURE_BARCODE after
normal detection; the value is still rejected by AddressPolicy. JVM and device
checks retain unsupported-value assertions. The first permission test used stock
Android button IDs; observing this RG's OEM dialog identified `btn_allow`/
`btn_deny`. Correcting those test selectors made the real Deny/Allow test pass.
The combined final regression invocation lost its result connection after eight
LocalBrowser passes. Its own process log records a strict Refresh-identity timeout,
two subsequent NativeInput screenshot failures caused by a dead UiAutomation Binder,
and a teardown crash. Original log/partial edge JSON/crash trace are retained as
integration-02 evidence. The direct unchanged edge-method recheck passed21.322s;
the original failed Refresh remains recorded rather than relabeled PASS. A second host stream disconnected during actualAddressKeysKeepDraftAndFixedToolbarUntilOpen;
its preserved process log and failure PNG/JSON record a LOADING/readiness timeout
and seven other method completions. It is not counted as a full-suite PASS. The
affected assertion and10s bound are unchanged. Subsequent class-scoped runs record
JUnit output into a unique temporary device file under a240s command bound, then
retain the ordinary log on the host and delete that specific temporary file. No
ADB/network/provider restart or configuration repair is involved. The transient load
timeouts' cause is not established; the lost Binder/result channel is established.
A later read briefly returned device-offline; a bounded connect to the existing
configured listener reported already connected, and serial/model matched again.
No server, provider or network configuration was restarted or changed.
The first cold-recovery capture was taken before a focused, drawn pointer could
be established. It is retained as an early startup observation, not cursor proof.
The shared standalone capture helper now waits for actual window focus and a
bounded pointer draw before its frame-commit capture. Affected screenshots and
cold recovery are rerun; production pointer behavior is unchanged.
No product rejection, field, pixel, deadline or cleanup assertion was relaxed.

## Correction02: current error and recovery evidence

[Review1](https://github.com/code2hack/EyeBrowse/pull/39#pullrequestreview-5479026632)
concluded I33-T03 attempt1 once for R1/R2. Manager assigned correction02 as
attempt2/failure1/lastConcluded1; the other todos retain their original history.
The original denial and screen-resume PNGs both contain the heading, message,
Try again and Cancel. Manager independently confirmed their immutable pixels;
the contrary visual subclaims are review mistakes, not extra Worker failures.
Those PNGs are unchanged. New QR assertions require fully visible, enabled,
laid-out native labels plus actual light text pixels inside each copied control.
The original/current QR pixels and native observations agree; no QR product
layout fix was required.

R2's original `local-browser-http-error.png` is a LOADING frame, not rendered
HTTP404 proof. Its original PNG/sidecar remain in `reviewed-originals` and the
original integration evidence. Current tests first reproduced the error overwrite
on the unchanged product: HTTP01/02 retain two failed strict HTTP404 awaits.
The HTTP02 callback trace identifies `onReceivedHttpError → ERROR`, then
`onPageStarted → LOADING`, then visual completion `→ READY`, before the hidden
input check starts. This establishes the current reproduction; it does not
establish the exact callback sequence behind the old LOADING capture or
retroactively diagnose other historical navigation timeouts or Binder failures.

The small ordinary page-session correction keeps ERROR until deliberate navigation.
Back/Forward explicitly hide the page and start LOADING, as Open/Refresh already
do. Document completion checks the existing callback URL against the WebView URL
before showing it. There is no new request identity, generation or input framework.
The new current-state oracles retain hidden-page input rejection and verify
ERROR/HTTP404 before and after PixelCopy, visible native error text, black backing,
and explicit native Back/Forward/Refresh/address/Settings recovery with zero submits.

| Current evidence in correction02-rg-state-evidence | Actual source/result |
| --- | --- |
| host-build03.log; host-check-summary.json | e1c6756; SUCCESS39s,78 tasks19 executed/59 up-to-date; fresh RG63 JVM pass, no failures/errors/skips; RG lint0 reported errors/7 warnings, metadata semantic limit retained |
| device-correction02-http03.log; http03-originals | e1c6756;2 passed19.337s; current 404 and history/Refresh/address recovery |
| device-correction02-browser04.log; browser04-originals | e1c6756;9 passed73.725s; all original strict browser/keyboard/tab/bookmark/palette checks plus error recovery |
| device-correction02-edge01.log; edge01-originals | e1c6756;1 passed21.112s; existing full-screen keyboard/edge cancellation and Refresh-identity recovery, unchanged strict deadline |
| device-correction02-qr02.log; qr02-originals | e1c6756;4 passed25.626s; actual OEM Deny/Retry/Allow/frame/Cancel, screen-resume deliberate camera reacquisition,13 controlled decoder/preview/Open/Cancel cases, launcher/four utilities |
| device-correction02-network01.log; network01-originals | e1c6756;1 passed3.983s; actual own-fixture interruption, other live tab/JS and utilities preserved, explicit Refresh/zero submissions |

These are17 successful method executions covering15 distinct methods at e1c6756;
the focused HTTP pair is also included in the browser class. The earlier QR01
pair passed17.628s at test4026a67 on the unchanged original product; it is not
relabeled as execution on e1c6756. HTTP01/02 remain failed diagnostic runs.

The correction simplification pass retained one existing page phase and ordinary
URL comparison, without a second navigation state store or generalized callback
framework. Current label/pixel assertions extend the existing capture helpers;
callback tracing is test-only. Phone/shared sources and protected articles are
unchanged. Only this ledger and the HTTP404/recovery images follow the tested
executable checkpoint, so no additional build is needed for the final commit.

## QR and visual evidence boundaries

The real-camera test receives RG analyzer frames, observes PreviewView STREAMING
and actual CameraManager unavailable→available transitions. Cancel, Activity
pause, utility exit and repeated acquisition release the owned camera within2s.
Screen-off/resume separately stops the camera/pointer listener, preserves live
JS/field value, presents the paused-scanner state, and acquires frames only after
Retry. Empty-selector binding exercises actual CameraX error reporting.

The controlled-input test independently expects7 accepted and6 rejected QR values:
local explicit HTTP, HTTPS and a scheme-less domain; script, intent, file,
free text, data and out-of-range port. It renders13 actual QR images at128/192/256/
320px, with0/90/180/270/+15/−15° rotation, luminance35/220, padded field and16-byte
row-stride padding. Both production bitmap and Y-plane decoders must return the
exact payload. The normal visible preview shows the expected normalized URL or
unsupported error; every Cancel preserves the selected page's JS identity. A
separate deliberate Open navigates selected tab2 while tab1's live identity stays
unchanged. `standalone-controlled-qr-results.json` and13 source rasters are retained.
These controlled inputs are software coverage, not an optical camera decode.

No usable screen/QR target was present in the stationary camera's observed view.
No person was asked to wear or align the RG. Optical distance, focus, perspective,
blur and wearer readability were not newly qualified. Per the Owner direction
this is a stated test limit, not a pending target-presentation gate.

The final transition report contains 90 actual PixelCopy frames over 11 state
labels and five predeclared backing points. It excludes webpage media/control
outlines and the actual cursor footprint. All sampled app backings are literal
RGB(0,0,0); no failing frame occurred. This bounded transition sample does not
claim every possible future frame is flash-free. Existing live dark/dynamic/form/
media tests additionally inspect supported website output without inverting media.

## Original ownership → current SPEC evidence

“RG pass” means the stated available device/software evidence passed. The final
Manager/Reviewer acceptance remains open. Phone portions of V2-A02/V2-A13 are
removed from scope under the Owner direction above. Earlier accepted feature
evidence retains its source and outcome.

| Requirement | Primary owner | Current evidence / disposition |
| --- | --- | --- |
| V2-A01 | #28/#30 | Build/APK/provider/display binding above; normal launcher and native root/toolbar; RG pass with stated lint limit |
| V2-A02 | #30; final#33 | RG works locally without a Phone link/remote runtime; no Phone package is installed on the RG; Phone-stopped and simultaneous different-page checks removed from scope by Owner, not PASS |
| V2-A03 | #30 | approvedGeometryAndAddressEditingStayBlackAndRecoverable, actualAddressKeysKeepDraftAndFixedToolbarUntilOpen, fourLiveTabsAndPadGesturesKeepCurrentNativeEffects; current HTTP404/hidden-input and native error history/Refresh/address recovery; strict AddressPolicy host tests |
| V2-A04 | #30/#31 | Existing single full-screen pointer in native captures, real listener lifecycle and all reachable controls; no remaining mode/recenter UI |
| V2-A05 | #31 | allBoundariesDiscardOvershootWithPromptSlowInwardDraw: four edges/corners, three amounts, multiple holds; current source replay/device-draw observations |
| V2-A06 | #31 | settingsPersistAndActualPageUsesEveryDeclaredRateAndGain, fullScreenKeyboardEdgesAndOrdinaryCancellationPreserveNativeEditing: actual page endpoints/rates/literal full-screen edge |
| V2-A07 | #31 | Same edge tests plus camera/screen pause: ordinary departure/cover/tab/tracking/pause stop paths; real UI lifecycle |
| V2-A08 | #30/#29 | fourLiveTabsAndPadGesturesKeepCurrentNativeEffects and ordinaryPadGestureActivatesNativeViewExactlyOnce: single activation, double suppression, native one-writer path |
| V2-A09 | #32 | fourNativeEditorsPreserveUnicodeSelectionCaseAndDone + actualAddressKeysKeepDraftAndFixedToolbarUntilOpen: text/password/textarea/plain editable, selection/Unicode/case/symbols/Space/Backspace/Enter/Done |
| V2-A10 | #32 | Native keyboard/password/reveal captures and strict viewport assertions; fixed toolbar/star; accepted native transient echo retained |
| V2-A11 | #32 | Four editor test + nativeFieldRevealSettlesOnceWithoutReloadOrFocusChange + edge/native input tests; live tab switches/dismissal keep current native editing |
| V2-A12 | #33 | Recreation, real sleep/resume, separate force-stop/install-r durable recovery, own-server interruption; honest EMPTY cold state and zero submissions |
| V2-A13 | #33 | Same signed identity/install-r; retained bookmark hash/data and inert obsolete RG records; historical Phone host194 pass retained; Phone data/browser checks removed from scope by Owner, not PASS |
| V2-A14 | #33/Manager | This ledger, source-bound originals, own cleanup/handoff; independent exact-head review/merge/integration/retirement pending Manager |
| V2-A15 | #30…#33 | G1 approved; native480×640/eight-target one row, keyboard/menu/QR/state evidence; G2 independent verdict pending |
| V2-A16 | #30 | Four live tabs/counter/history/add/select/close/last-tab + native DPAD admission current replay pass; accepted #30 physical direction observations retained separately |
| V2-A17 | #30 | bookmarksUseCommittedLocationAndDurableTruth: star on committed page, unsent draft preserved, add/open/remove/save error/Retry/persistence; cold install check |
| V2-A18 | #33 | Real camera and permission/error/release plus controlled production decoder/validation/preview/Open/Cancel tests; unattended method complete, optical limits above |
| V2-A19 | #31 | Existing rate/gain/default/persistence tests + cold High/Fast persistence and working Settings route; no recenter |
| V2-A20 | #30/#33 | liveBlackAndHiddenRecoveryUseProductionWindow + native black states +90 transition frames; supported document/dynamic/fields/media; bounded sample limit above |

## HUD-G2 conformance and state inventory

| Check | Current evidence / limit |
| --- | --- |
| HUD-C01 Canvas | Production native480×640 portrait root and screenshot dimensions; rotation0; no crop/stretch |
| HUD-C02 Toolbar | Existing strict geometry verifies all eight targets in seven fixed slots,48px row; address and star separate |
| HUD-C03 Star | Committed-page bookmarks test, filled/outline/disabled/draft/error states; independent star activation |
| HUD-C04 Long text | Existing address-keyboard tests retain full long draft in fixed128px field; i/n and every target stay visible |
| HUD-C05 Menu | Four exact labels/order, all four destinations exercised; existing consumed-outside-tap assertion preserved |
| HUD-C06 Keyboard | Four native editor and address suites, explicit Enter/Open versus Done, full-screen cursor/star, native masked feedback |
| HUD-C07 Tabs | Four real WebViews and bounded native key transitions; original accepted physical swipe direction remains historical evidence |
| HUD-C08 Palette | Uncropped original PNGs plus90 PixelCopy transition samples; literal black first-party backings/light controls; no global flash guarantee |
| HUD-C09 Website | Actual author-light/dark/dynamic/form/media fixtures and strict existing pixel/DOM assertions; current WebView95 capability boundary |
| HUD-C10 Cursor/edge | Single accepted local pointer style unchanged; four-edge/corner/rate/gain/keyboard/cover/pause tests and bounded captures |
| HUD-C11 Utilities | Real persisted Bookmarks, camera/controlled QR preview+confirmation/Cancel/denial, working Settings choices/exits; complete QR recovery controls and current rendered-state assertions; no placeholders |
| HUD-C12 State/lifecycle | H12 mapping below; actual sleep/recreation/cold/network and camera cleanup; current ERROR/HTTP404 and deliberate recovery source/captures; no stale-input/ABA framework |

| H12 states/events | Native images / exercised behavior |
| --- | --- |
| Empty/launch, address/draft/invalid/Open | local-browser-empty/address/keyboard-address; existing strict corrected-draft/history/toolbar tests |
| Browsing/field/password/Shift/symbols/Enter/Done | local-browser-keyboard-text/password-early/password-later/multiline/plain/reveal; current native editor assertions |
| More/outside dismissal | standalone-more + local-browser-more; exact rows and no click-through |
| Tab list/add/swipe/select/close | local-browser-four-tabs/native-dpad-before/native-dpad-after-right; counter/live history and no background edit |
| Bookmarks/star/empty/error | local-browser-bookmarks/bookmarks-empty/bookmark-error/bookmarked-draft; durable truth/Retry |
| QR scan/preview/Open/Cancel/denial | standalone-real-camera-0…2 (private actual media); controlled-preview-4/controlled-invalid-8/qr-permission-denied; no auto-navigation |
| Settings | edge-scroll-settings + standalone-settings; all declared presets and persisted cold choice |
| Edge/tracking unavailable | edge-scroll-pointer-top/pointer-corner/keyboard-bottom/tracking-unavailable; observed current draw/scroll and lifecycle stops |
| Loading/local network error | Current browser04 local-browser-loading/http-error/http-explicit-recovery and initial/forward/refresh-error buffers; actual ERROR/HTTP404 confirmed around copy. Original LOADING image retained as a limitation; separate network-error/explicit-recovery remain bound to their stated runs |
| Pause/sleep/resume/recreation/cold restart | standalone-screen-resumed-camera-paused/interrupted-recreation/cold-recovery-no-replay/durable-restored-explicit-open; live state versus cold locations distinguished |

Selected uncropped native device captures:

| State | Original capture |
| --- | --- |
| Four-item More / four-tab list | [More](images/local-browser-more.png), [tabs](images/local-browser-four-tabs.png) |
| Controlled decoded URL / unsupported intent | [URL preview](images/standalone-controlled-preview-4.png), [unsupported](images/standalone-controlled-invalid-8.png) |
| Real camera permission denial / screen resume | [permission error](images/standalone-qr-permission-denied.png), [paused scanner](images/standalone-screen-resumed-camera-paused.png) |
| Cold recovery with bounded cursor | [explicit recovery prompt](images/standalone-cold-recovery-no-replay.png) |
| Native masked password / Settings | [native feedback](images/local-browser-keyboard-password-later.png), [presets](images/edge-scroll-settings.png) |
| Author-light document and preserved SVG / current HTTP error | [live page](images/local-browser-author-light.png), [HTTP404](images/local-browser-http-error.png) |
| Explicit address recovery after HTTP404 / native field still usable | [recovered page](images/local-browser-http-explicit-recovery.png) |

The twelve PNGs are original, uncropped480×640 device captures. HTTP404 and
explicit recovery are current e1c6756 product captures from browser04. The two QR
recovery PNGs retain their exact complete original bytes; current stronger native
capture observations confirm their controls. Other selected images keep their
original14a878dc binding. They are not relabeled as fresh execution on the new APK.
`standalone04-originals` retains46 original scanner/recovery reports/images,
including13 QR source rasters and private actual camera media;
`integration-final-originals` retains84 original navigation/edge/native artifacts.
NativeInput originals remain from08986992 with unchanged fixture/class. All useful
older failures and images remain in their original directories. G1 simulated
reference pictures are not used as device evidence.

## Reproduction and handoff

Build under the existing lock with the toolchain/limits above:

```sh
./gradlew :app-rg:testDebugUnitTest :app-rg:assembleDebug \
  :app-rg:assembleDebugAndroidTest :app-rg:lintDebug \
  :core:browser:test --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2g'
```

Use the configured reserved RG and normal install-r. Instrumentation runner is
`com.code2hack.eyebrowse.rg.test/androidx.test.runner.AndroidJUnitRunner` with
`fixtureBaseUrl=http://100.92.81.33:39030`. Existing LocalBrowser/EdgeScroll checks
also take their normal `candidateHead` and `evidenceRunId` result bindings.
Standalone tests use current native controls and the existing production preview
seam, not a new entry or test framework.

Select the eight self-contained standalone methods for the routine run. Permission
recovery additionally requires revoking only this app's CAMERA before its method;
it clicks the actual OEM Deny and Allow controls, restoring the grant. The cold
prepare/read pair must be separated by force-stop/install-r with no unrelated scene
between; it restores previous tab/settings metadata and removes its own dummy
bookmark/site values. The network method sends explicit STOP/RESTART status markers
for the host to stop/restart only its owned fixture. Do not run these special methods
blindly as one unconfigured whole-class suite. Original methods and scratch commands
are retained for exact reproduction.

Original-round own cleanup at2026-10-10T12:27:20Z is recorded in `own-cleanup.json` and
`own-cleanup.log`: prior tab/settings metadata restored by the tests, the owned
cold backup removed, temporary device output files removed, the RG app process
absent, screen observed `mWakefulness=Asleep`, and own port39030 listener stopped.
Camera/sensor release is supported by the actual test results and normal app stop;
no global camera/sensor/trust attestation is claimed. No reverses were created.
Source, exact candidate APKs, JVM/lint reports, useful failures and original
captures are retained. No shared cache, worktree, branch, user data or unrelated
operation is deleted.

Correction02 own cleanup at2026-10-10T14:11:08.472660+00:00 is separately recorded
in `correction02-rg-state-evidence/own-cleanup.json` and `own-cleanup.log`.
The completed scenes restored their tab/settings metadata. The RG app process
was absent, the display was observed `mWakefulness=Asleep`, seven exact own
temporary result files were removed, and the recorded own fixture was stopped
with port39030 listener absent. No reverse mapping or Phone operation occurred.
RG1906092617103125/100.87.122.122:5555 and own100.92.81.33:39030 are returned to
Manager; these are timestamped observations, not a continuous state guarantee.

Manager retains independent review, PR/merge, acceptance accounting and archival.
Phone and simultaneous checks are removed from scope by Owner, not pending and
not PASS.
No human QR target-presentation gate remains.
