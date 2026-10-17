# Fold6 stock projector feasibility — PAV-01 / issue 66

**Partial feasibility demonstrated; full AV/input viability is unproved.** Actual
Fold6 virtual displays rendered the fixture, Chrome and Settings. A required
output capture received the owned clip's changing H.264 video and twelve decoded
1 kHz audio pulses. Real Phone keyboard pixels appeared, but stable visible-key
typing did not qualify. Audible output, acoustic Phone silence and AV sync were
not observed. These limits remain part of the result.

Observations were taken on 2026-10-17 UTC (2026-10-17/18 Asia/Shanghai).
The [authorized plan](../fold6-rg-projector-av.md) and
[issue 66](https://github.com/code2hack/EyeBrowse/issues/66) govern this separate
experiment. A supported negative can complete an investigation after independent
review; this report does not accept a downstream prerequisite, production merge
or release. There was no RG device operation or main/v0.0.4 merge.

## Reproducible inputs

The machine-readable [upstream record](upstream.json) pins the official
[scrcpy v5.0.1 release](https://github.com/Genymobile/scrcpy/releases/tag/v5.0.1).
The source tag resolves to commit
`a60891aea193d92e7e5c3942700eca63f9d19a5f`, tree
`eb740a30415199219479610728420ebcfd8a9064`.
The unchanged source was built privately for the Spark's AArch64 architecture;
the release's Linux desktop prebuilt was x86_64.

| Artifact | Binding |
| --- | --- |
| Official server | 733,930 bytes; SHA-256 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536` |
| Published checksum file | SHA-256 `b6e9a0b549caaaa4d0e5e1a2ef56069f9516cc10eced9477406dee4bb59180f8` |
| Native desktop diagnostic client | 1,193,304 bytes; SHA-256 `a61f28ac9b7d3b27975a02bdbb470144a5a846fec765196b21cf0ffa87eeeee4` |
| SDL source / compiled library | 3.4.18; hashes and feature selection in `upstream.json` |
| Owned fixture source | `23bf0664ea8ad85e5893cfdb80cd7602091b0a68`; fixture tree `32437e5ea971973bbb1acf0d44d88adb6f3c275c` |
| Fixture APK | 2,639,851 bytes; SHA-256 `d4d6583c25d5dea33fd48087d4047faf6c20ccb52d562626b168801fcbf7d8c1` |
| Owned flash/beep clip | SHA-256 `d4ac939c07a8679fa041187a093d46f80407773b3d0dd663a944d8ab4202d678`; 12 s, H.264 480×640/30 fps and AAC 48 kHz mono |

The server matched both the GitHub release-asset digest and the published
checksum list. A detached signature was not independently verified. Its APK
metadata declares version 5.0.1 / code 50001, minimum API 21 and target/compile
API 36. It is a shell/app_process server, not an installed privileged app.

The native client linked SDL 3.4.18, libavcodec 60.31.102, libavformat 60.16.100
and libavutil 58.29.100. All 12 upstream C tests passed, with no skips, failures
or timeouts. Initial optional-XTEST and private FFmpeg include-path build
failures are retained in the private run evidence. The successful private build
did not modify upstream source, system packages or the shared ADB server.

The [minimal fixture](../../../experiments/fold6-rg-projector/README.md) uses
platform Views, VideoView and an ordinary EditText. Its optional Chrome Custom
Tab and Display-settings buttons launch existing apps on the current display;
there is no embedded browser, reconstructed keyboard, networking permission,
microphone, proxy or background service. `assembleDebug` and `lintDebug` returned
success. Lint retained `OldTargetApi`, `DataExtractionRules` and
`MissingApplicationIcon` warnings. These host checks establish no device result.

## Device and preservation boundary

| Observation | Actual result |
| --- | --- |
| Hardware / OS | Samsung SM-F956N, q6q; Android 16 / API 36; One UI property `80500`; current user 0 |
| Physical identity | Serial and boot serial matched privately; SHA-256 `f962201cd42c1f34ebb1ed48244c8e1b009f29fb6421b35e17cfa1bcd1c7b232` |
| Termux transport | Strict retained-host-key SSH on port 8022; authenticated UID 10388, untrusted app context |
| Android shell | Independently authenticated UID 2000, `u:r:shell:s0`; Termux SSH alone did not establish this privilege |
| Debug route | Owned Spark loopback SSH forward to the actual Phone's loopback ADB 5555; pinned existing Spark ADB server/client |
| ADB preparation | Exactly one necessary `tcpip 5555` returned; subsequent property and listener verified; no pairing, new trust or server reset |
| Physical display before tests | Display 0, INTERNAL, ON, 1856×2160, 420 dpi, rotation 0; device state OPENED; wakefulness Awake |
| Keyguard before tests | Secure, showing=false, inputRestricted=false; physical display state and wakefulness measured separately |
| IME observations | `juloo.keyboard2/.Keyboard2` at 14:39 UTC; `com.bytedance.android.doubaoime/.ImeService` at 15:05 UTC; Samsung `com.samsung.android.honeyboard/.service.HoneyBoardService` by 15:53 UTC; external changes preserved |
| Audio baseline setting | Music setting 7; Phone route `bt_a2dp(80)`; output capture qualified before owned playback; no route/volume change |
| Installed apps | EyeBrowse Phone 0.0.1/code 1; Chrome 155.0.8059.39; Samsung Browser 30.0.5.24; Settings 16; projector fixture initially absent |
| RG scope | RG browser package absent on this Phone. No RG hardware query, enumeration or operation |

The original private physical foreground was Paseo. Its pixels and contents
were not captured or used for input. IME observations are timestamped facts;
the later default is not a test-induced change or permission to force an IME.
Opened/cover geometry changed during the investigation; no controlled hinge or cover-display matrix was completed.

Before any installation or stock case, the stopped Phone app's run-as data was
archived privately on the device. The gzip archive is 17,183,934 bytes, SHA-256
`c52178a94e837fe3ab6a61e190c10e5cc90e65651363b640fb241451f664a393`.
The original 610-file manifest has SHA-256
`a6a4ecbe65573210f5e927bf7f154ca907a3623b4d51a43136d2d11f6fc58021`.
A separate owned extraction verified gzip CRC, all 661 members, zero unsafe
paths/special members, extraction exit 0, zero errors and all 610 file hashes
equal to the source manifest. Original app-UID processes were absent before
and after. Directory/archive permissions were 0700/0600.

This is recoverable run-as app-data preservation, not a full Phone, device-
protected-storage, SELinux or app-metadata restore. The original app was not
updated, cleared or overlaid, and no restore into it was performed. Manager
disposition 063 confirms this device-local snapshot suffices for these separately
named fixture and stock cases. The complete archive, manifest and extraction
remain private and retained.

The off-device copy is **incomplete**. The initial raw archive stream, sync pull
and binary exec-out slice timed out; they are retained as negative/unknown
transport evidence. Non-PTY shell-v2 received a 5,521,408-byte prefix whose hash
matched an independent device-prefix hash. A partial remainder read was stopped
before media qualification to avoid competing for bandwidth. Its exact client
exited with -15, the parent command completed, and subsequent PID/descriptor
readback established absence. These partials are not a received full backup or
product test result. No shared server, tunnel or device process was killed.

## Results by dimension

| Dimension | Actual evidence and limit |
| --- | --- |
| Independent virtual display | Actual stock displays 11–17, 480×640/240 dpi. Hardware `c2.qti.avc.encoder`, H.264 target 30 fps/2 Mbit/s; owned changing frames decoded and inspected. Configured maximum fps is not measured sustained fps. |
| Ordinary apps | Chrome 155 Custom Tab with `https://example.com/` and Android Settings 16 Display page actually rendered on display 16. No settings changed or private browser tab opened. |
| Physical other-app placement | A different physical foreground, Paseo, was observed through component/display metadata while the fixture/Chrome/Settings occupied the virtual display. Its pixels/content were never captured or used for input. A deliberate harmless physical-app replacement was not performed. |
| Real Phone keyboard | Local policy requested; actual keyboard pixels on the virtual display and IME token/show-display 11/17 were observed. This was the Phone IME, not a reconstructed local keyboard. |
| Visible key activation | Case 006's distinct `a`/`b` taps did not produce the expected text. In case 007 the IME moved from display 17 to physical display 0 **before any key tap**; a bounded refocus still targeted 0. Its guard submitted zero keys. Cause is unassigned. |
| Backspace, Enter, dismiss/reopen | Not qualified: the dependent sequence stopped after the unexpected typing/routing result. No blind replay, host `input text`, forced IME switch or substituted keyboard PASS. |
| Required output audio | Actual active shell `REMOTE_SUBMIX` capture established before one owned clip play. H.264 video plus Opus 48 kHz/stereo received; offline decode found all twelve expected pulses. |
| Audible/Phone output | Unobserved. The diagnostic host used SDL's dummy audio sink. Phone Bluetooth routing/volume were unchanged; no deliberate Bluetooth/RG playback, route switch, microphone, call or duplication case. |
| AV timing | Unmeasured. Native nonmonotonic-PTS corrections and buffer skips were retained; decoded pulse spacing does not establish latency, lip sync or drift. |
| Display/power/keyguard | Separate observations below. Natural physical OFF/Dozing/secure-lock also stopped the VD. An ordinary wake resumed owned VD pixels while the physical keyguard remained locked. Stock OFF/ON log strings alone did not prove panel OFF. |
| Phone-local IPC | Termux UID 10388's exact owned abstract-socket connect returned `Permission denied`; a separate same-Phone shell-UID pair established AF_UNIX locally without a PC socket forward. No universal APK or specific AVC cause is inferred. |
| Companion/bootstrap | A shell-context helper can launch/connect the pinned server. An ordinary companion does not inherit shell privilege; the bridge and its authorization/lifecycle remain work for a later ticket. |
| Teardown | Owned media, clients, VDs, stock servers, test sockets/forwards and Xvfb settled. Obsolete bootstrap listener absent. Primary debug tunnel explicitly retained under Manager cleanup custody, not claimed destroyed. |

## Display, IME and power observations

Case 001 showed the real fixture on VD11 with a separate physical foreground.
After a field-focus pointer, the owned stream became black. Readback showed
physical display 0 **OFF**, VD11 **OFF**, wakefulness **Dozing**, secure keyguard
showing and current user `deviceLocked=1`. These conditions co-occurred; they do
not isolate panel-off behavior or establish Android device-idle/deep Doze.

One authorized ordinary wake produced physical 0 **ON**, VD11 **ON**, **Awake**,
with the secure keyguard still showing and `deviceLocked=1`. The owned fixture
resumed on VD11 and its pixels returned. No PIN, dismissal or bypass occurred.
The stock trusted virtual display advertised its own focus/display group and
`FLAG_ALWAYS_UNLOCKED`; that platform display flag is not an unlocked physical
Phone or permission to change lock policy.

Case 006 rendered Chrome's intended Custom Tab and the separate Settings app.
Its actual Phone keyboard appeared, but expected owned text `ab` did not.
Post-readback showed IME target 0 and hidden state. Case 007 added a fresh target
check: after focus, IME token/show-display were both 17 and input was shown.
Before its first letter, both had moved to 0 with **no Worker key input**. One
bounded coherent refocus still reported 0 and therefore sent no keys. The
experiment cannot attribute that routing change to Samsung, the physical app or
Owner activity from these observations. Stable typing, editing and keyboard
reopen remain unproved.

For the separately declared case 006 stock `MOD+o` / `MOD+Shift+o` sequence, the
server logged “Device display turned off/on” and owned frames remained visible.
The sampled framework display API still reported ON. No independent physical
panel/hardware-state or optical observation was collected during that interval;
**physical-off success is not inferred from those log strings**.

Later ordinary sleep readbacks observed user 0 locked, keyguard showing and
Dozing, while display 0 reported OFF initially and then DOZE/DOZE_SUSPEND. An
initial overly strict combined predicate expected literal OFF and stayed false;
that original record is retained. A subsequent bounded control-only
characterization separately verified sleep → actual lock/Dozing → wake with
actual lock/Awake/SystemUI ownership → sleep with actual lock/Dozing. It found no
qualifying unique native PIN entry, used no credential and guessed no bouncer
coordinates. Final readback was locked/Dozing/display0 DOZE_SUSPEND. These are
sampled software states, not optical darkness or continuously guaranteed state.

Owner-authorized normal-PIN helper preparation is a separate private operational
record. Manager found three concrete defects in its first candidate; corrections
and 33 public-dummy checks cover strict window/package/UID ownership, atomic
file/directory-synced input intent and truthful credential-consumption reporting
through write failures. At that cut real PIN use remained held, and no real
credential read, normal unlock or native PIN-keypad qualification had occurred.

The later assigned native-surface check observed 33 SystemUI nodes, zero expected
PIN entries, zero password fields and zero numeric keys. Allowlisted lockscreen
root/indication nodes were non-clickable, and no explicit supported reveal target
was established. Its result was **PIN screen not presented**, rather than multiple
entries or a demonstrated OEM PIN-keypad mismatch. No reveal gesture/action or
credential was used at that cut. Under operational direction 074, the Manager
retired the clickable-affordance restriction and manual-keypad request, allowing
one ordinary upward gesture within the freshly verified SystemUI keyguard root.

The updated private helper, SHA-256
`1a3d90e1a86173be7d5d20af473db71e878d1ac48bba7f3f3e21a4a3b5951eb6`,
passed the earlier 33 host checks and 37 gesture/context/uncertainty/end-lock
checks. The first preparation found an already awake/unlocked state, sent no
reveal or credential action, and returned its retained negative before ordinary
end sleep verified locked/Dozing. At that locked cut, another bounded preparation
remotely woke the phone and sent one upward gesture inside current root-derived
bounds. Fresh user0/display0/SystemUI UID10048 ownership then bound one empty,
enabled password `pinEntry` (`android.widget.EditText`) and ten distinct enabled,
clickable, labelled digit targets with valid geometry and no detected native
error/lockout. The strict keypad predicates passed; no selector relaxation
was needed. Normal end sleep verified locked/Dozing/display0 DOZE.

The preparation read no credential and submitted no PIN. Actual normal unlock
remains untested and real use remains held pending Manager exact-helper
qualification. This operational keypad result adds no product media/input case
or AV acceptance. Owned control teardown returned success; both owned listeners
and server SCIDs were absent on subsequent readback. A supplemental forwarding
table read was rejected by the existing no-global-enumeration guard, and a
separate observer assumed a CLI-only teardown field existed in the earlier
attempt file. Both observer negatives are retained; the shared forward table was
not observed or treated as cleanup proof.

## Owned AV case and capture guard

The exact successful capture used matching stock 5.0.1, output source, required
audio, Opus `c2.android.opus.encoder` at 128 kbit/s and hardware H.264. AudioService
showed active shell recording RIID69215; its latest matching recording event
identified UID2000, `REMOTE_SUBMIX`, not silenced, package `com.android.shell`.
Native decoder buffering was observed before the owned clip was played.
Enumeration and configuration strings alone were not accepted as capture proof.

The clip began stopped. One play pointer was derived from fresh owned-Activity
bounds after active capture and current privacy/call guards. Actual VD pixels
showed `PLAYING owned clip` and source frame 109. The local guard completed
45 checks, each with the intended active recording, actual player rows, no
foreign started player and no active call. It stopped the owned fixture, verified
its active-player count zero and process absent **while capture remained active**,
then released the exact stock server. Subsequent recorder/player/client/display
and forward readbacks settled.

| Received case 005 artifact | Result |
| --- | --- |
| Closed recording | 795,074 bytes; SHA-256 `59dfc5120c0bbf630c014dcfccdedb1231ed548e7343806ace7c151e3536f4db` |
| Container/streams | Matroska, duration 78.784 s; H.264 480×640 and Opus 48,000 Hz/stereo |
| Decoded video | 411 frames, 399 distinct frame hashes, 410 adjacent-frame changes; offline decode exit 0. No sustained-fps or optical claim |
| Numeric audio decode | Exit 0; 3,779,208 mono samples at 48 kHz; twelve approximately 110 ms pulse groups, one second apart |
| Frequency check | Each pulse strongest at 1,000 Hz among tested 950/980/990/1000/1010/1020/1050 Hz; matches the owned source |
| Retained native warnings | 45 nonmonotonic-PTS corrections and 483 audio-buffer skips |
| Interpretation | Received/decoded owned audio and changing video demonstrated; audible output, acoustic Phone silence and AV timing unobserved |

Pulse analysis used concatenated decoded sample order. Its regular spacing must
not be converted to a container-clock, end-to-end synchronization, ten-minute
drift, p95 latency, endurance or battery claim.

The pinned [audio documentation](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/doc/audio.md)
and Android's [REMOTE_SUBMIX contract](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#REMOTE_SUBMIX)
describe eligible output diversion while recording. This is whole-device output
capture, not target-app audio isolation. It is intended routing behavior rather
than an acoustic measurement of the current Phone. The Phone route's recorded
Bluetooth name establishes neither RG identity nor forwarded IP-audio receipt.

Earlier audio preparations remain distinct:

| Case | Preserved outcome |
| --- | --- |
| 002 | Real active capture/decoder and a closed silent recording; guard hit Android external-printf argument limit and failed closed before any clip. Owned player stopped before server release. |
| 003 | Corrected file-input checker still selected zero rows because the actual Samsung player-line prefix differed. Stopped before any clip; zero rows was not accepted as zero active players. |
| 004 | Corrected actual-schema checker passed 45 checks and settled player before capture release. The play step reached an expired window and submitted no pointer. |
| 005 | Guard start and the one play tap were bound in the same call; owned AV result above. No preceding outcome was relabeled as this success. |

The five initial parser examples, seven revised actual-Android-shell guard cases
and missing-NumPy/stdlib-analysis recovery are retained as setup evidence, not
sensory or product acceptance.

## Concrete Phone-local startup and IPC boundary

On the authenticated Fold6, the pinned JAR was launched with Android shell UID2000
and `u:r:shell:s0`, using the existing authorized ADB route. This is an actual
shell capability, not a property of the Termux SSH app or an installed ordinary
APK. No local Termux ADB server, pairing change, root or security relaxation was
introduced.

Two narrow local-only cases used stock server code with video/audio disabled,
no input messages and an owned unique abstract socket:

1. A shell listener was verified in `/proc/net/unix`. Termux's current OpenBSD
   `nc -U` client, UID10388 / `untrusted_app_27`, attempted that exact address.
   It returned 1, `Permission denied`, zero bytes. No AVC record was attributed;
   this does not prove every ordinary APK is prohibited in every context.
2. A fresh shell listener and a stock reverse-role shell process, both UID2000 /
   `u:r:shell:s0`, connected on the same Phone. The socket moved from listening
   to connected state03, with its accepted inode owned by the listener process.
   No PC socket forward/media path existed. Both processes and socket were
   subsequently verified absent. An earlier too-early listening-only read is
   retained as establishment unobserved, separately from this later proof.

This provides a concrete shell-context LocalSocket path for the later bridge.
It does not deliver a companion APK bridge, on-Phone decoder, authenticated
application API or final no-PC AV data path. The existing Termux context cannot
simply consume this observed shell socket; a later design must preserve a
justified privilege boundary rather than weakening SELinux or assuming shared
UID/permissions.

After a server death, launch a new pinned, version-matched shell helper and
requalify its owned socket/client/lifecycle. After reboot or loss of shell access,
requalify the actual device, current transport, trust and shell permission;
TCP5555 persistence/reboot recovery was not tested. Reuse working authorized
access where present. Otherwise obtain the actual current approved bootstrap
capability; do not replay an old port/unknown intent, start/reset a shared server,
auto-pair or assume autonomous boot privilege. Autonomous persistence is not
required by issue 66.

The pinned [DesktopConnection](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/DesktopConnection.java)
and [Streamer](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/Streamer.java)
sources define separate video/audio/control sockets, version checks, a framed
video session header, codec metadata and packet flags/PTS. An older protocol
example is not a compatible replacement. The control-only qualification used
that release's exact 32-byte touch / 14-byte key formats with dummy data; real
PIN traffic was never sent.

## Repeatable bounded stock procedure

Use the exact current reservation and physical identity, not the historical
loopback port as a device identifier. Verify actual native/CWD/profile, private
preservation, current model/serial-hash/user/API, approved client/server hashes,
server PID/start/listener/protocol, exact owned tunnel and selected authenticated
transport. Stop on mismatch; no global device discovery or shared-server reset.

The initial direct Spark wireless-39225 connect timed out with unknown outcome
and was not replayed. Separately authenticated Termux SSH reached the Phone's
then-current loopback wireless service. Exactly one necessary persisted
`tcpip 5555` returned, followed by same-device property/listener/authentication
verification. Keep that working listener unchanged. SSH uses existing credentials,
strict retained host-key checking and bounded commands; no private keys are read
or exported.

For the stock diagnostic, substitute freshly qualified private paths/target and
an owned free port. This is the actual option family used, not a final gateway:

```sh
ADB="$qualified_adapter" SCRCPY_SERVER_PATH="$verified_server" \
  "$verified_client" --serial="$qualified_target" \
  --new-display=480x640/240 --display-ime-policy=local \
  --no-vd-system-decorations --video-codec=h264 \
  --video-encoder=c2.qti.avc.encoder --max-fps=30 --video-bit-rate=2M \
  --no-downsize-on-error --audio-source=output --require-audio \
  --audio-codec=opus --audio-encoder=c2.android.opus.encoder \
  --audio-bit-rate=128K --no-clipboard-autosync --no-power-on \
  --force-adb-forward --port="$owned_port" \
  --start-app=com.code2hack.eyebrowse.projector.hostprobe
```

The private operational ADB adapter validates the already running pinned server
on a stock startup request, exposes only the assigned transport on a stock device
list, and inserts `--no-rebind` for an owned media forward. It never starts a
shared server, lists other devices or authorizes another target. Host Xvfb and
SDL software rendering/dummy audio were diagnostic resources; only the exact
owned window was captured.

Begin with owned playback stopped. Require actual active intended capture and
current non-private-player/call guards before play. Keep capture active through
the clip; stop/settle owned playback before releasing it, including guard failure.
For video-only/IME cases use `--no-audio` and keep the clip stopped. Verify current
IME and exact intended display before visible-key input; stop a dependent
sequence on unexpected routing/UI rather than replaying input. Preserve current
externally changed IME/volume/route rather than restoring a stale default.

Declare each necessary ordinary display/power case and persist its intent before
input. Record display state, wakefulness, keyguard/current-user lock and Activity
state separately. No lock timeout, Doze, keep-awake/wake-lock or security policy
change, forced unlock, root, private physical content capture or repeated power
loop. Unknown outcomes stop the dependent action. Normal PIN setup/readiness is
not permission to use an unqualified helper or evidence of unlock.

## End state and preservation readback

The final product-cut user0 readback observed secure keyguard showing, `deviceLocked=1`,
wakefulness Dozing and physical display0 DOZE_SUSPEND. This is the recorded
locked end policy, not an assumed unlocked restoration or optical-off claim.
That product-cut read already satisfied the policy, so no extra end control was
sent at that cut. The later necessary native-surface characterization separately
finished with actual user0 locked/Dozing/display0 DOZE and its control resources
settled, including the later remote-reveal preparation under direction 074.
These timestamped results do not claim continuously unchanged state.
At the product cut, sixteen player records were present, zero started players,
a present recorder section with zero active recordings, call states 0/0, no
fixture-UID process and no stock server. Current Samsung IME, hard-keyboard-IME
setting 1, music setting 7 and `bt_a2dp(80)` were preserved.

The original Phone app remained version0.0.1/code1/UID10300, with zero app-UID
processes before/after the final read. All 610 file hashes and the exact manifest
hash still matched the pre-test source; the complete device archive hash also
matched. A default package query's separate-profile150 permission warning is
retained; the later explicit-user0 query succeeded without that warning. No
original app update, clear, restore overlay or historical backup replay occurred.

All owned VDs/stock media sessions/clients/socket tests and their media/control
forwards settled. Case006's first parent-present read and later exact parent/
child absence remain separate. The old wireless bootstrap PID was already
absent; its original listener inode and port41785 were absent. The exact empty
private Xvfb process was terminated once and its PID/X199 socket/lock disappeared.

The unchanged primary SSH loopback debug tunnel remains **live by explicit
Manager071 cleanup-custody acceptance**, with Worker as current permitted
consumer under the same reservation. It was not destroyed, automatically
reconnected or called a fully returned resource. Manager owns its eventual
exact teardown after authorized consumers finish. Shared ADB, Phone adbd5555,
Tailscale and unrelated processes were untouched. Fixture installation, private
archives, source/build artifacts and evidence remain retained for review and
Manager-coordinated version-end cleanup; no discretionary app/file cleanup or
workspace archival was performed.

## Evidence and handoff

The sanitized [evidence index](fold6-evidence.json) binds selected private records
by filename, byte count and SHA-256. Raw records, images and owned media remain
outside Git in the assigned run-001/issue-66/worker-66-001 mission space. Private
account/source data is represented only by hashes/counts/categories; no credential,
private physical screen content or original app data is published.

Retained negatives also include initial optional native-build dependencies,
private-runtime-icon setup, the forward-remove adapter parser defect and its
exact owned recovery, preservation-transfer timeouts/partials, the 35-second
physical-guard timeout, audio guard setup failures, focus-query corrections and
the initial three operational screen-helper findings. Source publication, SDK
message acceptance, a checksum match or a scoped host check is not recipient
receipt, independent review, acceptance or integration.

The demonstrated video/owned-audio/shell-IPC dimensions may inform PAV-02.
Stable Phone IME input, app audio isolation, audible delivery, AV timing, isolated
physical-off behavior, a complete harmless-foreground/lock/re-entry matrix and
the direct two-device path remain unresolved or untested as described. Manager
and Planner must apply the actual dependent-ticket criteria; this report grants
no downstream resource or capability PASS.
