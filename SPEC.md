# EyeBrowse SPEC v0.0.3

**Revision:** D3.1  
**Status:** Owner-directed implementation contract; unreleased development, not an implementation PASS  
**Owner:** code2hack  
**Editor:** Planner: SPEC Design  
**Integration branch:** `work/v0.0.3-rg-input-proxy`  
**Base:** merged v0.0.2 on `main@32b69fa8aa2c5aa360424b73b37657f74b3a399c`  
**Normative companion:** [RG HUD H3.1](docs/design/v0.0.3-rg-hud.md)  
**Delivery plan:** [v0.0.3 plan](docs/plans/v0.0.3-plan.md) and [canonical issue index](docs/plans/v0.0.3-issues.md)

## 0. Authority, scope and interpretation

The Owner explicitly instructed the Planner to draft and publish the v0.0.3 SPEC and all six issue drafts, and separately confirmed that this version needs its own branch. This contract synthesizes those decisions and the presented D1 version-plan defaults. It is a complete replacement for the active SPEC on this branch, not an override appended to v0.0.2. Historical documents and evidence keep their original version and meaning.

This version changes **the RG application only**. Phone code, package metadata, network configuration and physical Phone acceptance are not part of this delivery. RG remains standalone. The unchanged portrait toolbar, black presentation, tabs, bookmarks, native keyboard and accepted password behavior are retained. New work consists of faster pointer movement and numeric controls, shallow off-screen scrolling strips, QR reliability, and bundled app-local Mihomo with profile management, combined node selection, China-DIRECT routing and automatic refresh.

`MUST`/`MUST NOT` are requirements; `SHOULD` is a default requiring a reason to depart. Initial numeric values and import-format boundaries below are the completion defaults presented in the D1 packet, now used for the Owner-authorized implementation. They are not claims that the Owner personally selected each engineering number. Workers choose implementation details within these outcomes; concrete material changes return through the existing authority path, not a whole-ticket planning ceremony.

The existing `AGENTS.md`, `DEV.md` and Kotlin-only ADR remain authoritative for process and language. No separate Planner ticket plan, Worker approach preapproval, new blanket HUD approval round or repeated approval of v0.0.2's unchanged design is required. Device access and security changes still require their existing operational authority. Publication is not an agent assignment, successful device test, main merge, or release.

## 1. Product and user journeys

EyeBrowse is a local multi-tab RG WebView browser with built-in head pointer and English keyboard. It uses the full **480 pixels wide × 640 pixels high, portrait** display, one compact top toolbar and black backgrounds with light content. Pointer movement is faster and may intentionally enter shallow invisible top/bottom scroll strips. The built-in proxy starts automatically for EyeBrowse traffic; profile toggles contribute nodes to one shared automatic pool.

| ID | User story |
| --- | --- |
| V3-U01 | As an RG user, I want websites to run on my glasses without the Phone app, so that browsing remains independent. |
| V3-U02 | As an RG user, I want the same head movement to move the pointer twice as far by default, so that aiming is not sluggish. |
| V3-U03 | As an RG user, I want precise decrease/value/increase controls for pointer and scroll speeds, so that I can tune both independently. |
| V3-U04 | As an RG user, I want shallow off-screen scroll zones, so that a small inward head movement does not stop scrolling too soon. |
| V3-U05 | As an RG user, I want the cursor to reappear and scrolling to stop when it returns to the screen, without undoing unlimited invisible travel. |
| V3-U06 | As an RG user, I want all toolbar, tab and bookmark controls preserved, so that the update does not disrupt navigation. |
| V3-U07 | As an RG user, I want real camera QR scans to recognize usable codes reliably and recover after cancel/resume. |
| V3-U08 | As an RG user, I want the ordinary QR scanner to open websites and the Proxy scanner to add subscriptions, without guessing the intended purpose. |
| V3-U09 | As an RG user, I want built-in keys to edit the currently focused local field with normal Enter and Done behavior. |
| V3-U10 | As an RG user, I want the built-in proxy to start automatically without taking over my system VPN or requiring the Phone. |
| V3-U11 | As an RG user, I want each profile independently enabled or disabled, so that all enabled subscriptions contribute candidates together. |
| V3-U12 | As an RG user, I want the healthy node with lowest measured delay from the whole enabled pool, rather than one selected profile. |
| V3-U13 | As an RG user, I want mainland-China and local/tailnet destinations direct and other public traffic automatically proxied. |
| V3-U14 | As an RG user, I want direct browsing whenever no usable proxy candidate exists. |
| V3-U15 | As an RG user, I want subscriptions updated every 30 minutes without manual refresh controls or loss of working data on failure. |
| V3-U16 | As an RG user, I want a profile list containing only names, toggles, Delete and Add, not a proxy dashboard. |
| V3-U17 | As an RG user, I want data-preserving upgrades and honest recovery, without passwords or subscription tokens appearing in logs. |

## 2. Runtime and delivery boundaries

Use the existing Android-12/API-32-compatible RG app and current local WebView path as the starting point. The historical RG serial is `1906092617103125`; Manager must verify actual device/route/provider for execution. The target canvas is 480 × 640, always width × height. Measure display rotation, density and insets; do not transpose or stretch a screenshot into conformance.

First-party Android/control code, tests and maintained JVM tooling are Kotlin; Gradle uses Kotlin DSL. Unmodified third-party/generated code keeps the standing ADR exception. Select and pin a genuine compatible Mihomo core and existing Android binding/artifact in V3-03, recording source, version/hash, ABI, build and redistribution requirements. Do not claim a specific license or binding is compatible without checking the chosen artifact. A new maintained native-language bridge, provider replacement, root or system VPN is not an assumed workaround; report the concrete need if existing supported integration cannot work.

Preserve RG application ID, signing compatibility and app-private data. Finish with RG `versionName` 0.0.3 and a versionCode higher than the actual delivered/installed baseline, not merely the old Gradle label. Phone metadata stays unchanged. Do not add a Phone dependency or compulsory paired test matrix. Shared-consumer host checks apply only when their dependencies are actually touched.

## 3. Preserved local browser behavior

### 3.1 Navigation, tabs and bookmarks

Support complete URL entry/correction, HTTPS, explicit HTTP including legitimate local addresses, and existing scheme-less-domain normalization. Invalid input keeps the draft/current page; arbitrary free text is not silently sent to a search provider. Do not execute script/file/native-intent payloads from address or QR input or silently bypass certificate errors. A short displayed address never truncates the real editable draft or navigation target.

Tabs have separate local WebViews/documents and Back/Forward histories, while ordinary RG tabs may share the local website-data profile. At least four live tabs must remain usable. Tab counter `i/n` is one-based, e.g. `2/4`. Add appends/selects a new empty tab and begins address editing. Close selects the next remaining position, or the preceding tab when closing the last position. Closing the sole tab leaves a fresh empty `1/1` tab, not app exit. Selection does not wrap at boundaries. Normal switching preserves live pages/history/form values without intentional reload; report actual renderer loss honestly.

The counter opens a tab list. Touchpad swipe right selects next tab; swipe left selects previous tab. Swipes do not invoke history or vertical scrolling. The ordinary four-item More menu remains **Add new tab / Bookmarks / QR scan / Settings**. Unsolicited pop-ups do not create tabs; ordinary user-activated new-window links may open in the current tab.

The independently clickable star inside the trailing address slot saves/removes the committed current page, including when an unsent address draft differs. Outline/filled/disabled state reflects actual validity and persistence. Bookmarks support empty/list/open-current-tab/remove/error states and survive restart. A failed save is not shown as durable success. Preserve meaningful URL path/query/fragment identity.

### 3.2 Native input and keyboard

Use direct Android View/WebView dispatch and current focus with one gesture/input consumer. Do not create application-wide original-target identities, operation generations, ABA checks, deferred-key queues or remote frame/ordinal admission for local input. Ordinary lifecycle cleanup is sufficient for actual repeating scroll/camera work.

A recognized short tap activates once. A recognized double tap has no action and suppresses its constituent single taps. No recenter control or Normal/Reading mode returns. A tap while the logical cursor is off-screen has no target and does nothing; this is a current-coordinate hit-test rule, not a stale-input framework. Existing horizontal tab swipes retain their normal applicable-state behavior even while the pointer is off-screen.

Retain built-in QWERTY, Shift/case, numbers, common URL/form symbols, Space, Backspace, Enter/Open and separate Done. Real keys operate the address draft and actual text/password/textarea/basic plain contenteditable fields through native focus. Preserve native selection, caret, Unicode and Backspace behavior. Enter follows ordinary newline or field/form semantics; Done dismisses without deliberate submission, navigating an address draft or undoing completed text. Keyboard show/hide reflows the local viewport without intentional reload or data loss; selected field/useful context remains visible without fighting deliberate scrolling.

Native temporary final-character password echo followed by ordinary masking is **accepted**, as already decided by Owner in issue #29 comment 6072154841. Do not revive an immediate-no-echo gate or custom masking framework. Permanently plaintext password rendering, value mirroring and payload logging are not authorized.

## 4. Faster pointer and numeric Settings

### 4.1 Gain and migration

Pointer speed is one value applied to both axes, measured against v0.0.2 Standard displacement using the visible-screen reference. Range **0.1×–6.0×**, step **0.1×**, default **2.0×**. The same moderate head movement should yield approximately twice the old Standard displacement before saturation. Do not multiply it by another hidden doubling factor or use the padded height to unintentionally change gain. Initially retain existing smoothing; adjust a velocity ceiling if it would defeat the intended increase. Test both moderate movement and a faster trace that exercises caps, plus real small-target operation.

Migrate legacy Low/Standard/High once to **1.5×/2.0×/2.5×**. Preserve subsequent numeric choices; repeated launch must not double again. Scroll speed is independent: **10–1,200 native viewport px/s**, step **10**, default **240**. Old Slow/Standard/Fast become their same numeric **120/240/360** values.

Each setting is one label/decrease/value/increase row. One tap changes one step, applies immediately and persists automatically. Disable the appropriate arrow at a bound. No slider, preset button group, editable number field, Save button or required long-press acceleration. Report a storage error rather than silently promising persistence.

### 4.2 Bounded off-screen strips

Logical pointer coordinates use the actual drawable screen; below are canonical coordinates. Initial strip depth is **24 rendered pixels** above and below. Only vertical bounds extend; horizontal bounds keep the cursor footprint on-screen.

| Logical y | Cursor and scrolling |
| --- | --- |
| `-24 <= y < 0` | Cursor hidden; scroll the currently visible applicable surface upward. |
| `0 <= y < 640` | Cursor drawn; no head-triggered vertical scrolling. |
| `640 <= y <= 664` | Cursor hidden; scroll downward. |
| Outside outer limits | Clamp to -24 or 664 and discard further outward movement/filter backlog. |

Scale offsets from actual screen top/bottom if unavoidable insets exist; these are virtual strips, not extra physical display pixels or UI padding that reduces the page. Temporary partial clipping of the glyph while its center crosses the edge is acceptable. Do not create another hidden trigger band inside the screen.

A hidden cursor inside a strip remains tracking-available. A small inward movement still within a strip continues scrolling. Re-entering the visible screen stops scrolling and redraws the cursor. Extended outward head movement cannot accumulate unlimited return debt; the only intentional extra return distance is the finite strip depth. Test top/bottom and corners at several overshoot magnitudes and durations, including slow inward motion.

The full screen remains the reference with keyboard open. Its divider and toolbar bottom are not triggers; page scrolls while keys stay fixed. A long visible utility/profile list may scroll itself, never the covered webpage. No tap targets exist beyond the screen; off-screen taps must not be clamped onto the nearest toolbar/key/delete control.

### 4.3 Ordinary motion lifecycle

Use current pointer coordinates and fresh sensor input, not pitch-from-neutral or broad in-screen hot zones. Stop repeating scroll on visible-screen return, tracking expiry, pause, changed active tab or covering non-scrollable UI. Use ordinary callbacks/lifecycle, not per-action fences. On return to usable UI evaluate current pointer and tracking normally without a special rearm protocol.

Retain **250 ms** sensor-age cutoff, at most **50 ms** integrated motion per step and **100 ms** visible-return/scroll-stop target for declared valid input conditions. No catch-up backlog after stalls. Page endpoints cannot bank unused scroll distance. Sensor unavailability is distinct from purposeful off-screen hiding; a still-visible unavailable cursor may freeze in place, and fresh acquisition automatically establishes the ordinary usable reference without a recenter button.

## 5. QR scanning and two explicit purposes

Reuse one CameraX/decoder path. Diagnose the reported intermittent recognition failure as camera acquisition, preview/analyzer mismatch, exposure/focus/usable image quality, decoder failure or payload handling. Camera invocation is a hypothesis, not an established cause. Inspect actual camera identity, frame dimensions/crop/rotation/strides, arrival and release; use supported camera controls where verified. Do not replace the stack or change permissions/provider based on guesswork.

**More → QR scan:** decode a webpage address, show bounded URL preview, and navigate the current local tab only after explicit Open. Cancel, invalid/unsupported URL, permission denial and camera error have usable exits. Scanning never configures Wi-Fi, pairs with Phone or imports a subscription here.

**Settings → Proxy → Add a new profile:** scan a supported subscription/import URL, fetch and validate it, then automatically add/update the profile and return to the list with a brief result. It never navigates a tab merely because the decoded text is HTTPS. Purpose is selected by entry point, not URL heuristics.

A successful preview or bitmap-only unit test does not establish optical recognition. Use real camera scans of owned short URL and longer dummy subscription codes in a documented clear-code setup, including ten repeat cycles per representative code and a **five-second clear-code acquisition target** from the first usable complete-code frame. Record actual outcome/timing and lighting/distance/display conditions. Test repeated open/cancel/resume and releases. No universal lighting/distance guarantee is implied. If the reported wearer situation cannot be reproduced, state that limitation rather than claim it fixed. Real optical fixtures can be a monitor or available rig; no physical Phone requirement is introduced.

Use dummy QR payloads for retained diagnostic images. Release camera/analyzer/executors on exit using normal lifecycle handling; no stale-scan identity framework.

## 6. Built-in app-local proxy

### 6.1 Core and routing boundary

Bundle an attributable, pinned Mihomo core compatible with the RG and a supported Android integration. It is enabled automatically for EyeBrowse; there is no user master proxy toggle or new system VPN. Start/use it with the RG browsing runtime and stop unused foreground runtime resources normally. Do not keep it alive around the clock solely for update timers.

Target path: **RG WebView → loopback Mihomo → DIRECT or automatic enabled-node pool**. Feature-check supported Android WebView proxy APIs on the actual provider and wait for their configuration completion before relying on them. Route relevant native HTTP subscription/data clients explicitly: a WebView proxy setting is not a whole-process or whole-device routing guarantee. Qualify HTTPS, ordinary HTTP, WebSocket, relevant DNS and other actual supported browser flows. Do not silently claim untested WebRTC/UDP traffic is proxied.

No TUN/VpnService, existing Tailscale replacement, provider/private API or global network-setting mutation is implied. DIRECT uses the RG's ordinary OS routes, including its existing tailnet. Local listener is loopback only; management API, if used, stays private/authenticated and is not exposed to webpages, LAN or arbitrary native bridges. Use finite normal resource limits, not a new orchestration framework.

The normal no-profile state is direct browsing. Core startup/failure must not strand browsing behind a dead loopback listener: use ordinary connection/error handling and direct operation when no usable proxy is available. Do not fake proxy success, introduce a hidden privacy guarantee or auto-replay consequential form requests. DIRECT fallback is the Owner's availability policy, not a kill switch.

### 6.2 EyeBrowse-owned Rule policy

Mihomo stays in **Rule mode**, not Global mode. Ordered policy:

1. Local/private/loopback/link-local and known tailnet destinations/names, with relevant IPv4/IPv6 ranges, use **DIRECT**. Include RFC1918 ranges, Tailscale/shared 100.64.0.0/10 and applicable local IPv6/tailnet ranges. Keep local names routed/resolved on their usable local path rather than sending private names to remote subscription nodes.
2. Maintained mainland-China domain classification uses **DIRECT**.
3. Maintained mainland-China IP classification uses **DIRECT**.
4. Other destinations use the **shared automatic pool** if it contains usable healthy nodes; otherwise **DIRECT**.

Do not equate the China classification with only `.cn`, or claim any list perfectly classifies every domain/CDN. Bundle validated initial regional data with source/version/license provenance. Rule data refresh is daily. The subscription server supplies node data, not this policy. Imported `rules`, DNS, ports, controller/UI, global mode or scripts cannot override the application configuration. This is Mihomo configuration/rule data, not a PAC dependency.

### 6.3 One candidate pool and fallback

Each ON profile contributes all supported usable nodes to one candidate set; multiple ON rows are expected. Select the healthy candidate with **lowest measured latency** to the common configured test endpoint at a selection evaluation, across the full union. Do not first select a winning profile, round-robin profiles or limit testing to one source. Avoid collisions when different subscriptions use the same node names.

Use Mihomo's existing health/selection facilities; configure provider-sourced node tests explicitly where needed. Health tests occur on eligible-pool changes and every **5 minutes** while active. They measure delay to a documented common endpoint, not universal website speed. Exact core options, including selection tolerance/ties, must implement the lowest-delay policy; no custom benchmark engine.

DIRECT is a **fallback**, never a healthy candidate competing against proxy nodes. Test separately: no saved profiles; all OFF; no usable nodes; and all enabled nodes failing health checks. Each results in direct new connections. When eligible nodes recover, subsequent applicable connections return to the selected proxy. Existing sockets need not migrate or be force-closed; toggles, replacement and updates must not deliberately reload tabs. A group empty-fallback option alone is not evidence for the all-unhealthy case. A minimal Kotlin integration of real core health status is acceptable if necessary, but not a duplicate selector/scheduler framework.

## 7. Profiles and dedicated import

Settings → Proxy contains **only profile names, independent ON/OFF toggles, Delete, and Add a new profile**. No node list, selected-node/latency/traffic/core-version/last-update dashboard, manual mode switch, manual node selector or profile editor. Short progress/errors needed to finish an operation are allowed. Long names elide without displacing actions; long lists scroll using the same strips.

New successful source defaults ON. OFF keeps cached source/data but removes its nodes from new selections. Delete removes only that saved source and its contribution; refresh must not resurrect it. Other toggles remain unchanged. Store profile names, source addresses, cached nodes and inclusion state app-privately; UI never substitutes a token-bearing URL for a friendly name.

Initial supported import: HTTP(S) subscription URLs returning Clash/Mihomo YAML with a usable `proxies` list, including full YAML profiles from which **only node definitions** are extracted. Support the conventional `clash://install-config?url=...` wrapper as a URL container, not permission to execute its other settings. Validate using safe parsing and the chosen core's supported schema. Provider-reference-only or unsupported proprietary/encoded formats return an explicit unsupported-format result, not an empty successful row. The importer is not a universal conversion engine.

Rescanning the same subscription source URL updates its existing row, preserving name and ON/OFF. A different source adds a row. Do not merge by display name/hostname or strip account-bearing query components in a way that conflates sources. Use a safe supplied name or Profile N. Failed fetch/parse/validation leaves current profiles/cached working data intact. Do not delete a working source before validating its replacement.

Use current usable connectivity to fetch an initial subscription, including DIRECT when the pool is empty; the first download cannot depend on nodes it has not downloaded. Protect credential-bearing URLs and node secrets from normal logs, screenshots, repository records and web content. Imported data cannot expose a LAN listener or management port, run code or change OS settings.

## 8. Automatic updates

All saved subscriptions, including OFF sources, refresh on a **30-minute / 1,800-second** policy. OFF controls candidate eligibility, not update cadence; no ongoing node tests are needed for excluded candidates. Deleted sources stop updating. Validate new data before replacing the last valid copy. Network failures, malformed updates and one failing source must not erase other sources, silently re-enable OFF rows or defeat direct fallback.

Regional domain/IP data refreshes every **24 hours / 86,400 seconds** from the selected maintained source; startup can use bundled or last valid data before downloading. Validate downloads before activation. Subscription refresh, node health checks and rule-data refresh are separate intervals.

Use one scheduling owner for each refresh operation; coordinate core facilities and ordinary Android background work rather than running duplicate loops. When active, refresh due data and keep the configured cadence under usable connectivity. When inactive, use ordinary permitted scheduling; Android may delay work in Doze, network absence or force-stop. Refresh overdue data on launch/return of usable execution. Do not promise exact wall-clock execution when Android prevents it or introduce exact alarms/permanent foreground services merely for updates.

Recreation/resume must not multiply jobs, listeners or core instances. Test scheduler configuration and actual controlled-endpoint updates; accelerated tests are labeled as tests, not claimed as 30-minute/day-long observations. Apply updates to future connections without deliberately recreating live pages or submitting forms.

## 9. Black HUD, lifecycle, persistence and privacy

App window, toolbar, address, menu, key interiors, Settings, Proxy and empty/error/loading surfaces use opaque **#000000** with light content. No frosted/gray card backgrounds, gradients or bottom dock. Supported webpage primary backgrounds are black with readable forms/dynamic content while useful photo/video/canvas/QR-camera media colors remain. A dark flag alone or black toolbar above a white page does not pass. Preserve the existing qualified live presentation styling; do not replace pages, break links/focus/forms/scripts, globally invert media or claim universal recoloring. Observe transition frames for app-origin white flashes.

The top row stays `× | ← | → | ↻ | [compact address + ☆/★] | i/n | ⋮`. It never gains a title/address second row or loses Forward/Refresh/star to long text. Full detailed geometry and state checks live in H3.1.

Use ordinary tab/View/camera/sensor/core lifecycle cleanup. Preserve cookies, site storage, bookmarks, live tabs where still alive, numeric settings and saved profiles across supported recreation. Report renderer/process loss honestly; never claim unsaved JS/form memory survived a cold restart or automatically replay an uncertain POST. No data clearing is an upgrade prerequisite. No Phone app installation, credential copying or system-wide route change is required.

No browsing payload, passwords, subscription tokens or node credentials in normal telemetry. Use dummy accounts/codes in evidence. Ordinary URL parsing, safe configuration parsing, TLS checks, application storage and management-endpoint isolation remain necessary; these are not the removed local-input fencing protocol.

## 10. Acceptance and issue ownership

The six canonical issues are indexed in [v0.0.3 issues](docs/plans/v0.0.3-issues.md). Initial root work is V3-01, V3-02 and V3-03. V3-04 depends on V3-02/V3-03; V3-05 on V3-04; V3-06 on all five. Their publication numbers replace these aliases in the index; do not infer GitHub numbers from aliases.

| Group | Owner | Required demonstration |
| --- | --- | --- |
| V3-A01 | V3-01 | Default 2× movement on both axes, suitable cap behavior and actual small-target use. |
| V3-A02 | V3-01 | Numeric steps/bounds/persistence; one-time legacy preference migration. |
| V3-A03 | V3-01 | Both finite strips, hidden cursor, continued scroll on slight inward motion, visible return stop, no excess debt/off-screen tap; keyboard/list coexistence. |
| V3-A04 | V3-02 | Real camera recognition and repeat/open/cancel/resume behavior, short and long QR payloads, attributed original-bug limits. |
| V3-A05 | V3-03 | Genuine pinned core loads in RG and carries actual WebView/relevant native traffic without device VPN changes. |
| V3-A06 | V3-03 | China/local/tailnet DIRECT, other destinations shared AUTO; empty and all-unhealthy direct fallback plus recovery. |
| V3-A07 | V3-04 | Minimal profile UI and actual dedicated QR import/update/toggle/delete/storage. |
| V3-A08 | V3-04 | Fastest healthy node can come from either enabled source; OFF/deleted excluded; not a per-profile selector. |
| V3-A09 | V3-05 | 30-minute all-saved-source updates, separate health tests and daily data; last-good preservation. |
| V3-A10 | V3-05 | Single scheduling/resource ownership, overdue startup recovery and honest Android background limitations. |
| V3-A11 | V3-06 | RG-only data-preserving upgrade/version metadata; toolbar/tabs/bookmarks/native input remain intact. |
| V3-A12 | V3-06 | Complete native-size HUD and actual route/QR/input/refresh evidence, resource observations and independent exact-head review. |

Verify at existing highest practical seams: native keys/pad/pose through production handlers, real RG WebView/network requests, owned endpoint route observations and actual optical QR input. Do not introduce large mock/identity frameworks to satisfy a checklist. State what was observed versus simulated or unavailable. Record source/APK/provider/core/dataset/device bindings, useful failure evidence and cleanup; never relabel old v0.0.2 or bitmap-only evidence as a new camera/route PASS.

Starting host commands: `./gradlew :app-rg:testDebugUnitTest :app-rg:assembleDebug :app-rg:assembleDebugAndroidTest :app-rg:lintDebug`, reconciled to current DEV/toolchain. Include affected shared-consumer host checks only if needed; physical Phone tests are not a version gate. Optical/comfort/battery claims need their own observation and are not implied by screenshots. Missing core capability, actual QR setup or unsupported subscription is a concrete finding, not permission to waive the relevant feature result.

Existing v0.0.2 HUD-G1 approval remains historical and effective for unchanged controls/defaults. The Owner-directed v0.0.3 stepper/Proxy/strip changes replace only those specified aspects; no fresh image-generation or blanket approval gate is added. H3.1 structural, color and state checks are implementation acceptance. No whole-version PASS until the six outcomes and final review/cleanup are complete. A real platform/provider/security or scope change still needs a specific disposition; no separate ticket plan.

## 11. References and completion

- [Merged v0.0.2 baseline / PR #40](https://github.com/code2hack/EyeBrowse/commit/32b69fa8aa2c5aa360424b73b37657f74b3a399c).
- [Existing Owner HUD approval](https://github.com/code2hack/EyeBrowse/issues/30#issuecomment-6075367078); [accepted native password feedback](https://github.com/code2hack/EyeBrowse/issues/29#issuecomment-6072154841).
- [Android WebView app proxy API](https://developer.android.com/reference/androidx/webkit/ProxyController).
- [Mihomo providers](https://wiki.metacubex.one/en/config/proxy-providers/), [groups](https://wiki.metacubex.one/en/config/proxy-groups/), [URL-test](https://wiki.metacubex.one/en/config/proxy-groups/url-test/), [routing](https://wiki.metacubex.one/en/config/rules/).
- [Android periodic scheduling](https://developer.android.com/reference/kotlin/androidx/work/PeriodicWorkRequest).
- `AGENTS.md`, `DEV.md`, `docs/decisions/0001-kotlin-only-first-party-code.md`.

These references support available mechanisms, not a claim that the new core, proxy, optical fix or performance already passed. Exact dependency/data versions are selected with evidence during the assigned implementation. Completion is a working, verified RG v0.0.3 under this contract, not a similar-looking screenshot or successful YAML parse.
