# PAV-03: local RG input and the projected Phone IME

Worker: Worker-#68

Issue [#68](https://github.com/code2hack/EyeBrowse/issues/68), experimental branch
`work/issue-68-projector-input-ime`, accepted PAV-02 base
`077124ed21ee97f51a8c2fe15e5b165e7100f045`. This is an experimental capability finding in progress. The exclusive PAV-03
window was granted in mission revision2. Software-driven native effects and
actual Phone-keyboard images are demonstrated; physical head/touchpad operation
and successful Phone typing are **not yet established**. Independent review and
acceptance remain separate.

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

## Minimal interaction map

| RG action | Projector result |
| --- | --- |
| Aim with head | One RG-local cursor over the video |
| Ordinary short pad tap | One touch DOWN/UP at the current visible cursor target |
| Double tap / OEM double | No remote action; constituent short taps suppressed |
| Pad forward/backward swipe | Native wheel scroll at the current visible target, ±160 nominal pixels |
| Aim into upper/lower 24px strip | Repeating native wheel scroll at the same horizontal aim and the visible video centerline |
| Android Back | Open/close the local session menu |
| Touchscreen long press, where available | Open the same local menu |
| Local menu | Phone Back, Phone Home, numeric pointer/scroll controls, Resume, Disconnect |

There is no permanent toolbar. The hardware mapping reuses the donor's
`ROKID,PSOC-TP-R` keyboard source (Enter, OEM291, D-pad/OEM292/293). Desktop
keyboard injection is not evidence of physical RG input. A direct touchscreen
tap/drag is also supported, but a synthetic MotionEvent remains software-driver
evidence. Local menu buttons can be aimed at and tapped by the head/pad route.
Android Back opens the menu under software-generated RG key input; physical
touchpad ergonomics await the Owner observation requested at the ready setup.

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

## Actual device findings

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
| Physical head/touchpad | Real hardware name/source matched donor (`ROKID,PSOC-TP-R`,0x101); local cursor visible. Owner asked to test head aiming, actual pad tap/main+nested scrolling, Back menu and Disconnect. Result pending; screenshots/raw sensor availability do not establish ergonomics |

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

Current artifacts, with source/installed bindings in mission-private records:

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

Necessary final teardown/window return is pending the actual Owner observation.
The first case's stock child ended; bridge1 was stopped before the160dpi host
update. Bridge2/current VD58 remain owned for the ready physical trial. No
independent review, integration, release or mandatory-audio experiment PASS is
claimed. PAV-04 audio remains separate until matching combined integration.
