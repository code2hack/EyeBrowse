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
| `http://printer/`, `https://nas:8443/` (explicit LAN hostname) | kept/normalized |
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
* bare single-label input such as `printer` (a LAN hostname is accepted when an explicit scheme
  makes it unambiguous: `http://printer/`, `https://nas:8443/`); `localhost` is also accepted bare;
  trailing-dot hosts, out-of-range IPv4 literals, and underscores or leading/trailing hyphens in DNS
  labels are rejected.

Acceptance is syntactic only; it does not promise reachability. The same policy decides the main-frame navigations the WebView asks the host to handle. Three
different origins are kept distinct:

| Origin | Expected behaviour |
| --- | --- |
| Native address bar (typed or pasted, including `javascript:`, `content:`, `file:`, `intent:`, `mailto:`, `data:` and free text) | Rejected before any navigation; the live document, its page script state and the exact editable draft survive; compact feedback appears; no script runs. Covered by the native-control regression. |
| A webpage asks for an unsupported external/local destination | Refused or platform-suppressed: no other app launches, no file/content access, no capability grant, no second browsing session, no false success. A genuinely delivered refusal gets the "Blocked unsupported address" notice; a destination the engine refuses on its own may be a no-op, which is not an app notice. |
| The page's own JavaScript (including a `javascript:` link inside that page) | Ordinary WebView sandbox semantics: the page may change its own DOM/title/document. This is not an address-entry script facility, and EyeBrowse exposes no script bridge. |

Subresource failures never replace the page result.

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

* Activations use `Instrumentation.sendPointerSync` with `SOURCE_TOUCHSCREEN` touch events. This is
  synthetic instrumented input: it establishes ordinary activation (links, buttons, form submit,
  swipe) in the focused EyeBrowse window, but it is **not** human touch, IME or physical-display
  evidence. Espresso's view-level injection was not delivered to this WebView on the Fold6, so the
  suite injects through the system input pipeline instead, with a bounded window-focus wait and at
  most three attempts; a foreground/focus loss ends the attempt instead of retrying into another app.
* Page reads and fixture setup use `WebView.evaluateJavascript` from the androidTest APK only. The
  pinned `espresso-web:3.6.1` was dropped as a simplification approved by the Planner (clarification
  C1). Its active evaluation path already calls `WebView.evaluateJavascript`, so the earlier note here
  that it "drives the page through `javascript:` navigations" was wrong; the precise original failure
  was never established, and no product security property is claimed from it.
* Field values and sentinels written by `setElementValue`/`setElementText` are fixture setup, not
  typing or IME evidence, and `simulateProcessRestartForTest()` is a simulation, not real
  process-death evidence. Real IME, cover/inner display, actual process loss and RG optical rows
  remain separate device rows.
* `realClickElement` calls `scrollIntoView` as setup before measuring geometry; input-driven
  scrolling is proven by the dedicated swipe test, not by that setup.
* Unsupported page-origin destinations on the Fold6: `intent:`, `file:`, `mailto:` and `data:` links
  produce the "Blocked unsupported address" notice with the document unchanged (each case is now
  tested with its own clean baseline); `content:` links are an engine-level no-op with no app notice
  and no change to document or location. The fixture's page-owned JavaScript links are labelled as
  sandbox semantics: the `void(...)` probe changes only its sentinel, and the separate
  string-completion link (`javascript:document.title='javascript-ran'`) can replace the document
  with its completion value while keeping the URL - page behaviour, not an address-bar case.

Argument names and bounds: the suite reads `fixtureBaseUrl` and `secureBaseUrl` (revision 1's
`untrustedHttpsUrl` name is not consumed). Waits are bounded at 20 s per condition, 10 s per
JavaScript evaluation, 15 s for window focus, up to 3 injection attempts 500 ms apart, and 10 s for
fixture readiness.

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
* Page-owned JavaScript is not disabled or rewritten: a `javascript:` link inside a page executes in
  that page's sandbox, and a link whose script completes with a string can replace the document with
  that string while keeping the URL (HTML javascript-URL semantics). The native address bar rejects
  the same payload without running it, which is covered by the address-bar regression.
* Third-party fixture pages, RG Reading/gesture behavior, transport, hosting and multi-tab remain out
  of scope for this slice.
