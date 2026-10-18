# PAV-04 required direct audio and video timing

Worker: Worker-#69
Issue: [#69](https://github.com/code2hack/EyeBrowse/issues/69)
Assigned base: `077124ed21ee97f51a8c2fe15e5b165e7100f045`
Target: `spike/fold6-rg-projector-av`
Status: direct audio and ten-minute software timing measured on the assigned devices;
Owner heard RG output and qualitatively aligned flashes/beeps. The physical
100 ms offset target remains unquantified. Independent review/acceptance is pending.

## Implemented path

The existing explicitly shell-launched Phone bridge starts the unchanged pinned
scrcpy 5.0.1 server with `audio=true`, `audio_source=output`, `audio_codec=opus`
and 128 kbit/s audio. The source commit remains
`a60891aea193d92e7e5c3942700eca63f9d19a5f`; the server JAR SHA-256 remains
`764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`.
The inherited launch option `send_codec_meta=true` is unrecognized by pinned
scrcpy 5.0.1; its retained warning is harmless here because the actual
`send_stream_meta` default is true and the codec headers were received.
Video/audio/control local sockets are accepted in that upstream order. Audio
codec ID, raw OpusHead configuration, packet flags and media PTS travel through
the same authenticated TLS connection as the existing H.264 video. There is no
second media URL, PC media relay or Bluetooth path.

Wire version 2 requires audio in its welcome; version 1 peers fail negotiation.
The reserved channels remain AUDIO=2 and AUDIO_CODEC=7. Upstream codec IDs 0/1
are capture/configuration failures. An absent, malformed, stalled or failed
audio stream stops the session with an explicit failure state. Video alone is
not this feature's success condition. Coordination with #68 retains that
branch's SCROLL=8 and NAVIGATE=9 assignments for later Manager-directed source
integration; those input features are not implemented by this audio branch.

The RG uses Android's Opus MediaCodec decoder and PCM16, 48 kHz stereo
AudioTrack. CSD contains the upstream identification header, its pre-skip
converted to native-order nanoseconds, and an 80 ms seek pre-roll, according to
[Android's codec-specific data contract](https://developer.android.com/reference/android/media/MediaCodec#CSD).
Audio focus is requested for media; loss, including ducking, stops this bounded
experiment rather than leaving background audio. Stop/pause/disconnect cancels
the socket and workers, pauses and flushes AudioTrack, releases both codecs and
abandons focus. Reconnect creates fresh resources and a new playback clock.

There is no application encoded-audio or PCM queue. Audio codec input waits at most
250 ms; nonblocking AudioTrack writes have a 250 ms deadline. Track allocation
uses at least the platform minimum and 100 ms, rejecting a minimum above 200 ms;
observed queued output above 300 ms fails. Host and receiver audio liveness
limits are five seconds, with the existing bounded network-write and host
lifetime limits retained. These are source limits, not observed device results.

## Timing and retained data

Source PTS is preserved. Each decoded audio buffer maps its source PTS to the
cumulative RG PCM frame position. AudioTrack's hardware timestamp associates
that frame position with RG monotonic time. The video worker presents a frame
when the corresponding audio PTS is due, drops video over 100 ms late and fails
if it would need more than 250 ms of lookahead. There is no subtraction of a
Phone clock reading from an RG clock reading. An unavailable RG audio timestamp for five seconds is an explicit
`AUDIO_CLOCK_UNAVAILABLE` failure, rather than silently using arrival timing.
Clock extrapolation expires after
500 ms without an updated usable timestamp; unusable timestamps do not create
sync samples.

The existing frame-render callback is measured at its receipt using RG
`System.nanoTime()`. The vendor's incompatible callback timestamp is not used.
A software sync sample is video source PTS minus the audio PTS estimated at that
callback receipt. It can characterize the RG presentation pipeline. It does not
measure panel photons, speaker acoustics, the author's exact flash/beep output
alignment, or end-to-end sensory offset. Sample quantization is about 20.8 us at
48 kHz; vendor timestamp accuracy and callback/optical/acoustic delays remain
unqualified. A software result within 100 ms therefore does not establish the
required physical assessment target.

Periodic status retains only the latest sync sample. The existing on-RG driver
samples once per second for a bounded run and records actual elapsed time,
source PTS, rendered/decoded counts, underruns and peak queue size. It verifies
the current session's monotonic start and freshness rather than trusting an old
status file. Frequency measurements retain only 440 Hz and 1 kHz amplitudes
from the last second of decoded PCM; PCM itself is not retained. Neither these
values nor AudioTrack writes prove audibility.

## Owned sources and capture semantics

`directhost.AvFixtureActivity` loops the unchanged 12-second authored black/white
flash and 1 kHz pulse clip from PAV66 (SHA-256
`d4ac939c07a8679fa041187a093d46f80407773b3d0dd663a944d8ab4202d678`), using an
ordinary VideoView at 0.25 digital volume. `hostprobe.ToneActivity` is a separate
owned Phone app producing a quiet 440 Hz tone, with optional ordinary audio
focus. Both stop their media on Activity stop. The source clip is a deterministic
reference; looping it is not evidence that ten minutes have elapsed.

The pinned [scrcpy output mode](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/audio.md)
captures device output and normally suppresses Phone playback. It does not
provide per-app or virtual-display-only isolation. The measured two-source/focus/output results appear below. Protected audio,
app capture opt-out, microphone, calls, private notifications and any bypass
are outside these tests. No playback-duplication comparison has run.

## Executed checks

| Check | Actual outcome |
| --- | --- |
| Assigned baseline JVM tests | Passed before edits |
| Affected protocol, host and receiver JVM tests | 22 passed, zero failures/errors; receiver's 8 tests passed again after the production backpressure/lifetime corrections |
| Host/RG/test/second-source APK assemblies and lint | Passed; zero lint errors, retained OldTargetApi and the fixture's existing backup/icon warnings |
| Matching owned-package updates | Installed through USB debug; exact selected hashes and source bindings below |
| Actual RG output | Owner heard the repeating 1 kHz pulses and later both separate Phone sources |
| Phone output | Owner reported Phone silence for the single-source and two-source cases |
| Controlled ten-minute playback | 600,037 ms measured on RG; saved full timeline; final instrumentation reply crashed after save, described below |
| Software AV timing | Median -3.492 ms, range -12.661 to +26.626 ms; no accumulating software trend observed |
| Physical AV timing | Owner: “They seem together”; no calibrated numeric offset or uncertainty bound |
| Source isolation | Physical-display second app mixed with the virtual-display source; whole-device capture |

The first pilot's on-RG harness incorrectly emitted `passed=true` while repeatedly
reading an unchanged historical `disabled-intermediate` status: elapsed 35,259 ms,
290 video packets and 290 rendered frames throughout a 40,056 ms wall interval.
The new Phone bridge remained HOST_READY and produced no scrcpy session log.
This is **invalid product evidence**, preserved as a meaningful failed pilot.
An unavailable current Surface was not rejected by the old driver; its exact
cause remains unproved. The first edited JVM run also exposed a missing JSONArray constructor in its
test double; that intermediate failure was retained and corrected. The corrected
driver waits for its actual current
Surface, rejects historical/stale telemetry and requires decoded audio.

Pilot installed artifacts were Phone `directhost.r5` SHA-256
`1666e0928fb73fb9c2f6e4ad2a3d0ab99887b909850ed71f03fff58e44d35832` and RG
`directrg.r3` SHA-256
`ed06e27fd9689343e84553b3e7548b72752556a00f8f8c515a2a13980535284a`.
Later source corrections are not represented by those pilot artifacts.

The owned RG package and Phone projector were force-stopped; the owned bridge
PID was terminated and no corresponding bridge or scrcpy child remained in the
process readback. A direct listener-list read was unavailable because Android
denied netlink access. No media session had been established. There were no
volume/routing/IME/credential/global-setting changes. One ordinary RG wake was
sent; no forced lock was performed. The shared USB debug route was retained and
the exclusive device window returned to the Manager for #68. Mission files and
failed evidence remain retained; no workspace archival has occurred.

## W69-02 actual pilots and bounded corrections

The matching 7ac builds installed through the granted USB debug route. Pilot002
reached direct Opus capture/decode/playback, then failed at the 50 ms audio input
wait. The input wait is now 250 ms, matching the bounded output-write wait.
Pilot003 decoded silent VD audio for about 24 s, then stopped at the video-clock
lookahead bound as a later frame arrived. Its exact clock discontinuity cause
remains unproved. Its final 23,653.5 ms queue peak is a teardown artifact, not a
qualified live queue-growth measurement: AudioTrack flush reset the playback
head while the worker still sampled it. Metrics and flush now share a small
lifetime lock; the original failed result remains retained.

Pilot004 hit the inherited 8-frame video backlog limit while audio-clock timing
held video output. Its dependent Activity launch returned a SecurityException
for the just-ended VD61; it does not establish a new Android privilege need.
The fixed video inflight bound is now 16, still with 250 ms lookahead and no
application encoded-frame queue. The decoder reports 17 native output buffers.
Late shutdown socket errors no longer replace the first live failure category;
clock-limit exceptions now include the actual numeric video PTS/ahead value.

With those corrections, pilot005 launched the actual owned clip on VD62 and
completed 30,013 ms of measured playback (30,017 ms through the driver's final
status-read boundary). Both media counters and source audio PTS advanced.
RG output used `c2.android.opus.decoder`, 48 kHz/stereo/PCM16, route type 2
(built-in speaker), 100 ms track allocation, peak queued 240.875 ms and zero
reported underruns. The receiver detected the owned 1 kHz pulse at approximately
0.025 digital amplitude. The last software sample was -3.477 ms video minus
audio; this is callback/audio-clock evidence, not audibility or physical sync.
No microphone, recording, Bluetooth route or separate media URL was used.
The following measurements use that corrected production code.

## Ten-minute run and actual output

`audio-tenminute001` ran the looped owned clip on Phone VD63, with direct TLS 1.3
between Phone `10.199.167.157` and RG `10.199.167.154`. USB was debug/control
only. The matching production source was commit
`613e85b4a7c95f424e8f96d3dace8ec864f5c3b6`. Audio was Opus at 128 kbit/s,
decoded by RG `c2.android.opus.decoder` to 48 kHz stereo PCM16 and the built-in
speaker (Android route type 2). Video used `c2.qti.avc.decoder` at 480×640.
No PC player/relay, local RG media fixture, microphone, recording or Bluetooth
was involved.

| Quantity | Actual result |
| --- | --- |
| RG controlled measurement interval | 600,037 ms (600,039 ms through final status-read boundary) |
| Status counter interval | 600.172 s |
| Video rendered delta / rate | 17,323 frames / 28.863 fps; 0 late-video drops |
| Audio PCM frame delta / source PTS advance | 28,805,448 frames / 600.104908 s |
| Unique software AV samples | 585, deduplicated by sample elapsed timestamp |
| Video PTS minus RG-clock audio PTS | Median -3.492 ms; range -12.661 to +26.626 ms; absolute p95 17.758 ms |
| First / last 60 unique samples | Medians -3.562 / -3.7125 ms; delta -0.1505 ms (sample groups, not exactly 60-second windows) |
| Track allocation / estimated peak queued output | 100 ms / 246.979 ms |
| AudioTrack underruns | 0 initially, 1 at end; a glitch-free result is not claimed |
| Owned RG process resource samples | RSS 124,924 kB then 114,388 kB; later sample 34 threads; sparse samples do not bound all resource use |
| Safe volume/mute | Prior RG media 7 → test 2 → mute 0 → test 2 → restored 7 |

The Owner answered during this named, active clip: RG audibility **“yes i can
hear.”**, Phone output **“phone is not beeping”**, and flash/beep alignment
**“They seem together”**. These establish actual audible output, Phone
suppression and qualitative perceived alignment for this setup. They provide
no calibrated numeric optical/acoustic offset or uncertainty. The software
range above is within 100 ms, but the physical 100 ms assessment target remains
**unquantified**, rather than a physical PASS. No unsynchronized-clock
subtraction is used.

The full 1,042,268-byte report was saved before test completion. Returning that
whole JSON through Android instrumentation then crashed with
`TransactionTooLargeException` (2,084,688-byte Binder parcel); the native result
was `shortMsg=Process crashed`, despite saved `passed=true`. The interval and
volume restoration had completed, but this was **not a clean instrumentation
PASS**. Retained original logs include the fatal stack. The driver now returns
a small result/file-reference summary while retaining the full private report;
the subsequent 90-second mixing and 45-second focus cases returned normally.
This test-only correction does not change the production AV code measured for
ten minutes. No second ten-minute run is represented as having occurred.

## Whole-device mixing and focus

`audio-mixing001` completed **90,010 ms** of controlled playback with the owned
flash/beep clip on VD64. A separate `hostprobe.ToneActivity` produced 440 Hz on
physical display 0 without requesting focus. Both measured frequency peaks
exceeded 0.01 digital amplitude in 63 of 90 timeline samples. At an intermediate
sample the peaks were 0.02098 (440 Hz) and 0.02555 (1 kHz); neither frequency
measurement alone proves acoustic output. The second app was deliberately
force-stopped near the end; the lower tone fell while the source beep continued.
There were zero reported AudioTrack underruns and a 240.875 ms peak queue.
The Owner's replies bound to that test were **“I hear both”** and **“Phone is
silent”**. This is audible mixing of two separate Phone apps, including the
physical-screen app; it disproves target-only/per-VD audio isolation for output
capture in this setup.

`audio-phone-focus001` completed **45,042 ms** on VD65. The physical-screen tone
app requested ordinary media focus and received `AUDIOFOCUS_REQUEST_GRANTED`
(result 1). The VD VideoView remained `playing=true`; both frequencies persisted
in 39 of 45 timeline samples. **No source focus suppression occurred in this
setup.** This observation does not establish all apps' audio-focus or capture
policies. Its peak queue was 247.208 ms with 2 reported underruns. The tone app
was stopped after the case. No original app was opened or modified.

The experimental recommendation is output capture with RG playback for the
measured path, with its whole-device mixing limitation visible to users. No
per-app isolation, capture opt-out/DRM handling, Phone playback duplication,
universal focus behavior or private-content safety qualification is implied.

## Lifecycle and resource handback

Lifecycle checks use the same production AV code and the corrected compact
instrumentation reply, with the owned fixture actually playing before each
controlled interval. All five cases returned native success and saved the full
report. Expected audio failure/stop is the success condition of those controls.

| Case | Measured playback before control | Actual outcome |
| --- | --- | --- |
| `audio-rg-focus001` | 10,075 ms | Granted competing transient RG focus → `AUDIO_FAILED`, audio `STOPPED`; 1 reported underrun |
| `audio-pause001` | 10,080 ms | RG Home / Activity pause → session and audio `STOPPED` |
| `audio-local-stop001` | 10,070 ms | Local Stop button → session and audio `STOPPED` |
| `audio-reconnect001` | 10,082 ms after reconnect | Initial session closed; a distinct session start, new clock/counters and new VD70 produced actual AV; no old taps. Two underruns were already present at the measurement's first sample, with no further increase |
| `audio-host-stop001` | 10,073 ms | Authenticated Host Stop → remote EOF / `CONNECTION_OR_REQUIRED_AUDIO_FAILED`, audio `STOPPED`, bridge `HOST_STOPPED` |

Queue peaks across these lifecycle cases were 234.687–246.896 ms. RG pause is
an explicit disconnect; resuming requires Connect and fresh resources. Opening
the physical Phone tone app while the VD played, pausing RG to Home, restarting
RG sessions, mute/unmute and host termination exercise these scoped app/focus/
lifetime transitions. They do not qualify every Android/OEM interruption.

After Host Stop, the owned VD71 fixture recorded `playing=false`. RG written
frames stayed 567,360 in two stopped snapshots taken over one second apart.
`AudioDecoder.close()` pauses and flushes the track before worker/codec release;
no continued own writes were observed after stop. The acoustic stop-tail duration
was **not calibrated**. The 100 ms allocation and measured queued-output peaks
are software buffering evidence, not an acoustic tail measurement.

Necessary teardown stopped only assigned Phone r5/hostprobe and RG receiver
apps. Bridge PID11359, its scrcpy child and assigned package processes were
absent in the final process readback. The broad Phone readback still showed
**unassigned historical `directhost.r4` PID19735**; it was left untouched and
reported to Manager. This is not an all-projector-processes absence claim.
Whole-port absence remains unobserved (the earlier netlink read was denied).
Final RG public media-volume readback was 7, matching the prior value. Phone
volume/routing and route selection were not changed. Shared server15037 and
USB connections were retained; no forced lock or new credential operation was
performed. Both devices were proactively handed back to Manager. Owner's later
TCP5555 debugging-route instruction is a separate operational change after all
product cases, with no rerun or change to their direct media path. The actual
route resolved from USB was `192.168.0.193:5555` through u4090/server15037. Its existing
listener connected without enabling/restarting adbd; TCP identity matched
SM-F956N / R3CX70NHTHK / user0. USB fallback and RG USB remained intact; the
final route handback followed immediately.

## Artifact and evidence bindings

Selected Phone host APK SHA-256:
`c4586474b372188cf3982bd5cad6b9e439b40f5246a28b0411e6839ec57859b9`.
Selected RG production APK SHA-256:
`29a379c3a30c39310368e54062cfb9317e99e02e49f95456a625fdb91d44f6ff`.
Separate Phone tone fixture APK SHA-256:
`cfee5a1bfe3ef975a002b26ed3a77a1a05916c2f59536018a8954e16d4d12572`.
The host/tone source is unchanged from checkpoint
`7acb1b563ed65bf39581f33321883c5df1c55e7a`; the RG production fixes are in
`613e85b4a7c95f424e8f96d3dace8ec864f5c3b6`. The final test APK SHA-256 is
`644c6f78762dfbe7b672013c87e17a9615ad1b8aefd7fbbed6513b3a2b29f43c`.
The later change is test-result reporting only. Matching host/receiver binaries
were used; v1 interoperability is not claimed.

Raw case reports, own command errors, builds, APKs, signatures, installed-byte
bindings, CSV software samples, Owner responses and retained failed pilots live
in the assigned mission evidence namespace:
`runs/run-001/issue-69/worker-69-001/evidence` and retained `scratch` artifacts.
The final immutable candidate manifest binds these files to the pushed source.
Private TLS material and unrelated application/account contents are not
published. Test teardown is immediate; mission evidence/workspace cleanup
remains deferred until the Manager's experiment-end boundary.
