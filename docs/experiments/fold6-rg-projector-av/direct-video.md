# Direct Fold6 video and control — PAV-02 / issue 67

## Interrupted decoder cleanup correction — current receiver

Independent review2 resolved R67-1/R67-2 and found R67-3: error cleanup can run
on an executor worker interrupted by its own `shutdownNow`. The decoder's join
then throws before native release. `SurfaceDecoder.close` now releases the codec
in `finally` around the unchanged bounded join and restores the caller interrupt
after cleanup. Decoder publication, initialization and protocol remain unchanged.

A focused JVM regression holds the output thread live and closes from an already
interrupted caller. The old source releases zero codecs; the corrected source
releases exactly once, retains the interrupt and permits repeated close safely.
All four decoder lifetime tests pass. RG build/lint pass with zero errors and the
existing `OldTargetApi` warning. This is an automated interruption reproduction,
not a live hardware fault-injection claim.

The current same-ID RG3 APK is 2,559,958 bytes, SHA-256
`fb0d2036af77ba2d4cb1bfecc403659613041113837d8f40c829135ac979e35b`.
Installation returned Success and independent full installed bytes match.
On the existing Owner173 USB debug route, local Stop passed after3,009ms of
measured streaming (75 additional rendered frames, state STOPPED). Reconnect
passed with both sessions STREAMING and5,015ms/122 additional rendered frames
after reconnect. The actual owned fixture was inspected on RG; zero touches.
Both use direct TLS1.3 RG10.199.167.154 → Phone10.199.167.157:44367.
HostR5 and Probe4 are unchanged.

All three stock sessions stopped with their children gone. Necessary teardown
found no owned bridge/stock process, remaining test display or44367 listener;
own apps were stopped. One normal final screen-off/check per device reported
Fold6 Dozing and RG Asleep. Manager debug transport/both leases remain retained.
Own cancellation stacks and slow frame-read warnings remain in private logs;
no underlying timing or old reconnect cause is inferred.

The new exact source/APK capsule is
`bc5b91f6aea2f8595f3ab32970736b47070892573ae983f3de7f0456fff3f4ff`.
Prior USB173 normal lifecycle and capsule159310-second/app-effect/security/
Settings evidence retain their earlier source bindings. They were not repeated
for this bounded cleanup correction. Renewed independent review and Manager
acceptance remain pending; no integration, release or full AV claim follows.

## Previous decoder review correction — USB173 checks

Independent review of `6740018` found two decoder lifetime leaks. The corrected
receiver constructs each decoder locally and publishes it under the same small
lifecycle lock that Stop uses to detach it. If Stop wins, the local decoder is
closed instead. Detached codecs are closed outside the lock. Initialization
failure now releases the allocated MediaCodec and rethrows the original error,
including failures before the output thread exists.

Three focused JVM tests pass against the production classes with test-only
platform doubles. They reproduce cancellation blocked inside configure, check
normal replacement/repeated Stop, and inject failures at name/configure/listener/
start, including a failing release. Both original leaks were reproduced before
the fix. The final RG build and lint pass with zero errors and the existing
`OldTargetApi` warning. These doubles are not packaged in the application.

The RG3 APK for those USB173 checks is 2,559,234 bytes, SHA-256
`c18e228eb8acd85a6cd4c5dc800c25341f1294d0d570bcde3ab57a3863d356ab`.
Ordinary same-ID installation and an independent full installed-byte read pass.
Both devices use the Owner173 u4090 USB route for debugging. Media/control still
use direct TLS1.3 RG10.199.167.154 → Phone10.199.167.157:44367.
HostR5 and Probe4 remain unchanged.

| Current-APK check | Actual result |
| --- | --- |
| Reconnect | Both connections streamed; reconnected interval20,063ms/515 additional rendered frames; zero touches |
| Local Stop | 2,008ms measured stream; STOPPED |
| RG pause | 2,006ms measured stream; STOPPED |
| Authenticated HostStop | 1,004ms measured stream; EOF/disconnect and stopped stock child |

Owned teardown found no bridge/stock process, tested VD42–53 or listener44367.
Own apps were stopped. One normal final screen-off/check per device reported
Fold6 Dozing and RG Asleep. These are sampled observations, not continuous state.
Those normal checks remain bound to the USB173 source/APK capsule.

Earlier negatives remain: the original120s update and35s read timed out with
unavailable response buffers. A new USB read established the old installed APK,
then authorized same-ID updates established the corrected bytes. Those new
observations do not rewrite the old unknown transaction. Reconnect trial173c
reached both streams but failed before measurement with an unclassified
IllegalStateException. Diagnostic trial173e recorded a receiver connection reset
and matching host WRITE_DEADLINE. Their underlying causes remain unassigned;
the later passing run does not retroactively explain them. The first empty173c
report read and unavailable optional thread observation are retained too.

Useful own-session exception stacks and slow frame-read/handling/status-write
logs are now retained. They change no protocol, buffer or deadline. The passing
instrumented trial emitted no slow-operation warning; its normal cancellation
Socket-closed log is not a failed reconnect. Private images/logs are hash-bound,
not published as raw payloads. The prior310-second run below keeps its original
capsule159 binding and was not repeated for these lifetime corrections.

## Previous completed direct run

The direct HostR5/RG3/Probe4 run under Owner decision167 demonstrates the
video/control result for independent review. An on-RG driver measured **310,066ms**
of uninterrupted streaming, with6,301 additional changing/rendered frames
(about20.32fps),31 streaming samples and direct TLS1.3 endpoints. Its one generated
RG event at(240,320) advanced the actual Phone fixture counter0→1; inspected RG
images show `Visible taps: 0` then `Visible taps: 1`.

Samsung's actual Display settings page also rendered on its independent VD36.
That run's certificate-absent refusal, reconnect with zero stale touches,
local Stop, RG pause and authenticated HostStop passed. Phone logs show all
owned sessions stopped and their stock children gone. Final observations find
no owned bridge/server, tested VD35–41 or Phone listener44367. One normal end
screen-off/check reported Fold6 Dozing and RG Asleep; no continuous-state claim.

Owner167 retired the extra private guard/observer/geometry/journal test-admission
stack. The successful runs used ordinary approved ADB for setup/debugging and
ordinary credential-free wake. Actual VD/stream/Surface geometry is runtime data;
Fold6 physical dimensions are not an admission whitelist. Earlier failures and
incomplete observations below remain historical evidence, not prerequisites.

This remains the video-only PAV-02 intermediate result. Audio playback, physical
head/touchpad/keyboard input, IME, optical/acoustic timing, reboot startup and
independent acceptance are not claimed. No PIN or credential consumer was used.

## Scope, packages and privileges

This is the separate Owner-authorized PAV-1 experiment for
`spike/fold6-rg-projector-av`, based on
`38cb5f7ec8d320ef9833a36840ccec3febdbc885` and donor
`03de921e2400147842ce6c56101b58d514dde77c`. The accepted #66
[partial feasibility result](fold6-feasibility.md) retains its actual IME,
audio, lock and sensory limitations. This checkpoint does not complete AV
projection, optical readability, head input, Phone IME, acoustics or automatic
startup. Production browser code and protected articles remain unchanged.

The installed test packages are `com.code2hack.eyebrowse.projector.directhost.r5`,
`com.code2hack.eyebrowse.projector.directrg.r3` and its `.probe4` instrumentation.
Host5 installed successfully; the163 read qualified its full signed APK,
user0/UID10445 and version. The direct167 source/APK capsule is
`3e7afead0d64d4913c1423be933e7906463c2a2d018ecefe7d5939989d2d77f1`.
Its compiled sources and installed packages were unchanged during that run.
The previous corrected USB173 source/APK/short-lifecycle capsule is
`dc8dcde6d8b5af0d2146679946d88a4f9b3d86a8c73729a7ff79cd8d72d31fc1`.
Earlier isolated packages and unknown historical install outcomes remain retained.
Owner167 permits updates to owned isolated packages for real code changes;
original EyeBrowse/Chromium applications and data remain outside this experiment.

Host5 restores `active=true` in `onStart`, preserving ACTION_UP and draw counters.
The affected build/four host unit tests passed; lint has zero errors and the
existing target-SDK warning. The actual Android same-task test now passes:
Settings covered the fixture (`active=false`,85 draws), then a HOT task return
restored `active=true` with90 draws, without recreating/resetting that counter.
This does not retroactively explain the earlier null/black/touch failures.

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

Use the installed isolated packages and the existing private configuration.
The explicit Phone shell bootstrap loads the host APK and pinned **master** JAR:

```sh
CLASSPATH="$HOST_APK" app_process / \
  com.code2hack.eyebrowse.projector.directhost.ShellMain \
  "$PRIVATE_SESSION_DIRECTORY" "$PINNED_MASTER_JAR"
```

The APK is a dex container for the explicit shell process. It does not give the
installed app shell authority. The stock session log supplies its runtime
virtual-display ID for the target launch. Keep the existing debug
SSH/ADB service. The current credential-free path creates no desktop control
forward. Direct167 recorded both actual Phone/RG socket endpoints and no PC
media endpoint or desktop scrcpy process.

Owner decision106 retired repeated full old-store backups/equality/reconstruction
and restoration checks as prerequisites for these isolated tests. Existing cuts
remain historical evidence; newer device bytes remain primary and untouched.
All exact redundant backup clients were already ended, with zero signals. Cut008
remains NOT_READY. No new old-store scan or restoration rehearsal follows that
decision. Original EyeBrowse/Chromium lifecycle/data changes remain outside scope.

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
a zero-touch video case, followed by authenticated Host Stop. Direct167a completed
that310-second moving interval and demonstrated one real fixture app effect.
Product007 and the earlier incomplete Go outcomes remain historical negatives.
No experimental desktop control forward was created. Debug SSH/ADB remains
separate from the direct media/control connection. A scheduled tap can establish
controlled-path app effect only; it cannot establish physical head/IME or
optical acceptance.

## Earlier optional Surface diagnostic

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

Direct167 used the approved stock ADB/debug routes, with one physical-target
check at test start. No old private gate stack was imported or executed. All
measurement timing and the scheduled touch ran on RG; the PC only bootstrapped,
read diagnostics and copied a few screenshots. Host session SCID479c710b confirms
TLS1.3 local10.199.167.157:44367/peer10.199.167.154,7,784 frames/one touch,
`HOST_STOP` and `serverAlive=false`. An unrelated host socket reused port44367;
its first port-only observation is retained and is not the projector endpoint.

The310-second interval uses counter differences, avoiding the earlier bootstrap
period in the session-wide average. All31 samples were STREAMING; every adjacent
sample added rendered frames. The≈9.05ms p95 is the final rolling RG
packet-read-to-render-callback-receipt sample, and≈99.95ms is echoed network RTT.
These do not measure optical display, one-way network or input-to-visible time.
Configured bounds are1MiB per packet,8 pending decoder inputs,16 pending render
callback records and no queued taps. A live corrupt-codec fault was not injected.

The first read of the pause report timed out after30s. Its initial partial
buffers were not saved; the failure remains recorded. A later ordinary read
returned the completed passing report, and final HostStop completed without a
route reset or test replay. The initial app-maps read was unavailable; a later
selected app memory report observed `WebViews: 0`. Full owned logs/results and
public fixture/Settings/disconnected images remain private with hashes in the
sanitized index. No original app/store traversal or backup was performed.

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
| Isolated build/dependencies/install | HostR5/RG3/Probe4 run beside original apps; protocol-only RG dependency, no browser/proxy APK assets, actual RG process WebViews=0; old Host4 transaction remains UNKNOWN |
| Third-party VD content to real RG Surface | Actual Samsung Display settings page on independent VD36 visibly reaches RG; direct167c15,071ms stream and HostStop pass |
| Harmless touch with observed app effect | Direct167a generated one RG event at(240,320); Phone counter and inspected RG pixels0→1. Product007 remains NOT_PASSED |
| At least300 genuine seconds without PC media/control relay | Direct167a310,066ms,31 streaming samples,6,301 new changing/rendered frames; both app endpoints direct, no PC media endpoint/desktop scrcpy |
| On-device unauthorized peer refusal before media/input | Actual HostR5/RG3 certificate-absent request refused before media welcome; unauthenticatedRefused0→1, no stock child |
| Reconnect/Host Stop/RG pause/no stale input | Current reconnect10,027ms with touches0; local Stop5,020ms→STOPPED; pause5,013ms→STOPPED; authenticated HostStop closes stock/server and listener. Old failures retained |
| Moving FPS, codec/crop and latency | Measured moving interval≈20.32fps; c2.qti.avc.encoder/decoder,480×640 fit/rotation0. Last rolling packet-read→callback-receipt p95≈9.05ms; echo RTT p95≈99.95ms. No optical/one-way claim |
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

### Earlier checkpoint through Surface021

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

### Retained operational records

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


## Earlier correction and evidence boundary (historical)

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
