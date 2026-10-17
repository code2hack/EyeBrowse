# Fold6 → RG projector AV — canonical issue index

**Planner:** SPEC Design  
**Request-ID:** SPEC-PROJECTOR-AV-SPIKE-20261017-01  
**Status:** Owner-authorized experimental branch and five issues published; no runtime capability, test, agent startup or production migration is claimed.  
**Branch / PR target:** `spike/fold6-rg-projector-av`  
**Pinned source:** `v0.0.4@03de921e2400147842ce6c56101b58d514dde77c`  
**Authoritative experimental plan:** [PAV-1](fold6-rg-projector-av.md), first published at `189ed3a7a41f4ade167139837ce0d2a98f540486`.

## 1. Published work

| Alias | Issue | Required predecessor | Initial state |
| --- | --- | --- | --- |
| PAV-01 | [#66 — stock Fold6 virtual-display, Phone IME, audio and shell feasibility](https://github.com/code2hack/EyeBrowse/issues/66) | none | `ready-for-agent`; Manager preflight/device reservation still required |
| PAV-02 | [#67 — direct Fold6 host and RG video/control client](https://github.com/code2hack/EyeBrowse/issues/67) | #66 viable virtual-display/server path | dependency-blocked |
| PAV-03 | [#68 — RG head/touchpad input and real projected Phone keyboard](https://github.com/code2hack/EyeBrowse/issues/68) | #67 | dependency-blocked |
| PAV-04 | [#69 — required audio to RG and A/V synchronization](https://github.com/code2hack/EyeBrowse/issues/69) | #67; #66 audio observations | dependency-blocked |
| PAV-05 | [#70 — integrated operating/lock/isolation matrix and recommendation](https://github.com/code2hack/EyeBrowse/issues/70) | #68 + #69 evidence; transitive #66/#67 | dependency-blocked |

Order: **#66 → #67 → (#68 and #69) → #70**. Audio and input can develop concurrently after the direct transport works, subject to the current Owner concurrency limit and exclusive device access. Do not race on shared protocol/client lifecycle files; Manager coordinates source ownership. Host-only work may continue while another mission owns a device, but no unreserved device mutation is allowed.

A negative prerequisite is not a usable capability and cannot silently unblock implementation. A supported negative can complete its investigation after independent review; #70 may then synthesize the evidence and no-go/limited recommendation without fabricating missing positive results. Lack of permissions/devices or missing optical/acoustic evidence is unavailable, not proof of impossibility.

## 2. Non-negotiable experiment boundaries

- **No Spark/u4090 gateway in operation:** desktop build/initial authorized ADB bootstrap/stock diagnostic viewing is allowed, but the demonstrated video, audio and control path is Fold6 ↔ RG directly. Existing developer control connections need not be destroyed to prove absence of a media relay.
- **Audio is mandatory:** real captured Phone audio must traverse the network and play on RG in time with video. No silent video-only success, Bluetooth-audio shortcut or separately loaded RG media. Start with RG output playback/Phone-suppressed `output` mode; report duplication and whole-device mixing separately. This is not a final user preference decision about dual playback.
- **Phone keyboard first:** actually project the Phone's system IME on the virtual display and operate it with RG pointer/taps. No local RG keyboard in the initial client. A minimal donor-keyboard fallback may be evaluated only as a labeled alternative if the Phone-IME result is limited; it is not proof of native Phone-IME success.
- **No browser baggage in the prototype APKs:** no RG WebView/Chromium, Mihomo or subscription jobs/assets, address/tab/star toolbar. Reuse input primitives; provide only on-demand projector navigation/disconnect and black connection/error UI. The projected app's own controls remain part of its pixels.
- **Separate packages/build:** isolate under `experiments/fold6-rg-projector/`; first-party Kotlin and pinned third-party scrcpy. Distinct install IDs, no production browser updates/uninstall/data clearing. The v0.0.4 proxy/toolbar/keyboard are not deleted from that ongoing browser.
- **Screen-off is not secure-lock proof:** distinguish device power, keyguard, virtual Activity and continuation versus startup. No root, security/DRM/FLAG_SECURE bypass, microphone/call capture or global settings repair. Upstream shell operation needs an actual current grant, not a name in a diagram.

The existing browser SPEC is not silently repurposed into a projector contract. This Owner-authorized AGENTS §10 experiment follows PAV-1 only for its separate packages. Protected articles and existing product scope/DAG remain unchanged. No new ticket-plan or Worker approach-approval stage.

## 3. Ownership of results

| Required finding | Primary issue | Final synthesis |
| --- | --- | --- |
| Supported scrcpy version/shell bootstrap/virtual display | #66 | #70 |
| Ordinary apps independent of physical Phone display | #66, then real RG #67 | #70 |
| Direct authenticated encrypted video/control without desktop relay | #67 | #70 |
| Local head cursor/native app tap/scroll and minimal session controls | #68 | #70 |
| Real Phone system IME projected and activated from RG | #68 using #66 findings | #70 |
| Required captured audio decoded/played on RG | #69 | #70 |
| Media timing, buffer behavior and measured A/V drift | #69 | #70 |
| Whole-device versus target-app capture; Phone mute/duplication | #66 preliminary, #69 actual direct path | #70 |
| Screen-on/other app/screen-off/keyguard and reconnection | each relevant slice | #70 |
| No browser/proxy dependencies; data-safe install and resource cleanup | every code/device slice | #70 |
| Recommendation on keyboard/toolbar removal and next architecture | no implicit production change | #70 |

Initial numerical trial goals are in PAV-1: portrait 480×640 (240 dpi first), H.264/30 fps/2 Mbps starting video; Opus or qualified AAC for audio; ten-minute controlled AV run; measured interaction and A/V offset goals. They are assessment baselines, not claims of demonstrated performance or a requirement to repeatedly run until passing. Actual stream/latency/output routes and confidence limits must be reported.

## 4. Manager start message

Owner asked to publish this elemental experiment so local agents can work. Refresh the branch, PAV-1 and #66–#70, plus current main governance/DEV. Register/reuse participants through the current local registry; nothing in this publication starts one. Assign **#66 first** under ordinary mission authority, with actual Fold6 identity/shell/unlock grants and exclusive resource windows. Do not borrow the active v0.0.4 RG reservation or assume historical S20+ permission applies to Fold6. Reuse valid existing concurrency/quiet-notification answers; do not manufacture new prompts or alarms.

All implementer PRs target `spike/fold6-rg-projector-av`, never main or v0.0.4. Keep target-app/IME/audio state unchanged except authorized test operations and restore it on return. Hold only the exact owned virtual displays/processes/tunnels; do not reset shared networking. Credentials and raw private content stay out of source/issues/artifacts.

Independent Reviewer assesses source-bound evidence and distinguishes demonstration, measured limitation, qualified negative and untested conditions. Manager records startup/receipt, dependency results, cleanup and conclusion; GitHub publication is not peer-message delivery. An accepted experiment does not authorize the future browser refactor, proxy deletion in v0.0.4, or a production merge/release.
