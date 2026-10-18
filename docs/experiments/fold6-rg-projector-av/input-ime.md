# PAV-03: local RG input and the projected Phone IME

Worker: Worker-#68

Issue [#68](https://github.com/code2hack/EyeBrowse/issues/68), experimental branch
`work/issue-68-projector-input-ime`, accepted PAV-02 base
`077124ed21ee97f51a8c2fe15e5b165e7100f045`. Owner191 resumed work with the
[no-panel / double-tap-to-Phone-Back decision](https://github.com/code2hack/EyeBrowse/issues/68#issuecomment-6253136662).
The current correction removes the RG-local session panel and its invocation.
Disposition196/198 adds two bounded numeric rows using the preserved settings
model on the existing disconnected Connect view. It selects ordinary Phone/RG system navigation as
the routes to assess next, without claiming that those routes are available.
The W68-02 device window207 installed the matching input pair and obtained the
physical results below on852e1de. That trial exposed reversed head-pointer
directions on both axes. A two-sign correction also failed physically. The
current correction changes the relative orientation to the current head frame;
Owner subsequently confirmed both corrected directions on RG8911c4a3… .
Historical
app effects, Owner confirmations and IME failures retain their exact source/APK
bindings. Independent review, remaining navigation evidence and integration
remain outstanding.

## Implementation and provenance

Only `experiments/fold6-rg-projector` changes. HeadPointerModel, HeadOrientation,
SensorHeadPoseSource, PointerOverlay, EdgeScrollMotion, PadGestureRecognizer and
numeric settings originate at donor
`v0.0.4@03de921e2400147842ce6c56101b58d514dde77c`. The experimental copies use
their own package/preferences; the recenter method, browser mode callback, unused pointer telemetry/test seams and
legacy preference migration are removed. No browser, WebView, DOM, proxy, tabs,
bookmarks or local RG keyboard is linked or started.

One local overlay follows rotation-vector samples, smoothing on RG display
frames independently of decoded video. Sensor silence expires at 250ms; pause
unregisters the sensor and stops drawing callbacks. The first fresh sample after
restart establishes a neutral head pose. Pointer gain is 0.1–6.0×, default 2.0×,
with 0.1 steps. A 24-rendered-pixel strip beyond either video edge extends logical
vertical travel without changing visible-span gain. Outside-video taps have no
remote target. Inward motion reverses from the saturated strip immediately.

The Surface preserves the current stream aspect ratio. Input maps against its
current fitted rectangle and current encoded dimensions; physical Fold6 panel
size is not an admission rule. Stream pixels already carry Phone rotation;
RG display rotation separately remaps head axes. Decoder/geometry replacement
withdraws input until a frame from the replacement decoder is rendered.

## Current interaction map and remaining physical checks

| RG action | Projector result |
| --- | --- |
| Aim with head | One RG-local cursor over the video |
| Ordinary short pad tap | One touch DOWN/UP at the current visible cursor target |
| Double tap / OEM double | Android Back to the projected Phone app; constituent short taps and duplicate OEM reporting suppressed |
| Pad forward/backward swipe | Native wheel scroll at the current visible target, ±160 nominal pixels |
| Aim into upper/lower24px strip | Repeating native wheel scroll at the same horizontal aim and visible video centerline |
| Android Back while streaming | The same Phone Back path; it does not open an RG panel |
| Android Back while disconnected | Ordinary local Activity exit |
| Phone Home/app-return | Aim/short-tap the Phone's own projected navigation target if actually rendered and usable; availability/effect unverified. Explicit Home transport retained |
| Local Disconnect | Leave the receiver through the RG's existing system Home/app-exit if physically available; onPause closes the session. Actual exit invocation unverified |
| Numeric pointer/scroll settings | Two bounded numeric −/+ rows on the existing disconnected Connect view; local head/short-tap activation shares the Connect button path. Owner confirmed both rows on852e1de and restored2.0×/240 |

No session panel, panel-opening gesture, permanent browser toolbar, recenter,
mode toggle or local RG keyboard is present. The ordinary pre-existing
connection/status screen and its Connect action remain, with only the two numeric
settings rows added. Controls display saved values, disable at the existing bounds,
and preserve the prior value/show an error when saving fails. A touchscreen long press
does not open a panel. This is not a replacement startup or app-switcher design.

The physical pad mapping reuses donor `ROKID,PSOC-TP-R` keyboard-source Enter,
OEM291 and D-pad/OEM292/293. Both an OEM double event and two reported short taps
produce one Back callback within the bounded double-tap interval. A later,
distinct double tap produces another Back. Android Back also uses that path
while streaming. The callback cancels pending tap/touch/edge work and calls the
existing authenticated `navigate(BACK)` sender. Desktop key injection is not
physical RG evidence. Direct touchscreen tap/drag support remains diagnostic
software-input evidence when driven synthetically.

Manager disposition196 resolves the narrow source-placement question: numeric
settings use the existing disconnected view. Window207 still
must assess the actual Phone-provided Home/app-navigation target and the RG's
existing physical system exit. No fixed coordinate, launcher, assumed gesture
or global navigation change is selected. Phone-side Stop, ADB or synthetic pause
cannot establish physical RG exit. If either platform route is unavailable,
that exact missing function and the actual available controls return to Manager
before selecting a material alternate. No new Owner design gate, replacement
menu/gesture/framework or physical reachability claim is introduced.

Scroll speed is 10–1,200 **nominal** px/s, default240, step10. Wheel input uses
64 nominal pixels per wheel detent. Android applications apply their own native
scroll factor, so this is not a promise of exact content pixels per second.
Pad swipes aim at the actual main/nested widget; strips aim at the visible centerline,
not hidden padding. No JavaScript chooses a scroll target or assigns scrollY.

Input adds control channels8/9 to the existing authenticated protocol without
changing channels0–7 or audio negotiation. This source is the matching v1
input-only checkpoint. PAV-04 uses v2 mandatory audio and must be integrated by
the Manager/later implementer before a combined AV claim; v1/v2 interoperability
is not assumed. Scroll8 carries five int32 values:
x/y/current video width/current video height/nominal pixels (nonzero, ±1,200
maximum). Navigate9 carries current width/height/keycode, whitelisted to Back4
and Home3. The host validates payload lengths, coordinates, dimensions and key
before forwarding to pinned scrcpy5.0.1. Upstream
[ControlMessageReader](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/ControlMessageReader.java)
defines wheel/key encoding;
[Controller](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/Controller.java)
selects the new virtual display for native touch, scroll and key events.

The receiver uses the existing zero-backlog executor for all controls. Pause,
Disconnect, focus loss, geometry change and stream loss cancel pending gesture
confirmation/repeating scroll. Pause/Disconnect close the socket/decoder;
reconnect never replays input. No frame ordinal, original-target identity or ABA
framework is introduced. Focus loss stops the sensor; regain/reconnect/geometry replacement requires
a fresh neutral pose. Touch GestureDetector state is explicitly canceled too. The same bounded socket/write deadlines as PAV-02 apply.

## Phone keyboard and native fixture

The unchanged stock option is `display_ime_policy=local`. Field focus is sent as
a native touch. The selected Phone IME must render on the virtual display and
its actual keys must be operated through RG pointer/taps; neither clipboard nor
text/key injection is implemented as a keyboard substitute. No IME selection,
security setting or original application data is changed.

The owned HostR5 `InputFixtureActivity` contains an ordinary multiline EditText,
a main ScrollView and a 40-row nested ListView. Native counter/text/selection/
scroll observations are written only for this harmless owned fixture. Its text
is not preset. The original moving PAV-02 fixture remains available unchanged.
The instrumentation's `input-observation` case observes live sensor/pad counts
without synthesizing them; it does not declare human ergonomics passed.

## Historical actual device findings — b13c131 / original installed pair

Both target identities were checked at test start: Fold6 SM-F956N/API36 and
RG-glasses/API32, user0/shell2000, through the Manager's USB173 debug server.
Media/control used direct TLS1.3 RG10.199.167.154 → Phone10.199.167.157:44367.
There was no computer media/control gateway. Matching HostR5/RG3/Probe4 builds
were installed with ordinary same-ID updates; full installed bytes matched.
No original EyeBrowse/Chromium installation/data, IME selection, volume/routing,
credential, shared service or global display setting was changed.

| Case | Observed result and limit |
| --- | --- |
| VD57, 480×640/240dpi, owned native fixture | Actual RG screenshot showed the fixture; its recorded displayId was57 |
| Software RG tap | Fixture counter0→1 |
| Software RG drag over nested list | Native firstVisiblePosition0→1, first-row top−18; main scroll remained0 |
| Software RG drag over main rows | Main ScrollView scrollY0→110; nested position retained |
| Effect-command interval | 6,808ms for those commands/reads; not optical or end-to-end latency |
| Real Phone IME | Selected `com.bytedance.android.doubaoime/.ImeService`, unchanged; owned field focus true; actual keyboard pixels projected |
| 240dpi layout | Keyboard covered the app and cropped the bottom Space/Enter row |
| Three visible-key software taps at240dpi | H, I and Backspace attempted from the actual keyboard image; the owned field stayed empty after each. Receiver had sent5 total touches including app/field focus. Delivery alone is not a key-effect PASS |
| Local menu / Phone Back | Software RG Back opened the local menu; its Phone Back action dismissed the real keyboard. IME diagnostic after dismissal: display57, window visibility0, inputShown=false |
| Local Disconnect | Software menu activation stopped the stock session; host session elapsed119,082ms,1,228 encoded frames, CONTROL_EOF, serverAlive=false. This elapsed value includes session setup, not a separate continuous-stream measurement |
| VD58, 480×640/160dpi | The charter's one lower-density trial also showed the same cropped keyboard. Full phrase/Shift/symbols/Enter/dismiss-reopen editing sequence remains unproved |
| Browser | Existing owned hostprobe launched an ordinary Chrome CustomTab for public example.com on VD58; its actual page rendered on RG |
| App return | Phone Back did not establish return from that CustomTab. A HOT own-fixture launch also did not bring its image visibly forward; a cold owned-fixture launch restored the native test target. No cause or successful app-return qualification is assigned |
| Phone Home | Encoded and unit-tested; actual effect/physical reachability unexecuted |
| Physical head/touchpad | Owner replied “the first two succeeded” to head aiming and short-tapping Native Tap. The owned fixture reached taps2. Smoothness details, physical main/nested scroll, menu/Disconnect were not confirmed. Owner could not identify Android Back, then reported double-tapping; the installed b13 recognizer ignored that event. No broader physical/ergonomic PASS is inferred |

The 160dpi choice is only the owned stock `new_display=480x640/160` bootstrap
option. It changes neither physical Phone dimensions nor a global display or
IME setting. The initial240dpi outcome remains retained. No custom keyboard,
text injection, paste, DOM text, alternate field renderer or speculative
workaround chain was introduced.

Phone-IME compatibility is **limited for the selected IME and tested layout**.
No successful character insertion, meaningful Backspace, Shift/symbols, Enter,
or same-field continuation is claimed. The keyboard's minimum usable size and
the cause of failed key effects are not established. There is no basis to delete
RG's built-in keyboard or call it unnecessary; a labeled fallback might need a
future trial. This issue does not implement or validate that fallback, and makes
no arbitrary-language/IME or Samsung-policy claim.

## Artifacts and host checks

Private native-size480×640 RG frames and scoped own-fixture records are retained
under run-001/issue-68/worker-68-001/evidence; raw images/logs are not published.
The Manager can bind them through `evidence-index.json`:

| Private frame | SHA-256 |
| --- | --- |
| `rg-keyboard01.png` (240dpi) | `b6248af5a270885f56df4f635f6939266ec1e333b4feea38ab169e9326d5a145` |
| `rg-keyboard160-01.png` | `b6914cfd1c1f5255d98589d179d2b316a7294936d961f089b788608e64077777` |
| `rg-browser160-01.png` | `0f984b11597e7222b3040256a9bbad9736a13d255d62cf6af330ba96d33a58ee` |

Historical installed pair, freshly re-hashed at actual window handback191,
before the later audio69 and input68 W02 updates. These are retained bindings,
not the current installed pair:

| APK | Bytes | SHA-256 |
| --- | --- | --- |
| HostR5,160dpi | 2,543,674 | `0a28d6ae10222ebce75e07c92675fb222fb4e1c113411ce21cfe83e2a7393197` |
| RG3 | 2,607,565 | `1b734b82033673baebbe6502ce92bce712c88a1301a8ce1bc582c72dae79229e` |
| Probe4 | 73,438 | `944863658b787fb84ddae82d61aa020e75a3f3a2612786bd2865d6196a0976b9` |

Initial host check01 passed25 JVM tests, both APK builds, instrumentation build
and lint in42s. Check02/03/04 addressed actual UI/gesture/lifecycle changes;
their logs remain retained. Final host check passed **26 JVM tests**
(protocol11, receiver11, host4), all three APK assemblies and host/receiver
lint in27s. The later owned160dpi host change passed affected host4 tests,
assembly and lint in17s. Only the existing OldTargetApi warning remains on
each app. The code-built Surface delegates confirmed short taps/accessibility
activation through performClick; its two narrowly explained lint suppressions
cover XML-constructor and GestureDetector static-analysis limitations.

Checks cover current aspect/rotation/keyboard-area mapping, border rejection,
pinned wheel/key bytes and payload validation, pointer gain/rotation, bounded
strips/inward reversal, sensor expiry, gesture/double cancellation, scroll stop
without catch-up, settings write failure, pre-render/post-Stop rejection of all
three control kinds, and existing codec/TLS/server lifetime behavior. These are
host assertions, not physical, optical, IME or audible AV acceptance.

## Actual teardown and resource return191

The preserved bridge23864 ended through its existing HOST_LIFETIME. A fresh
process observation found no exact mission bridge/stock child before teardown;
no TERM signal was needed. Bridge status was HOST_STOPPED; last-session was
SESSION_STOPPED/serverAlive=false, with session elapsed888,328ms,6,210 encoded
frames and5 touches. These are host-session counters, not a continuous optical
or mandatory-audio measurement. Prior partial/failed outcomes remain retained.

Owned HostR5/RG3/used-hostprobe force-stops returned0. Final scoped process reads
showed no owned app/bridge/stock child, and a fresh display dump had no VD58 or
scrcpy match. One normal sleep/check per device observed Phone Dozing and RG
Asleep. There was no PIN/forced-lock loop, global setting change, original-store
operation, shared service reset, new input installation/session or discretionary
file cleanup. The USB173 route remains under Manager custody.

Independent socket-table port absence was not observed; the prior netlink
limitation remains a limitation. Process/VD settlement is not promoted to a
global listener-absence claim. The Manager received actual window handback and
the exact installed pair in `handback191/handback.json` and
`handback191/settlement-final.json`. Work then remained source-only through the
settings checkpoint196/198; mission207 subsequently granted W68-02. Required evidence/worktree cleanup is deferred to
experiment end.

## Current correction verification

The rejected double-tap-to-panel edit and its bounded27-second passing host
build are retained privately as `rejected-double-panel-proposal.patch` and
`rejected-double-panel-retained.json`. It was never installed. It is superseded
by the current no-panel/Phone-Back source, not rewritten as a successful Back
trial.

The a60226d no-panel correction passed **13 receiver JVM tests** (input8, decoder5),
receiver APK/instrumentation assembly and receiver lint in27s, recorded in
`no-panel-back-checks191.log` and `no-panel-host-checks191.json`. Lint has the
existing OldTargetApi warning and no errors. The unchanged host/protocol tests
above were not rerun for this receiver-only correction. New double-tap tests
check one Back for duplicate two-tap/OEM reporting, later independent Back,
constituent-tap suppression and canceled/held/stale sequence rejection.

Correction artifacts are retained separately from the historical pair above:

| APK | Status | Bytes | SHA-256 |
| --- | --- | --- | --- |
| RG3, a602 no-panel/Phone-Back | Never installed | 2,607,565 | `71e0a456004a20bb8ff523f7f41a1404f1944168c0a2b23dc44144bbd5f09975` |
| RG3,852e1de settings placement196/198 | Installed/tested W68-02; reversed axes retained | 2,607,565 | `45852fbc80cfaf3944c19ac5110abd86de0ba7928723f2e2f1a673b3549cad9f` |
| RG3, sign-only correction | Installed; Owner reports both axes still reversed | 2,607,565 | `6a26ea8c0c57c5fe6c47803fbbde24376c64c1322d6167b1a34ab464ac12fbe9` |
| RG3, current-head-frame correction | Installed; Owner confirmed both directions | 2,607,565 | `8911c4a3dcc78cc45a8a3dd1a77711fe2eb0ea50a6045dc8045c17c236eaf43d` |
| Probe4, direct lifecycle stop | Installed W68-02; unchanged by direction fix | 73,010 | `fd458f817bfa21e14c6792ed7e6615c3993f3fb0de5e6b2ccfac09a26856ba83` |

The numeric-row/local-hit-action change passed the affected **13 receiver
tests**, receiver/probe assembly and receiver lint in28s. A final status-refresh
fix preserves a prior display-unavailable message when changing settings; the
same affected checks passed in23s with only OldTargetApi. The intermediate source,
APK and28s results remain retained in `settings-placement-host-checks196.json`;
the final results are in `settings-placement-final-checks196.log` and
`settings-placement-final-host-checks196.json`. Probe bytes are unchanged. Existing model tests
exercise numeric defaults, increments, persistence and failed-save retention;
host build checks do not demonstrate the new buttons' physical reachability.
Host/protocol code and tests were not changed or rerun. The settings rows are
children of the existing connection view and disappear with it while streaming.
Back recognition/routing and lifecycle disconnect are unchanged from a602.

The source/Android test driver exercises Disconnect directly as a lifecycle
mechanism and explicitly does not claim physical RG exit. The earlier head/tap
observation belongs to source b13c131/RG1b734b82…; the later physical input and
direction findings below belong to852e1de/RG45852fbc… .

## W68-02 physical input and direction correction

Mission207 granted both devices. Phone debug used TCP192.168.0.193:5555 through
the Manager's existing15037 ADB server; RG remained on USB. Actual physical
identities were confirmed once at test start. Matching v1 HostR5, RG3 and Probe4
installed bytes matched the case artifacts. Media remained direct TLS1.3
RG10.199.167.154 → Phone10.199.167.157:44367; no computer media gateway.

| Observation on852e1de/RG45852fbc… | Result and limit |
| --- | --- |
| Disconnected numeric controls | Owner confirmed both rows worked using head aiming/short pad taps, with values restored. Own preferences read back pointer.tenths20 and scroll240 |
| Native fixture on VD72,480×640/160dpi | Owner confirmed Native Tap, nested scrolling and main scrolling. Own records show taps0→1, nested first0→2/top−21 and main scroll0→661 |
| Phone keyboard and Back | Owner confirmed field tap opened the actual Phone keyboard and physical double-tap dismissed it. Receiver recorded one navigation command. No text was requested or entered; this is not a full typing/Shift/symbols/Enter trial |
| Keyboard layout | Owner reports the popup Phone keyboard occupied the full RG screen. Earlier selected-IME cropping and failed key effects remain, without a fallback or layout workaround claim |
| Head direction | Owner explicitly reports cursor motion exactly opposite to head motion in both x and y. Successful target activation does not establish correct aiming ergonomics; this bug is retained |
| Phone Home/app-return | Phone-provided desktop/taskbar actually rendered before the native fixture launch and remained visible below it. An exact Home/app-return target and its physical effect remain unqualified |
| Session end | SCID5c1e17a6 ended by HOST_LIFETIME; elapsed735,570ms,6,421 encoded frames,3 touches, serverAlive=false. These are host-session counters, not continuous optical/audio measurements or physical RG exit |

The receiver's871 total scroll commands include repeating edge-scroll work;
they are not a physical pad-swipe count or a per-gesture timing measurement.

The first software Connect tap had not established a new session; its copied
status file was an old69 audio result and was not counted as fresh evidence.
Normal wake/Connect subsequently produced the fresh v1 session above. No cause
is assigned beyond these observations. The old fixed waitForGo/bootstrap
geometry predicate was not used. After the finite host ended, its11207 process
was absent and the owned HostR5/RG3 force-stops returned0 before the correction.
Unassigned r4/shared resources were not touched.

The first correction reversed the two inherited angular signs after display-axis
remapping. Its synthetic direction regression failed against the original signs
(one expected failure,8-second build), then **14 receiver tests** (input9,
decoder5), receiver/probe assemblies and receiver lint passed in23s. Owner
subsequently reported **both axes still reversed** on installed RG6a26ea8c… .
Those tests assumed raw-axis signs from an identity reference; their green
result did not reproduce the worn input path. APK disassembly confirmed the
sign change was actually present. This failed physical correction remains in
`window207/owner-direction-fix-still-reversed.json` and is not relabeled a PASS.
Owner briefly paused work and then explicitly resumed with “go ahead and fix it.”

The source comparison exposed a reference-frame error: `reference.inverse() *
current` expresses the relative pose in the startup axes. Rolling after startup
can reverse or exchange the local aiming axes. A synthetic roll regression
reproduced that behavior. A bounded worn neutral/right measurement also captured
an Owner-observed rightward head turn/circle-left result: the installed math
projects −28.78° horizontally, while the corrected transform projects +29.26°.
The sensor dump rounds components to two decimals; these are diagnostic angles,
not precise timing, ergonomic or optical measurements. No physical roll history
is inferred from this reproduction.

The correction uses `current.inverse() * reference`, expressing the reference
ray in the current head frame, and projects movement opposite that ray. Gain,
bounds, smoothing, settings, pad gestures and Phone Back routing retain their
existing mechanisms. Tests now cover the captured worn pose, both axis signs
and all display rotations, plus ±90°/±180° roll since startup. Before the
composition change, the captured-pose and roll tests both failed (two expected
failures,12-second build). The original sign-only, roll and measured-pose failures
remain retained separately. The final affected checks passed **16 receiver tests** (input11, decoder5),
receiver/probe assembly and receiver lint in26s, with only the existing
OldTargetApi warning. Results/source hashes are retained in
`window207/current-frame-host-checks.json`. Installed RG8911c4a3… matches the
built artifact; Probe4 bytes are unchanged. Owner then confirmed **“Both axes
correct”** on the disconnected Connect view,
bound to source26d8e5d/RG8911c4a3… in
`window207/owner-current-frame-directions-confirmed.json`. This establishes
physical direction on that build; it is not an optical latency or sustained
ergonomic measurement. No streaming session was started for that direction
check. The current saved pointer gain is4.0× and
scroll240; the earlier2.0× restoration remains scoped to its earlier observation.

A subsequent fresh case reused the same matching v1 pair and owned namespace.
Bridge21415 started successfully; its current stock log identified VD73 at
480×640/160, and the native fixture cold-launched there. Live stream counters
advanced. The software Connect setup had touchesSent1 before Owner interaction;
that count is not a physical tap result. The current receiver image has the
native fixture and a bottom Phone system bar. Narrow window observations show
NavigationBar73 visible at [0,592][480,640], but no confidently identifiable
Home/app-switch target has been established from the rendered image. A window
record does not prove a usable control. The Owner was asked at this ready setup
for one native tap with corrected aiming, any recognizable Phone navigation
target, and an already-known RG system Home/app-exit action/effect or “no known
RG exit.” Those remaining physical facts are pending; no alternate binding or
new menu/gesture is selected.

No independent acceptance, mandatory-audio result, integration or release is
claimed. Matching PAV-04 integration remains a later coordinated step.
