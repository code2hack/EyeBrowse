# EyeBrowse SPEC v0.0.1

**Status:** Owner-approved unreleased development specification — unattended device-automation acceptance  
**Target product version:** v0.0.1  
**Project Owner:** code2hack  
**Editor:** Planner: SPEC Design  
**Product decision baseline:** 2026-09-10  
**Acceptance baseline:** Owner-approved unattended automation, with real Phone and RG retained; see §15

## 0. Authority and interpretation

This document defines the current product and engineering contract for EyeBrowse v0.0.1. It replaces earlier specifications in full rather than retaining contradictory requirements beneath overrides.

The current Project Owner direction governs this specification under `AGENTS.md`. The document consolidates the approved browser-first scope, Phone/RG ownership, pairing boundary, the Owner's answers to refinement questions Q1–Q8, and the subsequent approval of unattended ADB-driven verification. The later correction retains the real Fold6; emulator-only/no-real-Phone directions are superseded.

This is the current v0.0.1 contract. Requirements from earlier drafts are not inherited unless restated here. Earlier design artifacts remain visual references only where consistent with this document. Historical specifications belong in Git history, not in an obsolete requirements appendix.

**MUST** and **MUST NOT** identify required behavior. **SHOULD** identifies a recommendation whose deviation needs a documented reason. **MAY** identifies an optional implementation choice, not additional required scope.

Specification approval, implementation completion, device validation, and release publication are separate events. The version number does not claim that v0.0.1 has been released or has passed acceptance.

## 1. Product definition

> EyeBrowse v0.0.1 is a minimal, single-tab, Phone-hosted browser that the user can operate either on Phone or through a Rokid Glasses HUD, including while an explicitly started Phone hosting session continues with Phone securely locked and its physical screen off.

There is one authoritative browser session and one active page. Phone and RG are two interfaces to that page, not independent browsers with synchronized URLs.

Phone MUST work as an ordinary touch-operated browser without RG. RG MUST provide usable browsing and text entry through the connected Phone host; it is not merely a passive display. RG does not provide standalone browsing when Phone is unavailable.

**Browser is the only permanent primary product surface in v0.0.1.** Pairing, scanning, connection status, hosting status, and handoff are utility views or controls. There is no Agent surface.

On RG, Browser has two presentation modes: **Normal** for interaction and **Reading** for consumption.

## 2. Scope and exclusions

| In v0.0.1 | Outside v0.0.1 |
| --- | --- |
| Phone-owned System WebView, one live page, one tab | Multiple tabs and independent simultaneous Phone/RG browsing |
| Manual address entry, navigation, scrolling, and ordinary field editing | Address-bar search, agent navigation, and browser automation tools |
| Responsive Phone interface and RG HUD | Agent runtime, LLM integration, chat, and placeholder Agent controls |
| RG head pointer, touchpad gestures, Reading head-scroll, and in-app keyboard | EyeBrowse ASR, microphone workflows, Chinese RG input, and system-wide IME/mouse |
| Local LAN/hotspot communication and Phone-displayed QR pairing | CXR/CXR-L/CXR-S, cloud relays, Internet rendezvous, and built-in Tailscale |
| Explicit hosting, handoff, remembered pairing, and recoverable connection loss | Automatic hosting after reboot or remote startup of an inactive Phone host |
| Basic app-private settings and browser persistence | Cloud sync, password manager, bookmarks, history-management UI, extensions, and elaborate settings |

Dedicated download/upload workflows, PDF/document viewers, guaranteed audio/video playback, DRM, and comprehensive compatibility with arbitrary rich-text editors are not acceptance requirements. A video codec used for HUD presentation does not imply website video-playback support.

Built-in Mihomo is removed from the required product architecture. EyeBrowse does not establish or manage a VPN. Built-in Tailscale remains a future product requirement but MUST NOT become a dependency or acceptance gate for v0.0.1. No unimplemented feature needs a placeholder module, menu, or settings screen.

**Wi-Fi and hotspot setup are permanently outside this project's scope.** EyeBrowse MUST NOT enable or configure a hotspot, provision Wi-Fi credentials through pairing, or join/switch Wi-Fi networks. This is not a deferred onboarding feature.

## 3. Targets and canonical terms

The primary Phone target is Samsung Galaxy Z Fold6, including its cover and inner displays. Phone layouts MUST respond to actual window dimensions, density, insets, orientation, and folding changes rather than fixed study screenshots.

The RG app MUST have an API-32-compatible runtime path. Approximately **480 × 640 physical pixels** is the RG display design target, not a guarantee of the app's available content viewport or its logical density. Insets, density, camera access, and sensor integration require target-specific checks. The unattended development evidence requirements and limits on optical/wearer claims are defined in §15.

| Term | Meaning |
| --- | --- |
| **Phone host** | The Phone-side runtime that owns and executes the browser session. |
| **Browser session** | The live single-tab page, navigation state, and associated browser state owned by Phone. |
| **Hosting session** | An explicitly started period during which Phone permits the paired RG to connect and use that browser. |
| **Pairing** | Remembered authorization between one Phone and one RG; not a Wi-Fi association. |
| **Connection** | A currently live, authenticated app-to-app link; losing it does not itself erase pairing. |
| **Control owner** | The device currently permitted to send ordinary browser input and determine the page's presentation viewport. |
| **Handoff** | An explicit transition of control ownership and presentation between Phone and RG. |
| **Content viewport** | The browser content area available on the controlling device, excluding that device's visible local controls and keyboard. |

Pairing, connection, hosting, control ownership, RG presentation mode, and text-entry state MUST be modeled separately. Being paired or connected MUST NOT, by itself, grant current browser control.

## 4. Runtime and component boundaries

EyeBrowse MUST remain one repository producing two Android APKs: Phone and RG. Shared contracts MUST NOT become divergent product forks.

| Boundary | Responsibility |
| --- | --- |
| **Phone browser host** | System WebView execution, navigation, live page and focus state, cookies/site storage, website requests, viewport management, and application of authorized input. |
| **Phone interface** | Touch browsing, Android IME, pairing-code display, hosting controls, and explicit handoff. |
| **RG interface** | HUD presentation, locally responsive controls and pointer, gesture interpretation, Reading behavior, in-app keyboard, QR scanning, and connection feedback. |
| **Local connection layer** | Pairing authentication, protected communication, browser presentation/state delivery, input delivery, and connection recovery. |
| **Shared contracts** | Browser actions/status, identity and session boundaries, handoff, viewport/focus identity, failures, and persisted non-page settings. |

The webpage MUST execute on Phone using Android System WebView. RG MUST NOT open a separate copy of the website to simulate continuity. Website cookies, private storage, and execution state remain on Phone.

RG presents the Phone-hosted page rather than mirroring the physical Phone screen. Phone-side offscreen hosting MUST allow RG use while Phone is locked. The spike's private-display approach is available implementation evidence, not a requirement to copy its debug harness unchanged.

RG controls, pointer feedback, keyboard, and connection UI MUST remain locally responsive rather than depend on a returned frame for every local interaction. The precise page-presentation format, encoding, and transport library are engineering decisions within this ownership contract.

Device-specific physical input MUST be translated into browser actions at device boundaries. A minimal action contract for human input is required; an agent semantic-observation runtime, arbitrary remote script executor, or CDP production control path is not.

## 5. Single-tab browser behavior

### 5.1 Address entry and navigation

Both controlling interfaces MUST support entering a webpage address, opening it, showing the current location, going Back and Forward when available, and Reloading. They MUST support ordinary link/button activation and vertical scrolling.

The location field is a primary manual navigation path, not an agent fallback. It MUST support HTTPS addresses and explicit HTTP addresses, including local host addresses. Ordinary domain names without a scheme SHOULD be normalized to HTTPS. Invalid or unsupported input MUST leave the current page intact, preserve the entered text for correction, and show a compact error.

Free-text search phrases MUST NOT be silently sent to a search provider. Searching inside a webpage remains ordinary browsing. The exact accepted address grammar and normalization cases MUST be documented and tested; arbitrary script/file/native-intent execution is not part of address entry.

Current URL, title where displayed, loading state, navigation availability, and errors MUST reflect the authoritative Phone browser. A failed navigation MUST offer a clear way to retry or enter another address.

### 5.2 One tab and webpage-created windows

Exactly one active tab is required. There MUST NOT be a new-tab button, tab counter, tab switcher, or hidden collection of independent browsing sessions.

Ordinary user-activated links requesting a new tab/window MUST be handled in the existing tab. Unsolicited pop-up windows MUST NOT create additional browsing contexts. Flows requiring multiple windows may be reported as unsupported; they MUST NOT silently introduce another tab model.

First use without a recoverable URL MAY present an empty browser with accessible address entry. A home feed or branded start-page product is not required.

### 5.3 Supported field interaction

Both controlling interfaces MUST support entering, correcting, and submitting text in standard single-line text inputs, password inputs, multiline text areas, and basic plain editable webpage regions. Ordinary form controls needed by the validated browsing journeys MUST remain operable.

Universal support for complex rich-text editors, file pickers, special authentication flows, and every custom webpage widget is not claimed. Unsupported behavior MUST be visible rather than represented as a successful action.

## 6. Phone interface

While Phone owns control, EyeBrowse MUST provide direct touch interaction, ordinary scrolling, the normal Android IME, and accessible browser navigation controls.

Android Back MUST first dismiss an active local keyboard or utility view when applicable, then traverse browser history when available. At the root, it follows normal application back behavior; leaving the activity MUST NOT be confused with the explicit Stop hosting action.

Fold/unfold, resizing, and ordinary activity recreation MUST NOT intentionally create an unrelated browser session or force RG handoff. A surviving Phone host remains the session owner across interface recreation.

While RG owns control, the Phone EyeBrowse interface MUST show compact hosting/connection status and a **Use on phone** action, not a second independently interactive page or a required live preview. It MUST also provide access to Stop hosting and pairing management.

Unlocking Phone or bringing EyeBrowse to the foreground MUST NOT automatically take control away from RG.

## 7. RG interface and interaction

### 7.1 Normal Mode

Normal Mode shows browser controls, webpage content, and a visible head pointer. Head yaw controls horizontal pointer movement and head pitch controls vertical movement. Pointer motion MUST be usable for both local controls and the remotely hosted page.

A short tap activates the current target. The pointer MUST use suitable smoothing, a noise dead zone, and a usable recenter/reset mechanism. No dwell activation or new hardware gesture is implied. Exact tuning and the recenter affordance MUST be documented with the evidence actually obtained. Automated controller and on-device sensor checks are required under §15; they do not establish wearer calibration or comfort.

Forward touchpad swipes scroll down; backward swipes scroll up. The target is the current scrollable browser region, not whichever field or page happened to be active before a handoff.

### 7.2 Reading Mode

Reading Mode shows the same webpage with EyeBrowse's browser controls, pointer, and keyboard hidden. It uses the available content viewport. It MUST NOT extract, summarize, invert, or otherwise rewrite webpage content. Ordinary layout changes caused by viewport resizing are permitted; a separate reader document is not.

On entry, EyeBrowse MUST dismiss the RG keyboard, end active text entry without deliberate submission, preserve entered text where the page permits, and initialize the head-scroll neutral reference. Returning to Normal MUST NOT automatically reopen the keyboard.

Relative upward head pitch scrolls upward; downward pitch scrolls downward. A neutral dead zone stops scrolling. Larger displacement SHOULD produce greater speed within a bounded maximum. Neutral acquisition, drift control, and the response curve remain tunable values. Automated tests MUST exercise their behavior; final wearer tuning is not claimed by the unattended profile.

An isolated short tap in Reading Mode does nothing. Double tap returns to Normal.

### 7.3 Gesture and scroll arbitration

| Input | Normal | Reading |
| --- | --- | --- |
| Short tap | Activate pointer target | No action |
| Double tap | Enter Reading | Return to Normal |
| Forward swipe | Scroll down | Scroll down |
| Backward swipe | Scroll up | Scroll up |
| Head movement | Move pointer | Control continuous vertical scrolling |

Double-tap recognition MUST NOT dispatch either constituent tap as an unintended activation. Its recognition window MUST be bounded and tunable.

A swipe in Reading Mode MUST suspend continuous head-scroll, perform the discrete scroll, and keep head-scroll suspended until the head returns to neutral. A held tilt MUST NOT immediately fight the swipe.

Head-scroll MUST stop on loss of usable sensor data, connection, browser control, or active RG presentation. The Phone host MUST expire continuous remote input after a bounded loss of liveness; safety MUST NOT depend on receiving a final stop packet from a disconnected RG.

After a handoff or connection recovery, continuous scrolling MUST remain stopped until fresh state and a neutral reference are established. Sensor failure MUST be reported recoverably and MUST leave a safe exit or Phone takeover path rather than uncontrollable motion.

### 7.4 RG keyboard

The in-app keyboard MUST allow URL entry and supported webpage field editing without unlocking Phone during an established hosting session. Phone-assisted typing MUST NOT be the only input path.

The initial keyboard uses English QWERTY with letters, Shift/case control, numbers, common punctuation/symbols, Space, Backspace, and appropriate Enter/Done actions. Chinese input, prediction, swipe typing, and a system-wide Android IME are outside v0.0.1.

Head pointer plus short tap operates keys. The focused field or useful field context MUST remain visible above the keyboard. Keyboard sizing must preserve usable text and targets on real RG hardware; study dimensions are not fixed requirements. On-device layout and automated key-selection checks support development acceptance under §15 without claiming optical comfort.

Done dismisses text entry without silently submitting an unrelated form. Enter follows the focused field's semantics, including newline for a multiline target or the relevant submit action. Editing MUST affect the current intended target, not a stale focus left by navigation or handoff.

Password fields MUST remain masked in EyeBrowse-controlled displays. Keyboard and transport diagnostics MUST NOT log passwords or typed text. Navigating, losing control, or invalidating the target MUST stop input delivery to that target.

## 8. Hosting lifecycle

### 8.1 Explicit start

The user MUST be able to start a hosting session explicitly from Phone while unlocked. Starting hosting makes the current browser available to the paired RG; it MUST NOT discard the current page or automatically grant RG control.

The active hosting session MUST have visible Phone status and an explicit Stop action. Implementation MUST use an appropriate ordinary Android lifecycle path for the supported device, without root, privileged signatures, device-owner privileges, a disabled secure lock, or an unlocked-black-screen workaround. MediaProjection-based physical-screen capture is not the hosting solution.

After successful startup, RG browsing MUST continue with Phone securely locked and its physical display off, while the supported local connection remains available. Merely locking Phone does not transfer control; an explicit RG handoff action remains necessary when Phone owns the page.

### 8.2 Stop and restart

Stop hosting MUST revoke live RG control, terminate the remote connection, and release hosting-specific resources such as capture/encoding, private displays, service work, and wake locks. Phone-only browsing MAY continue without retaining those remote-hosting resources. Pairing and normal persisted browser data are not erased by stopping.

A disconnected RG MUST receive or eventually detect the inactive/unavailable host state. It MUST NOT continue to present an old page as live.

Automatic hosting after reboot and remote startup of an inactive Phone host are not required. After a stop or process loss, the supported recovery is for the user to restart hosting on Phone. Resources retained during a temporary reconnect window MUST be bounded and documented; the app MUST NOT perform indefinite high-rate capture for an absent client.

## 9. Local networking and QR pairing

### 9.1 Existing-network prerequisite

Phone–RG communication MUST use ordinary local networking. The primary supported topology is RG connected to Phone's hotspot; a shared reachable Wi-Fi LAN is also supported. Website Internet access is separate from this local connection.

The apps MUST NOT depend on CXR, cloud relay/rendezvous, a tailnet, or an EyeBrowse-managed VPN. Website requests use Phone's externally managed networking and applicable VPN policy.

QR scanning MUST NOT be gated by matching SSIDs, matching subnets, or a same-network precheck. Scanning obtains pairing information; EyeBrowse then attempts to reach the identified Phone host.

When Phone cannot be reached, a sufficient prompt is: **“Cannot reach Phone. Connect both devices to the same local network and retry.”** The app MAY mention Phone's existing hotspot. It MUST NOT automatically change network settings or claim a particular failure cause without evidence. Reachability, expired invitation, and authentication failure MUST remain distinguishable.

### 9.2 First pairing

Phone MUST provide a **Pair glasses** action displaying a QR code. RG MUST scan and decode that code using its own camera, without CXR or a separate desktop/ADB decoding step in the normal journey.

The code MUST supply enough information to locate the host and securely establish the intended app pairing without manual IP/port entry. Pairing MUST authenticate the intended host and establish protected communication before browser content or input is exchanged.

Invitations MUST have bounded validity and be cancellable; successful consumption or cancellation MUST prevent reuse as a standing browser-control credential. Exact payload, invitation lifetime, credential format, and cryptographic implementation are engineering decisions. Long-lived trust MUST NOT be an unauthenticated IP address or a reusable screenshot of an invitation.

Successful pairing establishes authorization and connection, not implicit handoff. RG uses its explicit **Use on glasses** action to take control.

Camera permission denial, scanning failure, or an invalid/expired code MUST produce a recoverable status. The camera MUST be released on completion, cancellation, or leaving the scanner. QR pairing does not authorize continuous capture or webpage camera access.

### 9.3 Remembered pairing and reconnect

v0.0.1 MUST remember one Phone–RG pairing across ordinary disconnects and app restarts. Reopening RG may reconnect to the remembered Phone when that Phone host is active and reachable; ordinary reconnect MUST NOT require a new scan each time.

A changed network address is a locator change, not proof of changed identity. Reconnection MUST authenticate the remembered peer. A different device occupying an old address MUST NOT become trusted.

Automatic address rediscovery is not a requirement. A fresh Phone QR scan MAY recover when the saved locator no longer works, provided pairing/authentication and explicit peer-replacement rules are preserved. Re-scan does not configure the network.

Each device MUST offer a small Forget pairing action. Local forgetting removes its stored authorization, closes its live connection, and prevents reuse of the forgotten trust. It does not promise to erase secrets remotely from an offline peer. Replacing the one paired peer requires an explicit user action rather than silent replacement.

An unauthenticated local client MUST NOT view the page, submit browser input, or acquire control merely because it can reach the port.

## 10. Control handoff and viewport ownership

### 10.1 Single controller

Phone remains the execution host at all times. Only one device owns ordinary browser input and the presentation viewport at a time.

| Control owner | Phone interface | RG interface |
| --- | --- | --- |
| Phone | Interactive touch browser | Compact “Browsing on phone” status and explicit Use on glasses action |
| RG | Hosting status and explicit Use on phone action | Interactive RG browser in Normal or Reading Mode |

Handoff controls remain usable even on the inactive interface; they are not ordinary webpage input. A Phone takeover MUST remain possible after RG disconnects. Taking RG control requires an authenticated live connection and an active host.

Unlocking Phone, launching an interface, pairing, or restoring a connection MUST NOT implicitly change the current control owner. Reconnection can resume existing RG ownership only if Phone has not since taken control and the session is still valid.

### 10.2 Transition behavior

A handoff MUST suspend outgoing browser input and continuous scroll, end active text entry without intentional submission, preserve the live Phone-owned page, update the receiving content viewport, and enable the receiving input only against fresh presentation/state.

The Phone host MUST serialize competing handoff requests so both devices cannot simultaneously believe their ordinary input is authorized. Delayed input from the former owner MUST NOT execute after the transition.

EyeBrowse MUST NOT implement handoff by opening the same URL in a different browser or intentionally reloading the existing page. Navigation history, live form values, and page execution state MUST not be deliberately discarded. Exact pixel-identical scroll positions across different viewport sizes are not required; preserve logical reading position where practical.

### 10.3 Viewport changes

The controlling device determines the actual content viewport. On Phone it follows the current window and IME/insets. On RG it follows the available browser area in Normal, Reading, or keyboard presentation; the full 480 × 640 target MUST NOT be mistaken for the content size in every state.

Viewport or focus changes MUST invalidate incompatible in-flight coordinates and edit targets. Input MUST use the current document, control, and viewport context. RG MUST not activate a target using a frame from an earlier handoff or an incompatible viewport.

## 11. Connection, input, and presentation contract

The local protocol MUST provide version/capability compatibility checks, authenticated session identity, current ownership/status, ordered browser input, and enough document/viewport/focus identity to reject known-stale operations.

Browser presentation MUST originate from the Phone-owned page and carry sufficient freshness information to distinguish current output from buffered old output. Queues, message sizes, retries, and liveness timers MUST be bounded. Recovery MUST converge to current state rather than replay a backlog of obsolete frames or actions.

Inputs with potentially duplicating effects—clicks, text edits, navigation, and form submissions—MUST NOT be blindly resent after uncertain delivery. Identifiable duplicates MUST be rejected or safely resolved within the active session. An acknowledgement of delivery MUST NOT be represented as proof of a website-side transaction's success.

The connection MUST expose compact recoverable states such as connecting, connected, reconnecting, host unavailable, authentication failure, and incompatible protocol. A stale last frame may remain only with an unmistakable stale/disconnected indication and disabled webpage input. Local recovery controls MUST remain usable.

A disconnected client MUST NOT resume typing into a changed field, resume held head-scroll, or retake control without reconciling the host's current session and ownership. Continuous input needs fresh liveness and neutral acquisition even when the last page frame appears unchanged.

These are behavior requirements, not a mandated wire schema or transport library. The implementation MUST document its concrete protocol before integrated acceptance.

## 12. Persistence and recovery

| Event | Required behavior |
| --- | --- |
| Phone/RG handoff or RG Normal/Reading transition | Keep the same live browser session; no intentional reload or loss of entered text. |
| Temporary RG connection loss while Phone browser lives | Preserve page and pairing, stop remote input, show connection loss, and reconcile on reconnect. |
| Phone interface recreation while host lives | Reattach to the existing session rather than create another one. |
| RG app restart while Phone host lives | Use remembered pairing and reconcile the existing session and control owner. |
| Browser/host process loss | Report interruption; do not claim that the former live page survived. |
| Explicit Stop hosting | End remote use and release hosting resources without erasing pairing or normal browser persistence. |
| Forget pairing | Revoke local remembered authorization and end its active connection. |

App-private persistence MUST cover remembered pairing, relevant non-secret settings, and the last visited URL. The Phone browser MUST use normal app-private cookies and site-storage persistence, subject to site expiry and user/OS clearing.

After actual browser-process loss, recovery MAY reload the last URL with clear recovery feedback. Exact restoration of JavaScript memory, unsaved forms, or the prior in-memory history stack is not guaranteed. Reloading MUST NOT automatically replay an uncertain POST or other consequential action from the lost session.

Passwords, transient keystrokes, and arbitrary webpage form drafts MUST NOT be added to a custom persistent recovery log. Ordinary website-managed storage is distinct from EyeBrowse recording keyboard input.

A new browser-host lifetime MUST be distinguishable from the lost one so old commands, focus tokens, and presentation references cannot be reused against it.

## 13. Security and privacy boundaries

Phone–RG browser presentation, typed input, and session messages MUST use authenticated, encrypted communication through maintained platform/library primitives. No plaintext “trusted LAN” exception or custom cryptographic algorithm is permitted.

Secrets MUST NOT be committed to the repository or included in routine logs. Stored pairing credentials MUST use app-private secure storage appropriate to their type. Normal diagnostics SHOULD record timings, state transitions, and non-sensitive identifiers, not page images, full URLs with sensitive parameters, or field contents. Explicit debug evidence capture must be deliberate and kept separate from normal operation. The Owner authorizes every project agent to capture, transfer, inspect, and retain screenshots from both real devices when useful for mission-scoped testing, debugging, or review, without per-capture approval. Use authorized, explicitly targeted ADB; prefer EyeBrowse/test-fixture screens, redact incidental private content before public attachments, and do not bypass OS capture restrictions.

Arbitrary webpage code is untrusted. Web content MUST NOT obtain pairing credentials, hosting controls, unrestricted native bridges, or app-level device capabilities. Native/page messages, where needed for input integration, MUST be scoped and validated.

EyeBrowse MUST NOT silently bypass certificate errors or weaken Phone's VPN policy to make browsing appear successful. Local Phone–RG traffic and website traffic MUST be handled without globally bypassing the VPN for all EyeBrowse traffic.

Required hosting and RG interaction MUST NOT depend on root, system signing, device-owner privileges, a system-wide accessibility mouse, or disabling secure lock. Debug-only ADB controls from the spike MUST NOT become a production remote-control interface.

## 14. Visual and usability contract

Retain the applicable **Soft Dock** direction: restrained rounded browser controls, large enough RG targets, and sparse persistent chrome. Phone and RG should share a visual identity without identical physical layouts.

There MUST NOT be disabled Agent buttons, tab controls, microphone controls, or menus reserved for absent features. A small utility menu MAY contain actual navigation, pairing, hosting, handoff, and recovery actions; exact placement is a design implementation decision.

RG Reading Mode has no persistent app controls during healthy browsing. A genuine error or disconnection MAY interrupt the clean presentation with necessary recovery UI; hiding an important failure is not part of Reading Mode.

Text must remain legible after presentation transport. Pointer motion, key feedback, and local controls MUST not feel blocked by a remote round trip. Input-to-visible-page latency, frame freshness, reconnect delay, and active/idle resource use MUST be measured on the available intended devices under declared conditions. Screenshots and layout checks assess rendered output; optical readability and wearer comfort remain explicitly unqualified when no physical observation is available.

This specification does not invent fixed frame rates, bitrate, latency, battery-life, or ergonomic thresholds. The implementation plan MUST set measurable automated targets and test conditions before integrated acceptance. Synthetic inputs, screenshots, charging-state simulation, or emulator results cannot establish wearer comfort or unplugged physical power behavior.

## 15. Acceptance and evidence

### 15.1 Approved unattended development profile

The current v0.0.1 implementation run uses **unattended device-automation acceptance**. The real Fold6 and real RG remain authorized, primary test/debug targets, kept connected and operated through ADB. An explicitly selected emulator may supplement repeatable or otherwise unavailable conditions; it does not replace real-Phone testing by default. Host-side builds, fixture services, analysis, and Git operations do not need to run through ADB.

All agents may automate mission-scoped installation, launch, navigation, typing, test execution, screenshots, and diagnostics on these targets through existing authorized ADB connections. Manager coordinates explicit target identity, exclusive mutable-resource ownership, and recovery. Routine actions and screenshots do not require the Owner to act or approve each occurrence. The run's operating record is `docs/plans/v0.0.1-unattended-run.md`; governance and protected-article gates remain in `AGENTS.md` and `DEV.md`.

Acceptance MUST identify the exact candidate/build, actual targets/software, input source, and connection topology. Drive ordinary UI and application behavior through ADB-launched UI automation/instrumentation. Browser operations MUST still use the implemented browser, keyboard/controller paths, and authenticated app protocol; setting a field through JavaScript, directly setting paired state, or substituting the spike's ADB command receiver is not equivalent evidence.

Required automated functional, security, lifecycle, and target-integration checks MUST pass, with independent review and verified cleanup. Missing physical-only observations—wearer/optical judgments, physical hinge movements, optical QR alignment, unplugging, or a setup requiring unavailable private authentication—are **not merge, ticket-closure, or downstream-readiness prerequisites for this run**. Record each as `NOT EXERCISED — unattended profile`, with its exact coverage limit, rather than PASS. Do not move those same waits unchanged into the final integration ticket.

This is an evidence-profile change, not removal of production QR, sensor, keyboard, Phone-hosting, LAN, or security requirements. A failing executable software assertion, missing required implementation, or unavailable essential device/app integration is not excused merely by labeling it a physical limitation. Fix such defects or record a genuine blocker. A successful development-profile closeout is not full physical qualification or release authorization.

### 15.2 Required unattended acceptance matrix

| ID | Required demonstration and evidence boundary |
| --- | --- |
| **A01 — Build and targets** | Build both debug APKs and launch on real Fold6 and API-32 RG through ADB. Check the available real Phone display/window and supplement narrow/wide resize/recreation cases on a reserved emulator as needed. Record actual insets/density/layout screenshots; synthetic sizing does not prove physical folding or an inaccessible display. |
| **A02 — Phone-only browsing** | Automate Phone address normalization, Back/Forward/Reload, links, scrolling, supported input correction, harmless submission, and navigation-error recovery. Include actual native controls and supported IME/field integration; distinguish key-event injection from software-IME key selection. No search provider or extra tabs. |
| **A03 — QR and protected pairing** | Test Phone-generated QR output, the same RG decoder and genuine authenticated pairing with valid/invalid/expired/cancelled invitations and unauthorized peers. Where stationary camera alignment prevents an optical scan, feed the image through an instrumentation-only decoder input. Separately exercise actual RG camera open/capture, denial/cancel and release where ADB permits. Record optical scanning as unexercised, not simulated success. |
| **A04 — Network boundary** | Test scanning without SSID/subnet prechecks, unreachable-host feedback, and successful retry after controlled link restoration. EyeBrowse never joins/configures Wi-Fi or hotspot. Fault injection or test infrastructure must not become network-onboarding product code. |
| **A05 — Local networking** | Exercise both real applications and protected browser traffic on the existing reachable local network. Test already configured hotspot/VPN coexistence when it can be exercised safely through ADB without a physical setup gate. Where test routing/forwarding is necessary, preserve end-to-end app authentication/encryption and label that route; it is not evidence of direct hotspot/VPN routing or wireless performance. Unavailable physical topology qualification is non-blocking and explicitly unexercised. |
| **A06 — Hosting and screen state** | Start/stop hosting using real Phone UI automation; verify same-page offscreen output, navigation/input while backgrounded or display-off, and cleanup. Exercise secure lock only when the authorized setup remains recoverable without unavailable credentials; measure actual lock/interactive state. Screen-off is not proof of secure lock. Leave devices connected; physical unplugged/power and unavailable secure-lock conditions remain unqualified rather than blockers. |
| **A07 — Handoff and viewport** | Automate Phone → RG → Phone using actual apps and protocol. Preserve a deterministic page's state/history, check inactive status and fresh geometry, and reject stale/old-owner input. Unlock/foreground/reconnect must not implicitly change ownership. Use available real windows plus labeled resize tests without requiring physical hinge movement. |
| **A08 — RG Normal** | Test real RG rendering and sensor registration/event acquisition. Replay timestamped yaw/pitch and tap/swipe sequences into the same controller to verify pointer movement, local/page target activation, directions, recentering, and stale-input rejection. Record real versus replayed sources; stationary acquisition and screenshots do not qualify dynamic physical touchpad/head calibration or comfort. |
| **A09 — RG Reading** | Automate real RG Normal/Reading/keyboard transitions; replay tilt/neutral/swipe through the controller. Verify no double-tap leakage, isolated-tap no-op, hidden chrome/pointer/keyboard, correct up/down/neutral behavior, swipe suspension until neutral, and stop on lost liveness even without a final stop packet. No wearer sign-off is required for development acceptance. |
| **A10 — RG keyboard** | Operate actual RG keys through UI/controller automation and verify remote URL/text/password/multiline/basic editable input, correction, case/symbols, Enter/Done, masking, and stale-focus rejection. Verify actual key activation, not direct DOM assignment. Test against the real Phone host and supported background/screen states; declare untested secure-lock/optical conditions. |
| **A11 — Connection recovery** | Interrupt/restore the app link, delay/duplicate/drop messages, change a test locator, and refuse a different peer at the former locator. Preserve the live page/pairing, stop continuous input, mark stale output, reconcile ownership/focus, and prevent blind uncertain-input replay. Preserve ADB as the recovery channel. |
| **A12 — Restart, forgetting, and cleanup** | Automate Activity recreation, RG app restart, and scoped Phone/browser process loss separately. Verify settings/pairing/URL/site storage, honest cold recovery with no automatic consequential replay, Forget/re-pair, and Stop cleanup. Use app-scoped operations rather than factory reset, device reboot, or ADB revocation. |
| **A13 — Measured behavior and scope** | Record latency/freshness/reconnect/resource measurements against declared automated targets, with device/input/route labels and screenshots. Confirm no agent, ASR, built-in VPN/Mihomo, tailnet, CXR, multi-tab, or network-onboarding dependency. Include a non-blocking ledger of unexercised physical qualification; do not require an Owner optical/ergonomic sign-off to close this run. |

### 15.3 Test boundaries and completion

Prefer stable externally observable interfaces and end-to-end application journeys. Deterministic pages should expose counters, navigation, scrolling, focus, field values, and harmless submission observations. Live-page smoke tests remain separate from repeatable regressions. Preserve existing valid evidence with its original candidate, target, and human/automated provenance; do not repeat an already recorded Owner observation merely to fill a new form.

Test-only camera/sensor inputs and fault injection MUST be confined to instrumentation or controlled test-only seams. They MUST exercise the real downstream implementation and must not create a production authentication bypass, exported arbitrary-command interface, or user-facing ADB dependency. Source/manifest review and automated checks establish that boundary; no release build is introduced merely to check it.

Target screenshot capture and inspection are permitted under §13. Screenshots prove only visible rendering/layout; correlate them with state/event/fixture observations for interaction claims. Record actual durations and interruptions; separate physical plugged-in operation from simulated battery state, actual lock from display-off, real network routing from test forwarding, and physical sensor events from replay.

After the revised required checks pass and independent review/cleanup complete, implementation tickets may close and unblock dependents. The final integration ticket closes this same development profile, with a complete A01–A13 evidence ledger. Unavailable authorization, essential device connectivity, quota, privilege, or exhausted recovery remains a genuine blocker handled under the current run instructions; it is not a reason to fabricate evidence or bypass security.

### 15.4 Existing spike evidence

Issue #1 was accepted under revised Owner acceptance and merged through PR #3. That record includes accepted historical Fold6 locked-command/unplugged evidence and supplemental current-head observations; it does not demonstrate final-head Fold6 post-lock command execution or a complete Phone–RG product path.

The accepted evidence is sufficient to proceed with this architecture. This specification does not reopen the cancelled repeat spike test. New automated integration evidence must exercise the implemented product, while preserving historical coverage limits. New architecture approval comes from the Owner's subsequent decisions, not retroactively from the spike's scope.

## 16. Engineering decisions and implementation readiness

The following remain engineering work, not unresolved product features: Android module organization; offscreen-host integration and foreground-service details; transport/encoding; authenticated pairing protocol and key storage; concrete message schema and freshness checks; ordinary field-input integration; camera/QR library; sensor filters; and documented timing/resource budgets.

Implementers MAY propose the simplest supported choices within this contract. They MUST test device-dependent assumptions under §15, document unqualified physical conditions, and record concrete contracts before claiming development-profile acceptance. A limitation requiring scope or user-visible behavior to change is escalated to Planner/Owner rather than silently replaced with CXR, an RG browser engine, plaintext transport, remote-start machinery, or another unapproved workaround.

No ADR file, glossary framework, future subsystem scaffold, or particular third-party package is a prerequisite merely because a planning skill normally creates one. Meaningful architectural rationale can be recorded separately without duplicating or contradicting the current SPEC.

This document specifies the product; it does not dispatch work, create tickets, or claim a passing implementation. Execution, independent review, protected-document changes, and milestone approval follow the existing `AGENTS.md` and `DEV.md` procedures.

## 17. References

The current Owner instructions and approved Q1–Q8 answers are the product-decision basis. Subsequent Owner approval authorizes unattended automation while retaining the real Phone and RG and allowing all agents to inspect screenshots; the current run record consolidates those instructions. Existing repository materials provide governance, visual evidence, and bounded experiment evidence:

- Governance: `AGENTS.md`; verified development procedures: `DEV.md`.
- Visual evidence: `design/soft-dock/design-decisions.md` and `design/soft-dock/design-qa.md`. Earlier Agent/tab/menu fixtures are not current requirements.
- [Issue #1 — Phone-hosted locked WebView spike](https://github.com/code2hack/EyeBrowse/issues/1).
- [Independent review under revised Owner acceptance](https://github.com/code2hack/EyeBrowse/pull/3#issuecomment-5614157830).
- [Manager acceptance and preserved limitations](https://github.com/code2hack/EyeBrowse/issues/1#issuecomment-5614203779).

---

**v0.0.1 succeeds when one Phone-hosted page can be used as a normal Phone browser and as an independently operable RG browser over the user's existing local connection, with explicit handoff, QR app pairing, reliable recovery, and no agent or network-management product hidden inside the scope.**
