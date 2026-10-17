# PAV66 owned fixture

This separate Phone package provides an ordinary editable field and a 12-second
owned flash/beep clip for [issue 66](https://github.com/code2hack/EyeBrowse/issues/66).
It does not implement the projector or a keyboard. Stock scrcpy must capture its
actual Android Views, Phone IME and audio. `onStop()` releases its VideoView.

The isolated Gradle project uses the repository's existing AGP 8.7.3, Kotlin
2.2.21, JDK 17 and SDK 35 conventions. It has no browser/core/proxy dependency,
network/storage/microphone permission, background service or screen/lock control.
Install only the unused `com.code2hack.eyebrowse.projector.hostprobe` package in
the reserved Fold6 window, after required preservation. Do not replace another
package or clear application data.

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
