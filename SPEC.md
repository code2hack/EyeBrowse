# EyeBrowse SPEC v0.0.2

**Revision:** D2.2 — Owner-directed direct RG-local input simplification; standalone RG, multi-tab and strict HUD retained  
**Status:** Published development-branch contract; custom local stale-input fencing removed by Owner direction; HUD-G1 exact-design approval OPEN  
**Product version:** v0.0.2, unreleased development  
**Project Owner:** code2hack  
**Editor:** Planner: SPEC Design  
**Integration branch:** `work/v0.0.2-independent-clients`  
**Preparation base:** `f6ca270b41cebf04275be1b4b713e08d194967e2`  
**Preserved v0.0.1 source:** `2b217f5fb8d376d0f3d81dcac03006aa9919f197`  
**Mandatory companion:** [RG HUD contract H1.2](docs/design/v0.0.2-rg-hud.md)

## 0. Authority and document use

This file is the complete v0.0.2 portrait-corrected specification on the migration branch. It replaces the previous SPEC there; obsolete requirements are not appended beneath overriding paragraphs. `main` remains a separate preserved baseline until the authorized integration process completes.

The Owner's latest directions require a standalone RG browser, built-in input, a continuously visible bounded cursor, vertical edge scrolling, multiple tabs, bookmarking, QR scan and Settings. The one-row toolbar order and black-background presentation are mandatory. The earlier single-tab and unassigned-swipe assumptions do not govern the RG design.

The companion HUD document is **normative, not inspiration**. Read both documents. SPEC governs product behavior; HUD defines layout and transitions. Earlier generated images are not approved implementation references. **Owner-directed direct-input rule:** taps and keys use native Android View/WebView/focus handling. Do not build per-action target fencing, generation tokens, ABA/reentrant race frameworks, custom stale-key queues or redundant validation/admission layers for the RG-local browser. Remote-only guards belong solely to historical migration code.

`MUST` and `MUST NOT` denote requirements. `SHOULD` denotes a default requiring a recorded reason to depart. `MAY` is permission, not additional required scope. The HUD document identifies Planner completion defaults for details the Owner did not specify, such as last-tab behavior and pixel allocation; those defaults are included in the open exact-design approval gate.

The request to edit these documents authorizes recording the requirements on the migration branch. It does not record Owner visual approval, passing implementation, a release, new device privileges, or permission to merge protected articles to `main`. `AGENTS.md` and `DEV.md` continue to govern roles, assignments, review, device access and publication. The HUD gate may be satisfied by explicit review of the exact contract and native-size references; a new image-generation session is not required by this edit.

## 1. Product definition

> EyeBrowse v0.0.2 is a standalone RG browser with local tabs, a built-in head pointer and keyboard, a compact single-row top toolbar, and black-background/light-foreground presentation. The cursor never leaves the screen. Contact with the top or bottom screen edge scrolls the active browsing surface; horizontal touchpad swipes switch tabs.

RG executes and renders its own websites. Phone EyeBrowse continues ordinary touch browsing independently. Each device owns its website requests, browser memory, cookies and site storage. Closing, stopping, foregrounding or locking Phone EyeBrowse does not control RG browsing. Phone may provide Internet connectivity without running the Phone EyeBrowse app.

The RG browser has one interaction model. Keyboard visibility, menus, tab selection, bookmarks, settings and QR scanning are utility states, not Normal/Reading modes. There is no recenter control, replacement recenter gesture, mode toggle, bottom action dock, or mode-dependent cursor hiding.

The migration preserves useful existing browser/input logic, not the requirement for Phone-hosted execution. The ordinary RG path must not start pairing, hosting, remote-frame reception or control handoff. Legacy shared/Phone consumers must be traced before removal. Phone's established browser, data and installed identity remain intact; this revision does not require redesigning Phone into the RG HUD.

### 1.1 User journeys

| ID | User story |
| --- | --- |
| U01 | As an RG user, I want to browse without Phone EyeBrowse, so that another app is not a runtime prerequisite. |
| U02 | As a Phone user, I want normal browsing and stored data preserved, so that the RG migration does not disrupt my phone. |
| U03 | As an RG user, I want Close, Back, Forward, Refresh, address, bookmark star, tab position and More to remain reachable in one top row, so that the rest of the small display is useful content. |
| U04 | As an RG user, I want black surfaces and light content, so that the HUD uses the requested display presentation consistently. |
| U05 | As an RG user, I want the pointer to start automatically and remain visible, so that no mode or recenter step is needed. |
| U06 | As an RG user, I want slight inward motion to release a saturated pointer, so that excess outward movement never traps it. |
| U07 | As an RG user, I want edge contact to scroll and inward departure to stop, so that pointing and scrolling use one model. |
| U08 | As an RG user, I want to create, select, swipe between and close local tabs, so that I can keep several pages open. |
| U09 | As an RG user, I want each tab's Back/Forward history and live state preserved across normal switches, so that switching is not reloading. |
| U10 | As an RG user, I want the star to save the current page and Bookmarks to reopen it, so that I can return to chosen locations. |
| U11 | As an RG user, I want QR scan to offer a decoded webpage address for deliberate opening, so that scanning navigates without pairing. |
| U12 | As an RG user, I want genuine, bounded pointer/scroll settings, so that the Settings entry has useful behavior. |
| U13 | As an RG user, I want to edit full addresses with the built-in keys even though the toolbar shows a short location, so that compact display never truncates navigation data. |
| U14 | As an RG user, I want to edit text, password, multiline and basic plain editable fields, so that supported forms remain usable. |
| U15 | As an RG user, I want Enter and Done to differ, so that dismissal never unexpectedly submits. |
| U16 | As an RG user, I want to scroll while the keyboard is open, so that I can inspect the page without moving the keyboard. |
| U17 | As an RG user, I want taps and keys sent directly to the currently visible local browser and focused editor, so that interaction is immediate without artificial queues or per-action validation. |
| U18 | As a browser user, I want honest restart recovery and a data-preserving upgrade, so that lost runtime state is not fabricated or replayed. |

## 2. Targets, implementation and engine qualification

### 2.1 Display and devices

The RG display and canonical HUD review canvas are **480 pixels wide by 640 pixels high, portrait**. Dimensions in this specification always use **width × height** order. The full available app display is used. Android-reported buffer orientation, display rotation, insets and density must be measured on the real RG; they are not inferred from image dimensions.

The runtime must use the native **480 × 640 portrait** presentation. Do not request landscape rotation, transpose width and height, stretch a landscape screenshot, or crop the screen to satisfy the erroneous previous 640-wide layout. Any genuine runtime geometry mismatch must be recorded without silently changing the target. The one-row toolbar is recomputed for 480 pixels of width; its compact address text yields space, not the required controls or the bookmark star.

Retain an Android 12/API-32-compatible RG path. The historical physical RG identity is `1906092617103125`; the current Phone regression reference is the authorized S20+ SM-G9860/API31 (`R5CN30NA8GX`). Manager verifies current access, identity, software and screen procedures. No unobserved hardware or wearer condition is inferred from these references.

### 2.2 Language, packaging and boundaries

Keep one repository and the Phone/RG Android applications. ADR-0001 remains binding: first-party Android, shared/core, JVM/Android tooling and tests use Kotlin, Gradle uses Kotlin DSL, new first-party Java is prohibited, and actively changed Java debt is migrated. Generated and unmodified third-party sources retain their documented exceptions. Reused first-party renderer editing logic retains its Kotlin-authored build path.

Preserve application identifiers, signing compatibility and ordinary stored data. A qualification probe may use a distinct disposable package without replacing a product application.

| Component | Responsibility |
| --- | --- |
| Phone runtime | Its existing local page, touch/IME input, navigation and storage. |
| RG browser runtime | Local tab objects, documents, history, editor execution and rendering. |
| RG HUD/input | Exact toolbar, cursor, keyboard and utilities; direct Android View/WebView events and normal focus. |
| RG persistence | Local bookmarks, approved settings and safe location/recovery metadata. |
| Shared logic | Applicable browser policy, input models and tests, without a shared live WebView. |

### 2.3 Qualification boundary

Begin #28 with the installed System WebView in an ordinary RG application window. Record provider/version, supported update route, local navigation, editor isolation, real rendering and lifecycle observations. Provider upgrades and alternate engines require the applicable compatibility and authorization decision; this document does not silently install one.

The selected engine must also be qualified for the dark-content policy in §9. Passing a local HTML sample is not proof of arbitrary website compatibility, maintainability or dark rendering. Report native theme, author-light page, dynamic content, fields and media results separately.

A supported negative investigation can complete the investigation but cannot satisfy a downstream requirement for a working engine/editor. Qualify actual local WebView focus, field editing, rendering and layout through real key actions. Use the native input path where possible; any existing editor adapter remains minimal and must not impose custom target-identity fencing.

## 3. Browser navigation and local tabs

### 3.1 Navigation

Provide full-address entry/correction, Back, Forward, Refresh, links, ordinary buttons and clear errors. Support HTTPS, explicit HTTP including legitimate local addresses, and normalization of ordinary scheme-less domains to HTTPS under the existing address policy. Invalid input preserves the draft and current page. Free text is not silently sent to a search provider.

The compact address display may elide the path; the editable draft and navigation target remain complete. Display actual origin information without a false security claim. Current URL, title and loading state follow the selected local WebView using ordinary UI updates; no per-action callback-fencing layer is needed.

Browser-generated navigation never executes arbitrary script/file/native-intent strings from address entry or QR data. Certificate errors are not bypassed silently.

### 3.2 Tab contract

RG supports multiple local tabs, with at least four simultaneously open tabs demonstrated in acceptance. `2/4` means one-based active index 2 in a total of 4; it is not a hardcoded illustration or a four-tab limit.

Each tab has a local WebView/document and independent Back/Forward history. Input goes to the selected visible tab and the currently focused View/editor. Tabs may share the RG browser's local website-data profile; Phone keeps its independent profile.

The toolbar `×` closes the current tab. More → **Add new tab** creates and selects a new empty tab. The counter opens a compact list for explicit selection. Touchpad swipe **right selects the next tab** and swipe **left selects the previous tab**. Directions are wearer-facing directions qualified against actual input events, not blindly copied from old forward/backward key names.

Planner defaults to be reviewed at HUD-G1: tabs are ordered by creation; append new tabs; do not wrap at the first/last tab; after close, select the next tab at the same position or the previous tab if the last position closed; closing the sole tab leaves a fresh empty tab at `1/1`, not an app exit. A new empty tab focuses local address entry. These behaviors must be tested as state transitions, not inferred from a mockup.

Normal switching must preserve surviving tabs' live pages, history and form values without intentional reload. Only the currently visible tab receives head/pad/keyboard operations and edge scrolling. If a new tab cannot be allocated, report it without silently closing or replacing an existing one; report actual renderer loss honestly.

Unsolicited pop-ups may not create tabs. For this revision, ordinary user-activated links requesting another window may open in the current tab; explicit Add new tab is the required creation route. Do not invent automatic background tabs.

### 3.3 Direct local input

Use normal Android View/WebView event dispatch, focus and visibility. On tab selection or close, dismiss its old keyboard as appropriate and stop active edge scrolling. Recognized key/tap actions execute directly against the current visible control; do not create an independent delayed action-delivery queue.

**Custom local stale-input frameworks are prohibited.** No per-tap document/element identity tokens, operation generations, ABA tests, event-replay ledgers, redundant hit-test snapshots or queued-key cancellation layers. Remote frame/context/ordinal admission must not be carried into the local browser interface.

Use ordinary selected-WebView UI ownership and normal Android lifecycle behavior. Hidden tabs do not receive physical controller input. Any real asynchronous navigation/camera result follows the current visible UI state; no browser-wide callback-fencing protocol is required. Edge scrolling acts only on the visible page while its current start/stop conditions hold.

## 4. Required HUD and utility functions

[The HUD contract](docs/design/v0.0.2-rg-hud.md) defines exact geometry, colors, states and tests. The mandatory toolbar is one row, in this order:

`×  |  ←  |  →  |  ↻  |  [ compact address …  ☆/★ ]  |  i/n  |  ⋮`

**The bookmark star is a separate clickable control INSIDE the trailing end of the address slot. It must not be omitted or moved into More.** Back and Forward are separate visible controls, including when disabled. No second title/address row, lower action dock, time/battery strip, decorative device frame or duplicate menu is part of the app HUD.

The overflow contains exactly these first-level items, in this order: **Add new tab; Bookmarks; QR scan; Settings.** Every item has implemented behavior and a recoverable exit. Clicking through an overlay to the webpage is prohibited.

### 4.1 Bookmarks

The outline star saves the active tab's actual current navigable URL and useful title in app-private persistent bookmarks. Filled star represents saved state. Activating it again removes that bookmark; failed persistence must not be displayed as durable success. The active page's bookmark state updates after navigation, tab switch and list deletion.

The star always refers to the committed current page, not an unsubmitted address draft. It does not blur or submit an editor solely to save a page. During address entry it stays visible and still bookmarks the underlying page; an empty/non-navigable page shows a disabled outline star with no hidden replacement action.

The Bookmarks view supports empty state, opening a saved URL in the current tab, and explicit removal. Duplicate saving is idempotent by the documented URL identity; preserve meaningful path/query/fragment components. Bookmark persistence is distinct from logging sensitive URLs in diagnostics.

### 4.2 QR scan

QR scan is a local address acquisition flow, not Phone pairing. Request camera permission only for deliberate scanner entry. Decode into a bounded preview; the user explicitly selects Open to navigate the current tab through the same address policy. Decode alone must not navigate, submit, run script or change device/network settings.

Provide cancel, permission-denied, invalid-code/unsupported-value and camera-unavailable states. Release camera resources when scanning stops. QR decoding presents a URL for explicit Open on the current visible tab; no stale-scan identity/fencing framework is required. Camera preview remains media.

### 4.3 Settings

Settings must contain working controls, not future placeholders. Planner completion default for HUD-G1: **Pointer sensitivity** (Low `0.75×`, Standard `1.0×`, High `1.25×`) and **Edge-scroll speed** (Slow `120`, Standard `240`, Fast `360` native viewport px/s). Persist the selected non-secret values locally; fresh installs use Standard. Report the exact setting in test evidence.

These bounded alternatives are review defaults; the mandatory initial rate stays 240 px/s. Settings changes apply prospectively without recentering or motion backlog. Dark presentation is fixed. Numeric layout/tuning defaults and minimal Settings contents require approval at the open HUD gate.

## 5. Bounded always-visible pointer

Keep a single built-in pointer over the active EyeBrowse window, including keyboard, menu and utility states. Sensor unavailability freezes a visible unavailable cursor and disables unsafe actions; it does not hide the pointer or continue scrolling. The app is not required to draw over Android system permission screens it does not own.

Automatically establish a fresh head reference near the screen center on initial usable acquisition. Resume/reacquisition must avoid stale deltas and cannot rearm an old edge episode. No button, menu item or substitute gesture for manual recentering is introduced.

Yaw and pitch control horizontal and vertical pointer motion through timestamp-aware smoothing/noise handling. Measure the full drawable app screen, actual insets, rotation and glyph size. The entire glyph, internal executable pointer position and hit-test coordinates stay in bounds. The page viewport and full cursor bounds are distinct.

At any boundary, discard outward excess from angular offset, target position and filter backlog. Equal inward input must yield equivalent inward return regardless of previous overshoot amount or hold duration. Merely clipping the drawn cursor while storing hidden travel does not meet the requirement. Test all edges, corners and slow intentional inward movement.

A relative-motion model or equivalent boundary rebasing is an engineering choice. Preserve bounded velocity and noise suppression without permanently discarding deliberate small movements.

## 6. Edge scrolling and gestures

### 6.1 Direction and rate

On a usable browser surface, actual clamped displayed-pointer contact with the full-screen top boundary scrolls the active tab up; contact with the bottom scrolls it down. Interior positions and left/right-only contact do not generate page scrolling. At corners the vertical edge supplies the direction. A held pointer at the edge continues while samples remain fresh; additional outward rotation is unnecessary and does not accelerate scrolling.

No broad near-edge hot zone or absolute-pitch threshold substitutes for actual edge contact. Only declared pixel/subpixel rounding is allowed. Initial standard rate is constant **240 native viewport px/s**; the bounded Settings choices in §4.3 are subject to HUD-G1. Page endpoints cannot accumulate unused distance for later replay.

### 6.2 Cancellation

Use an ordinary active-View repeating-scroll callback: scroll the visible WebView only while the cursor is at the actual top/bottom screen edge and tracking remains fresh. Stop when the edge is left, tab changes, page is covered, Activity pauses or tracking expires. Integrate at most 50 ms of movement per step; do not build per-tick identity/generation checks, stale-event queues or catch-up backlogs.

Stop edge scrolling on inward departure, tab switch/close, navigation, covering utility, pause or loss of tracking. When active again, evaluate the actual current pointer and fresh samples normally; no special interior-to-edge rearming state or preserved edge episode is needed.

Menus, tab list, bookmarks, Settings and QR scanner do not scroll a covered webpage. A currently visible scrollable utility list may respond to the same top/bottom edge behavior; dismissal restores normal page input without an old-episode replay.

### 6.3 Keyboard coexistence

With the built-in keyboard open, the full-screen top/bottom boundaries still scroll the active webpage; the keyboard remains stationary. Its top divider is not a trigger. All keys have selectable interior positions. Page scroll alone must not change the selected editor or invalidate an otherwise unchanged native key solely because page pixels moved.

Required field reveal, page-authored scrolling and explicit webpage scroll controls are distinct from EyeBrowse head/pad scrolling. Reveal logic must not fight a deliberate edge-scroll episode or focus another field. Keep cause labels in evidence.

### 6.4 Gesture contract

A recognized short tap activates the current control or WebView once. A recognized double tap has no action and suppresses its constituent taps. Swipe right/left selects the next/previous tab, not a page scroll or history action. Do not double-dispatch via both recognizer and native controls; do not add target snapshots or identity tokens.

A swipe during keyboard entry changes tabs and dismisses the previous tab's keyboard without deliberately submitting. Swipes in non-browser utility/scanner states do not change an underlying tab. Tab-list selection is an explicit tap.

## 7. Built-in keyboard and ordinary WebView editing

Retain English QWERTY, Shift/case, numbers, common punctuation/symbols, Space, Backspace, Enter/Open and a distinct **Done**. The HUD contract names required keys; generated pictures cannot add or remove them. Actual built-in keys operate both address drafts and local webpage editors.

Full address editing uses a horizontally revealable draft inside the same compact toolbar slot; it does not widen the row. Invalid Open preserves the draft/current page. Done dismisses without navigating. Saving the current page via star never substitutes the unsent draft.

Supported webpage targets are text/password fields, textarea and basic plain contenteditable. Use native WebView focus/input behavior for selection replacement, caret movement, Backspace, Unicode text and field constraints. Multiline Enter inserts a newline; single-line/password Enter follows ordinary field/form behavior. Done dismisses without deliberate submission or undoing completed text.

Dispatch actual built-in keys directly to the current focused editor through Android/WebView input handling. Use ordinary View focus, keyboard visibility and tab switching. Do not implement original-element/selection binding tokens, keyboard-session generations, ABA validation, target registries or a browser-wide deferred-edit queue.

No separate application-level execution-boundary validation is required for ordinary local keypresses or taps. Rely on native View/WebView semantics and simple event ordering, not redundant reentrant callback checks, acknowledgement matching or uncertain-result reconciliation.

Keep the selected field/context visible during editing and preserve password masking; do not log passwords or text drafts. No custom late-key or session-fencing machinery is required.

Keyboard show/hide changes only the local content viewport; do not intentionally reload the tab or erase form values. Use current View bounds for pointer hit testing and current WebView focus; scrolling does not require custom pending-page-hit invalidation.

## 8. Lifecycle, persistence and Phone independence

Maintain ordinary local browser state: selected tab/WebView, navigation, focus, keyboard visibility, pointer/tracking, visible utilities and active scrolling. Do not add operation generations, per-input identities or a stale-input state machine.

| Event | Required behavior |
| --- | --- |
| Initial/empty launch | Usable local tab and address entry; automatic pointer acquisition; no Phone connection. |
| Normal tab switch | Preserve surviving live tabs; select visible WebView, dismiss previous keyboard as appropriate and stop active edge scrolling. |
| Keyboard/layout change | Preserve document and entered values; use current layout and focused field. |
| Pause, occlusion, screen-off or stale tracking | Stop controller motion; release/suspend appropriate work; preserve still-live browser state. |
| Resume | Resume pointer tracking and ordinary input; no synthetic tap or automatic keyboard opening. |
| UI recreation with surviving runtime | Reattach local browser Views/state where supported without intentionally replacing surviving tabs. |
| Actual tab renderer/process loss | Mark the affected context interrupted; do not claim JS/form memory survived; offer safe local recovery. |
| Network request failure | Show actual error; loaded local content and local controls remain usable as appropriate. |
| Phone stopped or absent | No effect on RG runtime when its own network route remains available. |

Persist local bookmarks, approved settings, safe last-location/tab metadata and normal engine cookies/site storage in app-private storage. Ordinary RG tabs may share local website login state; none imports Phone credentials automatically. Cold recovery may reconstruct recorded tab slots/locations with honest interruption feedback and explicit safe opening; it must not automatically replay an uncertain POST or form action. Unsaved forms/JavaScript memory are not custom-persisted.

Preserve data already durably established before abrupt loss; distinguish graceful persistence from unflushed renderer memory. Upgrade does not require clearing either product application's data. Old pairing/mode records are inert and cannot start remote work or restore a removed mode. Missing new preferences use documented defaults.

Stop sensor listeners, QR scanner, scroll callbacks and browser resources with ordinary View/Activity teardown. Hidden tabs do not receive controller events. No unused Phone-link/capture work should run for RG browsing. Trace consumers before deleting shared code. Phone retains touch/IME, navigation and storage.

## 9. Native black presentation and website compatibility

The RG presentation is black by default regardless of Phone or OS light theme. App window, toolbar, address field, menu, keyboard, utilities, empty/error/loading surfaces and non-media background fills are **opaque `#000000`**. Use light text/icons and outlined controls; no gray-filled cards, frosted panels, gradients, wallpaper or ornamental glow. Exact tokens are in the HUD document.

A black toolbar over a white webpage does not pass. The selected engine/theme path must provide light readable text on black primary document surfaces for the supported website/fixture set, including fields and dynamic content. Apply presentation styling without replacing the live page or breaking links, focus, forms and scripts. Native/author dark-theme hints and engine darkening are mechanisms to qualify, not proof that every background became black.

Photos, video/canvas content and camera previews are media, not background fill; they need not be flattened to black or globally inverted. Preserve useful media colors. Document custom-painted/cross-origin or incompatible content limitations separately. Do not silently report white primary page output as conforming, conceal a software failure under a generic compatibility label, or claim universal recoloring is proven. An unsatisfied required site/fixture result returns to Planner before weakening the contract.

The black launch/recovery surface must prevent app-origin white flashes during page load, tab switch, keyboard transitions, menus and engine failure. Test startup and transition frames, not only settled screenshots. No confidence claim about real optical comfort follows from RGB screenshots.

Android's documented darkening behavior depends on the app target/theme, WebView feature support and page styling; see the official references in the HUD contract. Engine limitations must be measured on the installed provider. Do not silently downgrade this output requirement to setting a dark-theme flag.

## 10. Privacy, networking and permission boundaries

Each device uses its own existing Internet and VPN route. A Phone hotspot is connectivity, not proof of Phone VPN routing or an EyeBrowse app dependency. EyeBrowse does not configure Wi-Fi/hotspots. Explicit HTTP is not presented as encrypted; certificate failures remain visible and are not bypassed for testing.

Webpages and scanned values are untrusted. Native/page integrations expose only scoped validated operations, not private credentials, unrestricted native bridges or arbitrary commands. No ordinary browsing, typing, scrolling or QR decoding depends on production ADB commands.

Use app-private storage for deliberately saved locations/bookmarks. Routine diagnostics record timing, classes and non-sensitive IDs, not passwords, typed payloads, QR tokens or arbitrary full sensitive URLs. Mission-scoped screenshots remain permitted under existing authorization and must be sanitized before public sharing. Use dummy credentials and fixture text.

Camera access is limited to explicit QR scanning, with permission, cancellation and release. Permission dialogs are system UI; do not imitate permission success or manufacture device access. Device/provider/security-setting mutations still need their applicable authority.

## 11. Quantitative contract

| Quantity | Bound/default |
| --- | --- |
| Canonical HUD canvas | 480 × 640, width × height, portrait. |
| Reference toolbar | One fixed 48-reference-pixel row, details in HUD H1.2; numeric layout awaits HUD-G1. |
| Standard edge-scroll rate | 240 native viewport px/s; new bounded Settings presets require HUD-G1. |
| Valid sample → visible cursor / edge response | At most 100 ms in declared device/replay conditions. |
| Inward return after saturation | At most 100 ms after valid inward input, independent of prior outward angle/duration. |
| Sensor-age cutoff | No scroll effect when last valid sample age is at or beyond 250 ms. |
| Scroll integration step | At most 50 ms elapsed motion; no catch-up backlog. |
| Confirmed tap/key → local dispatch | At most 100 ms; recognition, editor completion and visible results reported separately. |

Measure native scroll units and qualified monotonic clocks. Do not call a no-op successful input dispatch or hide timing violations. Observe normal tap/key dispatch and scroll start/stop without extra generations. Resource/engine/tab bounds remain documented; local rendering does not inherit the old streaming frame-rate cap.

## 12. Acceptance and mandatory HUD gate

### 12.1 HUD-G1 / HUD-G2

**HUD-G1 is OPEN.** Before production HUD work is accepted for implementation, the Owner must review the exact HUD contract revision and a complete native-size reference/prototype set or explicitly waive the image portion while approving the exact layout contract. The record identifies revision/hash, approved states and any deviations. None of the existing generated images is a complete approved reference: omissions, incorrect dimensions/orientation, duplicate pointer marks or missing keys are not authorized behavior.

Qualification and behavior-preserving work in #28/#29 may continue. Prototype/layout validation specifically needed for HUD-G1 may be assigned as design work; that is not permission to ship a provisional layout. Manager must ensure the design gate and expanded tab/bookmark/QR/settings/dark-output requirements are covered by authorized canonical issues and dependencies before dispatching affected implementation. Separate Planner-authored ticket plans are not required.

**HUD-G2** is exact-candidate implementation conformance. Automated structure/geometry/color/state checks and native-size screenshots must demonstrate the approved contract. Missing star, Forward, Refresh, Done, menu item, wrong order, a second toolbar row, wrong orientation or nonblack app background produces `CHANGES_REQUESTED`, not stylistic discretion. Passing functional navigation alone is insufficient.

### 12.2 Evidence boundaries

Use the real RG and currently authorized Phone for applicable regressions, via reserved mission-scoped ADB automation. Replay raw pose/pad events through production input paths. Positive keyboard/QR/navigation evidence uses actual implemented controls; direct DOM assignment or arbitrary state injection is not a replacement for those journeys.

Use independent owned-fixture observations for values, selection, submissions, tab identity, navigation and scrolling. Predeclare cases, timebases and finite per-command safety limits. Preserve exact source/APK/provider/device/route/input bindings, executed versus cached checks, failure history, screenshots and cleanup. Physical optical/wearer claims require their own observation; unattended screenshots do not establish them.

### 12.3 Acceptance groups

| ID | Required demonstration |
| --- | --- |
| V2-A01 | Exact-head builds, applicable tests/lint, real launches and engine/display/rotation measurements. |
| V2-A02 | RG local execution with Phone stopped; simultaneous independent browser use. |
| V2-A03 | Complete address policy, draft preservation, Back/Forward/Refresh, links and errors on the active tab. |
| V2-A04 | Always-visible pointer, automatic acquisition, all control targets reachable, no recenter/mode routes. |
| V2-A05 | Four-edge/corner overshoot regression with three amounts and multiple hold durations; no hidden angle/filter payback. |
| V2-A06 | Actual vertical edge-only movement, held-edge continuation, bounded configured speed, no lateral/pad vertical scroll. |
| V2-A07 | Edge scrolling starts at actual top/bottom pointer contact and stops on departure, hidden tab/page, unavailable tracking or pause using ordinary View lifecycle behavior. |
| V2-A08 | Recognized short tap activates exactly once; double tap suppresses constituent actions; no duplicated native/recognizer dispatch or custom target-identity tests. |
| V2-A09 | Real built-in keys across four editor classes; selection/case/symbols/Space/Backspace/Enter/Done. |
| V2-A10 | Keyboard viewport preservation, full-screen pointer/edge limits, visible current masked feedback. |
| V2-A11 | Built-in keys edit the currently focused WebView field correctly across text/password/textarea/plain editable, tab switches and keyboard dismissal; no ABA/stale-key test matrix. |
| V2-A12 | Restart/durable storage/network loss with honest recovery and no consequential replay. |
| V2-A13 | Data-preserving upgrade, inert obsolete records and intact Phone browser/data. |
| V2-A14 | Exact-head review, combined journey, limits/privacy and verified cleanup. |
| V2-A15 | HUD-G1 receipt and HUD-G2: all eight toolbar hit targets, one row, 480-wide × 640-high portrait geometry, black palette, keys/menu/state inventory. |
| V2-A16 | At least four real tabs: add/select/counter/swipe/close/boundaries/last-tab behavior, history and no background input. |
| V2-A17 | Outline/filled star on correct committed page, add/remove persistence, list/empty/error and reopen semantics. |
| V2-A18 | Real camera lifecycle, decoder, URL preview/explicit confirmation, invalid/permission/cancel handling; no separate stale-scan fencing requirement. |
| V2-A19 | Actual bounded settings, persistence/defaults and ordinary scroll start/stop without recentering. |
| V2-A20 | Black first-party surfaces and dark supported website/field/dynamic output, media preservation, no app-origin white transition flash. |

Final integration includes navigation, multiple tabs and both swipe directions, star save/remove/list, QR URL confirmation, settings, overshoot/reversal, edge scrolling, actual field entry, utilities, sleep/resume and safe cold recovery. Each feature must appear in a canonical plan/evidence map; no new rows are automatically passed by the old six-ticket inventory.

## 13. Migration and planning reconciliation

Issues #28–#33 remain the original canonical migration tickets. Their publication predates the added tabs/bookmarks/QR/settings and strict HUD directions. The issue index records the updated authority. It is not accurate to treat the old single-page text or unassigned swipes as current requirements.

The responsible Planner and Manager must reconcile affected canonical ticket scopes/dependencies before the relevant implementation starts. Workers execute directly from authorized issues, current SPEC/HUD requirements, and Manager mission assignments, without a separate ticket-plan gate. #28 engine/editor qualification and #29 refactoring remain independently useful. #30 and later UI work must use this SPEC and HUD H1.2, satisfy HUD-G1, and have mapped authorized scope for the newly required functions. This edit does not silently assign all new work to an already dispatched Worker or invent completed tickets.

Keep historical R3 and v0.0.1 evidence at its original reference. Previous remote-only target-binding/cancellation/ABA checks are superseded for local input by this Owner-directed direct-dispatch rule; functional pointer, scroll, gesture, keyboard and lifecycle outcomes remain mandatory. Trace shared consumers and do not carry remote-only ordinals/capture leases into local operations.

The designated editors handle protected articles. Implementation PRs target the migration branch with independent exact-head review. Publication here does not move `main`, reopen retired agents, dispatch new ones, or record a release.

## 14. References

- [Mandatory RG HUD H1.2](docs/design/v0.0.2-rg-hud.md).
- [Current issue index and reconciliation status](docs/plans/v0.0.2-issues.md).
- `AGENTS.md`, `DEV.md`, and `docs/decisions/0001-kotlin-only-first-party-code.md`.
- Historical R3: `docs/plans/v0.0.2-standalone-rg-r3.md`; its earlier tab/swipe/layout assumptions are not the current contract.

**Completion means independent RG browsing with the complete one-row black HUD, built-in input, local tabs and utilities, safe bounded edge scrolling, and verified conformance—not merely a similar-looking screenshot.**
