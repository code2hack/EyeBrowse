# RG-local browser/editor qualification — issue #28

Worker: Worker-#28. Assignment revision 2; I28-T01/T02/T03, attempt 1.
Device execution: 2026-10-09 Asia/Shanghai. This is an isolated qualification report for independent review.

## Recommendation and authority

**Positive, bounded recommendation:** the installed System WebView and a direct public native editor path are viable for the declared owned fixture set on the tested RG. The qualifying presentation path uses fixed app-applied CSS, black loading backing and disabled native View focus highlighting. Native editing, navigation, rendering, networking and ordinary lifecycle checks passed on that path. Independent acceptance and the downstream dependency disposition remain with Manager/Reviewer.

`FORCE_DARK_ON` alone is **nonconforming**: it produced `#121212` primary/field backgrounds, a historical `#343434` dynamic fill and changed vector color. Its classifier passing means the observation ran, not that this configuration passes SPEC9. A prior CSS configuration also failed the first live page after empty startup (`#101010`); the final public View setting corrected that observed failure without changing the pixel oracle.

Authority: [issue #28](https://github.com/code2hack/EyeBrowse/issues/28), [SPEC D2.2](https://github.com/code2hack/EyeBrowse/blob/6750d3d2eea6e2eebc9b5a3ffd4370fb4bbb2abd/SPEC.md), [normative HUD H1.2](https://github.com/code2hack/EyeBrowse/blob/6750d3d2eea6e2eebc9b5a3ffd4370fb4bbb2abd/docs/design/v0.0.2-rg-hud.md), [ADR-0001](../../decisions/0001-kotlin-only-first-party-code.md), and the [Planner's bounded public-presentation disposition](https://github.com/code2hack/EyeBrowse/issues/28#issuecomment-6067182885). The source branch starts at `86eb903ac7f94102e243e20d555dce3521954614`; current requirements were read at `6750d3d2eea6e2eebc9b5a3ffd4370fb4bbb2abd`. Current main governance was refreshed separately, without merging protected articles into this implementation.

D2.2 supersedes the earlier local original-target/ABA/reentrant requirements. The final probe uses current native focus without document/element/selection tokens, input generations, key queues, remote frame prerequisites or a parallel editor protocol. Earlier evidence is retained as history. Production HUD, QWERTY/head input, gestures, tabs, bookmarks, QR and Settings remain outside this probe; HUD-G1 is not resolved here.

## Actual target and engine

| Observation | Measured value |
| --- | --- |
| Hardware | RG-glasses, physical serial `1906092617103125` |
| Android / ABI | Android 12 / API32 / arm64-v8a |
| Firmware fingerprint | `Rokid/glasses/glasses:12/SKQ1.240613.001/1.26.011-20260929-150201:user/release-keys` |
| Security patch property | `2024-07-05` |
| Physical canvas | **480 wide × 640 high**, portrait, rotation0 |
| Density | Physical240; existing override204, unchanged |
| System WebView | `com.android.webview` `95.0.4638.74`, versionCode463807403 |
| Provider eligibility | Sole installed/enabled valid/preferred/current provider; minimum target31, minimum versionCode463807403; multiprocess enabled |
| Probe | `com.code2hack.eyebrowse.rglocalprobe`, min32 / target35 / compile35, debug version `0.0.2-probe` |
| Theme | `Theme.Material.NoActionBar`; black window/root/address/status/keys and WebView backing, light text; immersive ordinary application window |

The provider package's installer was null and no Play-based provider update route was observed. An eligible update must satisfy the OEM-configured provider/signature/version/target requirements; the realistic route is an OEM-approved signed provider or firmware update. **No available update, package compatibility, installation/recovery procedure or ongoing security support was qualified.** The existing Android/provider/device settings were not changed. This old-provider result does not certify production maintainability or security.

RG reached the host's owned HTTP fixture over the actual Tailscale network route; the server recorded RG's source peer. The fixture is a host test service, not the Phone browser or a Phone rendering/link runtime. An independent HTTPS request to `https://example.org` loaded `Example Domain` with no recorded main-frame error. Cleartext HTTP is enabled only in this separate experimental package for the fixture. No certificate bypass, native JavaScript bridge or private API is used.

## Probe and evidence binding

`experiments/rg-local-probe` is a separate Kotlin Android application; its child `fixture` is a small Kotlin/JVM fixed-file HTTP server. The only product dependency is existing `core:browser` address validation. Production Phone/RG sources and identities are unchanged. No `core:link` or historical renderer/editor asset is included in the probe.

The probe has one WebView, address/Open/Back/Forward/Reload controls and A/B/Space/Delete/Enter/Done keys. Nonfocusable native buttons retain the current editor. Web input creates a fresh public `InputConnection` for each immediate action: `commitText` for text and native Down/Up key events for Backspace/Enter. It does not store page values or passwords. Native address entry edits its own `Editable`. Done hides the local keys without submitting. Keyboard opening avoids a repeated `WebView.requestFocus`, which selected the first field on provider95 during development.

The public [InputConnection API](https://developer.android.com/reference/android/view/inputmethod/InputConnection) defines text commitment and current-focused-view key delivery; method returns do not establish a webpage effect. These tests independently observe actual fixture values, focus, rendered output and form events after native actions. JavaScript only prepares selection/scroll visibility or observes the fixture; it never assigns expected values to pass an editing test.

| Exact final artifact | SHA256 |
| --- | --- |
| App APK | `84c7d81995a6116720cb000abe09b589dcb76068827be6b2a475605de78bda56` |
| Android test APK | `b65210ecc9096e0f873c1dd3145060b943e6ad634d78f03ae3897f0a597eb96e` |
| Fixture JAR | `a93115086125bc42d4429ca5dbe1fcbff8ec885475337d6aaaf18fbb819f1837` |
| Probe/settings source manifest digest | `51d88f6ec06551751bd9efdb37c683ae1a061325a0e3848159651b30109226e5` |

Both installed APK hashes matched the host files before the final cycle. `final-candidate-hashes.json` records every probe/settings/fixture source hash, build-log hashes and the installed match. The source digest is SHA256 of the sorted source-hash dictionary serialized as compact sorted-key JSON. The exact remote commit is supplied in Manager's candidate report; these binary/source bindings identify the device-exercised content independently of report-only edits.

## Native functional and lifecycle results

All methods below are in `com.code2hack.eyebrowse.rglocalprobe.NativeQualificationTest`.

| Executed method | Actual check / result |
| --- | --- |
| `fourEditorClassesUseActualNativeKeys` | Native field taps and A/B/Space/Delete keys in text, password, textarea and plain contenteditable; selection setup followed by actual B replacement; current focused field entirely in the visible viewport; lossless visible-field PNG for each class. Text Enter invoked one harmless prevented form submission; password outside the form did not submit; textarea/plain editor created native line breaks. Done preserved document/content/submission count. PASS. |
| `currentFocusViewportAndPauseUseOrdinaryNativeBehavior` | Ordinary text→other focus change; key effects followed current focus. WebView372px with keys and532px without keys; document/value/current focus remained across resize. Actual ActivityScenario CREATED→RESUMED preserved the live page and allowed another native edit. PASS; this is separately identified from device sleep. |
| `localNavigationAndAddressUseActualControls` | Native page-link tap and actual Back/Forward/Reload controls changed real documents/history. Native address key changed the draft; rejected address retained draft/page. PASS. |
| `boundedWorkloadMeasuresMemoryAndActualEffectRendering` | Twelve sequential actual load/focus/key/resize cycles across the four editor classes, independent value observation and public visual-state callback. PASS within declared100ms dispatch /2000ms effect-and-visual bounds. |
| `darkOutputAndMediaAreClassifiedFromRealPixels` | Actual authored-dark/light document, field interior, dynamic script action and external SVG image colors measured in lossless480×640 captures. Qualifying path PASS; values below. |
| `automaticEngineDarkeningIsMeasuredSeparately` | Diagnostic `FORCE_DARK_ON` path measured `#121212` primary output. Classifier executed successfully; **configuration FAIL**. |
| `appLoadingPresentationUsesBlackFrames` | Owned empty native startup, three samples during an actual pending fixed HTTP response, then the styled live response. Native/page predeclared pixels remained exact black. PASS for these sampled frames. |
| `publicInternetRequestRecordsTheActualOutcome` | RG WebView actually loaded public HTTPS example.org, no main-frame error. PASS for this request, not arbitrary networking/site compatibility. |
| `authorizedRealSleepPreservesDocumentAndNormalEditing` | Separately granted actual device sleep/wake; public interactive/pause/window state, same live fixture/value/current field, then actual native B key after wake. PASS. |

`final-native-test-06.txt`: runner `OK (9 tests)`,34.689s. Eight normal methods executed; the cycle method was deliberately skipped without its authorization argument. The same exact APK/test pair then ran the cycle method alone: `final-physical-cycle-test.txt`, `OK (1 test)`,21.618s. The negative dark classifier is not counted as a conforming presentation path.

The password-open PNG shows two masked bullets while local keys remain open. Visible text, textarea and plain-editor PNGs show actual inserted dummy content. Values/passwords are not transported to another app or logged as normal payloads. These screenshots are rendering evidence, not optical readability or wearer ergonomics.

Phone independence uses the authenticated Owner observation routed by Manager: **“Force-stopped just now; Internet available”**, recorded at `2026-10-08T18:44:51.027471+00:00`. “Just now” is the human observation, not an invented exact stop timestamp. All final RG checks occurred subsequently. No Phone ADB operation or independently instrumented Phone stop-state claim is made.

The final cycle consumed `MGR-V002-W28-ADDITIONAL-EXACT-CYCLE-01` exactly once. The controller verified physical identity, owned foreground, initial Awake and exact hashes, issued one explicit ADB Sleep, observed Asleep, then issued one Wake and observed Awake. The test independently observed noninteractive state, an actual Activity pause/resume, unchanged live document/value/current focus and a subsequent native key effect. Both commands used the primary Tailscale5555 route; USB recovery was not needed. This is **agent-triggered actual device evidence**, not Owner physical-button action. The earlier granted cycle and its older APK remain historical; no third cycle is implicitly authorized.

## Strict black rendering and limits

The final path sets `FORCE_DARK_OFF`, applies one fixed presentation-only stylesheet through public `evaluateJavascript`, waits for a public visual-state callback and reveals the live WebView. The fixed selectors cover ordinary HTML containers and form/editor elements with `background-color:#000!important;color:#fff!important`. It does not assign values, replace the document, install a script engine, invert media or style `img`, `svg`, `canvas` or `video`. Source fixtures retain their authored white/light/dynamic declarations.

`open` shows the black backing immediately when starting its request; `onPageStarted` also covers ordinary page starts. Alpha0 retains normal native focus while loading; settled color checks explicitly require alpha1 and the real applied stylesheet. There is no overlaid image standing in for a page. The fixed `/loading.html` route serves the same author-light bytes after a ten-second response delay, making actual pending-load frames observable rather than claiming settled screenshots are transition evidence.

The public [View focus-highlight setting](https://developer.android.com/reference/android/view/View#attr_android:defaultFocusHighlightEnabled) is disabled on this WebView. With the default highlight enabled, the first live response after empty startup failed at `#101010` and the media was lightened. The one-setting correction passed the same pixel oracle. This ablation supports attributing that rectangle to native focus highlighting; no provider-internal cause is claimed. APIs used here exist on the actual API32 platform; evaluateJavascript, input connection and visual-state callbacks ran on provider95. Modern provider/AndroidX algorithmic-darkening feature availability was not assumed. Android's [WebView darkening guidance](https://developer.android.com/develop/ui/views/layout/webapps/dark-theme) describes conditional mechanisms, so actual pixels determine this result.

| Declared region/state | Final observed RGB |
| --- | --- |
| Authored-dark blank page,450,620 | `#000000` |
| Authored-light primary blank,450,620 | `#000000` |
| Authored-light field interior,90% width /50% height | `#000000` |
| Dynamic container interior,90% width /80% height, after actual script button | `#000000` despite author's inline `#ddd` request |
| External SVG image center | Original `#e04040`, preserved |
| Native startup/loading/ready edge,479,20 | `#000000` |
| Startup/loading/first live response,450,300 and450,620 | `#000000` |
| Diagnostic automatic darkening primary,450,620 | `#121212`, **FAIL** |

Regions/fractions were chosen in test source before output; DOM bounds are mapped through the current View's width/location rather than deprecated `WebView.scale`. Initial authored-light, field, dynamic, media and loading outputs are separate observations. Inline SVG and image-vector colors remain useful in the final screenshots; text and controls remain light and readable in those native pixels. Three loading samples and the first ready sample show no observed app-origin white flash; this does not prove every compositor frame or every future transition.

The declared set is ordinary text/password/textarea/plain-contenteditable HTML, simple navigation/forms/scripts, authored dark/light containers and inline/external SVG media. Raster photographs, video/canvas, background images, shadow DOM, cross-origin frames, CSP restrictions, hostile inline-important styling and complex custom editors were **not qualified**. A fixed CSS selector cannot establish universal recoloring or arbitrary-site compatibility. No required fixture failure remains hidden under that limitation list; initial failures and their corrections are retained. Production must qualify its actual supported sites/media and HUD transitions separately.

## Workload and host verification

For twelve final samples, native key dispatch was2–17ms; independent fixture effect plus a public visual-state callback was30–178ms. The callback establishes readiness for a subsequent draw, not measured optical photon time. App+instrumentation PSS was113731→120545KiB; per-sample114599–120033KiB, native allocated16747136–16848920bytes. Android's isolated renderer memory was not included. The short workload does not establish leak freedom, long-running stability, thermal behavior or battery life.

Host gates use JDK17, the existing SDK, the shared build flock, `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`, and explicit debug/unit/lint tasks. Probe debug, Android test APK and fixture builds succeeded. Probe/fixture JVM test tasks have no source; their meaningful coverage is real-device Android instrumentation. The common affected-consumer gate covers browser/link unit tests, both Phone/RG debug/unit/lint and instrumentation-APK builds plus Python tools tests. Detailed executed/cached counts and exact committed-source bindings are in the final host report.

The earlier executed common gate had458 JVM cases (browser14, link154, Phone194, RG96), zero failures/errors/skips, and40 Python tests passing. Lint reports had zero errors with Phone10/RG17/probe13 warnings. Console Kotlin2.2 metadata versus lint2.0-reader diagnostics limit Kotlin static analysis despite Gradle task success; a successful lint task is not complete Kotlin analysis. This limitation is retained for review, not waived or repaired by an unrelated toolchain change.

## Reproduction and evidence package

From the exact assigned/reviewed checkout, with a Manager-supplied shared build lock and sufficient memory:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/home/code2hack/Android/Sdk \
timeout 900s flock "$HOST_BUILD_LOCK" ./gradlew \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g --console=plain \
  :experiments:rg-local-probe:assembleDebug \
  :experiments:rg-local-probe:assembleDebugAndroidTest \
  :experiments:rg-local-probe:lintDebug \
  :experiments:rg-local-probe:fixture:installDist
```

Reserve RG through Manager before installation or instrumentation. Reverify its physical serial/model before every mutation using the approved guarded ADB route; do not change a working server/listener to switch transport. Install the two debug APKs without clearing data. Start the fixture on the specifically owned host interface/port with:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
experiments/rg-local-probe/fixture/build/install/fixture/bin/fixture \
  "$FIXTURE_BIND_IP" "$FIXTURE_PORT" experiments/rg-local-probe/fixture/pages
```

The server accepts a numeric existing loopback/private/Tailnet interface, a port1024–65535 and fixed files only; two threads, queue8, backlog4, max128KiB/file. It does not reflect/upload field values. Logs contain fixed routes/counts/peer only; queries are excluded. Stop only the verified owned PID after tests and confirm its listener closed.

With the reserved, verified endpoint and actual RG-reachable fixture URL, run the normal methods under the mission's guarded180-second ADB command:

```sh
adb -s "$ADB_SERIAL" shell am instrument -w \
  -e fixtureBaseUrl "$FIXTURE_BASE_URL" \
  -e class com.code2hack.eyebrowse.rglocalprobe.NativeQualificationTest \
  com.code2hack.eyebrowse.rglocalprobe.test/androidx.test.runner.AndroidJUnitRunner
```

The sleep method skips in that command. A **separate explicit cycle grant** is required to select `NativeQualificationTest#authorizedRealSleepPreservesDocumentAndNormalEditing` with `-e deviceCycle authorized`. Wait for that exact candidate's new `DEVICE_CYCLE_READY`, recheck identity/owned foreground/Awake, issue one public explicit Sleep then Wake, observe both states and preserve bounded wake cleanup. Do not blindly rerun/toggle or infer a grant from the method name. The recorded final one-off controller, authority, hash manifest and power evidence are in the mission artifacts.

Tests use elapsedRealtime for bounds and uptimeMillis for native touch timestamps; fixture observations have3-second callbacks and ordinary10-second waits, public Internet30seconds, device sleep120seconds/wake30seconds. Window focus is checked before captures. Screenshots are lossless native480×640 PNGs saved only in the probe's scoped external files. No user page or actual sensitive password is captured.

Manager supplies the durable run-001/issue-28 evidence directory to Reviewer. Core review artifacts:

- `final-candidate-hashes.json`, `final-probe-source.tar.gz`, final app/test APKs and build logs.
- `final-native-test-06.txt`, `final-native-observations.txt`, fixed fixture log and `final-provider.txt`.
- `final-physical-cycle-test.txt`, `final-physical-cycle-observations.txt`, `final-authorized-rg-cycle.json`, three public power snapshots and the one-off guarded controller.
- Eighteen `final-screenshots/rev2-*.png` and `final-screenshot-hashes.json`, including each visible editor, masked password, dark/light/dynamic/media, empty/loading/ready and resumed fixture.
- Retired revision1 source/APKs, earlier native/adapter results, nonblack automatic darkening, focus/layout measurement failures and native-focus-highlight failure archives; these are historical, not substitutes for current evidence.
- Common/final host logs, XML/lint summaries, exact remote/source binding and `final-rg-safe-release.json`.

## Simplification and cleanup

The reconciliation removed the custom target-fencing adapter, queued/generation/race tests, remote assets and link dependency rather than imposing retired remote semantics on local input. One ordinary native writer remains. Repeated focus requests and extra test retaps were removed. Presentation remains one fixed sheet plus standard View/WebView settings and loading callbacks; no production styling subsystem or engine/provider change was added.

After the final cycle/captures, both separate probe packages were force-stopped with physical guards; no own process/instrumentation remained. The exact owned fixturePID was verified by command/cwd, stopped with SIGTERM, and its port confirmed closed. No sensors or ADB forwards/reverses were used. At `2026-10-08T19:38:27Z`, primary connectivity was reverified, RG was Awake with Tailscale foreground, and provider95 was unchanged. Installed probe/data/evidence and product data/settings/connectivity were preserved. The device was released to Manager; no direct peer handoff or RG screen-off cleanup occurred. All later work is host/documentation only unless Manager grants a new reservation.
