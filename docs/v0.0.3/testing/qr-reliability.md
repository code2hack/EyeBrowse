# QR reliability — issue #42 / V3-A04

Worker: Worker-#42. SPEC D3.1 §5, HUD H3.1 H3-C06 scanner half, P3.1 /
V3-02; assigned integration baseline `74798ae0d47e009a0a145c21b0ea116cb5fee94b`.
The Owner also directed diagnosis and correct rendering of the exact privately
supplied Paseo link encountered during live QR testing.

This is an implementation/evidence handoff, **not full optical acceptance**.
The historical intermittent recognition cause is not established. Known-short
and known-long repeated optical qualification remains **0/10 and 0/10**.
Actual live Paseo acquisition, same-input decoder evidence and the separately
demonstrated browser presentation defect are described below.

## Demonstrated corrections

### Decoder and acquisition

The existing CameraX1.4.2 / ZXing Java3.5.3 stack remains. A preserved real
640×480 analyzer PNG returned no result through production HybridBinarizer
(normal, pure and inverse), while the same Java QR reader with
GlobalHistogramBinarizer decoded those **same acquired pixels**. The bounded
fallback now recovers that exact frame: HTTPS,243characters, address accepted.
No private payload or actual QR pixels are included in this repository.

An owned low-contrast code in a dark 640×480 frame reproduces the threshold
limitation without copying the private QR. `threshold-red.xml` records9 focused
tests/1 failure before the fallback; the corrected9 decoder tests pass. The
original normal and pure paths remain first, followed by the standard global
threshold on a miss. The same bounded paths also try inverted luminance.
A separate owned reduced-contrast inverse test failed before polarity support
(`decoder-red.xml`,7 tests/1 failure) and passed afterward. These findings do
not establish the cause of the Owner's earlier wearable situation or guarantee
blurred, clipped or poorly rendered codes will decode.

Other controls cover invalid payloads, blank/noise, reuse across frames, padded
rows, no final-row padding and representative short/long QR rasters in a
640×480/stride672 frame at0/90/180/270°. Synthetic rasters are supplemental,
**not optical proof**.

The default fill preview clipped the camera field inside the existing448×272
slot. `FIT_CENTER` shows the full field in that same slot with black surrounding
pixels. No resolution, camera selection, exposure/focus, provider or permission
change was made. Its effect on repeated optical reliability remains unmeasured.
[CameraX preview scale contract](https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType#FIT_CENTER).

The camera still supplies YUV420. Android guarantees Y-plane pixel stride1;
U/V interleaving is irrelevant to this luminance-only path. No speculative
provider/format conversion was added.
[Android YUV420 layout contract](https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888).
The analyzer duplicates the Y buffer before reading. Every acquired image closes
in `finally`; cancel clears the analyzer, unbinds only its own use cases and
shuts down its executor. Internal read-only camera/executor observations and an
optional frame observer support focused instrumentation; normal scans retain
no images or payload logs.

### Loaded page hidden by opaque presentation overlays

After the Owner scanned a PC-screen QR and chose Open, the exact destination
was public HTTPS at app.paseo.sh:443, with connection information in a private
fragment. The Owner later supplied the full link explicitly; private comparison
confirmed it equals the243-character decoded link. Only private storage holds it.
The Owner separately reported a full-page direct-phone load and withdrew the
broad proxy-access hypothesis. That is an Owner observation, not an agent-run
Phone test or reinstated Phone gate.

RG TCP443 succeeded; Spark direct and configured-proxy root/script requests
returned HTTP200. Those route observations alone did not prove RG page success.
Actual RG WebView reproduction then reached READY with a complete DOM containing
welcome and host-confirmation controls, while its screenshot was black apart
from the pointer. No captured resource/native/SSL error or JavaScript exception
explained that blank screen. WebView age or missing modern APIs alone was not
used as a diagnosis.

Same-page background-only ablation demonstrated the app defect: the existing
all-div black override turned an authored transparent fullscreen overlay into
an opaque layer above the loaded content. Removing that background override
made the page visible immediately, but left light text over original light
panels. The ablation is **not** a final rendering pass.

The existing presentation route now preserves authored background alpha while
recoloring supported non-media fills black and text light. It observes ordinary
added containers and class/style changes, sampling authored alpha with its own
stylesheet temporarily disabled within the synchronous script. Its own writes
are excluded from the observer cycle. Native/root black backing remains; no
site-specific identity, DOM replacement, field-value access, input bridge,
media inversion, provider replacement, TLS bypass or network/proxy change was
introduced. This remains a supported-page presentation route, not universal
recoloring of every CSS/media construct.

Actual production APK
`195971ac93dd0309cb9e95695e0c862e330f7c77afd277f097ec1ff5aba7948b`
visibly rendered readable welcome/host-confirmation content on black with the
ablation disabled. One ordinary native scroll moved the inner connection panel
from scrollTop0 to172.55CSSpx and brought enabled Connect inside the viewport.
Native activation led to readable Add a project/Open a folder and Import session
UI. No project/session execution was initiated. Original before, ablation and
corrected screenshots/DOM/source/test/APK bindings remain private and separate.

The compact footer's Star/Sponsor/Community text bands overlap the Import
session band in the authored layout too. A synchronous same-page comparison
with the presentation stylesheet enabled and disabled found exactly equal
rectangles, positions and z-index values for those controls. Each tested center
hit its own control. No EyeBrowse-caused geometry change or center obstruction
was observed; this is a bounded layout observation, not a site redesign or
proof of every possible hit point. The original diagnostic transport stream
interrupted before its JUnit terminal result; the separately recovered fresh
geometry output is observational evidence, not a claimed JUnit PASS.

The owned `presentation.html` regression checks transparent fullscreen layers,
a dynamically added layer and changed opaque fill, actual heading pixels,
black page/toolbar pixels, unchanged red canvas media and a native button effect.
It passed on that production build. The exact-page footer/layout comparison and
existing native regressions are recorded in the final check results below.

## Two purposes, one scanner

More → QR scan preserves address validation, decoded preview, deliberate Open
in the selected tab, Try again and Cancel. Unsupported addresses never navigate
or configure/import a proxy. HTTPS text does not determine scan purpose.

`LocalBrowserActivity.scanProfileQr(onResult)` is the #44 integration seam.
Explicit PROFILE purpose shows “Add a new profile” and uses the same
`CameraQrScanner` / `QrDecoder`. A decoded payload returns unchanged after camera
release and scanner dismissal; it does not navigate or show webpage Open.
Null reports ordinary cancellation; the callback clears before delivery.
Pause releases scanning and retains the purpose for deliberate Try again.
Import validation/fetch/storage and the Settings/Proxy list belong to #44.

No Phone code, native keyboard/tab behavior, system settings, provider, proxy
configuration or protected article was changed. Phone checks remain
`REMOVED_FROM_SCOPE_NOT_PASSED`.

## Bindings, observations and checks

RG identity was initially verified at assigned tailnet TCP5555, then reverified
at the Owner-supplied live endpoint100.87.122.122:44615 before mutations:
serial1906092617103125 / modelRG-glasses; Android12/API32,
build SKQ1.240613.001 release-keys, WebView com.android.webview95.0.4638.74,
480×640 portrait/density override204. Existing CAMERA permission and app/site
data were preserved. The Owner was wearing RG during live observations; no
automatic display sleep was applied.

At the Owner's later route-priority correction, a bounded ordinary connection
verified the same serial/model on100.87.122.122:5555 again. That listener was
already working; no tcpip/adbd restart was needed. Final scanner checks and
cleanup used5555.44615 remains a recovery fallback. An unsupported
`dumpsys activity instrumentation` command is retained as unsupported and
supplies no instrumentation-absence proof.

Host spark uses JDK17/SDK35. Every Gradle invocation uses the Manager's shared
host-build flock, `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`, with
available memory observed. Baseline build,67 RG JVM tests (9 decoder tests),14
browser-core tests and debug app/instrumentation assembly passed. Lint exited
successfully with7 warnings, but logged Kotlin metadata2.2/2.0 analyzer
compatibility diagnostics; this limits lint coverage and is not clean analysis.
No toolchain change was attempted. Older v0.0.2 results are context only.

### Preserved live chronology (2026-10-13 UTC)

| Observation | Actual result and limit |
| --- | --- |
| Initial camera | Advertised missing-front validation retries, then camera0/back opens and frames arrive; not an invocation-failure diagnosis. |
|08:41:52 baseline | Installed APK3c908bce… (historical source unverified) delivered frames without raw result/preview; usable complete-code quality unestablished. |
|08:44:52 candidate | APK71500829… also delivered frames without raw result; not evidence of address-policy rejection. |
| Early metadata diagnostic |3 acquisitions,12 early sampled frames each plus5s decoding opportunity; advancing timestamps/STREAMING; no raw result/camera error.640×480/full crop/rotation270/Ystride640/pixel1/buffer307200; fixed focus(AF[0],minimum distance0); sampled Y7…18,means10.29…11.42. Camera and executor released each cycle. |
| Misidentified short request | A relayed readiness answer was misunderstood; the first “short” invocation retained one frame then timed out. The Owner confirmed the original Paseo target remained. Original request/result and target-uncertain provenance are preserved; it is not a known-short trial or demonstrated product failure. |
| Later private inspection | Owner explicitly authorized inspecting that retained frame and the original Paseo QR. It contains a tilted QR, luminance1…255/mean44.89. Independent ZXing-C++ full/crop/enlarged variants and production missed that frame. Earlier dark samples are separately timed, not evidence this later QR was absent. |
| Fresh preview | One snapshot had the dense QR upper edge cut at the viewport boundary; production/independent decoding missed it. This supports inspecting completeness/detail before blaming the decoder alone. |
| Fresh paired acquisition | Camera delivered27 frames; production raw decode243/HTTPS/accepted, no camera error; release verified. The retained first frame was Java GlobalHistogram-readable but production-Hybrid-unreadable. Independent ZXing-C++ decoded a different retained last frame that Java variants missed. Live success used another frame, not a claimed exact match to the last retained PNG. |
| Owner PC screen | Owner reported immediate QR success and chose Open. This is qualitative Owner evidence, not ten/ten or measured first-usable-frame latency. Terminal-rendering/white-line suspicion remains a hypothesis, not a demonstrated historical cause. |
| Exact page | Loaded DOM/black screenshot; controlled background ablation; corrected readable production page and native scroll/Connect flow as above. QR decode, address acceptance, navigation and page rendering are separate stages. |
| Host/test assembly failure | A test-only incorrect namespace failed assembly. One stale APK selector returned0tests. Original failure and stale binding limitation retained; that invocation is NOT PASS. Corrected test assembly later succeeded. |
| Transport interruption | A later native invocation returned device-offline before launch: NOT RUN. One ordinary connect44615 plus actual get-state/serial/model checks restored access; no server/trust/network repair or blind replay. A separately named native invocation preserves both records. |

On production195971ac, the owned presentation test and3 controlled purpose/Open
tests passed. Existing strict live black/dynamic-fill/media/native-field/Done,
pause/resume, hidden-input/loading/error recovery and first-party transition
black-backing tests passed. The four-native-editor test passed after supplying
its existing invocation/head arguments:117 key dispatches, maximum4ms, with
Unicode, selection, case, Done and cleanup assertions retained. The initial
argument omission stopped that method before its body; it was not a product
failure or PASS.

The HTTP-error controls test initially tapped disabled Back before WebView
committed the failed history entry. Private event observations captured the
error with history size1/Back disabled, then size2/Back enabled. After the test
waits for that ordinary enabled control, actual Back reaches visible READY;
the unchanged Forward/HTTP404, Refresh and address-recovery assertions pass.
No production navigation change or assertion weakening was needed. Original
failure, diagnostic and corrected invocation records remain separate.

Earlier candidate71500829 additionally passed actual three-cycle
acquisition/release, profile cancel/pause/resume/retry, real bind error and
permission-denial/Retry/Allow recovery. They remain attributed to that earlier
APK, not silently inherited by a later candidate.

The final production APK is byte-identical to the successfully observed
195971ac build above, with production source from implementation commit
`2f4c75b32ee9cf1dcea3064e6e775a444fbd91c0`. The final public test APK is
`44c71b96e6826e7befeccea886ddd376d318e4c1b818a9022aa6fb9accd6778b`;
its DEX excludes the archived private page diagnostic. The only subsequent
existing-test correction is the enabled-Back wait described above. Ordinary
per-file source hashes bind the built package to the final candidate tree.

| Final public-package execution on5555 | Outcome |
| --- | --- |
| `actualFrameLayoutArrivalAndExecutorRelease` | PASS:3 camera0 acquisitions,83 actual frames each, advancing timestamps, no camera error, camera availability and executor termination verified. No raw decode in this current unqualified scene; no QR-quality or timing PASS. Default metadata only; no images retained. |
| `transformedQrInputsUseProductionValidationPreviewAndCurrentTabActions` | PASS:13 controlled raster/Y-stride/address cases, rejected schemes, preview/Cancel, deliberate Open and selected-tab behavior. Supplemental software evidence. |
| `cameraBindFailureReportsAnErrorAndReleasesResources` | PASS: actual unavailable-selection error and cleanup. |
| `repeatedProfileScanCancelAndPauseResumeReleaseTheCamera` | PASS:3 profile open/cancel cycles including ordinary pause/resume and deliberate retry; no stuck/competing camera. |

These4 methods have terminal JUnit success records. The preceding44615
invocation ended with an incomplete stream and failed result read: **NOT PASS**.
Its separately recovered fresh three-cycle metadata remains observational
evidence; the new5555 invocation has its own binding and terminal result.
Final permission denial/retry was not repeated; the earlier71500829 result is
separately attributed above.

Reserved operations then ended: own app force-stopped/PID absent, camera and
analyzer/executor released, own verified Java fixture stopped and no39030
listener remained. Power and display observations recorded Awake/ON for the
worn RG; no sleep/relock/USB cleanup was issued. A first cleanup check expected
a missing power-dump field and did not conclude return; its original is
preserved, with the separate display dump supplying the actual ON observation.
RG and100.92.81.33:39030 were explicitly returned through the Manager route.
Remaining documentation/commit/push work uses the host only.

Manager concludes canonical todo accounting; no failure counters are changed
by this document. Independent exact-candidate review and optical disposition
remain separate from these implementation results.

## Repeatable optical fixture and remaining limit

[The fixture index](qr-fixture/index.html) links two owned SVG targets generated
by ZXing3.5.3 with black modules, white background and four-module quiet zone.
Regenerate using `QrDecoderTest`; output is in
`app-rg/build/qr-optical-fixture/`. `QrFixtures.kt` binds both software and optical
requests:

| Target | Owned decoded value |
| --- | --- |
| short | `https://example.com/` |
| long | `https://subscription.example.invalid/api/v1/client/subscribe?token=owned-dummy-0123456789abcdef&format=clash&device=rg&source=qr-reliability` |

For a future authorized real display setup, record display/resolution, code
size, distance/angle, lighting, actual selected target and UTC time. Establish
a complete clear code and quiet zone in the camera field. Bitmap injection,
a virtual display or a network listener alone is not optical proof.

With the actual RG reservation and source-bound APKs, invoke once per actually
shown owned target:

```sh
adb -s "$RG_ENDPOINT" shell am instrument -w -r \
  -e class com.code2hack.eyebrowse.rg.QrReliabilityInstrumentedTest#opticalTenCyclesOfDeclaredOwnedTarget \
  -e qrFixture short \
  com.code2hack.eyebrowse.rg.test/androidx.test.runner.AndroidJUnitRunner
# Use qrFixture long only when long.svg is actually displayed.
```

Each configured run performs10 acquisitions/releases, records monotonic first
frame/decode times and expected-payload booleans, and retains first analyzer
PNGs plus `issue42-optical-<target>-results.json`. Inspect first frames for usable
complete codes before claiming the five-second target. The recorded clock starts
at the first analyzer observation and includes capture/copy time; if that frame
is unusable, retain and qualify the result rather than reset the clock silently.
The method stops on failure and preserves partial observations. Never include
it in an unconfigured whole-class invocation or use it on a private target.
Default acquisition diagnostics retain metadata; explicit private capture is a
separate run option requiring actual authorization.

Initial unattended optical work was **BLOCKED_CAPABILITY**: all probed Spark
display outputs disconnected, no accessible aligned rig documented. The later
Owner-present original Paseo target enabled genuine optical observations but
never established the owned short/long scenes. The Owner expressly rejected
substitution with example.com during the live diagnosis; no further target or
alignment request was imposed. Qualification remains **known-short0/10,
known-long0/10**, without waived criteria, invented timing, historical-cause
claim or optical PASS.

Private originals, command results, normal source/APK bindings and useful
failures are retained under
`/home/code2hack/.local/state/eyebrowse/v0.0.3/runs/run-001/issue-42/evidence/`.
Paseo payloads, QR pixels, browser DOM and screenshots are excluded from tracked
fixtures/public reports. Temporary private page-diagnostic source is archived
privately and removed from the candidate; it is not a new production framework.
Review, acceptance and any later optical disposition remain Manager-coordinated.
