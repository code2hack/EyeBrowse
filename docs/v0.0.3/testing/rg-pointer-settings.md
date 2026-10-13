# RG pointer, numeric Settings and strips — issue #41

Worker: Worker-#41

Contract: SPEC D3.1 §4 / V3-A01–A03, HUD H3.1 H3-C03/C04 and retained toolbar/native-input checks. Source starts at integration baseline `74798ae0d47e009a0a145c21b0ea116cb5fee94b`, branch `work/v0.0.3-issue-41`. This record is currently **host verified; actual RG verification pending an exclusive Manager handoff**. It is not ticket acceptance or a release.

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

Local evidence: `/home/code2hack/.local/state/eyebrowse/v0.0.3/runs/run-001/issue-41/evidence/`, including `host-unit-initial.log`, `unit-initial/`, `host-debug-round1.log`, `host-debug-round2.log`, `host-final-precommit-v2.log` and JUnit XML. Extending the raw aiming checks to the new gains exposed an 8.5-px error from the old test generator's fixed deadband compensation; its failures and correction are also retained in `unit-aim-calibration/` and `unit-aim-unconditional-compensation/`. Final source/tree/APK bindings will accompany the pushed candidate and native run.

## Required native verification — pending

`EdgeScrollInstrumentedTest` reuses native touch dispatch, raw quaternion input, the current View tree, and read-only DOM/native scroll observations. It snapshots/restores all four owned input preference keys and tab recovery metadata. Its native checks cover:

- All nine legacy preset migrations, numeric text versus effective gain/speed, one step per native tap, recreation, exact row/arrow bounds, both endpoint states and actual 480×640 Settings captures.
- Both hidden strips, small inward continuation with real page effects, visible return/callback cancellation, rejected off-screen taps and native horizontal tab gestures while hidden.
- Multiple outward magnitudes/holds/corners, slow inward travel and tracking expiry.
- Native page rates at 120/240/360, retained small-target/native-key behavior, keyboard-fixed/full-screen boundaries, actual long visible tab-list scrolling, and covered-page/lifecycle/endpoint cleanup.

Run only after Manager grants the physical RG and fixture. Bind `candidateHead`, `evidenceRunId` and `fixtureBaseUrl` to the exact source and assigned endpoint. Required changed Settings screenshots, actual device/provider facts, executed results and cleanup are **not yet available**. No device or fixture was queried during host preparation. No wearer comfort or physical head-motion claim follows from raw replay. Phone checks: **REMOVED_FROM_SCOPE_NOT_PASSED**.
