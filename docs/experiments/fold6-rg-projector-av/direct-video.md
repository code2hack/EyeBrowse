# Direct Fold6 video and control — PAV-02 / issue 67

Component checkpoint, **not CANDIDATE_READY**. Smoke003 delivered 80 genuine
seconds of authenticated direct Fold6 video to the real RG hardware decoder.
The captured public vendor screen is Samsung DeX onboarding; its Start button
was not pressed. Harmless touch, the five-minute interval, on-device unauthorized
peer refusal and the complete lifecycle cases remain unexecuted.

A subsequent stream exposed stock scrcpy's self-unlink behavior: its cleanup
process deletes the JAR from which it was started. The corrected Phone host
keeps a pinned master and gives every authenticated session a new verified
copy. Three focused regression tests, the Phone build and lint pass. This new
Phone package installation is **unknown**: a failed streamed install returned
no Android failure code, and its one read-only reconciliation stopped before
ADB because the pinned debug tunnel PID was absent. No install was replayed or
new credential caller admitted. Phone work is held at this concrete binding
failure; source/build work has continued independently.

## Scope, packages and privileges

This is the separate Owner-authorized PAV-1 experiment for
`spike/fold6-rg-projector-av`, based on
`38cb5f7ec8d320ef9833a36840ccec3febdbc885` and donor
`03de921e2400147842ce6c56101b58d514dde77c`. The accepted #66
[partial feasibility result](fold6-feasibility.md) retains its actual IME,
audio, lock and sensory limitations. This checkpoint does not complete AV
projection, optical readability, head input, Phone IME, acoustics or automatic
startup. Production browser code and protected articles remain unchanged.

The compiled Phone package is `com.code2hack.eyebrowse.projector.directhost.r2`;
its installation is unqualified at this cut. The original experimental `.directhost` remains
installed. The current RG app and test are `.directrg.r3` and `.directrg.r3.test`,
with the common prefix `com.code2hack.eyebrowse.projector`. Their installed APK
bytes match the frozen artifacts. Earlier RG packages `.directrg`, its `.test`,
`.directrg.r2`, `.r2.test` and `.r2.diag1` remain retained. Every installation used
an unused ID; no existing app was updated, cleared or uninstalled.

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
incorrectly reused that file. Host r2 creates a distinct session JAR with
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

Debug logs in the private experimental namespace allow the next actual run to
record the selected encoder. Smoke003's encoder name was not captured and is
not inferred from the requested H.264 codec. The measured RG decoder was
`c2.qti.avc.decoder`, with 480×640 output, full crop and rotation0.

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
SSH/ADB service; remove only the test's own desktop control forward before the
no-PC interval.

Owner decision106 retired repeated full old-store backups/equality/reconstruction
and restoration checks as prerequisites for these isolated tests. Existing cuts
remain historical evidence; newer device bytes remain primary and untouched.
All exact redundant backup clients were already ended, with zero signals. Cut008
remains NOT_READY. No new old-store scan or restoration rehearsal follows that
decision. Original application lifecycle/data changes remain outside scope.

The RG counter measures received packets and actual Surface callbacks. Smoke003
reported an impossible roughly1.337e9ms render delay, so that metric is invalid.
RG3 measures packet-read to callback **receipt** with `System.nanoTime()` at both
ends, named `packetReadToCallbackP95Ms`. It has not yet been measured on-device.
Echo RTT uses one RG clock. Neither value is optical latency or cross-device
input-to-visible latency.

The prepared on-RG driver waits for guarded setup/end-lock/control settlement,
then measures310 genuine seconds, schedules one focused synthetic tap at155s
and requests authenticated Host Stop on the same stream. It has not run. A
scheduled tap can establish controlled-path app effect only; it cannot establish
physical head/IME or optical acceptance.

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
| Isolated build/dependencies/install | Build/lint PASS; RG3 installed; corrected Phone r2 install UNKNOWN |
| Third-party VD content to real RG Surface | Public vendor onboarding rendered in smoke003; normal Settings page and current encoder identity incomplete |
| Harmless touch with observed app effect | Unexecuted |
| At least300 genuine seconds without PC media/control relay | Unexecuted |
| On-device unauthorized peer refusal before media/input | Unexecuted acceptance case; four JVM mTLS cases passed |
| Reconnect/Host Stop/RG pause/no stale input | Unexecuted for current host; old second-stream failure retained |
| Moving FPS, codec/crop and latency | Smoke003 frame/crop/decode observations; its latency invalid; current metric unmeasured |
| Audio/head/IME/optical/acoustic behavior | Not claimed |

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
unknown; its failure cause and tunnel exit cause are unassigned. No install,
connect, tunnel, input or credential action was replayed. Current route/binding
reconciliation belongs to the Manager under existing custody. The last secure
cut is historical, not a continuously verified state. No shared service was
reset or signaled by this Worker. Both-device reservation and old Worker64 tab/hook/resource-return
obligations remain; there is no old-state overlay or assumed resource return.
Independent exact-candidate review and Manager acceptance remain pending.
