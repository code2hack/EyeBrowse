# Phone browser slice — testing and behavior notes

Scope: the v0.0.1 Phone browser (`:app-phone`), the shared policy module (`:core:browser`) and the
unconnected RG launcher (`:app-rg`). This document records implemented behavior and the verification
procedures for this slice; it is not a product specification.

## 1. Address grammar (implemented)

`AddressPolicy.resolve()` trims surrounding whitespace, then accepts only these forms:

| Input form | Result |
| --- | --- |
| `example.com`, `example.com/path?q=1#part`, `example.com:8443/` | normalized to `https://…` |
| `http://example.com`, `https://example.com:8443/x` | kept (scheme and host lower-cased) |
| `localhost`, `localhost:8080`, `127.0.0.1:25341/` | normalized to `https://…` |
| `[::1]:25342`, `http://[2001:db8::1]/a` | kept/normalized with the bracketed IPv6 literal |
| Unicode host labels (e.g. `例子.测试`) | converted with `java.net.IDN` to ASCII form |
| valid `%XX` escapes in path/query | kept as written |

Rejected without any navigation, search fallback or "repair":

* empty input and any remaining whitespace/control character (interior spaces, tabs, newlines);
* `javascript:`, `data:`, `file:`, `content:`, `blob:`, `about:`, `intent:`, `mailto:`, `tel:`,
  `ftp:`, `ws:`/`wss:`, `chrome:`, `android:` and other non-HTTP(S) schemes;
* userinfo (`user@host`, `http://user:pass@host`);
* ports that are not 1–65535 (non-numeric, `0`, `65536`, over-long);
* malformed authorities (`host:1:2`, unbracketed IPv6 colons, bad brackets), malformed `%` escapes,
  unsafe delimiters (`\ " < > ^ \` { | }`);
* single-label hosts other than `localhost`, trailing-dot hosts, out-of-range IPv4 literals,
  underscores or leading/trailing hyphens in DNS labels.

Acceptance is syntactic only; it does not promise reachability. The same policy gates in-page
destinations: only main-frame `http`/`https` navigation stays in the session; other schemes are
refused with "Blocked unsupported address". Subresource failures never replace the page result.

## 2. Session, window and lifecycle behavior

* One application-owned `PhoneBrowserSession` holds the single WebView, its state and the app-private
  last committed URL. All WebView work stays on the UI thread.
* Activity recreation detaches the WebView (context reverted to the application context) and
  reattaches the same live document to the new Activity: no reload, no second page, no leaked
  Activity. `android:configChanges` is deliberately not declared so this path is exercised.
* New-window policy is `setSupportMultipleWindows(false)` plus
  `setJavaScriptCanOpenWindowsAutomatically(false)`: an activated `target=_blank` link (or gesture
  `window.open`) becomes a current-tab navigation, and an unsolicited `window.open()` is dropped. No
  child-WebView fallback exists.
* Back order: dismiss the IME → WebView history → leave the Activity.
* Web settings: JavaScript and DOM storage on; file/content access, geolocation, camera/microphone
  and file-chooser grants, mixed content, form-data saving and SSL-error bypass are off. SSL errors
  cancel the load. Main-frame load errors show a compact recovery status; the address bar stays
  usable.
* `onRenderProcessGone` disposes the dead WebView, marks the session not live and shows an honest
  recovery status. Recovery is explicit (the user presses Open); nothing is replayed.
* Persistence is limited to the last successfully committed HTTP(S) URL in app-private preferences.
  After a process restart the address is offered with "no longer live" copy and is loaded only as a
  new GET. Cookies are flushed at the Activity stop boundary. No keystrokes, form drafts, passwords
  or serialized JS state are persisted.

## 3. Fixture server

`tools/browser-fixtures.py` (Python standard library only) serves `test-fixtures/browser/` on
127.0.0.1:25341 (HTTP) and 127.0.0.1:25342 (HTTPS with a disposable self-signed certificate
generated into the state directory). Pages carry a changing per-load marker (`L1`, `L2`, …), a click
counter, `target=_blank`/popup/history/scroll/form/cookie/localStorage/destination pages, a
controlled `500` (`/fail`), a 404 and an untrusted-TLS page.

```bash
python3 tools/browser-fixtures.py --state-dir /tmp/eyebrowse-fixtures
# READY http=http://127.0.0.1:25341 https=https://127.0.0.1:25342 state=…
```

`/api/observations` exposes load counters, request metadata and POST submissions. The POST endpoint
records only a synthetic `test_id` and the submitted field **names**; typed values are discarded and
never written to the state file. The TLS key pair is test-only scratch material, is never installed as
a trusted certificate and is never committed.

## 4. Host verification (V1)

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/code2hack/Android/Sdk
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
  :core:browser:test :app-phone:testDebugUnitTest \
  :app-phone:assembleDebug :app-rg:assembleDebug \
  :app-phone:assembleDebugAndroidTest \
  :app-phone:lintDebug :app-rg:lintDebug
```

Expected: non-zero JUnit counts for `:core:browser:test` and `:app-phone:testDebugUnitTest`, no lint
errors, and three debug APKs (`app-phone`, `app-rg`, `app-phone` androidTest) with recorded hashes.

## 5. Device verification (debug only)

Prerequisites: fixture server running on the host; `adb reverse` for the two reserved ports only
(`tcp:25341`, `tcp:25342`); the Fold6 connected and authorized. No release builds are used.

```bash
adb -s <phone-serial> reverse tcp:25341 tcp:25341
adb -s <phone-serial> reverse tcp:25342 tcp:25342
adb -s <phone-serial> install -r app-phone/build/outputs/apk/debug/app-phone-debug.apk
adb -s <phone-serial> install -r app-phone/build/outputs/apk/debug/androidTest/app-phone-debug-androidTest.apk
adb -s <phone-serial> shell am instrument -w \
  -e fixtureBaseUrl http://127.0.0.1:25341 \
  -e secureBaseUrl https://127.0.0.1:25342 \
  com.code2hack.eyebrowse.phone.test/androidx.test.runner.AndroidJUnitRunner
adb -s <rg-serial> install -r app-rg/build/outputs/apk/debug/app-rg-debug.apk
```

The instrumented suite (`BrowserInstrumentedTest`) covers: fresh start, address open, real-touch
activation, `target=_blank` and gesture popup in the same tab, unsolicited popup no-op, real swipe
scrolling, recreation retention (document, field values, load count), simulated process-restart
recovery without auto-load, untrusted-HTTPS refusal, controlled HTTP failure, unsupported
destinations, and the harmless POST correlation (one submission, field names only).

Device-observed notes from this slice's first run:

* Activations that must count as a user gesture use `Instrumentation.sendPointerSync` with
  `SOURCE_TOUCHSCREEN` touch events. Espresso's view-level injection was not delivered to this
  WebView, and `espresso-web` cannot evaluate JavaScript against it because that library drives the
  page through `javascript:` navigations, which the product (correctly) refuses. `espresso-web` was
  therefore dropped from the test dependencies; page reads use `WebView.evaluateJavascript`.
* Real input injection is only accepted while the browser owns the focused window, so the device
  must be awake, unlocked and not in use by another app for the automated suite.
* Unsupported in-page destinations on this WebView: `intent:`, `file:`, `mailto:` and `data:` links
  are refused with the "Blocked unsupported address" notice; `content:` links are an engine-level
  no-op (no navigation, no notice); `javascript:` links are refused but leave a blank document
  instead of running the script, so the session URL and single-WebView guarantees still hold.

Whether those `content:` and `javascript:` outcomes satisfy the ticket's rejection requirement is
under Planner clarification: this document records observed behaviour and does not treat it as
accepted.

Physical rows that automation cannot satisfy and that are exercised with the Owner: unlock and
fold/unfold on the cover and inner displays, real IME entry/correction/submission (including masked
password entry), and the RG optical check of the unconnected status screen.

## 6. Known limitations and open device items

* IME/insets: the layout applies system-bar and display-cutout insets with
  `windowSoftInputMode="adjustResize"`. Whether the keyboard resizes the WebView viewport as expected
  on Android 15+ edge-to-edge is verified on the device, not by host tests.
* `usesCleartextTraffic="true"` is the documented allowance for explicitly typed `http://`
  addresses. It is a browsing capability of this slice, not a Phone–RG transport decision.
* The Kotlin stdlib duplicate-class conflict (`androidx.lifecycle` → coroutines → split
  `kotlin-stdlib-jdk7/jdk8` vs merged `kotlin-stdlib`) is resolved by version-alignment constraints
  in `app-phone/build.gradle.kts`; no pinned product dependency changed.
* A gesture `window.open()`/`target=_blank` current-tab result depends on the platform WebView's
  documented single-window behavior; a device mismatch is reported rather than worked around with a
  hidden popup view.
* Clicking a `javascript:` page link is refused at the navigation level, and the engine then leaves a
  blank document (not the request URL). The session URL, history ownership and single-WebView
  guarantees are unaffected, and the address bar itself rejects `javascript:` input outright.
* Third-party fixture pages, RG Reading/gesture behavior, transport, hosting and multi-tab remain out
  of scope for this slice.
