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
controlled `500` (`/fail`), a 404 and an untrusted-TLS page. The index page also prints the
advertised fixture origin so the configured bind is visible in-app.

```bash
python3 tools/browser-fixtures.py --state-dir /tmp/eyebrowse-fixtures
# READY http=http://127.0.0.1:25341 https=https://127.0.0.1:25342 state=… bind=127.0.0.1
```

`/api/observations` exposes load counters, request metadata and POST submissions. The POST endpoint
records only a synthetic `test_id` and the submitted field **names**; typed values are discarded and
never written to the state file. The TLS key pair is test-only scratch material, is never installed as
a trusted certificate and is never committed.

### 3.1 Opt-in private-LAN mode (C2)

`--bind` defaults to `127.0.0.1` and accepts a numeric loopback or RFC1918 IPv4 address only;
wildcard, public, multicast, link-local and hostname values are rejected, and the address is checked
against the host's own interfaces before startup. Every advertised URL (`READY`, the in-page
`{{BASE_URL}}`/`{{SECURE_URL}}` placeholders, the generated certificate SAN) derives from that
validated configuration, never from a request `Host` header. Omitting `--bind` keeps the loopback
mode unchanged. HTTPS stays an untrusted-certificate rejection fixture.

```bash
python3 tools/browser-fixtures.py --bind 192.168.0.52 \
  --http-port 25341 --https-port 25342 --state-dir "$TMPDIR/fixtures-lan-c2"
```

Because the LAN phase is a different origin (`http://192.168.0.52:25341`), use a new state directory
and fresh persistence baselines: cookies and Web storage are per-host, so absent data at the new host
is not lost data from the loopback origin, and a new-origin success is not continuity of an old page.

Fixture-specific safety guards apply whenever the server runs:

* only the approved fixture pages and fixed API routes are served (no directory listing, upload,
  proxy, execution or write endpoint); page resolution refuses symlinks, nested/encoded traversal,
  hidden files and repository paths;
* bounded requests/resources: 2 KiB targets, 16 KiB POST bodies with one valid `Content-Length`
  (chunked/missing/conflicting lengths refused), 16 form fields, 8 active connections shared across
  both listeners (including TLS handshakes), 5-second idle I/O and an absolute 10-second connection
  deadline that interrupts partial TLS/header/body reads, and no keep-alive after a response;
* bounded state and logging: fixed allowlisted load/note keys, 200-entry request history, records
  limited to known synthetic test IDs, field names, note names/outcomes and cookie modes/tokens, and
  log lines limited to route/status (never raw targets or values);
* fixture-owned cookies: `/api/cookies` exposes only `fixture_session`/`fixture_persist`, and the
  cookie page clears only those two names.

```bash
python3 -m unittest discover -s tools/tests -p 'test_browser_fixtures*.py'
```

Those tests start isolated loopback listeners on ephemeral ports with temporary state directories;
actual LAN reachability is a separate check and is not claimed by them.

### 3.2 Per-case trace and fresh-document protocol

`/api/case` version-2 records distinguish `baseline`, `dispatch`, `observation`, and `failure`.
A baseline is not called dispatched. DOM marker/title/location/readiness come from one evaluation;
`sampleStartMs`/`sampleEndMs` bound that evaluation. `actionStartMs`/`actionEndMs` and `dispatchStage`
describe input API calls, not proof of a website effect. `uptimeMs` is client report-construction time;
server `loadSeq` is sampled at receipt, not atomically with the client DOM snapshot.

Capture is acknowledged explicitly. A false/missing acknowledgement fails the check. Preparation,
partial/unknown dispatch, observation, assertion and capture failures retain a bounded failure record
where possible; missing capture is reported separately and cannot replace the original exception.
No uncertain tap is retried. A changed document is recorded as `changed` and still fails the strict
continuity assertion; it is not accepted as an `engine-reload` exception.

Reload baselines retain the settled outgoing identity **before** clicking Reload, including the
first matrix case. The returned readiness snapshot must have a distinct marker, expected title and
location, and complete DOM readiness. Later loading/title observations cannot bless an older marker.
`HarnessProtocol` lives in `src/testShared` and is included only in JVM/androidTest sources; its
regressions cover first-reload/interleaved snapshots, one-shot partial input, acknowledgement failure
and preservation of the original failure. These host tests are not Android reproductions.

Case IDs/outcomes/stages and numeric fields are bounded. Trace locations are sanitized at the client
and parsed against the exact configured origin at the fixture: only an allowlisted fixture path or
`(other)` is stored, with no raw query, fragment, userinfo or arbitrary path identifiers. Actual
in-memory location-equality assertions remain strict and use the original location, not this redacted
representation. Native matrix traces have `native-0` through `native-9` IDs; the six page-origin cases
are distinct. Preserve older trace formats/results with their original meanings.

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

Prerequisites: the mission-owned fixture is running and the explicitly selected Fold6/RG are
connected and authorized. Loopback mode uses only the two reserved reverse mappings; private-LAN
mode uses its recorded origins directly without reverse. Follow the current unattended profile and
resource reservations; no release builds or per-operation Owner prompts are used.

```bash
adb -s <phone-serial> reverse tcp:25341 tcp:25341
adb -s <phone-serial> reverse tcp:25342 tcp:25342
adb -s <phone-serial> install -r app-phone/build/outputs/apk/debug/app-phone-debug.apk
adb -s <phone-serial> install -r app-phone/build/outputs/apk/androidTest/debug/app-phone-debug-androidTest.apk
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

Input/evidence boundaries:

* Activations use `Instrumentation.sendPointerSync` with `SOURCE_TOUCHSCREEN` touch events. This is
  synthetic instrumented input: it establishes ordinary activation (links, buttons, form submit,
  swipe) in the focused EyeBrowse window, but it is **not** human touch, IME or physical-display
  evidence. Prepare focus and geometry before one DOWN/UP attempt. Any partial/uncertain failure
  ends that attempt and is recorded; focus reacquisition never licenses replay. Input API return is
  distinguished from the observed fixture effect.
* Page reads and fixture setup use `WebView.evaluateJavascript` from the androidTest APK only. The
  pinned `espresso-web:3.6.1` was dropped as a simplification approved by the Planner (clarification
  C1). Its active evaluation path already calls `WebView.evaluateJavascript`, so the earlier note here
  that it "drives the page through `javascript:` navigations" was wrong; the precise original failure
  was never established, and no product security property is claimed from it.
* Field values and sentinels written by `setElementValue`/`setElementText` are fixture setup, not
  typing or IME evidence, and `simulateProcessRestartForTest()` is a simulation, not real
  process-death evidence. Actual automated input/IME, window and process-loss evidence remain
  separate from setup. Unavailable physical-only observations follow the current unattended profile,
  not a new Owner wait.
* `realClickElement` calls `scrollIntoView` as setup before measuring geometry; input-driven
  scrolling is proven by the dedicated swipe test, not by that setup.
* Page-origin cases independently distinguish an actual app refusal from an engine no-op; both
  require the original document/location and usable controls, with no inherited block notice. Do not
  infer a universal callback/cause from earlier observations. The separate page-owned JavaScript
  `void(...)` probe may change its sentinel; the labeled string-completion probe may replace its
  document under normal page-script semantics. Neither permits native-address script execution.

Argument names and bounds: the suite reads `fixtureBaseUrl` and `secureBaseUrl` (revision 1's
`untrustedHttpsUrl` is not consumed). Condition polling has a 20 s budget and may finish after the
last bounded predicate call; each JavaScript evaluation has a 10 s bound, with at most one retry for
read-only observations. Focus preparation has a 15 s bound, input is single-shot, failure-only DOM
capture has one 2 s evaluation, and each trace HTTP connect/read has a 5 s timeout. Record actual
runtime and nested waits rather than claiming a tighter wall-clock guarantee.

Run required software checks on the available real devices and use reserved supplemental window
conditions as specified by the current plan. Unavailable physical-only observations are
`NOT EXERCISED — unattended profile`, not passes or unchanged Owner blockers. Preserve valid prior
Owner observations with their actual build/source. Secure-lock/private-authentication and management
route safety remain real boundaries.

For a private-LAN run, the address-field journey uses `fixtureBaseUrl=http://192.168.0.52:25341` and
`secureBaseUrl=https://192.168.0.52:25342`, and must not depend on `adb reverse`. Before treating the
fixture as available for an input journey, prove the load through EyeBrowse's own address field:
visible form page, fresh load marker,
real page location and a correlated server-side load observation on the pinned installed APK.
Device-side curl or ping is not a substitute for that proof.

Resolve current device access according to DEV.md. A failed cached IP, empty service discovery or
stale ADB listing does not establish LAN absence, sleep, or a product cause. Keep website reachability,
ADB control availability and actual app loading separate; preserve any failed run and its exact
route/provenance instead of relabeling it after recovery.

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
