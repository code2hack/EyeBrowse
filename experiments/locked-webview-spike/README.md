# Locked WebView host spike

Issue: #1 — validate whether EyeBrowse Phone can keep an authoritative WebView rendering and accepting remote-style commands while the Fold6 is securely locked and its physical display is off.

This project is deliberately isolated from production EyeBrowse. It uses only Android platform APIs and **does not use MediaProjection, CXR, Pi, Mihomo, tailnet, or an RG client**.

## What it does

- starts a foreground service;
- acquires a partial wake lock for the explicit validation session;
- creates a private 480×640 `VirtualDisplay` at a deliberately provisional 160 dpi;
- attaches the display to an `ImageReader` surface;
- shows an app-owned `Presentation` containing a real `WebView` on that display;
- loads a deterministic local page with a 250 ms counter, scrollable blocks, button, and text field;
- samples rendered-frame hashes and WebView DOM state once per second;
- writes `files/lockprobe/telemetry.jsonl` and refreshes `files/lockprobe/latest.png` about every two seconds;
- accepts debug-only commands from `adb shell` through a receiver protected by `android.permission.DUMP`.

The test is intentionally crude. A pass only proves the locked/offscreen rendering premise is worth pursuing.

## Build

Open this directory as an Android Studio project, or use a local Gradle/JDK 17 setup compatible with Android Gradle Plugin 8.7.3:

```bash
gradle :app:assembleDebug
```

The prototype intentionally does not add a Gradle wrapper binary to the repository.

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.code2hack.eyebrowse.lockprobe/.MainActivity
```

Tap **Start probe** while the phone is unlocked. Confirm the foreground-service notification appears.

## Sanity check before locking

```bash
adb logcat -s EyeBrowseLockProbe
```

You should see `frame` and `page` telemetry. Pull current evidence:

```bash
adb exec-out run-as com.code2hack.eyebrowse.lockprobe \
  cat files/lockprobe/telemetry.jsonl > telemetry.jsonl

adb exec-out run-as com.code2hack.eyebrowse.lockprobe \
  cat files/lockprobe/latest.png > latest.png
```

`telemetry.jsonl` records Android's `interactive` and `deviceLocked` values on every persisted event.

## Post-lock commands

Securely lock the Fold6 with its normal PIN/biometric lock and let the physical display turn off. Do **not** use an unlocked black-screen workaround.

The receiver is exported only for this debug spike and requires the privileged `DUMP` permission, which `adb shell` has.

Scroll down:

```bash
adb shell am broadcast \
  -a com.code2hack.eyebrowse.lockprobe.COMMAND \
  -n com.code2hack.eyebrowse.lockprobe/.CommandReceiver \
  --es command scrollDown
```

Scroll up:

```bash
adb shell am broadcast \
  -a com.code2hack.eyebrowse.lockprobe.COMMAND \
  -n com.code2hack.eyebrowse.lockprobe/.CommandReceiver \
  --es command scrollUp
```

Click the known test button:

```bash
adb shell am broadcast \
  -a com.code2hack.eyebrowse.lockprobe.COMMAND \
  -n com.code2hack.eyebrowse.lockprobe/.CommandReceiver \
  --es command click
```

Set the test input value:

```bash
adb shell am broadcast \
  -a com.code2hack.eyebrowse.lockprobe.COMMAND \
  -n com.code2hack.eyebrowse.lockprobe/.CommandReceiver \
  --es command type \
  --es value locked-input-worked
```

Other commands:

```text
reload
reset
dump
navigate   (supply --es value https://example.com)
```

After each locked command, pull telemetry and `latest.png` again. The evidence should show `deviceLocked:true`, fresh/changing frame hashes, changing counter values, scroll position changes, button status changes, and typed text as applicable.

## Record device versions

Capture these alongside the result:

```bash
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.incremental
adb shell getprop ro.build.version.security_patch
adb shell dumpsys webviewupdate
```

Also record the One UI version from the phone's About screen if it is not obvious from build metadata.

## Longer / Doze validation

First repeat the locked test **unplugged and stationary** for a meaningful interval. The exact duration is not a normative EyeBrowse value; the goal is to see whether normal power management changes behavior.

A separate forced-Doze experiment can be useful:

```bash
adb shell dumpsys deviceidle force-idle
# rerun the commands and pull evidence
adb shell dumpsys deviceidle unforce
```

Forced Doze is a harsher diagnostic, not the normal pass criterion by itself.

## Stop and cleanup

Unlock the phone and tap **Stop probe**, or:

```bash
adb shell am stopservice -n com.code2hack.eyebrowse.lockprobe/.LockProbeService
```

The service dismisses the `Presentation`, destroys the WebView, releases the virtual display and ImageReader, stops the capture thread, and releases the wake lock.

## Pass criterion

**PASS** only if, while the phone is securely locked and its physical display is off:

1. new frame evidence continues to arrive and hashes/content demonstrate the renderer is not frozen;
2. the page counter continues progressing;
3. post-lock scroll, click, and type commands execute and are reflected in later page/frame evidence;
4. the behavior does not depend on MediaProjection, root, system UID, or keeping the phone effectively unlocked.

A failure is still useful: it tells us not to restructure EyeBrowse around a phone-hosted live browser before exploring another rendering/ownership architecture.

## Known limitations of the spike

- 480×640 and 160 dpi are validation constants, not final RG display metrics.
- `latest.png` is diagnostic only; no streaming codec or frame pacing is being evaluated.
- the partial wake lock intentionally tests an explicit active glasses-session style host; later production power policy is a separate design problem.
- the debug receiver is not a production transport or security model.
- no attempt is made to synchronize Chrome cookies; the tested WebView is the authoritative browser itself.
