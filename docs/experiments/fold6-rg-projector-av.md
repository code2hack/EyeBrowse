# Fold6 → RG general projector — experiment PAV-1

**Planner:** SPEC Design  
**Request-ID:** SPEC-PROJECTOR-AV-SPIKE-20261017-01  
**Status:** Owner-authorized experimental implementation and evidence; NOT a replacement product SPEC or production migration approval.  
**Branch / all implementation PR targets:** `spike/fold6-rg-projector-av`  
**Pinned donor:** `v0.0.4@03de921e2400147842ce6c56101b58d514dde77c`  
**Observed main:** `8929241f348bae9005b9b6bc1a93fbabd47145c1`  
**Scope:** external Fold6 projection host plus independent RG thin client, video + audio + input. No computer gateway in the demonstrated operating path.  
**Canonical tickets:** [issue index](fold6-rg-projector-av-issues.md).

## 1. Questions and authority

The Owner requested a separate elemental prototype and publication of its issues. We will determine whether a reusable projector can run ordinary Android applications on a Fold6-owned virtual display, send their real video and sound directly to RG, accept RG head/touchpad input, and project the Phone's actual system keyboard. This is an AGENTS §10 experiment, not a rollback of EyeBrowse or a new browser version. Current main AGENTS/DEV and ordinary independent review apply. No separate Planner ticket plan or Worker approach approval is required.

`main` and `v0.0.4` stay untouched. Pending v0.0.4 issues/PRs are not canceled, repurposed or merged by this experiment. Pin the donor now; later donor changes require an intentional evidence-linked update, not a floating dependency.

The browser SPEC remains the contract for the browser packages. This charter is the explicit narrow experimental exception for separate projector packages: no browser WebView, Mihomo, subscription manager, address/tab/bookmark toolbar or browser-specific page scripting is required in them. Historical always-visible-cursor/browser-toolbar rules must not be used to reject a deliberately separate projector UI. No protected article edit is authorized by this publication.

## 2. Proposed deployment and reuse boundary

**Fold6:** a pinned external scrcpy server running through explicitly authorized Android shell bootstrap; minimal on-Phone bridge/session host as needed. It creates a virtual display, lets the target Android application render itself, uses its video/audio encoders and forwards control to that display.

**Transport:** Fold6 ↔ RG directly over a verified local network/hotspot, with an authenticated encrypted channel. Reuse a suitable existing authenticated transport or platform TLS; do not invent cryptography, expose raw input-control/shell access, assume hotspot multicast works, or create a cloud relay. An existing verified direct encrypted tailnet path may support development, but relay use must be disclosed and cannot prove direct LAN/hotspot operation.

**RG:** Android video decoding to a full-screen Surface, audio decoding/playback, and a local head-pointer/touchpad interaction layer. Keep pointer drawing local. No RG webpage engine, Mihomo process/service/assets, profile refresh, proxy settings or browser chrome in the projector APK or runtime. Existing v0.0.4 browser files/data are not deleted; only genuinely useful input/model code is reused with its donor SHA.

Place new code under an isolated `experiments/fold6-rg-projector/` build or opt-in modules. Use distinct application IDs, for example `com.code2hack.eyebrowse.projector.hostprobe` and `com.code2hack.eyebrowse.projector.rgprobe`; verify they are unused before installation. Do not update/uninstall the installed EyeBrowse browser, Chromium probe, Phone browser or target apps. Avoid depending on the browser app module just to import its pointer, because that would also drag in browser/proxy dependencies. Minimal copied or separated input code with provenance is preferable to a broad production refactor. Kotlin for first-party Android/control/test code; unchanged third-party scrcpy code remains attributed upstream code under the existing vendor exception.

A PC may build APKs, authorize/bootstrap through existing ADB, observe stock scrcpy for initial qualification, and collect evidence. It is not permitted to encode/transcode/forward the final demonstration's media or input. Prove that closing the desktop scrcpy client and removing its media/control tunnel does not stop the direct Fold6–RG session. Do not disconnect a shared debug route just to demonstrate this.

## 3. Upstream baseline and technical assumptions

The official GitHub release endpoint inspected for this plan reports scrcpy **v5.0.1**. Start with this candidate, resolve its source commit and verify release artifact hashes before executing it; pin matching client/server protocol. It is not yet qualified on these devices. A documented compatible pinned release can replace the candidate within the experiment if the exact reason and retests are recorded. Do not blindly copy older version literals from examples in its documentation.

Primary references:
- [Official release](https://github.com/Genymobile/scrcpy/releases/tag/v5.0.1).
- [Virtual display and local IME policy](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/virtual-display.md).
- [Audio modes/codecs and require-audio](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/audio.md).
- [Server privileges, sockets and protocol](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/develop.md).
- [Device display controls](https://github.com/Genymobile/scrcpy/blob/v5.0.1/doc/device.md).

The server requires shell privileges; an ordinary host APK does not acquire those just by embedding a JAR. The named scrcpy shell path and its existing upstream wrappers are the authorized candidate, not general permission to bypass Android protections. Qualify Unix-socket access/SELinux and bootstrap on the actual Fold6. A minimal separately launched Kotlin shell helper is within scope if needed to bridge the pinned server without modifying its capture logic. No root, system installation, framework patch, target-app injection or speculative persistence daemon.

Reuse scrcpy's separate video/audio/control framing. The protocol is version-dependent, not a stable Android service API. Handle its actual codec metadata, lengths, PTS, EOF/error and reconnection semantics. Do not assume an unmodified scrcpy server already exposes a secure Android-to-Android service. Host bridge and Android thin client are real experimental work.

## 4. Initial experience and trial settings

These are starting experiment settings, not a production design approved for every Android app:

| Item | Initial choice / question |
| --- | --- |
| RG physical display | 480 pixels wide × 640 high, portrait; measure actual bounds/rotation |
| Host virtual display | Begin at 480×640, 240 dpi; test a lower density only if needed for usable app/IME layout and record it |
| Video | H.264, target 30 fps during changing content, initial 2 Mbps; use actual supported hardware capabilities |
| Audio | Mandatory; try the pinned core's Opus path, use AAC if actual encoder/decoder compatibility calls for it; record codec/rate/channels |
| Default audio destination | RG playback; stock `output` capture is the initial mode. This is a test default, not a final exclusive-versus-duplicate preference decision |
| Keyboard | Phone's real system IME on the virtual display via supported local IME policy; initially no built-in RG keyboard |
| RG chrome | Full projection plus local pointer. Black connection/error/local-control UI; no always-on browser toolbar |
| Essential local controls | A documented on-demand way to open remote Back/Home and local Disconnect; not the old address/tab/star bar |
| Pointer/scroll | Reuse donor pose/gain/24-px strip concepts, adapt to current decoded display geometry; no off-screen tap clamping |

Target apps render themselves. Their own toolbar and system IME are streamed pixels, not duplicate RG UI. Do not reconstruct a DOM/accessibility tree or fake the Phone keyboard with RG keys. Keep captured content faithful; choose available dark-theme app settings where practical, but do not require a general projector to rewrite every third-party app into black. App compatibility and bright/poorly sized output on a waveguide are measured limitations.

Head input should remain responsive locally even when network/video stalls. Remote touch/wheel/swipe coordinates refer to the actual virtual display, not the physical Phone screen. For scrolling, use a valid on-screen content location derived from the visible pointer/last interior position, never inject the off-screen padding coordinate. Test nested lists and keyboard areas as ordinary Android input; do not carry browser DOM scrolling helpers into arbitrary apps. One event consumer. No imported remote ordinal/ABA framework. On link loss, stop generating input and discard queued commands; reconnect establishes a new usable stream and geometry without replaying old taps/text.

## 5. Audio is first-class, not a silent optional channel

The initial upstream `output` mode captures whole-device output and normally suppresses Phone playback. It does **not** establish virtual-display-app isolation. Use owned test tones/clips to characterize simultaneous physical-display app audio, notification/media mixing, audio focus and whether the Phone actually becomes silent. Do not test by collecting private calls, messages or microphone audio.

Android 13+ `playback`/duplication is a secondary comparison if available, not required final UX. Record opt-out/protected-content limitations instead of bypassing them. A/V success requires audio to traverse the network and decode/play on RG; Phone-to-RG Bluetooth audio or a separate media player is not proof of IP audio forwarding. No microphone uplink, voice calls or bidirectional conferencing in this spike.

Video-only can be a useful intermediate checkpoint but cannot be reported as a successful AV projector. Use `--require-audio` in relevant stock experiments or the equivalent explicit failure handling in the custom client. Missing/failed audio is visible in diagnostic status and the result ledger. Confirm actual sound at the RG output with a permitted controlled recording or operator observation; AudioTrack writes alone are not audible-output proof.

Preserve source media timestamps and use normal playback-clock handling with bounded buffers. Test an owned synchronized flash/beep or equivalent clip. Separate packet/decode/AudioTrack timing from actual optical/acoustic observations, and do not subtract unsynchronized device clocks as end-to-end latency. Initial assessment targets: sustained near-30-fps delivery for a 30-fps changing fixture, under 200 ms interaction-to-visible-result at the 95th percentile where measured, and absolute A/V offset within 100 ms without accumulating drift during a ten-minute run. These are go/no-go assessment goals, not arbitrary reasons to rerun indefinitely or claim unmeasured sensory performance. Record actual results and bottlenecks.

## 6. Investigation sequence / DAG

| Alias | Experiment | Blocked by |
| --- | --- | --- |
| PAV-01 | Qualify stock scrcpy virtual display, local Phone IME, audio and shell bootstrap on actual Fold6 | none |
| PAV-02 | Independent Fold6 host and RG thin client carry real video directly, without a computer gateway | PAV-01 viable virtual-display/server path |
| PAV-03 | RG head/touchpad input operates apps and projected Phone keyboard without browser toolbar | PAV-02 usable direct video/control foundation |
| PAV-04 | Required IP audio playback and synchronization with the projected video | PAV-02 usable direct transport; use PAV-01 audio observations |
| PAV-05 | Integrated two-device validation, lifecycle/lock/audio-isolation matrix and architecture recommendation | PAV-03, PAV-04 evidence |

PAV-03 and PAV-04 may develop in parallel after PAV-02 within the Manager's actual concurrency limit, but Fold6/RG device use is exclusive. A negative upstream capability blocks dependent implementation, not independent read-only synthesis. PAV-05 may synthesize an evidence-backed no-go without pretending missing dependencies passed. No separate parent issue or new version release is necessary.

## 7. Result matrix and completion meaning

Report each item as demonstrated, demonstrated with named limitation, failed for tested setup, or untested/unavailable:

1. Real third-party app runs on a virtual display while another permitted app occupies the physical Phone display.
2. Video, input and required audio operate directly Fold6↔RG with no computer data-plane process/tunnel.
3. Phone system keyboard is visible on that display and real remote key taps edit the target app, including Backspace/Enter/dismiss and reopen.
4. Local pointer/scroll works without importing RG WebView/proxy/browser-toolbar dependencies.
5. Actual RG audio plays in time with video; whole-device versus target-only capture limits and Phone output are explicit.
6. Physical Phone display off, system asleep state and secure keyguard locked state are separately observed; test both already-running and re-entry/start conditions where authorized.
7. Ordinary app switches, disconnect/reconnect, RG pause and host stop recover or fail visibly without replay or leaked media streams.
8. Measured frame/input/audio behavior, resource counts and short active/idle workload support a recommendation; no unsupported battery-endurance claim.

Use at least a browser and one unrelated ordinary app (e.g. installed media player or Settings) plus owned media/typing fixtures. Enumerate installed versions; do not assume Chrome, a particular keyboard or a player is installed. No new logins or purchases required. A Phone Activity on a secondary display is not the same as an arbitrary stopped background app; never claim arbitrary-app compatibility from two tests. Respect FLAG_SECURE, DRM, audio opt-out and keyguard. A secure-lock limitation is a qualified product limitation, not permission to disable the lock. Do not demand impossible access to protected pixels.

A well-supported negative may close an investigation under AGENTS §10 after independent review. It does not mean successful projection, grant a product migration, or make missing hardware evidence PASS. Missing device/credential/optical access alone is unavailable evidence, not a universal technical impossibility. Do not build a speculative workaround framework to avoid reporting a narrow limit.

## 8. Operational boundaries and handoff

Manager refreshes current repository/governance, uses the existing registered participants/resources policy, assigns unique branches/worktrees and pins candidates. Reuse valid current device/concurrency/quiet-notification grants; do not silently borrow another mission's device or reopen answered planning questions. This charter does not invent Fold6 shell authorization or a secure-unlock credential. Qualify the exact actual Fold6 and RG identities, Android/One UI/codec/input/audio state and granted bootstrap route before mutation. Normal permitted ADB setup is allowed; publicly exposed ADB, grant escalation, root, changed security settings, forced IME replacement or disabled keyguard are not.

Only new separately named projector/owned fixture packages may be installed, and only in the Manager's reserved window. Do not clear data or re-pair existing apps/tailnet/debug identities. All audio/video evidence uses harmless owned content; disable scrcpy clipboard autosync and microphone sources, keep private authentication tokens out of logs/commits, and do not record unrelated personal applications. Connections are opt-in with visible start/stop status; encrypted authenticated peers only. Disconnect stops owned media/input. No unauthenticated shell command API.

Restore prior Phone audio mode/routing/volume and IME/display settings if an authorized test changed them; release owned virtual display, codec/audio devices, process/socket/tunnel and temporary APK/fixtures as assigned. Restore each device to its current Owner-required final state, with observed evidence rather than assuming a dark screen means locked. Never terminate another run's process or networking route. Preserve useful negative evidence and exact source/artifact provenance.

Expected outputs belong under `docs/experiments/fold6-rg-projector-av/` and mission-scoped private artifacts. Only sanitized commands/versions, fixture results and checksums belong in Git. No current browser version merge, production SPEC change, v0.0.4 proxy deletion, runtime agent startup, device mutation or release is performed by publishing this plan.
