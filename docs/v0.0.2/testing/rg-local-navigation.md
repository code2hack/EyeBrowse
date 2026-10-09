# RG-local navigation preparation and HUD-G1 reference

Worker: Worker-#30 · issue #30 · assignment revision 1 · batch-002.

This record covers **I30-T01/T02 pre-G1 preparation**, not the completed issue,
production HUD conformance or the ordinary RG launcher cutover. The assigned
baseline is `5e7585fb532958343222dedeca19ab0ad74b42ba`, SPEC **D2.2** and HUD
**H1.2**. [HUD-G1](../../design/v0.0.2-hud-g1-review.md) remains **OPEN**.
I30-T03 production tabs/HUD/Bookmarks completion awaits the actual Owner design
disposition; final I30-T04 review/acceptance follows the required evidence.

## Source boundary

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

## Supported black-page route and limits

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

## Deterministic G1 reference

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

## Host checks and reserved-device procedure

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
