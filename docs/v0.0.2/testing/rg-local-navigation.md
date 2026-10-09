# RG-local browser, HUD and navigation evidence

Worker: Worker-#30 · issue #30 · assignment revision 1 · production continuation 1 · batch-002.

**HUD-G1 is approved.** The [Owner decision](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6075367078)
approves unchanged SPEC D2.2 / HUD H1.2 and its numeric, typography, tab-close and
Settings completion defaults. The [Planner reconciliation](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6075552489)
is at integration 221d676f8f0eabdceca38aa959819a64d7efbe81; its two changed planning
documents do not change the implementation inputs. SPEC blob
1acb8ad6d7a0d77d660a5137010efe17ad39ff0a and HUD blob
070ef8b84d321974af2f35fc68dc1ed2febb91cc remain binding.

The production continuation starts from independently accepted preparation
3d308c04ec34d9f28064bdcec23f486b2b26f89e, on the original integration baseline
5e7585fb532958343222dedeca19ab0ad74b42ba. T01 attempt3/failure2 and T02
attempt1/failure0 remain their historical accounting. Production T03 is now
correction attempt3/failure2 after actual physical swipe negatives; T04 remains
attempt1/failure0. R4 and its scoped native verification remain preserved history.
**Affected native verification and independent whole-candidate
review are pending**; this source/check record does not complete issue #30 or HUD-G2.

## Production source and entry

LocalBrowserActivity is main-source product code with native Views and one
LocalBrowserSession/WebView per tab. Its release manifest entry is non-exported;
the debug overlay exports an explicit development entry for qualification.
The ordinary MainActivity launcher is unchanged until #33. This Activity starts
no Phone pairing, remote frame, controller, ordinal or Phone-link service.

LocalTabs owns ordered live browser objects. Add appends and selects without a
four-tab cap; boundaries do not wrap. Close selects the next remaining position
or the preceding last position; closing the sole tab creates a fresh empty 1/1.
A new empty tab begins address entry. Selection hides the old surface, clears
ordinary native focus, dismisses its keyboard and pauses that WebView; selection
resumes the existing chosen View and its own history. No tab is reconstructed
merely to select it.

The reused RgInputRouter is the single pad recognizer. One confirmed short tap
dispatches one ordinary native touch at the current pointer; a double tap has
no action. Only the named ROKID,PSOC-TP-R keyboard source admits native horizontal
DPAD_RIGHT/DPAD_LEFT (22/21) and the retained OEM Android 292/293 to next/previous
tab in browsing and keyboard states. Other keyboards/sources and vertical keys
retain ordinary native dispatch. Utility swipes are no-ops. **A replay using the observed pad
device ID does not establish actual wearer-facing right/left directions.**
That physical qualification remains an explicit resource/evidence need.

NativeRgInputTarget remains the only direct input writer. Keys use the current
shown native focus and its InputConnection; page/utility visibility controls
ordinary input eligibility. No page-hit snapshots, retained editor/selection
identities, generations, ABA validation, deferred keys or action queues are
added. Navigation retains the unchanged shared AddressPolicy.

A short, cancelable animation-frame observation after native page touch checks
the current public WebView.onCheckIsTextEditor/hasFocus state for up to 750 ms to
show the local keyboard. It retains no touch/key or original editor. Dismissal,
navigation, tab/utility changes and pause remove the callback. This accommodates
asynchronous renderer focus while the app window excludes the system IME.
Native RG verification of automatic opening remains required; full field types,
constraints and editor semantics remain #32's acceptance. Renderer replacement
reattaches the same ordinary touch callback.

## Approved geometry and current scope

All coordinates and text sizes are rendered pixels, independent of Android dp.

| Native region | Bounds (x, y, width, height) |
| --- | --- |
| Canvas / pointer overlay | 0, 0, 480, 640 |
| One toolbar | 0, 0, 480, 48 |
| Close / Back / Forward / Refresh | x = 0 / 48 / 96 / 144, y = 0, 48 × 48 |
| Address slot | 192, 0, 176, 48 |
| Editable address / independent trailing star | 192, 0, 128, 48 / 320, 0, 48, 48 |
| Live tab counter / More | 368, 0, 64, 48 / 432, 0, 48, 48 |
| Normal page / page with keyboard | 0, 48, 480, 592 / 0, 48, 480, 392 |
| Built-in keyboard | 0, 440, 480, 200 |
| More menu | 256, 48, 224, 192 |

All eight controls remain visible and separately actionable, with disabled
Back/Forward/Refresh/star retained. A non-navigable page keeps a disabled outline
star even if an earlier page was saved. The compact address elides inside its fixed
region; native editing reveals the retained full draft without moving the star
or other controls. Outside editing, the compact preview uses the actual native
location, independently of an unsent draft. A visible compact error/status
consumes its covered-page taps. Ordinary labels are 20 px, secondary/status
18 px, headings 24 px.
App-owned fills are opaque black; outlines are #606060 at 1 px or selected
#F2F2F2 at 2 px. Light/disabled text uses H1.2's palette. The production pointer
uses a 16-pixel bounded light ring/dot footprint and gray unavailable indication;
legacy consumers retain their existing appearance.

Keyboard rows use H1.2's 44/44/44/48-pixel heights, 4-pixel gaps and 8-pixel side
padding. English rows are qwertyuiop / asdfghjkl / Shift + zxcvbnm + Backspace.
Shift changes case. Symbol rows are 1234567890 / :-@_?&=#% /
Shift + +,;!'"() + Backspace. Both layers retain 123/ABC, Space, period, slash,
Enter/Open and Done in the bottom row. Open and Done are separate real targets
at (296,588,96,48) and (396,588,76,48). Address Open validates and navigates;
invalid Open retains the draft/current page. Done does not intentionally submit.

More has exactly Add new tab / Bookmarks / QR scan / Settings. Its outside-page
tap is consumed while dismissing; native toolbar controls remain available.
Tabs and Bookmarks have local Done exits below the same toolbar. QR scan and
Settings destinations explicitly say they are not implemented here and name
#33 / #31. They do not request camera access or change settings. Edge scrolling
and overshoot remain #31, the complete field keyboard #32, and QR/ordinary
launcher cutover/final integration #33.

## Persistence, recovery and truthful errors

A star toggles the committed READY page URL/title, even while an unsent address
draft differs. It never submits the draft or deliberately blurs a current
editor. The filled star reflects a successful durable result. LocalBookmarks
stores normalized URL keys and titles in app-private
local-browser/bookmarks.properties. Identity preserves meaningful path/query/
fragment distinctions; repeated saves update the same entry.

The store writes a same-directory temporary file, syncs its bytes and atomically
replaces the destination before publishing success. Read/write/validation
failures retain truthful state and a compact error; an unreadable file is not
silently overwritten. Bookmarks lists saved entries, opens one in the current
tab, removes an explicit item, and offers Retry/empty/error states. Native
qualification must prove this filesystem route on RG, not only on the host.

Only safe committed locations and selected tab index are saved at normal stop
and in Activity saved state. Cold/recreated pages start interrupted/empty with
recovery locations and require deliberate Open or bookmark selection; no
uncertain navigation, POST, field values or JavaScript memory is replayed.
Ordinary still-live pause/resume keeps the actual WebView/document. Normal
engine cookies/site data remain under WebView ownership.

Black loading/error pages use the accepted native INVISIBLE/clearFocus route;
visible recovery controls remain usable. The live public stylesheet and
FORCE_DARK_OFF policy remain bounded by the supported fixtures. Media is not
inverted. This implementation does not claim arbitrary-site compatibility,
provider security, compositor/optical evidence or full field acceptance.
Release network policy, provider/global/private settings, trust and data are
unchanged. The existing debug HTTP fixture allowance remains debug-only.

## Current checks and requested native evidence

Host checks use the shared build lock, measured memory before/at acquisition,
JDK17, the assigned Android SDK and the existing two-worker/Xmx2g caps:

~~~sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/home/code2hack/Android/Sdk \
timeout 900s flock "$HOST_BUILD_LOCK" ./gradlew \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g --console=plain \
  :app-rg:testDebugUnitTest :app-rg:assembleDebug \
  :app-rg:assembleDebugAndroidTest :app-rg:lintDebug
~~~

The new host cases exercise four surviving tab objects/history, boundaries/
closing, actual allocation failure, bookmark Unicode and URL distinctions,
disk reload/removal, real filesystem failure and corrupt-file recovery.
Only RG source/resources/manifests/tests change; no shared core or Phone source
changes. Phone remains unavailable/unreserved, with applicable simultaneous
integration evidence pending #33.

The first host run executed 103 JVM cases with zero failures/errors/skips and
built the app. Instrumentation compilation then rejected IntArray.mapNotNull;
conversion to List corrects that test-source error. Lint had not completed.
The failed log is retained as an intermediate check within this same T03 attempt.
The final complete run passed all four requested gates in 42 seconds, with all 103
RG JVM cases executed successfully (16 XML reports; zero failures/errors/skips).
Gradle reported 19 executed/63 up-to-date actionable tasks. The full task log
also retains skipped and NO-SOURCE tasks; dependency cache reuse is not a new
core/Phone test result. Lint reported zero errors and 22 warnings. Two new
orientation warnings describe the explicitly required portrait Activity; no
suppression or layout waiver is applied. Nineteen Kotlin2.2-versus2.0 metadata
reader diagnostics remain a static-analysis limitation despite task success.
The final instrumentation observation change then passed test-APK assembly
and lint in 17 seconds (7 executed/52 up-to-date tasks; 6 metadata diagnostics).
Product source and JVM cases were unchanged from the complete run.
Exact task states, reports, APK/source hashes and diagnostics are preserved in
the candidate handoff.

The simplification pass retains one session class, one small ordered-tab model,
one durable bookmark store and native Activity composition. It reuses the
address policy, input writer, recognizer and pointer acquisition/model. Redundant
keyboard layout requests during repeated loading callbacks were removed;
focus observation is canceled through ordinary lifecycle handling. There is no
new framework, parallel writer or feature/policy expansion.

LocalBrowserInstrumentedTest targets the real production Activity and unchanged
#28 fixture, with no device/bootstrap/service-start code. Proposed one reserved
original class invocation had four methods; the current correction adds the
native-DPAD boundary method below:

| Method | Real observation required |
| --- | --- |
| approvedGeometryAndAddressEditingStayBlackAndRecoverable | Measured 480×640/toolbar/eight hit regions, 200-pixel keyboard, independent Open/Done, invalid long draft, actual links/history/refresh, exact More geometry/order/outside consumption, explicit incomplete utility exits and visible unavailable cursor. |
| fourLiveTabsAndPadGesturesKeepCurrentNativeEffects | Four actual WebViews and independent history/live field, native pad short/double allocation effects including a fifth tab, both OEM tab bindings/boundaries, keyboard dismissal, no page scroll/submit/input effect, list selection and close/last-tab policy. Physical right/left qualification remains separate. |
| bookmarksUseCommittedLocationAndDurableTruth | Actual star while draft differs, preserved focus, disk persistence, Activity recreation without automatic replay, bookmark open/removal, controlled owned app-private filesystem failure and truthful error/Retry/empty state. |
| liveBlackAndHiddenRecoveryUseProductionWindow | Unchanged author-light/dynamic/author-dark field/media RGB samples, current native input/Done/pause-resume, real delayed loading/404, hidden touch/key/submit no-effect and visible input recovery. |
| nativeDpadAdmissionPreservesScopeAndTabEffects | Actual assigned-device/source/scan/key/action/repeat/meta/timing KeyEvents through the production Activity, four tabs/no wrap, legacy OEM support, utility/focus/lifecycle/aged/repeat/cancel guards, other-device/source/vertical pass-through and real current-editor recovery. Synthetic native metadata does not establish wearer-facing direction. |

The original four methods declare fifteen reached-only captures: empty, address, more,
tracking-unavailable, four-tabs, bookmarked-draft, cold-recovery, bookmarks,
bookmark-error, bookmarks-empty, author-light, dynamic, field, loading and
http-error. The native-DPAD method adds `native-dpad-before` and
`native-dpad-after-right`, with actual counter/selection sidecars. Each is an uncropped 480×640 owned-window PixelCopy after native
frame commit, with a field-free geometry/focus/phase/visibility/timing sidecar.
Shown READY pages first await the public WebView visual callback. Opaque
utilities and empty/loading/error backing await the owned native window frame;
they do not claim a hidden page was presented.
Native built-in key return and confirmed pad More/Add dispatch retain the
SPEC's strict 100-ms check; recognition, editor completion and rendered results
are separate observations. Sidecars retain measured dispatch counts/maxima.
One recovery JSON records fixed dummy-fixture predicates/counters before/after
native b, including after a failed wait. Strict original black/media oracles
and fixture bytes are unchanged. Captures do not prove optical or physical
compositor behavior; the 28 approved-design drawings remain simulated.

The suite temporarily changes only this Activity's recovery metadata and
restores the prior value in-process after closing. It adds/removes only its
previously absent dummy fixture bookmark. It verifies that no preexisting
entries would enter a capture. The controlled failure uses a unique owned cache
file as a non-directory parent, then removes that file; it does not
fill disk, change permissions, replace normal bookmark bytes or clear app data.
The R4 correction retains entry ownership until both memory and readable disk
confirm removal. Failure cleanup reloads the current store before removing only
that owned entry; unreadable or unwritable persistence remains an explicit
unresolved cleanup error. Repository restoration/removal and blocker deletion
are both attempted, with the original failure and all cleanup errors retained.
The existing test-shared source set compiles these cleanup functions for both
instrumentation and three focused JVM cases. Actual failed atomic replacement,
transient recovery preserving unrelated entries, and persistent failure with a
second cleanup error all passed on the host (3 cases, zero failures/errors/skips).
Affected test-APK assembly and lint passed in 20 seconds, with 14 executed/51
up-to-date tasks; lint retained 22 reported warnings and 13 metadata-reader
diagnostics. Product code and the app APK are unchanged from the original
production candidate; the changed instrumentation APK needs fresh qualification.
Those precise effects require a fresh Manager native grant alongside the exact
frozen app/test hashes, identity/access/trust preflight and fixed fixture.

~~~sh
adb -s "$RESERVED_RG_ENDPOINT" shell -T am instrument -w -r \
  -e fixtureBaseUrl "$RESERVED_FIXTURE_BASE_URL" \
  -e class com.code2hack.eyebrowse.rg.LocalBrowserInstrumentedTest \
  com.code2hack.eyebrowse.rg.test/androidx.test.runner.AndroidJUnitRunner
~~~

Exact `db01e14610774f08cb91f3bad35de179e1f14a70` received one grant6 native
invocation: four methods passed in 45.368 seconds, with all 15 fresh 480×640
captures/sidecars and recovery JSON. Owned operations settled, opaque trust
matched, and the prior safe Tailscale foreground/connectivity/Awake state were
verified at 2026-10-09T07:53:37.598599Z before resource release. These are
timestamped observations, not current device-state claims. Independent source
review then identified R4's ownership loss on failed removal; that normal-path
4-PASS result did not exercise the failure and the round was NOT_PASSED.
Exact `1179c7363e2b34e99e2ba265bdf61589edff1232` subsequently passed the one
affected bookmark method in 10.909 seconds under grant7, with five fresh native
captures/sidecars and unchanged app APK. Independent review supported that
software/native correction; the host failure controls remain JVM evidence, not
a reproduced RG persistence failure. The later physical checks still failed to
switch tabs, independently of that supported R4 result.
Physical swipe direction, Phone PASS, final HUD-G2 and whole-issue acceptance
remain pending. The dynamic capture's visible focused input/open keyboard is
retained without a DOM focus-cause inference. Exact candidate requests supply
bounded affected verification and owned cleanup; Manager controls resources.

## Native pad admission correction and remaining physical evidence

Actual physical checks at `1179c736` retained the Owner's right/left negatives
and counter2/4; mixed kernel names did not establish a framework mapping cause.
The separately reviewed, opt-in debug probe at `21f57ffc` then recorded assigned
pad device3/source257 native scan105/key21 and scan106/key22 at the original
Window.Callback. The existing router returned `unmapped-key` before eligibility
or recognizer admission, while recorded current browsing state was active,
focused, shown and pointer-available. No292/293 or recognized swipe appeared in
that diagnostic trace. Recorded counter remained2/4. Gesture counts/onsets and
individual physical directions were unmeasured; these events do not establish
wearer-facing orientation or explain all older mixed OEM history.

The current correction adds only those horizontal native keys to the same
named-pad/source admission function and existing recognizer. A focused JVM red
regression first failed at scan106/key22 admission while legacy and foreign-input
controls passed. It uses the production admission function plus the existing
recognizer and LocalTabs to assert selection effects and no-wrap boundaries.
The Android regression constructs real KeyEvents with the observed metadata
shape and fresh Android uptime, passes them through the production Activity,
and checks current tab effects and retained native editing/input guards.
It requires its own exact-candidate RG grant; compilation is not execution.

All temporary diagnostic Application/manifest/router/recognizer observers and
the opt-in probe control are removed. The immutable probe source/APKs, original
native traces and all earlier failures remain retained as separate evidence.
During the diagnostic run, a finite watcher stopped both readers on foreground
loss; the post-report foreground was the ordinary launcher, so no new owned
counter screenshot was fabricated. Exact own app/helper/readers settled, only
prior local.tabs absence was restored, opaque trust matched, and the retained
end-state was Awake/interactive at the prior safe launcher with connectivity.

A fresh candidate still requires affected native regression and separately
coordinated one-real-right/one-real-left plus boundary observations, with owned
UI/counter evidence and authenticated human direction kept separate. Logical
DPAD names, JVM PASS and native injection do not satisfy that physical criterion.
Manager reserves resources and coordinates the visible prompt; no device access
or new human step follows from this document.

## Preserved preparation record

The following is the original pre-G1 preparation boundary, results and procedure.
Its OPEN/proposed/later-work statements describe that historical phase and are
superseded by the current approval/production sections above. Original negatives,
pixels, oracles and exact-head provenance remain intact. T01 attempt3 later
completed five actual methods with five PASS terminals and five fresh 480×640
captures/sidecars plus the field-free recovery JSON. The bounded owned cleanup
was verified and resources released. Independent preparation acceptance is
[PR #36 review 5466008458](https://github.com/code2hack/EyeBrowse/pull/36#pullrequestreview-5466008458).
It is scoped preparation evidence, not a production HUD result.

### Source boundary

`LocalBrowserSession` owns one local Android WebView and black backing. Its
ordinary callbacks expose actual URL/title/history/loading/error state to its
owner. Address entry and page navigations reuse the unchanged shared
`AddressPolicy`: HTTPS normalization, explicit HTTP including local hosts,
invalid-draft preservation, no free-text search, no address-origin script/file/
native intent execution. SSL failures are canceled and shown; there is no
certificate bypass, native JS bridge or automatic popup creation.

Back, Forward and Refresh use normal WebView methods. Main-frame network/HTTP
failures remain on a black recoverable surface. Renderer loss destroys/removes
the lost View and reports interrupted memory honestly; only a deliberate new
Open recreates a page. No form action is replayed automatically. Cold recovery,
durable locations, multiple live tabs and the final launcher remain later
authorized work.

`LocalBrowserPreparationActivity` is a **debug-only explicit development entry**
with no launcher intent filter. Its diagnostic controls and layout are not the
production HUD or the G1 reference. Existing `MainActivity`, ordinary launcher,
Phone source and remote migration consumers are retained. The new local entry
starts no Phone pairing/link/frame/controller activity. The debug manifest
permits the explicitly supported HTTP fixture route; it does not change a
device/provider/security setting or add a production launcher route.

The development entry composes exactly one existing `RgInputRouter`,
`NativeRgInputTarget`, `PointerOverlay` and current native focus. Available local
keys edit the ordinary native address editor or focused WebView directly. Enter
opens an address; Done dismisses without deliberately submitting. Navigation
buttons/keys do not steal editor focus. Pause/focus loss stops the recognizer and
sensor listener; ordinary WebView pause/resume preserves a still-live document.
Production tab swipes and #31 edge/overshoot behavior are not substituted with
debug behavior. Existing pointer geometry/color is retained for preparation,
not claimed as approved H1.2 conformance.

There are no document/element identities, operation generations, ABA tests,
deferred keys, input queues, remote image maps or a second writer. UI callback
state is ordinary page/loading/error state, not an input-admission protocol.
The three small session responsibilities are native page ownership, navigation
and fixed presentation; the diagnostic Activity and deterministic renderer
remain separate. The simplification pass retains the existing policy and input
components without introducing a browser/provider plugin framework.

### Supported black-page route and limits

Preparation reuses #28's positively qualified route: black native window/root/
page backing, `FORCE_DARK_OFF`, disabled native WebView focus highlighting, and
one fixed public presentation-only stylesheet on the live document. A public
visual-state callback reveals the attached styled WebView from `INVISIBLE` to
`VISIBLE`. Empty/loading/error pages relinquish native focus and remain
`INVISIBLE`, retaining layout while black backing is shown. This uses ordinary
Android visibility so hidden fields/buttons do not receive native touch,
focused keys or scroll. Visible address/navigation recovery remains available.
The fixed selectors cover ordinary HTML containers and editors;
they do not assign field values, replace the document, invert images, or style
`img`, `svg`, `canvas` or `video`.

The accepted #28 evidence is **bounded**, not universal compatibility, provider
security, optical readability or endurance acceptance. The scoped tests below
recheck ordinary authored dark/light, fields, dynamic CSS updates and the owned
external SVG color fixture on this actual candidate when a device is reserved.
Raster photos/video/canvas, background images, shadow DOM, cross-origin frames,
CSP, hostile inline-important rules and complex editors remain unqualified;
required actual failures must be retained/routed, not hidden as generic limits.
The narrow existing Owner native temporary password-echo decision is preserved.
This preparation adds no masking or global/provider setting workaround.

### Deterministic G1 reference

[Open all 18 state IDs and 28 original PNGs](../hud-g1-reference/index.html).
[The manifest](../hud-g1-reference/manifest.json) contains exact source/font/
artifact SHA-256 values, fixture seed, native pixel dimensions, eight toolbar
regions, utility/key bounds and pointer positions. The standalone
[Kotlin renderer and reproduction recipe](../../../experiments/hud-g1-reference/README.md)
refuse mismatched pinned SPEC/HUD/font inputs. Two generation runs produce
byte-identical images, manifest, media fixture and index.

Every picture is a **SIMULATED NON-PRODUCTION DESIGN REFERENCE**. These are Java2D
drawings, not Android screenshot captures, working controls, real native
password masking, functional camera/Settings/edge scrolling, engine darkness
or Owner approval. Caption/index text is outside the 480×640 app canvas.
The complete set preserves proposed H1.2 bounds, one-row eight-action toolbar,
trailing star, black fills, one pointer, Enter/Open and Done, exact More order,
tab/bookmark/QR/Settings/tracking/error/edge/dark state coverage. Numeric
completion defaults remain proposed. #31–#33 functional ownership is unchanged.

### Host checks and reserved-device procedure

Use the mission's lock with memory observations before and at acquisition,
JDK17 `/usr/lib/jvm/java-17-openjdk-arm64`, SDK `/home/code2hack/Android/Sdk`, and
`--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`. Explicit affected gates:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/home/code2hack/Android/Sdk \
timeout 900s flock "$HOST_BUILD_LOCK" ./gradlew \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g --console=plain \
  :core:browser:test :app-rg:testDebugUnitTest \
  :app-rg:assembleDebug :app-rg:assembleDebugAndroidTest :app-rg:lintDebug \
  :experiments:hud-g1-reference:installDist
```

The first complete run has browser14 + RG97 JVM cases, zero failures/errors/
skips, successful debug/test APK and renderer builds, and lint zero errors/
20 warnings. Lint's existing Kotlin2.2 metadata versus Kotlin2.0 reader
diagnostics limit Kotlin static analysis despite task success. Executed,
up-to-date and NO-SOURCE tasks are distinguished in the private phase evidence.
The initial renderer plugin-version configuration error is preserved as an
intermediate correction within attempt1, not a concluded todo failure.

The first exact-source RG run at
`6a4dd3ae39eea0a5ea696e992ade079779e55517` concluded **NOT_PASSED**: four tests
ran, three passed and the loading/error method failed its original black sample
at `(450,620)` (white). `(450,300)` was black. The retained loading screenshot
shows the earlier address-entry status and visible keyboard; `(450,620)` lies
on the Done label. It does not establish a white page-background cause. The
actual HTTP404 continuation and fifth error capture were not reached; four
original native PNGs and the full negative remain preserved. Safe cleanup,
unchanged opaque trust, actual Awake/interactive, connectivity and prior Rokid
launcher foreground were verified before the Manager released the resources.

**I30-T01 attempt2** addressed Reviewer finding I30-R1: opacity
alone did not remove the hidden page's native input eligibility. The correction
uses normal visibility/focus and adds a focused hidden touch/key/form-no-effect
and visible recovery check. Capture observation now waits for the owned hardware
window's public frame-commit callback, then uses `PixelCopy` on that exact
480×640 window buffer, with origin `(0,0)` checked and no scaling/cropping. Ready
pages first retain the existing WebView visual-state wait. The original black
coordinates, fixed authored fixture, loading state and actual HTTP404 checks
are retained. Sidecars record phase, page/keyboard visibility, focus and commit/
copy times without field values. This addresses a missing rendered-frame wait;
the original synchronization cause remains an inference until revalidation.
Window-buffer captures do not establish compositor presentation or optical time.

The public contracts are
[WebView visual-state visibility](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback))
and [frame commit](https://developer.android.com/reference/android/view/ViewTreeObserver#registerFrameCommitCallback(java.lang.Runnable)).
The actual corrected-source run at
`ce283d7e91391d9c889d48f6367c7cc37f5e92f7` was also **NOT_PASSED**:
five methods ran, two passed and three failed in 30.218 seconds. The field black
sample was `#101010`; the loading sample `(450,300)` was `#030303` instead of
exact black. The retained `(450,620)` loading pixel was also `#030303`, but its
assertion was not reached. The hidden-loading eligibility/no-effect assertions
preceded a timeout awaiting recovered visible editor value `b`; this does not
pass the complete hidden-input/recovery method. Actual HTTP404/error input and
recovery, dynamic/author-dark/error capture continuation remain unqualified.
Only two new original PNGs and two sidecars were reached, separately retained
from the four original-run pictures. Both new captures record a focused root
`FrameLayout`; the loading sidecar records LOADING/page hidden/keyboard hidden.
The strict oracles and authored fixture bytes remain unchanged. Owned safe
cleanup was verified and the Manager released grant3; none of its operation
budgets may be reused.

**I30-T01 attempt3/failure2** disables the preparation root's default focus
highlight. That focus holder has a flat black background but was allowed to
paint Android Material's highlight over its entire child tree. Primary
[Android 12.1 View source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.1.0_r1/core/java/android/view/View.java#L23808)
and [Material ripple source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-12.1.0_r1/graphics/java/android/graphics/drawable/RippleBackground.java)
support this application-level explanation for both the lifted field/media
colors and transient loading grey. This is an evidence-supported source
diagnosis, not a new device reproduction or proof of the vendor's actual
highlight resource. The existing WebView visibility/input correction is kept;
neither focus nor the recognizer is disabled to obtain black pixels.

Native test observation now waits for current laid-out key geometry and actual
shown WebView/text-editor focus before typing. Recovery first observes the
authored replacement fixture's blank editor, then checks READY/current focus;
the final expected `b`/`a`, no-hidden-effect checks and exact RGB samples stay
unchanged. A field-free recovery observation records native state, fixed
empty/expected-`b` predicates and fixture input/submit counts before/after `b`,
including after a failed effect wait. No field strings, URLs or expected DOM
assignments are stored. This closes previously missing observation conditions;
the exact native recovery-timeout cause remains unproven. Capture sidecars also
record root focus/highlight, page focus and address focus.

These changes require fresh exact-candidate RG resources and renewed independent
review. Compiling the updated tests is not native PASS. The next bounded
procedure retains all five methods, all original black/media samples, five
uncropped 480×640 captures/sidecars and the one field-free recovery observation;
only actually reached artifacts count.

**No device access is implicit.** Request/obtain the exact-candidate RG and
owned fixed-fixture listener reservation first. Phone is unavailable/unreserved;
no Phone command, simultaneous-use claim or Phone regression PASS is made.
Record the assigned source/app/test hashes, physical serial/model, actual
provider/software/display/route and canonical opaque trust match before any
installation/mutation. Do not switch a working listener/server, pair/change
trust, clear app data, change screen/security/provider/network settings or issue
power cycles as part of these scoped checks.

The existing fixed #28 fixture serves `keyboard.html`, `history.html`,
`author-light.html`, `media.svg`, and a real delayed `loading.html`. One scoped
instrumentation class now exercises five methods:

| Method | Observations |
| --- | --- |
| `localNavigationPreservesInvalidDraftAndUsesActualHistory` | Real built-in address keys/Open, rejected draft/current document, ordinary link, Back/Forward and a fresh Refresh document. |
| `currentNativeFocusAndDonePreserveEditingAcrossPauseResume` | Native field tap, actual built-in key effect, Done without submit, pause sensor cleanup and the same still-live document/value after resume. |
| `liveBlackPresentationKeepsFieldsDynamicUpdatesAndMediaColors` | Actual author-light/field/dynamic/author-dark pixels and un-inverted external SVG; owned native captures. |
| `pendingLoadAndHttpFailureLeaveBlackRecoverableLocalControls` | Real delayed HTTP load, black pending frames and actual main-frame HTTP error with usable local recovery controls. |
| `hiddenPageRejectsNativeEffectsAndVisibleRecoveryRestoresInput` | Hide an actually focused editor during real delayed owner navigation; attempt native hidden field/button/Enter input with unchanged field/input/submit observations; actual HTTP404 native invisibility and ordinary visible address recovery restore ready-page editing. |

```sh
adb -s "$RESERVED_RG_ENDPOINT" shell -T am instrument -w -r \
  -e fixtureBaseUrl "$RESERVED_FIXTURE_BASE_URL" \
  -e class com.code2hack.eyebrowse.rg.LocalBrowserPreparationInstrumentedTest \
  com.code2hack.eyebrowse.rg.test/androidx.test.runner.AndroidJUnitRunner
```

The suite does not connect/bootstrap devices, install packages or start fixture
services itself. Individual native/JS/frame/copy observations are bounded;
screenshot regions/fractions are declared before capture. JS reads only owned fixture
truth/geometry; positive navigation/editing uses the actual native controls.
These synthetic native touch checks support only the exercised direct input;
they do not claim physical head/pad directions, optical comfort, full field
keyboard acceptance, four live production tabs or HUD-G2. Pending actual device
results remain explicitly open in the phase handoff until run.

After a granted run, stop only the owned instrumentation/application/fixture
operations as authorized, verify listener/process/sensor cleanup and preserve
data/provider/connectivity and the Manager-specified RG end state. No Phone
screen-off policy is applied to RG. Manager receives the exact source/prototype/
host/native evidence and remaining limits; only Manager schedules independent
review and reconciles the shared mission/attempt records.
