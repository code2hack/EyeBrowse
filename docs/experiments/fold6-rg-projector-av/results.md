# PAV-05 — final Fold6 → RG projector investigation

Worker: Worker-#70

Issue: [#70](https://github.com/code2hack/EyeBrowse/issues/70)

Experimental target: `spike/fold6-rg-projector-av`; baseline `9477976678862dda27ccd4fbbe4a66276d8588bf`, tree `1ab0c6618cc7935035e729107d4bb0010e643d1a`.

## Recommendation

Retain the direct two-device capture/decoder/input architecture as an experiment. The integrated projector is a no-go for production replacement: the tested sleep/panel-off cases did not provide usable AV, and PhoneIME editing is insufficient. Locked-but-Awake AV, native software input and two sampled ordinary-app pages worked within the limits below. The selected Phone IME has a retained, actual cropping/editing failure; no usable typing sequence has been demonstrated. Startup still requires authorized shell/ADB bootstrap and private peer provisioning. Lock/panel-off limits and the measured final run are reported below. There is no production migration, release, arbitrary-app, deep-Doze, battery-endurance or quantified optical/acoustic timing approval.

This is a fresh unattended integration investigation after accepted #68/#69, not a restatement of their demonstrations. RG media volume was retained as **8**, set to **0** before owned audio, and restored during final teardown. No wearing, listening, calibration, recording, credential entry, keyguard dismissal, global power/security changes or original browser-store operation was performed. New frame/audio-clock/input evidence is software evidence. Earlier Owner sensory observations keep their original source scope.

## Exact tested system

| Component | Current binding |
| --- | --- |
| Phone | Fold6 SM-F956N, serial R3CX70NHTHK, Android 16/API36, build BP4A.251205.006.F956NKSS4DZH5, user0 |
| RG | RG-glasses, serial 1906092617103125, Android 12/API32, SKQ1.240613.001 release-keys, user0; 480×640 portrait |
| Direct addresses | Phone 10.199.167.157:44367 ↔ RG 10.199.167.154, authenticated TLS1.3 |
| Debug/bootstrap | Existing u4090 ADB server through Spark 127.0.0.1:15037; Phone 192.168.0.193:5555 and RG USB serial. This route was retained throughout |
| External server | Unmodified scrcpy 5.0.1, source `a60891aea193d92e7e5c3942700eca63f9d19a5f`; JAR SHA256 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536` |
| HostR5 APK | `493879dc567dc9fb20029fbb3bdeea4eacfcab621bc476f2de5e631db87e93ac` |
| RG3 APK | `e805103f4d445101439626a1ae678166a7f23bb79feca97cc7cfbe16f861b9c3` |
| Probe4 APK | `68ea9844a562c34075de9dbedfab857c6b66dbbfa333b30b676d97ab50b5d8f7` |
| Installed source | All 57 accepted #68 source-manifest entries matched this integration baseline before tests; installed APK bytes were checked on both devices |
| Browser | Chrome active 155.0.8059.79; factory/hidden system copy 140.0.7339.207 is a separate inventory entry |
| Ordinary app / IME | Settings 16; selected Doubao IME 1.3.6, `com.bytedance.android.doubaoime/.ImeService`, unchanged |
| Owned content | Existing native field/main/nested-list fixtures; PAV66 12-second synthetic flash/1kHz clip, SHA256 `d4ac939c07a8679fa041187a093d46f80407773b3d0dd663a944d8ab4202d678`, looped by HostR5; no RG-local media source |

Stock creates a 480×640/160dpi virtual display, H.264 at an initial 2Mbit/s/max 30fps and mandatory whole-device `output`/Opus 128kbit/s. RG decodes using `c2.qti.avc.decoder` and `c2.android.opus.decoder`, with 48kHz stereo PCM16 AudioTrack. Video retains source PTS and follows the RG AudioTimestamp clock. `power_on=false`, local IME policy and disabled clipboard autosync remain explicit. See the [audio implementation](audio-sync.md), [input implementation](input-ime.md) and [direct path](direct-video.md).

## Findings against the seven canonical criteria

| # | Required finding | Final disposition |
| --- | --- | --- |
| 1 | Bound browser/ordinary-app/fixture and app-state matrix | See matrix; each positive is limited to the actual app/state/source tested |
| 2 | Genuine ten-minute direct AV path, including native input evidence | Actual final timeline and separate native effects below; muted audio decoding is not new audibility |
| 3 | Moving FPS, AV timing, interaction timing, buffers and resources | Software measurements below; end-to-end input latency and sensory timing remain unmeasured |
| 4 | Thin RG APK/runtime and donor provenance | Protocol is its only app-module dependency; manifest has only INTERNET, no service or microphone/camera permission; defined-dex inventory has no Chromium/WebView/Mihomo/subscription/local-keyboard implementation. Donor/input provenance remains in input-ime.md |
| 5 | Dark controls and faithful target content | RG connection/settings/error UI uses a black background and light text. Target app and IME pixels are faithful; bright Settings/browser/IME and cropped keyboard are compatibility limits, without page rewriting or optical legibility PASS |
| 6 | Cancellation/pause/loss/reconnect and complete necessary teardown | Actual cases and final handback below; no shared reset, uninstall, original-store cleanup or workspace archival |
| 7 | Truthful deployment and architecture recommendation | Shell bootstrap remains required, PhoneIME insufficient, minimal reviewed controls retained, lock/audio/performance limits explicit; no production feature deletion |

## Fresh app and state matrix

`native-matrix001` used the unchanged installed pair on VD89. Software-generated RG input changed the actual Phone native fixture: tap counter0→1, nested first-row top0→−48, main ScrollView0→134. The current empty field focused, and software Phone Back dismissed the cropped keyboard. No key sequence, Backspace/Enter editing, Shift/symbols or same-field dismiss/reopen typing is newly proved. The retained earlier attempts at visible H/I/Backspace left the field empty at 240dpi; 160dpi also cropped. The current actual screenshot again shows a full-frame IME with its bottom row cropped.

| Current app/state | Video / audio | Input / IME / independence | Outcome |
| --- | --- | --- | --- |
| Owned looped AV, sampled Phone Awake and secure keyguard showing | Current 600.1s capture, RG decode/render/AudioTrack progress | Sensor registration; separate same-pair native-UI segment | Demonstrated with muted/physical-timing/underrun limits |
| Owned native field/main/nested list, VD89 | Actual projected Android Views; required audio path continues | Native tap and both scroll effects; PhoneIME visibility/focus/Back; typing remains unproved | Native software controls demonstrated; IME insufficient |
| Settings Display, VD89 | Actual resumed Settings component and RG before/after scrolling frames | Software wheel scroll moved the native page; Phone Back returned own launcher; no setting values changed | Demonstrated for this page, with bright target UI |
| Chrome example.com CustomTab, VD89 | Actual resumed Chrome component and RG page frame | Software Phone Back returned own launcher, verified by frame and resumed component | Demonstrated for this benign CustomTab; exact Home/browser re-entry and arbitrary tabs unqualified |
| Different owned app launched on physical0 while AV remained on VD89 | AV advanced 151 frames and 5.07727s source audio PTS after the physical launch | Physical launch returned ok, but secure keyguard remained showing; ordinary-app visibility behind it was not established | Concurrent VD progress demonstrated; visible two-app physical-display case unavailable |
| Normally unlocked Phone, either physical panel | No credential or human recovery in the unattended scope | Phone arrived securely locked; no attempt to dismiss it | Unavailable, not universal impossibility |
| New physical head/pad, optical readability, real typing sequence | No Owner attendance or recording | Current input is software; historical physical primitives retain their prior bindings | Untested for this integration |

Settings and example.com did not emit authored app audio in these cases; no per-app sound compatibility is inferred from their silent audio path. The current software Back return from both sampled apps is a new scoped positive. The older #68 CustomTab-return failure remains historical evidence; this new result neither rewrites it nor proves Phone Home, physical navigation ergonomics, every browser-return workflow or arbitrary-app compatibility.

## Final ten-minute software measurement

The accepted installed pair completed `tenminute001` on current VD88/SCID `102f2859`. RG was muted throughout the owned workload; an explicit in-run media-volume read was 0. Current route observations showed Phone`swlan0`10.199.167.157/24 → RG`wlan0`10.199.167.154/24, with direct interface routes to each other and matching actual TLS peer/local addresses. The exact media endpoint tuples were absent from Spark's socket sample; unrelated same-port connections were ignored. No desktop media/control relay was started or debug route reset.

| Quantity | Actual result |
| --- | --- |
| On-RG controlled measurement | 600,100ms; 600,104ms through final status-read boundary |
| First/last status interval | 599.925s |
| Rendered/changing-frame delta | 17,189 /17,189; 28.652 rendered fps |
| Audio progress | 28,792,968 PCM frames; source PTS advanced 599.873357s |
| Unique in-window sampled software AV offsets | 585; median−3.432ms, range−24.372 to+24.248ms; nearest-rank absolute p95=17.980ms |
| First/last 60 unique-sample medians | −3.3535 /−3.8265ms; delta−0.473ms; OLS software trend−0.05268ms/minute |
| Audio allocation / peak estimated queue | 100ms /247.8125ms |
| Underruns / late-video drops | 0→3 underruns /0 late-video drops; not glitch-free |
| Final reported rolling256-event p95 | Packet-read→callback305.396ms; application echo RTT82.283ms. Neither is interaction-to-visible latency nor a whole-run latency percentile |
| Local head telemetry | Registered QTI game-rotation source; accepted samples486→30,791. Stationary cursor remained centered; no new physical head/pad/ergonomic proof |

The receiver reported a 1MiB maximum encoded packet and 16-frame inflight cap; these are application bounds, not measured kernel/network-buffer residence. The exact socket/codec queue residence and input-to-visible latency were not measured.

The result is near the initial 30fps target for this changing fixture, with three reported audio underruns. The sampled software offset remained within 100ms during this interval, without a large accumulating sampled trend; this does not pass the physical 100ms target. First/last 60-sample groups are not exact 60-second windows. Reported rolling latency windows contain at most 256 events; they are not all 17,189 frames. The original 586 latest-status unique-sample analysis is retained; one callback preceded the controlled first status by 231ms and is excluded from the 585-sample in-window figures. Maximum saved sample age in this moving-content run was 1047ms. This differs from the stale sample observed after normal sleep, which is not live lock-state timing evidence.

Sparse `top` samples at approximately 30/300/570s are retained. At 30/300s the owned stock process showed 20.5/13.3% CPU and 157/161M RES; the bridge 11.7/6.6% and 132/144M RES; RG 60.0/60.0% and 116/110M RES. These are the tool's displayed task percentages and rounded resident sizes, not total-device utilization, peak bounds, PSS or a battery estimate. The finite test/instrumentation overhead is included. The initial Phone power observation reported external power. At 570s, stock/bridge/RG samples were 13.7/6.8/53.3% CPU and 135/114/111M RES. A 13.970s RG-only post-disconnect sample had audio state STOPPED and unchanged rendered/written counters. Its scoped RG `top` read returned only the header, so no numeric RG idle CPU/memory value is inferred. Its empty initialized `ownedNamespaces` field was not a Phone observation; the retained annotation makes this explicit. The first two-device idle observation failed at the Phone`top` call with `device offline`; Phone idle CPU/memory is unavailable. A distinct planned media case then used the original endpoint successfully without reset/reconnect; the mission-listed USB fallback was not found. These transport observations are retained, not converted to an idle performance result.

The authored 12-second media loop is a stimulus, not elapsed-time evidence. The current on-RG driver owns the timing and sampling. Source audio PTS is mapped through RG AudioTimestamp and RG render-callback receipt; no Phone/RG clock subtraction is used. Callback/audio-clock offset does not establish panel-photon/speaker timing. Packet-read-to-callback and application echo RTT are not input-to-visible-result or isolated network latency. The charter's physical 100ms and interaction-p95<200ms targets remain unquantified when no appropriate measurement exists.

## Screen, keyguard and narrow keep-active comparison

The default source's current VD87 requested/advertised `FLAG_OWN_DISPLAY_GROUP`, `FLAG_ALWAYS_UNLOCKED`, trusted and own-focus flags, but Samsung base/override DisplayInfo both reported group 0. That is an observed OEM runtime result, not evidence that the flags were omitted and not an assigned cause. Logical display0 (`local:4630947194243491972`) is the active 968×2376 physical panel; display1 (`local:4630946165277524611`) is the 1856×2160 second physical panel. A second-panel OFF entry cannot prove the active physical panel is off. Detailed existing viewport/device records identify those panels, while current stock logs bind each owned VD. The retained pilot/ten-minute abbreviations have `VD:null` and unlabeled physical arrays; those fields alone establish neither panel-off nor group isolation. Later state records include full logical-display IDs, unique IDs, states and groups. Final state evidence binds each logical display ID and current owned VD.

The initial state was secure/showing/locked, with global wakefulness Dozing but DeviceIdle ACTIVE. An ordinary wake left keyguard showing and secure. Locked-but-Awake streaming therefore differs from physical-panel-off operation. No PIN/helper/authentication path was used. A separate always-unlocked VD flag does not dismiss physical keyguard. The Planner's retained primary-source research described conditional AOSP behavior; runtime grouping and actual media/input behavior govern these findings.

| Current power/session case | Actual state and content | Finding |
| --- | --- | --- |
| Default existing session before normal sleep, VD91 | Physical0 ON, physical1 OFF, VD91 ON; all group 0; global Awake; secure/showing/locked | Locked-but-Awake AV/input is possible; this is not panel-off operation |
| Same session after ordinary sleep223 | Physical0/VD91 DOZE; physical1 OFF; global Dozing; DeviceIdle deep/light ACTIVE; RG black. Rendered/changing855→855 over 9152ms; source audioPTS advanced 9.129743s with owned 1kHz peak0 and source playing=false | Normal-sleep continuation negative. STREAMING status alone did not describe usable AV |
| Native target after that sleep | Owned InputFixture current display91 but active=false; no tap sent | Input/IME unavailable while target inactive; original assertion retained |
| Same-session ordinary recovery addendum | Wake224 plus owned AV relaunch on VD91; physical0/VD91 ON, keyguard still showing/secure; sourceplayingtrue; 123 frames and 4.093661s sourcePTS advance in the sampled interval | Scoped software recovery demonstrated, not automatic arbitrary-app resume or unlock |
| Default fresh start/re-entry while strict panels OFF | Physical0/1 OFF before startup; newVD92 and both panels stayed OFF/group 0 after owned AV launch. GlobalDozing, secure/showing/locked; target playing=false | Negative: WAITING_FOR_VIDEO,0 packets/rendered/touches; silent decodedPCM was not AV; controlled measurement never began |
| Unlocked panel-off / deep DeviceIdle | No authenticated recovery or force-idle/exemption within the unattended scope | Unavailable/untested; no universal claim |

The old sleep-state avSample stayed at elapsed 39822ms while its age grew 4869→14021ms. Its −6.982ms value is **stale**, so it supports no live lock-state AV offset claim. The original report conflated the 9.129743s source-audioPTS advance with sample separation; the retained clarification uses the actual 9152ms RG status interval. No extra test or query was required for that correction.

A temporary HostR5 comparison added only the existing stock argument `keep_active=true`, retaining `power_on=false` and the same server, RG APK and controls. Its host-source SHA256 was `e5915d55c72cb6fbbd3da02c56220289b24a21f967181a079e8fd100a03dd92f`; installed and namespace APK SHA256`0feaaba8eeca6c19217d807f6416fd032970622667d411db7767374ce5aa4b2b`. All 4 affected host tests, build and lint passed before installation. Actual stock argv and child/parent PIDs bound this option to new VD93/SCID `1903d14e`. The verified pinned Controller selects its current new-display ID for keep activity; no additional/global power API was introduced.

| Keep-active comparison | Actual outcome |
| --- | --- |
| Existing session, normal sleep, VD93 | Physical0/1 andVD93 OFF/group 0; keyguard remained showing/secure/locked; DeviceIdle deep/light ACTIVE. Over 7112ms status elapsed, rendered/changing delta0; audioPTS+7.123871s but owned1kHz peak0; source playing=false and native target inactive. No locked tap sent |
| Before ordinary wake | Physical0/1/VD93 still reportedOFF; the option did not supply an observed wake/unlock workaround |
| Explicit ordinary wake224 | Physical0/VD93 ON, keyguard still showing/secure; this was an explicit test recovery, not keep-active lock continuity |
| Fresh start while physical panels OFF, VD94 | Both physical panels andVD94 stayed OFF/group 0; source playing=false, owned-tone peak0. RG emitted 11 callbacks before the sampled phase, then no frame/changing progress; its 35,057ms controlled interval failed. STREAMING status did not establish moving AV |

The comparison did **not** repair the tested sleep/start requirement. No keep-active-specific exception was logged; the inherited unrecognized`send_codec_meta` warning remains. This does not prove every private binder call succeeded or assign the OEM root cause. No further power workaround was attempted. The one-line change was reverted, all 4 host tests/build/lint passed again, and the exact accepted 493879… APK and namespace copy were restored for final baseline checks. The final review candidate retains the accepted product source and APK tuple.

A separate negative is retained: after the keep-active continuation driver's150,044ms run, the host-side ADB shell command had exited 0 and the last-session record still named earlier SCID `6e656390`, not`1903d14e`. The worker's `currentSessionStopped` and overall `passed` therefore remained false, despite the driver's scoped progress check. A subsequent native read found bridge PID20284 absent, statusDISCONNECTED/refused1, no owned namespace/SCID processes and no VD93. That later resource observation does not manufacture a missing current-SCID normal-stop record. Cause remains unassigned. Host-client exit alone is not used as native-process proof; final teardown checks the Phone PID/namespace and actual VD independently.

Deep/light DeviceIdle, global wakefulness and physical display state are separate observations. No force-idle, exemption, timeout change, global power API or lock-policy workaround was attempted. Unavailable normally unlocked testing is left unavailable; a measured failure is scoped to this firmware/setup rather than universal Android impossibility.

## Audio isolation and physical evidence limits

The fresh `mix001` on VD90 completed 45,060ms of current AV. Its separate owned 440Hz source was requested on physical0, but the source's own record was STOPPED behind the secure keyguard; decoded 440Hz amplitude stayed at its low baseline while the1kHz source remained approximately 0.025. Thus this is **not** a fresh simultaneous-source/isolation result. No background service or keyguard workaround was added to force the fixture to run.

Stock `audio_source=output` captures whole-Phone output rather than only the VD app. Earlier #69 directly measured a separate physical-display 440Hz source mixing with the VD 1kHz clip, and the Owner heard both while reporting Phone silence. Its 600,037ms run used source `613e85b4a7c95f424e8f96d3dace8ec864f5c3b6`; the Owner's qualitative “together” observation was not a calibrated 100ms result. Those observations do not become new physical evidence for the changed integrated playback path. The present muted run cannot establish audibility, Phone acoustic silence or physical AV offset. Protected/DRM/opt-out apps and notification/call audio are untested; there is no capture bypass, microphone or camera uplink, or conferencing claim.

## Startup and run guide

1. Use the exact reviewed experimental HostR5/RG3/server tuple, the reserved devices and existing authorized debug route. Build separately with the debug commands in the [experiment README](../../../experiments/fold6-rg-projector/README.md). No production browser module is required.
2. Provision matching private mutual-TLS identities and a private direct endpoint using the existing experiment procedure. Keep passwords, keys and identity material out of logs, command arguments and Git. APK installation alone does not provision a peer or grant shell authority.
3. Explicitly launch the shell-side HostR5 `ShellMain` with its private namespace and verified pinned master server JAR. An ordinary host APK cannot bootstrap that privilege. The host has a 15-minute lifetime; it is not a boot service, persistence daemon or self-contained launcher.
4. Open the RG receiver and select Connect. Open a permitted target on the actual current VD using the owned launcher/bootstrap procedure; do not reuse a historical VD number. Head pointer and scroll run locally; the RG sends native touch/wheel/Back directly to the Phone. One pad tap operates the Phone, two send Phone Back, three disconnect locally. Numeric pointer/scroll controls exist only on the disconnected view. Exact Phone Home/browser re-entry and the complete navigation workflow remain unqualified; the current scoped software Back return is documented above, and Owner-reported bottom-left launches are not Home proof.
5. Stop with Disconnect or ordinary RG pause; the owned stock child/VD/codecs/audio/input must stop. Reconnect gets a fresh session without queued input replay. Stop the shell host when done. Restore any authorized temporary volume state and follow the recorded device end state.

Once started, media/control sockets are between the Phone and RG; the desktop is neither encoder nor media/control gateway. ADB is still needed for this prototype's privileged bootstrap/target launching and evidence collection. Reboot ends that bootstrap; there is no demonstrated automatic restart, durable shell admission, end-user provisioning UI or recovery-after-reboot flow. Preserving the debug route is not evidence of a computer data-plane dependency, and no shared route was severed as a demonstration. Physical power-off/removal of the bootstrap computer and independence of the shell lifecycle from that connection were not tested; the demonstrated no-PC claim is the media/control data path, not a self-contained deployment.

## Verification, failures and handback

The host used Gradle 8.11.1 with launcher/daemon JVM 21.0.11; the Android source targets Java/JVM17 and SDK 35. Rebuilt verification APK bytes differ from the retained installed APKs and were not silently substituted. Before product changes, the current baseline passed 53 JVM tests, Host/RG/test APK assembly and Host/RG lint in 32s, with zero test failures or lint errors. Existing target/deprecation warnings remain. Available checks were executed locally, with the corresponding source and test XML retained. Documentation-only changes require no invented product regression suite; any temporary comparison source receives affected verification and exact artifact binding.

The baseline command was:

```sh
ANDROID_HOME=/home/code2hack/Android/Sdk ./gradlew \
  -p experiments/fold6-rg-projector --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx2g \
  :protocol:testDebugUnitTest :directhost:testDebugUnitTest \
  :directrg:testDebugUnitTest :directhost:assembleDebug \
  :directrg:assembleDebug :directrg:assembleDebugAndroidTest \
  :directhost:lintDebug :directrg:lintDebug
```

The comparison and subsequent source restoration each reran the same Gradle/environment options with `:directhost:testDebugUnitTest :directhost:assembleDebug :directhost:lintDebug` (4 tests, build and lint passed in 18s each).

The fresh pilot001 failed in setup because inherited `ls scrcpy-*.log` treated a new empty namespace as an error; no media session or controlled interval began. Its identified owned bridge was terminated, the driver used an empty-safe file listing, and the original negative remained. Pilot002 then passed 30,100ms with advancing AV and verified stopped stock child. This was a driver correction, not a product or credential failure.

All earlier failures retain their original evidence and source: #66's failed/unknown one-shot credential operations, cropped 160/240dpi PhoneIME and missing key effects; #68's first physical Disconnect failure and corrected contact mapping, unqualified exact Home/browser return, startup/live lookahead failures and failed 200/100ms PCM pacing candidates; and #69's stale-status pilot, bounded audio/video failures, one underrun and oversized instrumentation reply failure. See [Fold6 feasibility](fold6-feasibility.md), [direct video](direct-video.md), [PhoneIME/input](input-ime.md) and [audio/sync](audio-sync.md). No old result was overwritten, a clock timeout was not converted into a new attempt, and no physical failure became a software PASS.

After restoration, the accepted baseline received these fresh lifecycle checks. Each controlled interval had advancing AV before the operation, driver `passed=true`, and an actual current-SCID `SESSION_STOPPED/serverAlive=false` record. These are scoped outcomes, not a guarantee that every shutdown was graceful or every failure race is covered.

| Case | Current VD / controlled interval | Observed result and limit |
| --- | --- | --- |
| Disconnect then reconnect | VD95→96 / 20,031ms on the new session | New session had zero touches and queued taps, then moving AV. Its eventual host stop label was `AUDIO_ERROR`; this is retained with cause unassigned, not relabeled as clean shutdown |
| Ordinary RG Home/onPause | VD97 / 20,070ms | Receiver/audio `STOPPED`, zero queued taps; host `CONTROL_EOF` |
| Authenticated host stop / peer loss | VD98 / 20,075ms | Receiver `CONNECTION_OR_REQUIRED_AUDIO_FAILED` with `EOFException`; host `HOST_STOP`. This is expected visible peer loss, not a cable/network-drop test |
| Local Disconnect invocation | VD99 / 20,041ms | Receiver/audio `STOPPED`, zero queued taps; host `CONTROL_EOF`. This uses the existing local Disconnect method, not a new physical three-tap result |

All owned host/stock/receiver/probe processes were then stopped. Final successful Phone/RG process queries had no owned matches; actual logical-display records contained none of owned VDs87–99, including the first reconnect VD95. The last current SCID `232c9ab4` record was stopped. This does not assert global port absence, which remains unobserved. The exact accepted host/RG/probe installed hashes matched the tuple above, and a final RG media-volume read confirmed restoration to the actual initial 8.

Both devices were reported returned under `PAV-BATCH004-FOLD6-RG-W70-01`; the final handback record was sampled at 2026-10-18T21:40:38Z. After one final ordinary sleep per device, the sampled Phone physical0/1 panels were OFF, secure keyguard showing/locked, global Dozing and deep/light DeviceIdle ACTIVE; the RG panel was OFF/globalAsleep and DeviceIdle ACTIVE. These are final samples, not continuous-state guarantees. No further device work follows this handback. The shared debug service, unrelated historical R4, original apps/stores and selected IME were left untouched. Mission namespaces, comparison APK/source, raw evidence and workspace remain retained for the Manager's whole-experiment cleanup boundary.

The [evidence index](results-evidence.json) records exact hashes, case dispositions, affected checks and private evidence locations. The final product files are byte-identical to the assigned accepted baseline; only this report and its evidence index are committed. The private full hash manifest retains raw status samples, XML, logs, harmless app/IME screenshots, driver/source snapshots, original failures and additive clarifications. No TLS key/config, credential, runtime registry or raw screenshot is published.

Independent exact-candidate review and Manager acceptance are pending. This report neither merges the experiment into main/v0.0.4 nor authorizes a production redesign or release.
