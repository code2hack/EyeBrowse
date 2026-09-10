# EyeBrowse

EyeBrowse targets Android phones and Rokid Glasses. The current implementation is the first
runnable product slice (v0.0.1): a single-tab Phone browser and an honest unconnected RG launcher.
Product direction lives in `SPEC.md`; agent/governance rules live in `AGENTS.md`/`DEV.md`.

## Modules

| Module | Package | What it is |
| --- | --- | --- |
| `:core:browser` | `com.code2hack.eyebrowse.core.browser` | Pure Java address/navigation/startup policy, unit-testable without Android |
| `:app-phone` | `com.code2hack.eyebrowse.phone` | Single-tab System WebView browser for the Phone |
| `:app-rg` | `com.code2hack.eyebrowse.rg` | Reading Glasses launcher showing "Not connected to Phone"; no WebView, no network |

Deliberately not in this slice: hosting/capture, pairing, transport, RG browser/input/Reading,
agent/ASR/VPN/Tailscale/CXR, multi-tab infrastructure, release builds, and any remote-control or
debug command surface in the product APKs.

## Build and host verification

Environment: JDK 17, Android SDK (`compileSdk`/`targetSdk` 35, `minSdk` 32), Gradle wrapper 8.11.1,
AGP 8.7.3. Keep machine-specific SDK paths in the ignored `local.properties`.

The slice ran with the resource envelope `--max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g` and debug
variants only:

```bash
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
  :core:browser:test \
  :app-phone:testDebugUnitTest \
  :app-phone:assembleDebug :app-rg:assembleDebug \
  :app-phone:assembleDebugAndroidTest \
  :app-phone:lintDebug :app-rg:lintDebug
```

Application ids: `com.code2hack.eyebrowse.phone` and `com.code2hack.eyebrowse.rg`
(`versionCode` 1, `versionName` 0.0.1).

## Phone behavior in one screen

* Address control accepts explicit `http://`/`https://` destinations; a scheme-less dotted domain,
  IPv4 literal, bracketed IPv6 or `localhost` normalizes to HTTPS. Everything else is rejected with
  a compact message and **no** navigation and **no** search fallback.
* One application-owned WebView survives Activity recreation; a process restart offers the saved
  address but never auto-loads it.
* New-window requests stay in the same tab; unsolicited `window.open()` does nothing.
* JavaScript and DOM storage are on; file/content access, geolocation, camera/microphone/file
  chooser, mixed content and SSL-error bypass are off.

The exact grammar table, fixture procedures, device steps and known limitations are in
[docs/testing/phone-browser.md](docs/testing/phone-browser.md).

## Repository layout

* `app-phone/`, `app-rg/`, `core/browser/` — implemented modules (see table above).
* `test-fixtures/browser/`, `tools/browser-fixtures.py` — deterministic local test pages and server.
* `design/`, `SPEC.md`, `MILESTONES.md` — authoritative product/design inputs.
* `experiments/` — isolated spikes that are not part of the product build.
* `docs/` — architecture, decision and testing records.
