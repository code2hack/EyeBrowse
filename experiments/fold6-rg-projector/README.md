# Fold6 projector experiments

PAV-02 adds three isolated modules:

- `directhost`: a Phone control screen, owned motion and looped flash/beep fixtures, and the
  explicitly shell-bootstrapped Kotlin TLS/Unix-socket bridge.
- `directrg`: the independent Surface/MediaCodec receiver and bounded on-RG
  instrumentation driver. Its only application dependency is `protocol`.
- `protocol`: pinned scrcpy 5.0.1 parsing, coordinate mapping, a small framed
  transport and platform mutual TLS. PAV-04 uses mandatory Opus audio in wire version 2, platform
  MediaCodec/AudioTrack playback, and the RG audio clock for video scheduling.
  Version 1 and version 2 peers fail negotiation rather than silently lose audio.

Build these separately from the production browser:

```sh
./gradlew -p experiments/fold6-rg-projector --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx2g :directhost:assembleDebug \
  :directrg:assembleDebug :directrg:assembleDebugAndroidTest \
  :directhost:testDebugUnitTest :protocol:testDebugUnitTest :directrg:testDebugUnitTest \
  :directhost:lintDebug :directrg:lintDebug
```

The receiver JVM tests use test-only platform doubles to suspend codec
construction across Stop, inject initialization failures, and verify native
release/interrupt retention when close interrupts its bounded join. These exercise
production lifetime code; actual Android decoding remains a device check.

Use the assigned devices and the isolated projector packages. Owner decision167
removed the extra private test-admission stack; use normal ADB/debug access and
the actual stream/Surface dimensions. Existing owned projector packages may be
updated for code changes. Keep original EyeBrowse/Chromium applications and data
untouched. Owner decision106 retired repeated old-store backups and scans.
See [direct-video.md](../../docs/experiments/fold6-rg-projector-av/direct-video.md)
for the actual protocol, bootstrap, limitations and acceptance evidence.

## Owner manual session lifetime (#76)

The explicitly bootstrapped host has no overall elapsed-time cutoff. Existing
host Stop, receiver Disconnect/pause, and genuine failures still release their
owned resources. The shared scheduler retains the 8-second session startup,
2-second stalled-write, and 5-second stalled-audio safeguards.

Stock scrcpy receives `keep_active=true` for the current projected display and
`power_on=false`. The keep-active setting follows activities on that display;
it does not establish screen-off or secure-lock continuity. Prior investigation
failures and the Owner's manual black-screen result remain unchanged.

`HostLifetimeTest` advances a virtual scheduler past the former 15-minute
boundary while running the actual host accept loop, then checks explicit close
and scheduler cleanup. This is an elapsed-boundary regression, not a battery,
unlimited-uptime, optical, or acoustic qualification.

## PAV-03 input

The RG receiver draws its donor-derived local head cursor over the stream.
Short pad taps operate the projected Phone app; double taps send Phone Android
Back, and pad swipes use native wheel input at the visible target. There is no
RG-local session panel or panel-opening gesture. The existing disconnected
Connect view contains the two bounded numeric pointer/scroll settings rows.
Home/app-return uses Phone-provided projected navigation if actually available;
RG system Home/app-exit should trigger the existing Disconnect lifecycle. Those
platform routes and physical settings activation still need device verification.
`directhost.InputFixtureActivity`
supplies an owned native field, main scroll area and nested list using the actual
Phone IME. See
[input-ime.md](../../docs/experiments/fold6-rg-projector-av/input-ime.md) for the
exact source/APK bindings, physical findings and remaining limits.

## PAV66 owned fixture

This separate Phone package provides an ordinary editable field and a 12-second
owned flash/beep clip for [issue 66](https://github.com/code2hack/EyeBrowse/issues/66).
It does not implement the projector or a keyboard. Stock scrcpy must capture its
actual Android Views, Phone IME and audio. `onStop()` releases its VideoView.

The isolated Gradle project uses the repository's existing AGP 8.7.3, Kotlin
2.2.21, JDK 17 and SDK 35 conventions. It has no browser/core/proxy dependency,
network/storage/microphone permission, background service or screen/lock control.
The `com.code2hack.eyebrowse.projector.hostprobe` package is isolated from the
original applications. Keep their data intact; do not clear it for a test.

Two buttons request ordinary apps on the fixture's current display: Chrome opens
`https://example.com/` in a Custom Tab, and Settings opens its Display page. The
fixture neither opens the existing browser tab switcher nor edits any setting.
The Custom Tab uses the [official low-level null-session API](https://developer.chrome.com/docs/android/custom-tabs/howto-custom-tab-low-level-api)
without adding a browser library or WebView. Actual app/display placement and
rendering still require device verification.

From the repository root:

```sh
./gradlew -p experiments/fold6-rg-projector --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs=-Xmx2g :hostprobe:assembleDebug :hostprobe:lintDebug
```

The first build and lint completed. Lint retained three metadata warnings:
`OldTargetApi` (the assigned SDK 35 baseline), `DataExtractionRules`, and
`MissingApplicationIcon`. This disposable fixture has no persisted Owner data;
no additional backup or icon machinery was introduced. Build/lint do not prove
display, keyboard, audio or lifecycle behavior on the Fold6.

The bundled MP4 was generated from synthetic black/white frames, a frame counter
and a quiet 1 kHz pulse for the first 100 ms of each second. It contains no
camera, microphone, downloaded or private material. Its SHA-256 is
`d4ac939c07a8679fa041187a093d46f80407773b3d0dd663a944d8ab4202d678`.
The authored flash/beep timing is a fixture reference, not a measured network,
optical or acoustic latency result.

To reproduce it with the recorded host FFmpeg toolchain, run this from this
directory after moving aside any existing destination:

```python
import subprocess

video = (
    "color=c=black:s=480x640:r=30:d=12,"
    "drawbox=x=0:y=0:w=iw:h=ih:color=white:t=fill:enable='lt(mod(t,1),0.1)',"
    "drawtext=text='PAV66 owned AV %{n}':fontcolor=0x44ff88:fontsize=24:x=20:y=300"
)
audio = "aevalsrc=exprs='0.1*sin(2*PI*1000*t)*lt(mod(t,1),0.1)':s=48000:d=12"
subprocess.run([
    "ffmpeg", "-nostdin", "-n", "-hide_banner", "-loglevel", "warning",
    "-filter_threads", "1", "-f", "lavfi", "-i", video,
    "-f", "lavfi", "-i", audio, "-map", "0:v:0", "-map", "1:a:0",
    "-c:v", "libx264", "-threads", "2", "-preset", "fast", "-crf", "18",
    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "96k", "-ar", "48000",
    "-ac", "1", "-t", "12", "-movflags", "+faststart",
    "hostprobe/src/main/res/raw/pav66_flash_beep.mp4",
], check=True)
```

Actual source/APK/device bindings and case outcomes belong in the
[Fold6 feasibility report](../../docs/experiments/fold6-rg-projector-av/fold6-feasibility.md)
and mission-private evidence. The fixture alone satisfies no downstream
capability prerequisite.

## PAV-04 audio

See [audio-sync.md](../../docs/experiments/fold6-rg-projector-av/audio-sync.md)
for current implementation, actual test outcomes and observation limits.
`directhost.AvFixtureActivity` loops the unchanged synthetic 12-second
PAV66 flash/beep clip at one quarter digital volume. The distinct owned
`hostprobe.ToneActivity` emits a quiet 440 Hz tone, optionally requesting audio
focus, to distinguish whole-device mixing from target-only capture. Both
release media when stopped; neither records audio or requests microphone access.
