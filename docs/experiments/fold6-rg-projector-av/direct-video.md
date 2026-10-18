# Direct Fold6 video and control — PAV-02 / issue 67

Incomplete implementation checkpoint: host builds and nine protocol/TLS tests
pass. Device installation and direct-path verification are blocked by an observed
RG connection failure before the first install. This is unavailable physical
evidence, not a negative feasibility result. No successful AV projection, optical
readability, head input, Phone IME operation, acoustic output or reboot-independent
startup is claimed.

## Scope and source

This is the separate Owner-authorized PAV-1 experiment, targeting
`spike/fold6-rg-projector-av`, with prerequisite merge
`38cb5f7ec8d320ef9833a36840ccec3febdbc885` and donor
`03de921e2400147842ce6c56101b58d514dde77c`. The independently reviewed #66
[partial feasibility result](fold6-feasibility.md) remains partial. Its original
IME failures, timing corrections/skips, missing sensory evidence and historical
unlock failures are retained. Operational unlock qualification is separate from
projector acceptance. No production browser or protected article changes belong
to this experiment.

New install IDs are `com.code2hack.eyebrowse.projector.directhost` and
`com.code2hack.eyebrowse.projector.directrg`, plus the latter's test package.
Neither APK links the Phone/RG browser, WebView/Chromium, Mihomo, proxy profiles,
browser assets, microphone capture or clipboard synchronization. The old PAV66
fixture is a separate module and is not a dependency of either APK.

## Runtime boundary

The installed Phone APK is an ordinary sandboxed application. Its local Stop
screen does not acquire capture/input privileges. An explicitly authorized
`app_process` bootstrap runs the Kotlin `ShellMain` as Android shell UID 2000.
It verifies the unchanged upstream server JAR before execution. The bridge owns
only that child, its new virtual display and its local sockets.

The pinned upstream version is **5.0.1**, source
`a60891aea193d92e7e5c3942700eca63f9d19a5f`, server SHA-256
`764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`.
Upstream attribution and artifact provenance remain in [upstream.json](upstream.json).
The ordinary-APK/Termux socket denial and shell-local socket success from #66
are not silently reclassified as ordinary APK privileges.

After mutual TLS and a valid stream request, the bridge creates a random named
AF_UNIX listener. The matching stock server connects to it in reverse mode:
video first, then control; audio is explicitly disabled. There is no network
listener for raw scrcpy and no remote shell command API. The new TCP endpoint
binds one explicitly configured private interface address, never a wildcard.
One authenticated streaming session is allowed. A second authenticated request
can stop the host. Unauthenticated peers cannot create a server/display, receive
media or inject input.

The bridge's explicit bootstrap ends after fifteen minutes or an earlier Host
Stop. This is a finite process lifetime, not a mission time limit or persistence
service. Target-app launch is a bounded, separate ADB bootstrap step; there is
no remote arbitrary-app/shell endpoint. Use a harmless public page such as
`android.settings.DISPLAY_SETTINGS`, rather than an account-bearing Settings
home screen. The owned silent fixture is used separately for motion/tap timing.

## Exact wire formats

The implementation follows the pinned upstream
[Streamer](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/Streamer.java),
[DesktopConnection](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/DesktopConnection.java)
and [ControlMessageReader](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/ControlMessageReader.java).
In this version the video stream starts with a four-byte codec ID, **not** a
codec/width/height tuple. Twelve-byte session records carry geometry changes:
`0x80000000 | clientResize`, width, height (three big-endian integers). Encoded
packet headers contain a 64-bit PTS/flags value and a 32-bit length. Configuration
is bit 62, keyframe is bit 61, and PTS occupies bits 0–60.

The TLS application request contains magic `PAV1`, 32-bit protocol revision 1,
and operation 1 (stream) or 2 (Host Stop). Welcome repeats the magic/revision,
the Java UTF string `5.0.1`, H.264 codec ID `0x68323634`, and `audio=false`.
Each subsequent frame has a 13-byte big-endian envelope: channel byte, 64-bit
stamp and 32-bit payload length, followed by that payload.

| Channel | Payload and stamp |
| --- | --- |
| 0, geometry | Two 32-bit dimensions; zero stamp |
| 1, video | Unchanged upstream encoded bytes and original PTS/flag bits |
| 2, reserved audio | Same upstream encoded packet and PTS/flag framing; not negotiated here |
| 3, tap | Four 32-bit values: x, y, video width, video height |
| 4/5, ping/pong | Empty payload; RG-originated local monotonic stamp is echoed for network RTT |
| 6, end | Empty payload; stop this session |
| 7, reserved audio codec | Four-byte upstream audio codec ID, before audio config/sample packets |

Audio configuration packets retain upstream bit 62 and their original codec
extradata, including Opus/AAC configuration. PAV-04 can add negotiation and
decoding on those channels without changing video/control packing. The current
receiver rejects audio negotiation and audio records; it cannot claim AV success.

Touch messages are validated against the current stream dimensions before the
bridge emits the pinned 32-byte scrcpy finger DOWN and UP records. They cannot
target display 0 through a separately supplied display ID. RG keeps aspect ratio,
rejects touches in black letterboxing and recreates the decoder on a session
geometry change. An unsupported output crop fails visibly instead of stretching
coordinates.

## TLS provisioning and reproducible bootstrap

Use short-lived experiment-only host/client identities provisioned outside Git.
Each private session directory contains `identity.p12`, `password`, `peer.crt`
and `endpoint.properties` (`host` and `port`). The explicit peer certificate is
the private trust anchor. Both peers require platform TLS 1.2/1.3; the RG client
also verifies the server address against the certificate SAN. No system trust
store, ADB identity, browser trust, pairing or network policy is changed. Private
key/password material must never enter command arguments, logs or this report.
The new apps disable backup/transfer of their private files.

After checking current physical identities, exclusive resources, complete current
CE/DP preservation and unused application IDs, install only those new packages.
Provision their session files through the approved private route. Put a matching
host APK and the hash-verified upstream JAR in the shell-owned private mission
directory. With the authorized shell and actual resolved paths:

```sh
CLASSPATH="$HOST_APK" app_process / \
  com.code2hack.eyebrowse.projector.directhost.ShellMain \
  "$PRIVATE_SESSION_DIRECTORY" "$PINNED_SERVER_JAR"
```

The APK is a dex container for this explicit shell process, not a privilege grant
to the installed app. The exact stock child options are:

```text
5.0.1 scid=<new-31-bit-id> log_level=info tunnel_forward=false
send_dummy_byte=false send_device_meta=false send_codec_meta=true
send_frame_meta=true video=true audio=false control=true video_codec=h264
video_bit_rate=2000000 max_fps=30 new_display=480x640/240
display_ime_policy=local vd_destroy_content=true clipboard_autosync=false
power_on=false cleanup=true
```

Resolve the newly observed display ID from this owned server and independently
bind actual Activity/display placement before launching the permitted public
target there. No reboot/automatic restart path is implemented. Keep the existing
debug SSH/ADB tunnel; it is not a media/control forwarding tunnel for this client.

## Bounded lifetime and measurement

RG uses a local black connection/error screen, Connect and an accessible Stop.
Stop/Back/Activity pause/Surface destruction close the network session, discard
input work and release the decoder. Reconnection is an explicit new session;
there is no stored command replay. The tap executor has a `SynchronousQueue`,
so it holds no pending tap. Native socket/encoder EOF and peer loss terminate
the corresponding owned host session and release its upstream child/display.

Encoded packets are capped at 1 MiB and dimensions at 4096 per axis. RG waits at
most 50 ms for a decoder input slot, tracks at most eight unrendered inputs and
sixteen released Surface callbacks, and fails closed on backlog. TLS reads and
handshakes have finite timeouts; writes stalled for two seconds close the socket.
One-second ping/pong keeps a static screen distinguishable from a broken link.
Timers use fixed delay and end with the session.

RG telemetry counts received/changing encoded packets and actual Surface render
callbacks. Its packet-read-to-Surface-callback samples use the RG's own clock;
echo RTT also uses one clock. Neither is optical latency or interaction-to-visible
latency. No subtraction of unsynchronized Phone/RG clocks is used. The silent
fixture records actual draws and visible tap counter changes independently on
the Phone. The bounded on-RG driver can measure at least 300 genuine elapsed
seconds and generate its own scheduled synthetic tap while the PC performs no
media/input forwarding. Its setup signal precedes the measured interval.

## Executed checks and current capability gate

The [sanitized evidence index](direct-video-evidence.json) binds compiled source,
all three APK hashes, host checks and private evidence hashes. No private archive,
TLS identity/password or original browser/account payload is included in Git.
The build used JDK 17, SDK 35, AGP 8.7.3, Kotlin 2.2.21 and Gradle 8.11.1,
under the assigned shared build lock with two workers and a 2-GiB Gradle heap:

```sh
./gradlew -p experiments/fold6-rg-projector --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx2g :directhost:assembleDebug \
  :directrg:assembleDebug :directrg:assembleDebugAndroidTest \
  :protocol:testDebugUnitTest :directhost:lintDebug :directrg:lintDebug
```

All three APKs build and pass APK signature verification. Nine JVM tests pass:
five pinned protocol/geometry/touch cases and four actual platform mTLS cases
covering a valid client, missing identity, an untrusted identity and wrong server
hostname. Lint reports zero errors and one `OldTargetApi` warning per new APK;
the assigned target SDK 35 is retained. RG's measured runtime dependency graph
contains only the protocol module, Kotlin stdlib and its annotations. These are
host checks, not on-device authentication, decoding or runtime-process evidence.

Before dependent installs, the current Fold6 original Phone store and both RG
browser stores were preserved as opaque CE/DP archives. Full member/header/byte
and host restoration checks passed, with four, four and eleven separate-copy
SQLite checks respectively. RG's two filesystem sockets are represented by
explicit metadata and closed host placeholders. Both RG tar operations actually
returned 1 with the exact supported vendor `140000` warning; these are not clean
tar exits, live sockets, process-memory captures or on-device restores. Chromium's
newer complete archive matched both later stable current views. The earlier
before/after discrepancy, corrupt gzip, failed raw/partial transfer and capacity
check ordering deviation remain retained and do not become successful evidence.

At the pre-install guard, Fold6 and the original RG browser still matched their
preserved views. The next Chromium stream represented only 888 exact CE nodes,
with zero added/changed nodes, omitting the remaining 722 expected nodes and the
entire DP tail. The selected RG `5555` transport then reported **device offline**
under the unchanged pinned ADB server. This incomplete stream does not establish
file deletion or a coherent changed store. The one authorized bounded `44235`
bootstrap connection timed out; its unknown outcome and the subsequent absent
registration are retained without replay, pairing, `tcpip` or service reset.

No projector APK has been installed, no real credential invocation has occurred,
and no projector process, socket, generated input or virtual display has started.
The existing debug services were retained. A read-only Fold6 state query
admitted before the capability hold observed Dozing, keyguard showing and zero
owned bridge processes. Current RG return remains unavailable. No further device
or connection operation follows the Manager capability hold. Required live checks
remain open:

| Criterion | Actual result at this checkpoint |
| --- | --- |
| New-package build/dependency isolation | Host PASS; coexistence install/process proof unexecuted |
| Third-party app on owned Fold6 VD to real RG Surface | Unexecuted |
| Harmless RG-to-Fold6 tap with observed app effect | Unexecuted |
| At least 300 genuine seconds without a PC media/control gateway | Unexecuted |
| On-device mutual TLS refusal before media/input | Unexecuted; JVM TLS cases pass |
| Decode/reconnect/Host Stop/RG pause and no stale input | Unexecuted; bounded paths implemented |
| Moving-content frame rate, actual codec/crop and latency | Unexecuted; telemetry implemented |
| Audio/head/IME/optical/acoustic acceptance | Not claimed; later work and prerequisite limits remain |

The on-RG Kotlin test driver is built but has not run. Its scheduled synthetic
tap can support controlled-path evidence only. The owned silent fixture's draw
and visible tap counters cannot establish third-party app compatibility or
physical head/IME/optical behavior. Public third-party rendering must be exercised
separately. A successful ACK, static page or proposed test ordering is insufficient.

The implementation was simplified to one stream boundary and platform TLS:
unused arbitrary-app control was removed, app launch remains a separate bounded
bootstrap step, and no new engine, discovery, remote shell or browser framework
was added. All evidence/files remain retained for experiment-end cleanup. Old
Worker64 tab/hook/resource-return obligations remain unresolved; this checkpoint
does not restore an old snapshot or infer that those resources were returned.
The required live work resumes only after a material RG capability event and
current physical/state qualification. Independent exact-candidate review and
Manager acceptance remain pending.
