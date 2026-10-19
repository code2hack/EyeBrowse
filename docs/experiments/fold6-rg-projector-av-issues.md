# Fold6 → RG projector AV — canonical issue index

**Planner:** SPEC Design  
**Current Request-ID:** SPEC-PAV-PANEL-POWER-20261019-01  
**Branch / all implementation PR targets:** `spike/fold6-rg-projector-av`  
**Current follow-up baseline:** `ecb5b59ca152360ecaa6014ab1f815b58b62c6cd` (accepted #76 / PR #77)  
**Original donor:** `v0.0.4@03de921e2400147842ce6c56101b58d514dde77c`  
**Current publication:** Owner-authorized panel-power investigation #78 is ready for assignment; no new device result, fix or production migration is claimed.

## 1. Current actionable follow-up

| Alias | Canonical issue | Prerequisites | Dispatch status |
| --- | --- | --- | --- |
| PAV-PWR-01 | [#78 — Physical panels off with projection active and keyguard locked](https://github.com/code2hack/EyeBrowse/issues/78) | #70 investigative findings and #76 current host; both completed | `ready-for-agent`; ordinary Manager assignment and current device reservation required |

Read [PAV-PWR-1: panel-power follow-up](fold6-rg-projector-av/panel-power-followup.md), first published at `229f61edb4bc5d8df9e9c57fa3b9f589706cfda9`. It is the narrowly authorized next experiment, not a request to repeat or reopen the original five issues.

The target is **physical Fold6 panels off, existing keyguard locked, virtual app/video/audio/input still active**. Test scrcpy's stock physical-panel-power control separately from ordinary Android sleep. Preserve `keep_active=true`, `power_on=false` and the **removed overall host lifetime cap** delivered by #76; the old fifteen-minute cutoff must not return. Measurement windows do not limit runtime or Worker duration.

The allowed implementation is a minimal fixed stock OFF/ON operation in the existing authenticated host/control path. Stock type10 carries Boolean OFF/ON (`0A 00` / `0A 01`) on the local scrcpy control socket; this is not a raw message to the EyeBrowse TLS endpoint. Keep the pinned unmodified server and current media/client behavior. No new permanent RG panel, gesture, arbitrary power API, server fork or capture framework is required.

A useful result needs independent physical-panel evidence, actual keyguard state, changing target video, meaningful owned audio and a harmless native input effect. Logical DisplayInfo may remain ON after compositor-level blanking; neither that fact nor an OFF log alone decides the result. Silent audio timestamps or a STREAMING label are not usable AV. Retain all sensory-measurement limits honestly.

If the stock route fails, only read-only requested/effective display-group and power diagnosis is included. Framework/display-flag changes, wake locks, global timeout/Doze settings, keyguard changes or secure-content bypass are not authorized by this follow-up. Route a concrete new mechanism rather than guessing or claiming universal impossibility.

## 2. Original investigation and subsequent accepted change

The original [PAV-1 charter](fold6-rg-projector-av.md) was published under `SPEC-PROJECTOR-AV-SPIKE-20261017-01`. The [original index at 2346784](https://github.com/code2hack/EyeBrowse/blob/23467848448a1c8251a68a553deea3d2426cbd4e/docs/experiments/fold6-rg-projector-av-issues.md) preserves the original ready/blocked states and first-dispatch instructions. Those are historical, not instructions to start #66 again.

| Original alias | Issue | Original dependency / accountable finding |
| --- | --- | --- |
| PAV-01 | [#66](https://github.com/code2hack/EyeBrowse/issues/66) | Root; actual Fold6 scrcpy/shell/virtual display/Phone IME/audio feasibility |
| PAV-02 | [#67](https://github.com/code2hack/EyeBrowse/issues/67) | #66 viable server path; direct authenticated Fold6 host → RG video/basic control |
| PAV-03 | [#68](https://github.com/code2hack/EyeBrowse/issues/68) | #67; local RG head/pad controls and projected Phone-IME qualification |
| PAV-04 | [#69](https://github.com/code2hack/EyeBrowse/issues/69) | #67 plus #66 audio evidence; required IP audio and AV clock behavior |
| PAV-05 | [#70](https://github.com/code2hack/EyeBrowse/issues/70) | #68 + #69 evidence; integrated performance/power/lock/isolation findings and recommendation |
| Lifetime follow-up | [#76](https://github.com/code2hack/EyeBrowse/issues/76), [PR #77](https://github.com/code2hack/EyeBrowse/pull/77) | #70 completed investigation; remove host lifetime cap, enable keep-active and retain Owner manual-use preview |

Original DAG: **#66 → #67 → (#68 + #69) → #70**. Current extension: **#70 → #76 → #78**, with #70 evidence also directly informing #78. No old result, failure budget, acceptance or source binding is reset.

[Accepted integrated findings](fold6-rg-projector-av/results.md) retain locked-but-Awake AV, normal-sleep/startup failures, failed keep-active-only sleep comparison, IME limitations and physical/acoustic measurement boundaries. The report's statements about the then-current timed host are historical after #76; neither its no-go conclusion nor earlier failed screen-off cases becomes a success because the host now lasts longer. #78 will publish a separately attributed result.

## 3. Retained experiment boundaries

- **Direct Fold6 ↔ RG runtime.** Development/authorized ADB bootstrap and optional stock desktop comparator may use a computer; the qualifying media/control path may not depend on a PC gateway.
- **Real audio remains required.** Owned Phone source audio traverses the network to RG; no video-only success, Bluetooth substitute or separately fetched RG media. Report software, audible and synchronization evidence at their actual scope.
- **Thin client remains thin.** No RG WebView/Chromium, Mihomo/subscriptions, browser toolbar or custom editor/identity framework. Preserve current no-local-menu, two-tap remote Back and three-tap local Disconnect behavior. #78 adds no permanent user-interface control and does not repair Phone-IME compatibility.
- **Isolated packages and data.** Preserve production browser packages, data and archived v0.0.4 work. Separate experimental source and exact donor provenance remain; no production rollback, main merge or release is authorized.
- **Locked is not asleep.** Only the named stock panel-power and keep-active behavior, plus ordinary already-authorized wake/recovery, are included. No PIN/password, keyguard dismissal, root, DRM/FLAG_SECURE/audio-opt-out bypass, microphone/call capture or global policy changes.
- **Current host lifetime and preview.** #76's no-overall-timeout behavior and explicit Stop/Disconnect/failure handling remain. The Owner's installed/current live preview is under Manager custody: do not silently stop, overwrite, uninstall or revert it merely because a test or report ends.

PAV-PWR-1 is an explicit experimental supplement, not a protected-article edit. Current main AGENTS/DEV govern execution; no separate Planner ticket plan, Worker approach approval, new broad design review or repeated answered Owner prompt is required.

## 4. Manager startup handoff for #78

Refresh current branch, the linked follow-up and #78; inspect current main governance/DEV and the installed/retained #76 session before touching devices. Assign a mission-specific branch/worktree from the current spike with **PR base `spike/fold6-rg-projector-av`**. Reuse applicable current concurrency/quiet-notification grants and coordinate exclusive actual Fold6/RG access and one source writer. No automatic new agent, direct peer message or device action is performed by this publication.

Declare the test-owned session/resource handoff so the Owner's preview is not interrupted without coordination. Capture only harmless owned content and preserve private trust material. Do not reset shared ADB/SSH/Tailscale or revive canceled browser work. Stock cleanup may restore panel power; verify and return to the Manager's actual requested device state, rather than assuming exit restored darkness or lock. Independent approved source/analysis work need not wait for unavailable optical/acoustic access.

Expected result: `docs/experiments/fold6-rg-projector-av/panel-power-results.md`, with exact baseline/candidate/server/device bindings, stock panel-handler evidence, physical/logical/virtual/keyguard observations, live AV/input, restoration and separately labeled unobserved conditions. A qualified negative completes an investigation, not a working-feature promise. Missing access is unknown, not universal impossibility. Receive independent exact-candidate review and normal Manager closure.

**Publication status:** follow-up plan and issue are available; readiness is not local-agent receipt/startup or test acceptance. Old #66–#70 results, #76's accepted source change and current Owner preview remain preserved. Main/v0.0.4 and protected articles are untouched.
