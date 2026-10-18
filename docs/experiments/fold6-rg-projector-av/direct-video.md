# Direct Fold6 video and control — PAV-02 / issue 67

Component checkpoint, **not CANDIDATE_READY**. The current host and RG receiver
APKs remain byte-identical. New Probe4 instrumentation adds one bounded read of
its own decoder Surface to diagnose reported callbacks with black composed
pixels. The test APK builds and lints, installs once into an unused ID, and
matches its complete installed bytes. No actual PixelCopy request has run.

Manager147 restored the debug routes. Under the fresh Owner150 event, the existing
RG LAN route was already registered and independently qualified the physical RG;
no connect or SSH launch was needed. Later exact observations settled video014's
owned Phone and RG resources across separate cuts. Its original timeout and
unknown Go/teardown outcomes remain unchanged. Original Worker64 resource-return
obligations remain unknown.

Video019 qualified both native foregrounds, lifecycle state, UID, window ownership,
geometry and direct TLS1.3 endpoints. Its inspected RG image showed a black video
area and the public Stop button, without the fixture title or tap counter. The
public-pixel guard rejected it before Go. Surface020 then observed a complete
inactive owned fixture and null focus; its Phone-wake path was disabled. The
distinct Surface021 restored the previously qualified conditional ordinary wake,
but the necessary fresh Phone read returned73 after six complete stages. The
unchanged display0 focus-token selector is the source-bound failure boundary;
its exact count/token subpredicate was not retained. No wake, diagnostic Go,
PixelCopy, touch or measurement followed.

All three cases verified immediate owned teardown and two complete strict secure
user0 locked/showing/Dozing end cuts, with zero extra sleep. These are sampled
observations only. All authentication consumers remain held; no PIN, reveal or
screen caller was used. The actual native read failure stops dependent power and
visual work pending a correlated operational disposition; source checks and the
reviewable checkpoint are complete.

Earlier owned-fixture video reached the RG Surface over direct TLS1.3. The one
automated on-RG touch in product007 produced a transport write but **no observed
app effect**: the actual after-image and fixture counter remained0. It was
software-generated input, with no Owner touchpad test. Security009 separately
observed actual RG certificate-absent TLS refusal before media on the previous
Host3 build. Genuine>=300s, third-party rendering, touch effect, Host Stop and
remaining lifecycle/timing evidence are pending.

## Scope, packages and privileges

This is the separate Owner-authorized PAV-1 experiment for
`spike/fold6-rg-projector-av`, based on
`38cb5f7ec8d320ef9833a36840ccec3febdbc885` and donor
`03de921e2400147842ce6c56101b58d514dde77c`. The accepted #66
[partial feasibility result](fold6-feasibility.md) retains its actual IME,
audio, lock and sensory limitations. This checkpoint does not complete AV
projection, optical readability, head input, Phone IME, acoustics or automatic
startup. Production browser code and protected articles remain unchanged.

The current host-only build targets `com.code2hack.eyebrowse.projector.directhost.r5`.
It has not been installed or qualified as unused on the Phone. Installed R4
and its evidence remain retained; R4's original installation transaction remains UNKNOWN. A distinct read-only
query qualified its current whole installed APK, package, UID, version and user0. The previous
`.directhost.r3` artifact had a matching installed-byte cut and remains retained,
as do the original `.directhost` and r2's unknown old transaction. RG main
`.directrg.r3` had a fresh matching installed-byte cut before the uncertain Host4
install; its previous test package `.directrg.r3.probe3` remains retained. New test package
`.directrg.r3.probe4` installed once into its unused ID, with explicit Success and
complete installed APK equality. Earlier test
and RG experiment packages remain retained. Every installation targets an unused
ID; no existing app was updated, cleared or uninstalled.

Host5 moves the fixture's `active=true` transition from `onCreate` to `onStart`,
so the same stopped instance restores its status when started again. `onStop`
still records inactive status. Tap ACTION_UP and draw counters are unchanged.
The affected host build and four unit tests passed; lint reports zero errors
and the existing target-SDK warning. Compiled lifecycle bytecode confirms the
status transitions. Actual Android restart and rendering validation remain
unexecuted; this fix does not explain earlier black frames, null focus or the
generated touch with no app effect.

The later158 diagnostic retained a complete owned-inactive context and exactly
one display0 focus field whose value was explicitly null, with zero user0 token
matches. The unchanged selector returned73 after six complete source frames.
Owned teardown and two secure locked/showing/Dozing/OFF samples qualified;
Phone wake, sleep, Go, PixelCopy and touch stayed zero. This new sample does
not recover021's unretained predicate or establish a cause. A separate159
restricted wake-scalar consumer is host-prepared with real use disabled;
complete129 and all authentication holds remain unchanged. No new physical
case or product acceptance follows from that preparation.

The installed Phone application is sandboxed. Its Stop screen does not acquire
capture/input privileges. An explicitly authorized shell-UID2000 `app_process`
loads the Kotlin bridge from the APK. Target-app launch is a separate bounded
ADB bootstrap step. There is no network command to launch an arbitrary app or
shell, no root/boot service and no PC media gateway.

Neither current application links a browser, WebView/Chromium, Mihomo, proxy
profiles or browser assets. Clipboard synchronization, audio and microphone
capture are disabled. The old hostprobe fixture is not a dependency.

## Stock server and direct transport

The unchanged upstream server is scrcpy **5.0.1**, source
`a60891aea193d92e7e5c3942700eca63f9d19a5f`, SHA-256
`764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`.
Attribution remains in [upstream.json](upstream.json). The ordinary-APK socket
denial and shell socket success from #66 retain their original scope.

The bridge binds one configured private interface, never a wildcard. Platform
TLS1.2/1.3 requires the mission client identity before a server/display or media
stream is created. RG also checks the server address against the certificate
SAN. One authenticated stream is allowed; another authenticated connection can
request Host Stop. Reverse AF_UNIX video/control sockets remain device-local.
No raw scrcpy or new unauthenticated ADB/control listener is exposed.

Stock [CleanUp.unlinkSelf](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/CleanUp.java)
deletes `Server.SERVER_PATH`, the first Java classpath entry. The initial bridge
incorrectly reused that file. The corrected host creates a distinct session JAR with
`CREATE_NEW`, hashes the copied bytes against the fixed server hash and launches
only after equality. Upstream cleanup consumes that copy; the master remains
available for the next explicit connection. Existing session paths are never
overwritten. No upstream capture/cleanup code is forked.

The explicit bridge lifetime is fifteen minutes, with earlier Host Stop. This
finite process lifetime is separate from mission duration. Its stock options are:

```text
5.0.1 scid=<new-31-bit-id> log_level=debug tunnel_forward=false
send_dummy_byte=false send_device_meta=false send_codec_meta=true
send_frame_meta=true video=true audio=false control=true video_codec=h264
video_bit_rate=2000000 max_fps=30 new_display=480x640/240
display_ime_policy=local vd_destroy_content=true clipboard_autosync=false
power_on=false cleanup=true
```

Owned product003/004 recorded `c2.qti.avc.encoder` on VD21/22 and
`c2.qti.avc.decoder` on RG, with480×640 output, full drawable fit and rotation0.
The older smoke003 encoder name was not captured and remains unknown.

## Framing and bounds

The pinned [Streamer](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/Streamer.java)
starts with a four-byte codec ID. A twelve-byte geometry record contains
`0x80000000 | clientResize`, width and height. Encoded records contain a 64-bit
PTS/flags word and 32-bit byte length; configuration uses bit62, keyframe bit61
and PTS bits0–60. The pinned
[ControlMessageReader](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/ControlMessageReader.java)
receives the validated 32-byte finger DOWN/UP messages.

TLS requests contain magic `PAV1`, revision1 and operation1(stream) or2(Host Stop).
Welcome repeats magic/revision, Java UTF `5.0.1`, H.264 ID `0x68323634` and
`audio=false`. Subsequent envelopes are channel byte, 64-bit stamp and 32-bit
payload length, followed by the payload.

| Channel | Meaning |
| --- | --- |
| 0 | Geometry: two 32-bit dimensions |
| 1 | Unchanged encoded video packet and upstream PTS/flags |
| 2 | Reserved audio packet with the same upstream PTS/config flags |
| 3 | Tap: x, y, current video width and height |
| 4/5 | Ping/pong with an echoed RG monotonic timestamp |
| 6 | Session end |
| 7 | Reserved four-byte audio codec ID before audio packets |

Audio is not negotiated or decoded here. Its configuration/extradata framing is
reserved for PAV-04. Full head/keyboard integration belongs to PAV-03.

RG preserves aspect ratio, rejects black-border taps and fails on unsupported
crop. Packets are capped at1MiB and dimensions at4096 per axis. Decoder input
waits at most50ms; at most eight inputs and sixteen Surface callbacks are
tracked. The tap executor has no pending queue. Two-second stalled writes close
the connection; finite reads, handshake limits and one-second echo probes detect
loss. Pause, Surface destruction and Stop close the session and discard input.
There is no automatic command replay.

## Provisioning and measurement

Session directories outside Git contain private `identity.p12`, `password`,
`peer.crt` and `endpoint.properties` files. No identity, password, credential
hash, browser payload or original private path is published. System trust,
network, lock, IME and audio policy are unchanged.

The RG/API32 loader rejected the first PKCS12 container's SHA256 MAC. The same
experiment key/certificate/password exported with PBE-SHA1-3DES, SHA1 MAC and
2048 iterations loaded successfully. Platform TLS, explicit peer trust and SAN
checks stayed unchanged; the old container remains retained. Smoke003 then
observed a genuine TLS1.3 media connection.

After current identity, reservation, source, unused-package and private
configuration checks, stage the matching host APK and pinned **master** JAR in
the new shell-owned mission directory:

```sh
CLASSPATH="$HOST_APK" app_process / \
  com.code2hack.eyebrowse.projector.directhost.ShellMain \
  "$PRIVATE_SESSION_DIRECTORY" "$PINNED_MASTER_JAR"
```

The APK is a dex container for the explicit shell process. It does not give the
installed app shell authority. Observe the exact owned server PID/SCID and its
new display ID before launching the harmless target. Keep the existing debug
SSH/ADB service. The current credential-free path creates no desktop control
forward; actual direct endpoint/process evidence is still required.

Owner decision106 retired repeated full old-store backups/equality/reconstruction
and restoration checks as prerequisites for these isolated tests. Existing cuts
remain historical evidence; newer device bytes remain primary and untouched.
All exact redundant backup clients were already ended, with zero signals. Cut008
remains NOT_READY. No new old-store scan or restoration rehearsal follows that
decision. Original application lifecycle/data changes remain outside scope.

The RG counter measures received packets and actual Surface callbacks. Smoke003
reported an impossible roughly1.337e9ms render delay, so that metric is invalid.
RG3 measures packet-read to callback **receipt** with `System.nanoTime()` at both
ends, named `packetReadToCallbackP95Ms`. Product003's setup sample reports
10.729323ms and a same-RG-clock echo RTT p95 of104.24151ms, with402 rendered and
403 changing packets. These are one pilot sample, not a stable frame-rate
interval, an optical latency measurement or cross-device input-to-visible time.

The current credential-free orchestration launches the shell bridge and its own
virtual-display fixture without a Phone display0 Activity, unlock or screen
caller. The on-RG driver first qualifies authenticated geometry for fixture bootstrap,
waits at most180 seconds for Go, then separately demands real streaming before
starting310 genuine seconds. It supports a scheduled software-generated tap or
a zero-touch video case, followed by authenticated Host Stop. Product007 sent one
generated event. Video014 and later019 stopped before a completed measurement Go.
No experimental desktop control forward was created. Debug SSH/ADB remains
separate from the direct media/control connection. A scheduled tap can establish
controlled-path app effect only; it cannot establish physical head/IME or
optical acceptance.

## Bounded Surface diagnostic

The existing on-RG instrumentation has a `surface-diagnostic` operation. It waits
for a distinct external Go after fresh owned-fixture, RG focus, geometry, counter
and direct-endpoint checks. It accepts no scheduled pointer input. The runner
selects its single shown, focused480×640 SurfaceView, submits one
[PixelCopy request](https://developer.android.com/reference/android/view/PixelCopy),
and waits at most five seconds for the callback. The report retains a fixed API
result category and same-RG-clock request-to-wait duration. A successful copy is
saved to a new exclusive PNG; a timeout never triggers another request.

The API reads the Surface's most recently queued buffer. Its result can help
separate decoder-Surface data from the composed screenshot, but establishes no
composed visibility, optical output, genuine300-second interval or input effect.
Normal video/touch pixel eligibility remains unchanged. The private orchestration
now retains fixture, renderer, geometry and pixel predicates before rejecting a
normal pixel check. Its diagnostic-only image classification cannot admit touch
or normal video acceptance.

The new test APK is38,734 bytes, SHA-256
`0e46832d5d384d46ee2faff60184a02458ecd71587f9b606cffc1b1bbc4e0151`.
Signature, package, target and runner were independently checked before install.
RG debug/test assembly and lint completed in14 seconds with zero errors and one
existing `OldTargetApi` warning. Ten affected installer, thirteen orchestration
and eight restored-power-branch host cases passed. Those host checks made zero
Android PixelCopy calls. Earlier unrelated suites were not rerun.

The initial wrapper-path failure, dummy-fixture errors and pre-admission missing
proof are retained. No failed or uncertain device action was replayed. Four
public host-only dummy temporary directories were automatically removed by test
contexts; their exact random names/metadata were not retained. Harness sources,
proofs and actual outcomes remain. No Owner/device/credential record was affected;
future host fixtures remain until the version-end cleanup boundary.

## Executed evidence and remaining criteria

The [sanitized evidence index](direct-video-evidence.json) binds source, frozen
APKs, checks and private record hashes. JDK17/SDK35/AGP8.7.3/Kotlin2.2.21/Gradle8.11.1
builds use the shared lock, two workers and a2GiB heap. Nine protocol/mTLS JVM
cases passed previously and their sources remain unchanged. Three new host
cases passed: self-unlink followed by another session, wrong-byte rejection and
existing-path preservation. Both affected applications and the RG test build;
lint reports zero errors and the retained target-SDK warning. Signatures verify.

Smoke003 observed an80,043ms on-RG interval: TLS1.3, Phone-to-RG peer addresses,
VD18, 2,109 received/changing/rendered frames and approximately25.93fps at the
last sample. The Phone independently counted2,124 transmitted frames through
closure. RG drawable/fit were480×640 with rotation0 and no letterboxing. The
captured vendor onboarding is public content; the Display settings component
was resumed on VD18, but a normal Settings page was not visually established.
Its following new-stream Host Stop probe failed with zero frames and an8,016ms
startup. The subsequently missing master JAR and upstream self-unlink source
explain the restart failure. This old result is retained, not upgraded by the
untested-on-device host fix.

| Criterion | Actual result at this checkpoint |
| --- | --- |
| Isolated build/dependencies/install | Build/affected checks PASS; current Host4/RG3/Probe4 whole installed bytes match; old Host4 transaction remains UNKNOWN |
| Third-party VD content to real RG Surface | Earlier product003/004 owned pixels retained; current019 composed image is black despite native eligibility; third-party app still pending |
| Harmless touch with observed app effect | Product007 generated one on-RG event; actual fixture counter/image stayed0; NOT_PASSED |
| At least300 genuine seconds without PC media/control relay | Unexecuted |
| On-device unauthorized peer refusal before media/input | Actual security009 no-client-certificate refusal before media on Host3; Host4 live verification pending |
| Reconnect/Host Stop/RG pause/no stale input | Unexecuted for current host; old second-stream failure retained |
| Moving FPS, codec/crop and latency | Current encoder/decoder/fit and scoped setup metrics observed; genuine moving interval pending; old smoke003 latency remains invalid |
| Audio/head/IME/optical/acoustic behavior | Not claimed |

The private socket observer had a source-proven conditional layout defect: a
valid `tcp ESTAB` row was rejected by a first-field `ESTAB` test. A distinct
strict observer passed38 public socket/error cases and nine orchestration and
binding checks before product004. It kept unknown/source-error data negative;
its actual rejection does not identify product003's old cause. A separate pure
validator now checks the existing platform `SSLSocket` endpoint metadata and
exact closed-session SCID, with36 affected public host cases and six prepared
orchestration cases. Later product007 observed the actual authenticated direct
endpoints. The original rejected socket data remains negative. Manager137
replaced the exited debug route; a distinct137c reconciliation independently
settled product004's owned resources and sampled end state. Its initial unknown
teardown outcome remains retained.

## Historical operational observations

Earlier negatives remain: initial RG decor/insets crash, original PKCS12 loader
failure, smoke002 final native end-state contradiction, corrupt/failed/partial
preservation transfers, route009 unknown consumed outcome and the latest
read-only native/physical qualification timeouts. No failure or credential
attempt was reset. A specific Manager resolution permits future qualified
caller admission while leaving smoke002 failed and consumed.

Smoke003 ended WORK_FINISHED with no credential read, verified control teardown
and locked/showing/Dozing/display0OFF. Independent readback found its bridge,
upstream process and port44367 absent after exact owned fallback TERM. Graceful
Host Stop was not passed; VD18's final disappearance was not separately captured.
The later status read timed out25s; one read-only reconciliation failed at the
existing physical guard before new install/caller/input. A correlated Manager
capability event then allowed one bounded existing-primary qualification, which
passed. A fresh native pre-install cut was locked/showing/Dozing/display0OFF.
One unused Phone r2 install returned1, with no Android failure code or Success.
The one read-only package reconciliation was rejected by the guard before ADB:
the previously pinned debug SSH PID was absent. Independent host observation
also found its selected listener absent. The installation outcome remains
unknown; its failure cause and tunnel exit cause are unassigned. No consumed
install or connect was replayed. Route/binding lifecycle remains under Manager
custody. Secure cuts are historical, not continuously verified state. No shared service was
reset or signaled by this Worker. Both-device reservation and old Worker64 tab/hook/resource-return
obligations remain; there is no old-state overlay or assumed resource return.

The Manager's replacement tunnel/pin handback changed only operational record
references and source digests; credential/admission/end-lock logic remained
unchanged. Fresh actor/SDK/kernel/client/server and replacement tunnel checks
passed. The exact selected transport was absent. One newly authorized host-connect
returned0 but printed **failed to connect**. That is a failure, not authentication.
Execution stopped before physical/package reconciliation or credential work,
with no further registration query or replay. Host r2 remains UNKNOWN. An
exclusive private intent had been closed before the connect, but the helper
lacked explicit fsync. This durability gap is retained without retroactive PASS.
The corrected private helper syncs the file and parent directory before return;
three host-only cases verify ordering, existing-record preservation and abort
on sync failure. No production device, network or credential calls ran in those
cases. All failed112 records remain intact.

A new persistent SSH binding and event114 authorized one distinct, durably
recorded selected-host registration. It succeeded, followed by exact Fold6,
user0 and shell qualification. The complete r2 query returned no package path,
but did not settle the old installer transaction. The fresh unused Phone r3
APK was staged device-local, independently hashed, installed once and read back
with the same full installed APK hash. All three current installed APKs, private
configuration, staged product server and direct interfaces then matched their
source-bound preflight. No original application/store traversal occurred.

The distinct `w67-five-minute001` journal was admitted. Its first control-helper
command, a SHA256 check of that helper's expected stock master JAR, returned
nonzero before SCID, control process or forward construction. The product's
verified master was in a different mission namespace. The immutable helper did
not retain that command's result buffers, so this failure alone does not prove
a missing file or a transport cause. An injected host-only check of the exact
class confirmed the failure boundary with zero actual device or credential IO.
The actual caller reported `FAILED_OR_UNKNOWN_NO_RETRY`, credential consumption
`NOT_ATTEMPTED`, and no product work. No bridge or virtual display was started.

The parent stopped only its own RG package, retained the failed journal and
observed an Awake/unlocked Phone. One recorded ordinary sleep223 was followed
by two independent locked/showing/Dozing/display0OFF reads. No PIN, new caller
or control setup was used for settlement. The old smoke002 resolution exempts
only that exact old record; it does not exempt this new consumed journal. Both
failures remain unchanged.

Manager116 authorized a separate control-only stock JAR in a new exclusive
mission namespace. Staging and independent device readback verified its pinned
SHA,733,930 bytes, shell UID2000, directory0700 and file0600. No old master was
reprobed or overwritten. That setup's native cut was Awake/unlocked; it made
no input or credential call. The earlier secure cuts remain historical.
A separate caller candidate adds only the dedicated path and an exact001
admission resolver. All existing authentication, one-PIN, journal, privacy,
end-lock and old002 validator functions remain unchanged. Sixty-three affected
dummy-only host cases pass, with zero production loader/status/device calls.
Manager117 independently qualified and activated that exact caller and its
two specific old-record resolutions. The consumed old IDs remain non-replayable;
every other failed or unknown record still holds.

The test driver's initial60-second setup wait included native end-lock and
control settlement, each potentially requiring slow readbacks. Its bounded
setup wait is now180 seconds; the real-clock310-second measurement still starts
only after the explicit go signal. The new unused `.r3.probe2` test APK builds,
passes lint and signature verification, and matches its full installed hash.
The current RG application APK remains byte-identical and was not replaced.
The distinct `five-minute002` attempt used those current bindings and the
qualified117 caller. The control-only stock JAR passed its initial hash guard
and the owned control forward started. The native keypad prerequisite then
rejected `NATIVE_SYSTEMUI_OWNERSHIP_REQUIRED`, before keypad XML capture,
credential read/submission or product work. The precise current/expected
focus, owner-token or state difference was not retained, so its cause remains
unproved. Three exact-function dummy cases reproduce that boundary without
actual native, XML, credential or device calls; they do not identify the actual
cause or relax the guard.

The caller remains `FAILED_OR_UNKNOWN_NO_RETRY`, with `submissionIntent=false`,
`workRan=false`, verified relock and verified owned control settlement. Its one
forward was removed and its local listener is independently absent. No product
bridge, virtual display or RG measurement driver started. Parent teardown
stopped only the owned RG app; three native cuts were already locked, showing,
Dozing and display0OFF, so it sent zero extra sleep inputs. The new consumed
journal is outside the exact old001 and old smoke002 resolutions. Further
caller admission awaits an exact Manager disposition; no ID was replayed.
The five-minute/tap/Host Stop, lifecycle, on-device peer-refusal and current
timing criteria remain pending, as do independent exact-candidate review and
Manager acceptance.

Manager118 authorized one credential-free context observation. Its initial
native read took21.2 seconds, of which20.3 seconds was the shell command and
parsing reported0 at100ms resolution. Initial state was Awake/unlocked. One ordinary
initial sleep was sent, but its next native read and the final-before read both
reached the unchanged25-second ADB bound. No wake, final sleep, XML, credential,
authentication entry or product operation ran. The requested `s1`/`s2` pair
was never obtained, so032's failing predicate remains unknown. Owned control
forward removal/local absence and standard close behavior are separately bound;
the final native end-state remains **UNVERIFIED**.

The one additional read-only comparison measured six status components at
0.8–4.5 seconds; the window-ownership dump reached its10-second individual
comparison bound. No aggregate was parsed after that missing component. This
isolates a slow read without establishing its execution, transfer or service
cause. Thirteen observer and three comparison host prechecks passed with zero
production IO. No speculative status/selector correction was prepared. All
failed records, original limits and the current guard remain unchanged.

Manager119 authorized three distinct read-only purposes, each at most once.
Installed help confirmed the window identifier selector in1.0 second. The
focused-window qualification command completed in6.0 seconds with complete
frames and zero command returns. A fresh strict-hex display0/user0 focus token
matched the single returned window. The unchanged ownership validator then
rejected its exact SystemUI binding. The observer retained the aggregate
rejection, without the individual ownership subpredicates, so the specific
mismatch is unknown. Raw private window output was transient and unretained.

The third, complete-status purpose was not run after that rejection. No retry,
fallback, power, control, credential, XML or product operation ran. Forty-three
synthetic host checks passed before device reads; no source/parser guard was
weakened. There is no status/caller correction or new native end-state claim.
The consumed five-minute002 record retains its original bytes and full node
metadata. The Phone/RG source/APK capsule remains unchanged, and all required
product evidence remains pending.

Manager120 identified a host classification flaw: the119 observer demanded
SystemUI ownership before parsing otherwise complete native state. The separate
120 observer accepts complete unlocked or locked foreign-focus observations as
read data while the unchanged authentication predicates reject them. Missing,
duplicate, inconsistent or ambiguous native fields still fail. Its fixed
diagnostics contain counts and booleans for each required ownership check;
private titles, packages, UIDs and tokens remain unexported. Fifty-three affected
synthetic host cases passed before the single new capture.

That actual capture returned0 in2.9 seconds with nonempty stderr. The observer
rejected it before parsing framed stdout, losing the requested diagnostics on
that error path. Raw buffers were transient and are now unrecoverable. The
stderr cause and actual native predicates remain unknown; no read was retried
and no power, authentication or product operation ran. A separate prepared
response correction retains safe stderr and framed-native diagnostics before
rejecting any nonempty stderr. Eleven new host cases passed; earlier functions
and their53-case proof remain AST-bound without a rerun. This prepared correction
has not run on the device or replaced the live caller. The consumed records,
native end-state uncertainty and all pending product criteria remain unchanged.


## Current correction and evidence boundary

The Host4 termination record retains the first winning close category and the
startup stage at that point. Milestones use the same Phone `System.nanoTime()`
origin, with-1 for an unobserved milestone. These diagnose future sessions;
they do not recover the cause of the earlier closed zero-frame session.
Startup8s, write2s and bridge15-minute limits are unchanged. No exception text,
UI payload or key material enters those categories.

Probe3's corrected bootstrap accepts only authenticated direct geometry with
zero queued touches. It authorizes fixture setup, while the existing actual
rendered/changing-frame, exact owned focus/geometry and public-pixel guards
continue to gate Go and input. The prior ordering had a source-proven circular
precondition on an otherwise empty virtual display. That public counterexample
does not assign the cause of actual008 or012. Twelve cases compiled from the
exact Kotlin predicate passed. The new session-category unit test passed;
Host4 build/lint and Probe3 assembly passed without rerunning unchanged suites.

Actual008's fixture launch returned a Permission Denial/SecurityException;
its individual predicate/cause was not retained. Actual012 stopped before the
Activity command at a current owned-process guard. Neither released Go, a tap,
measurement or Host Stop. Both retained their own teardown and two secure native
cuts. No power, codec, protocol or keyguard cause is inferred from those failures.
Actual007's generated touch still lacks Android injection/app-effect proof.
The optional one-wake comparison remains unused.

Actual security009 sent one no-client-certificate connection from RG. It was
refused before media welcome; the same bridge's refusal counter changed0->1,
stock children stayed0, and previous session bytes stayed unchanged. Only-owned
teardown and two secure end cuts passed. This is scoped certificate-absent
refusal evidence on Host3, separate from Host4 verification and broader security
or lifecycle acceptance.

The original Host4 installer also recorded a false/true/false lock sequence
after its one final sleep. That contradiction remains unassigned. Reconciliation144
obtained a new complete unlocked/Awake before cut, one newly journaled sleep,
and two complete locked/secure/showing/Dozing cuts. It does not repair history or
prove continuous state. Its package query returned70 before package data because
the wrapper used an unsupported `whence` option. Twenty-three affected host cases
preceded that run; a separate known-builtin-guard correction passed five mode
cases and remains unexecuted. The original installer is never reentered.

Normal, nonzero and timeout install buffers now have a separate prepared
CREATE_NEW/file-and-directory-fsynced retention helper. Six affected host cases
cover availability, existing records/symlinks, privacy and fsync failure. The lost
original normal stdout remains unrecoverable. Probe3 installation used a
separate RG-only helper after positive current Host4 bytes and completed144
sampled end-state reconciliation. Its normal250B response was retained before
classification and its full installed bytes match. The distinct145 read-only
format correction accepts either supported UID label exactly once, still
requiring equality to the independently queried user0 UID; duplicates and
conflicts remain rejected. Video014 later passed setup and stopped before a completed Go/start observation;
its measurement and necessary owned teardown did not pass.
Owner106 continues to retire repeated original-store backups and scans.


Video014's source-bound setup rendered the actual public moving fixture on RG,
with strict resumed/focus/UID/window/display/geometry checks, VD28 and the
`c2.qti.avc.encoder` / `c2.qti.avc.decoder` pair. The agent inspected the exact
new image and observed `Visible taps: 0`. Current direct authenticated socket
and owned producer checks passed before Go. The next operation raised
`TimeoutExpired`. Its exact geometry-versus-Go boundary was not separately
journaled, so remote Go/start outcome is UNKNOWN; no completed interval or
Host Stop is claimed. Zero touches were scheduled and zero Phone power input
was sent in this case.

All attempted only-owned teardown checks then failed or were unavailable.
Bridge/stock child/fixture/VD/listener settlement and current native end state
remain UNKNOWN. A later local host cut found both previously bound SSH PIDs
absent. No old stop/Go command, connect, route recovery or producer signal was
replayed. Exact owned bindings and partial outcomes were returned to the Manager.
A separate host-only orchestration correction records each pre-Go stage and
an exclusive durable Go intent/result; five actual injected timeout seams pass.
It has not run on-device and does not recover video014's lost boundary.
