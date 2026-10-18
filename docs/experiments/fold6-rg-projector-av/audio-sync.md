# PAV-04 required direct audio and video timing

Worker: Worker-#69
Issue: [#69](https://github.com/code2hack/EyeBrowse/issues/69)
Assigned base: `077124ed21ee97f51a8c2fe15e5b165e7100f045`
Target: `spike/fold6-rg-projector-av`
Status: host implementation ready for a further device window; physical audio acceptance pending.
Mission revision 2 is source-only while #68 owns both devices.

## Implemented path

The existing explicitly shell-launched Phone bridge starts the unchanged pinned
scrcpy 5.0.1 server with `audio=true`, `audio_source=output`, `audio_codec=opus`
and 128 kbit/s audio. The source commit remains
`a60891aea193d92e7e5c3942700eca63f9d19a5f`; the server JAR SHA-256 remains
`764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`.
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

There is no application encoded-audio or PCM queue. Codec input waits at most
50 ms; nonblocking AudioTrack writes have a 250 ms deadline. Track allocation
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
provide per-app or virtual-display-only isolation. Actual two-source mixing,
focus suppression and Phone output must be measured/observed. Protected audio,
app capture opt-out, microphone, calls, private notifications and any bypass
are outside these tests. No playback-duplication comparison has run.

## Actual evidence so far

| Check | Actual outcome |
| --- | --- |
| Assigned baseline JVM tests | Passed before edits |
| Affected protocol, host and receiver JVM tests | 22 passed, zero failures/errors; includes framing, clock, known-tone and existing lifetime tests |
| Host/RG/test/second-source APK assemblies and lint | Passed; zero lint errors, retained OldTargetApi and the fixture's existing backup/icon warnings |
| Matching Phone/RG owned-package updates | Both installed and full installed APK hashes matched this pilot's build |
| First direct pilot `audio-pilot001` | **Failed to establish a live session.** No audio/playback result |
| Actual RG audibility / Phone suppression | Not observed |
| Real ten-minute controlled A/V run | Not run |
| Physical A/V offset / uncertainty | Unavailable; no common optical/acoustic observation |
| Two-app mixing and focus / mute / reconnect matrix | Pending |

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
