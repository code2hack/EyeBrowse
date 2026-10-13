# RG pointer, numeric Settings and strips — issue #41

Worker: Worker-#41

Contract: SPEC D3.1 §4 / V3-A01–A03, HUD H3.1 H3-C03/C04 and retained toolbar/native-input checks. Source starts at integration baseline `74798ae0d47e009a0a145c21b0ea116cb5fee94b`, branch `work/v0.0.3-issue-41`. Host checks and all eleven selected native methods passed under Manager window01. Manager owns independent review and acceptance. This is not a release.

## Implementation

Pointer speed uses integer tenths (1–60), displayed as 0.1×–6.0×, default 2.0×. Scroll speed uses 10–1,200 native viewport px/s in steps of 10, default 240. A native arrow tap saves before changing the effective value. Disabled arrows are dim; storage failures show an operation error. No preset groups, slider, editable number or Save button remains.

New app-private preference keys are `pointer.tenths` and `edge.pixels-per-second`. If a new key is absent, legacy `pointer.sensitivity` Low/Standard/High maps to 15/20/25 tenths and `edge.speed` Slow/Standard/Fast to 120/240/360. Successful migration writes its new key; subsequent launches use that numeric value. Old keys and unrelated data remain untouched. Each setting migrates independently, so a partial storage failure cannot make the other migrate repeatedly. Invalid numeric storage falls back to the documented default rather than reapplying an old preset.

At native 480×640, the old cursor-footprint reference remains x=[8,472], y=[8,632], giving gain spans 464×624. Logical vertical limits extend to [-24,664]; those extra 64 pixels never enter gain computation. The cursor draws only at 0≤y<640, while tracking remains available inside either strip. The native root hit test rejects off-screen taps without clamping them. Horizontal native pad tab gestures still use tracking availability.

Smoothing stays 60 ms and angular deadband stays 0.3°. The former 3 reference-spans/s cap scales with the selected gain (6 at default 2.0×), preventing it from silently undoing the gain increase. One advance integrates at most 50 ms; sensor-age expiry remains 250 ms. At an outer limit, angular excess and obsolete filter destinations are discarded. Inward motion inside a strip keeps scrolling; visible return stops it. The root screen defines strip boundaries with the keyboard open. Existing page/list ownership and pause/timeout/tab/cover cleanup are retained.

## Host verification

Environment: Spark, JDK 17 at `/usr/lib/jvm/java-17-openjdk-arm64`, Android SDK `/home/code2hack/Android/Sdk`, compile SDK 35. Host Gradle uses the assigned shared build lock and no daemon, two workers, 2 GiB heap:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/home/code2hack/Android/Sdk \
flock /home/code2hack/.local/state/eyebrowse/v0.0.2/runs/run-001/host-build.lock \
./gradlew :app-rg:testDebugUnitTest :app-rg:assembleDebug \
  :app-rg:assembleDebugAndroidTest :app-rg:lintDebug \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g
```

69 JVM tests passed in the initial corrected host run. Debug application and instrumentation APK builds passed. Lint completed with 0 errors / 7 existing warnings, but its Kotlin analyzer emits metadata-version errors (stdlib metadata 2.2.0 versus expected 2.0.0); its exit success is qualified by that limitation. No dependency/toolchain changes were introduced to hide it.

Tests cover every numeric value and persistence, all nine old preset combinations, no repeated doubling, invalid storage, failed saves/retry, moderate gain on both axes, cap behavior, expiry, endpoint rounding, and 72 boundary/corner magnitude/hold combinations with equal first slow inward travel. Existing raw-pose aiming controls now exercise 1.5×, 2.0×, 2.5× and 6.0× against the same eight-pixel target criterion. The test source corrects aiming through observed raw-pose feedback after smoothing; it no longer assumes one unconditional deadband offset works before and after a reversal. No cursor coordinate is assigned.

[Comparable JVM pose traces](rg-pointer-pose-traces.csv) contain the same moderate raw input at 1.0× old Standard reference and 2.0×, with and without strip padding, plus a fast capped frame. The reference runs the preserved old gain/cap parameters at 20-ms intervals; it is not a device observation from a second old APK. Initial test failures and corrected results are retained under the run evidence directory; these intermediate failures do not create new canonical attempts.

Local evidence: `/home/code2hack/.local/state/eyebrowse/v0.0.3/runs/run-001/issue-41/evidence/`, including `host-unit-initial.log`, `unit-initial/`, `host-debug-round1.log`, `host-debug-round2.log`, `host-final-precommit-v2.log` and JUnit XML. Extending the raw aiming checks to the new gains exposed an 8.5-px error from the old test generator's fixed deadband compensation; its failures and correction are also retained in `unit-aim-calibration/` and `unit-aim-unconditional-compensation/`. The initial host binding is `host-candidate-binding.json`; the corrected source/APK binding is `window01-corrected-candidate/binding.json`. The final publication binding records the documentation-only successor and equal app source tree.

## Actual RG verification — window01

Manager grant `MGR-V003-W41-RG-FIXTURE-WINDOW-01` supplied exclusive RG serial `1906092617103125`, model `RG-glasses`, and fixture `100.92.81.33:39030`. The actual device identity matched on the primary `100.87.122.122:5555` route. The unchanged fixed fixture was copied into private scratch and run with Java17 / `-Xmx128m`; no new routes or service implementation were added. Both retained APKs were installed with `install -r`, without clearing data; installed SHA256 matched the retained files. Current WebView is `com.android.webview` 95.0.4638.74.

`EdgeScrollInstrumentedTest` reuses native touch dispatch, raw quaternion input, the current View tree, and read-only DOM/native scroll observations. It snapshots/restores all four owned input preference keys and tab recovery metadata. Its native checks cover:

- All nine legacy preset migrations, numeric text versus effective gain/speed, one step per native tap, recreation, exact row/arrow bounds, both endpoint states and actual 480×640 Settings captures.
- Both hidden strips, small inward continuation with real page effects, visible return/callback cancellation, rejected off-screen taps and native horizontal tab gestures while hidden.
- Multiple outward magnitudes/holds/corners, slow inward travel and tracking expiry.
- Native page rates at 120/240/360, retained small-target/native-key behavior, keyboard-fixed/full-screen boundaries, actual long visible tab-list scrolling, and covered-page/lifecycle/endpoint cleanup.

The initial `82e4e66` invocation ran six methods in 131.291 s: four passed and two failed. All six cleanup reports passed. The keyboard procedure asserted the final y=664 clamp after only waiting for page movement, observing y=658.6243 while smoothing continued. The strip procedure's 1.45° return had not cleared the strip at its 100-ms deadline (y=642.71265, still hidden and scrolling). These originals remain in `window01-edge01-originals/` and `window01-edge01-instrumentation.log`; they are not passes. Old keyboard/scrolling captures and missing-file error bytes found during collection are excluded from that invocation's image evidence.

Test-only correction `4db534720d467c5a5a23135bc7cb43057f60c5c8` waits for the actual keyboard clamp and uses a 1.95° return after the small inward sample to clear the finite strip with the retained smoothing/deadband. It keeps the same strict 100-ms cancellation oracle. Capture sidecars now carry invocation/head/PID labels. Product source and product APK are unchanged. Affected `assembleDebugAndroidTest` / `lintDebug` passed with 7 executed and 48 up-to-date tasks; the metadata analysis limitation remains.

Corrected invocation `w41-window01-edge-4db5347-02` passed all six methods in 146.932 s. All six durable report bindings and scene/raw-source/settings/tab cleanup checks passed. All fourteen reached PNG/sidecar pairs are current, source/run/PID-bound, native 480×640 captures. The runner's terminal `OK (6 tests)` supplies the result; outer exit0 and runner code-1 alone do not.

| Observation | Actual result |
| --- | --- |
| Legacy migration | All nine combinations; one native step, displayed/effective agreement and recreation without repeated migration; min/max arrow geometry and dim states pass |
| Boundary/corner replay | 72 rows; maximum input-to-draw 38.435 ms and sample-receipt-to-draw 19.708 ms |
| Both hidden strips | Small inward motion continues page scrolling; off-screen taps reject; visible return stops/restores cursor in 49.988 / 67.737 ms; native horizontal tab keys still switch |
| Native page rates | 119.741 / 236.888 / 354.341 px/s for requested 120 / 240 / 360, with native/DOM agreement |
| Native gain and persistence | X displacements 112.520 / 150.027 / 187.533 px at 1.5× / 2.0× / 2.5×; recreation retains 2.5× and 360 |
| Long visible list | Actual list Y 51→62 during return; covered page stays Y=0; list stops on visible return |
| Keyboard and cancellation | Keyboard stays `(0,440,480,200)`; page focus/text/submission, full-screen boundaries, timeout, More/Settings, pause/resume, tabs/close/Refresh and endpoint/lateral checks pass; all measured stop observations ≤82.362 ms |

Originals and metrics are retained under `window01-edge02-originals/`, `window01-edge02-summary.json` and `window01-edge02-metrics.json`. These times include software observation cost and do not measure optical latency. Raw replay and real local-page effects do not establish wearer comfort or a new physical gesture direction.

Affected retained invocation `w41-window01-retained-4db5347-01` passed five methods in 44.568 s on the same installed source/APKs: both `NativeInputInstrumentedTest` methods and the existing `approvedGeometryAndAddressEditingStayBlackAndRecoverable`, `fourLiveTabsAndPadGesturesKeepCurrentNativeEffects` and `nativeDpadAdmissionPreservesScopeAndTabEffects` methods. These verify actual raw-pose native button/key effects, single versus double tap, native field focus/backspace/Enter/password/Done, the complete one-row toolbar and black geometry, native pad admission/no-wrap/current-tab effects, and real native caret/text preservation. The retained keyboard report records 105 native key dispatches, maximum 3 ms, and successful raw-source/tab cleanup. Three recorded main-window raw aiming targets reached the existing eight-pixel criterion. These results and thirteen additional 480×640 captures are retained in `window01-retained01-originals/`, with the terminal and summary next to it. They do not establish a new physical wearer-facing direction or comfort result.

| Native Settings image | Displayed values |
| --- | --- |
| [Default](images/issue-41-settings-default.png) | 2.0× / 240 px/s |
| [Minimum](images/issue-41-settings-minimum.png) | 0.1× / 10 px/s; left arrows dim |
| [Maximum](images/issue-41-settings-maximum.png) | 6.0× / 1,200 px/s; right arrows dim |

These unmodified native-size images were visually inspected. The one-row Close/Back/Forward/Refresh/address/star/tab-count/More toolbar and black palette are retained. [Image provenance and hashes](rg-pointer-native-images.json) bind the original bytes to the run, source/APKs and physical device. No model-generated image or optical-camera claim is involved.

At `2026-10-13T10:48:05.944842Z`, the Worker returned RG and 39030 to Manager. Both own app/test packages were force-stopped; no owned RG process or instrumentation remained. Only the newly owned fixture PID1167187 was verified and stopped with SIGTERM; it became absent and the assigned bind was free. Actual power was Awake and the built-in 480×640 display ON, following the current live-use directive without sleep/relock. No ADB/adbd restart, fallback repair, data clear/uninstall, shared cleanup or other-owner evidence change occurred. `resource-return-window01.json` retains the actual result and explicit return; no device or fixture operation followed it. Host build locks are released.

The final documentation/images successor has the same `app-rg/src` tree and APK bytes as native source `4db5347`; the external final binding records the exact published SHA/tree and equality. The 69 executed JVM cases are retained from identical production/JVM-test sources, not presented as another execution at a documentation commit. Fresh native evidence is eleven methods, 6+5, and its original two failed methods remain disclosed. Independent review/acceptance and canonical todo/attempt accounting remain Manager-owned; attempt1 is unconcluded. Phone checks: **REMOVED_FROM_SCOPE_NOT_PASSED**.
