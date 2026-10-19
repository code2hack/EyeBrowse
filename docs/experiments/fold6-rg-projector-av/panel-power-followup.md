# PAV-PWR-1 — Physical panels off, projection active

**Planner:** SPEC Design  
**Request-ID:** SPEC-PAV-PANEL-POWER-20261019-01  
**Status:** Owner-authorized follow-up experiment; NOT a demonstrated fix or production approval  
**Branch / all implementation PR targets:** `spike/fold6-rg-projector-av`  
**Current source baseline:** `ecb5b59ca152360ecaa6014ab1f815b58b62c6cd` (accepted #76 / PR #77)  
**Parent experiment:** [PAV-1](../fold6-rg-projector-av.md)  
**Canonical dispatch:** [experiment issue index](../fold6-rg-projector-av-issues.md)

## 1. Objective and exact authorization

The Owner asked to publish the proposed stock-panel-power investigation so local agents can execute it. Determine whether the actual Fold6 can keep its virtual-display app, video, audio and remote input usable with its **physical display panels powered off and existing secure keyguard left locked**, by combining stock scrcpy physical-display power control with its existing keep-active mechanism. This is not another test claiming `keep_active` overrides Android sleep.

This direction authorizes a narrow experimental host/control adaptation and actual ordinary wake/panel-off/panel-on tests in the Manager's reserved device window. It does not authorize keyguard dismissal, PIN/password use, lock-policy changes, root, framework patches or arbitrary power-service calls. Existing quiet-notification, resource ownership and direct-execution governance apply; no separate Planner ticket plan or implementation-approach approval is required.

Use the current accepted implementation, not the pre-#76 report source. **#76 already removed the 15-minute host lifetime cap and enabled `keep_active=true`, retaining `power_on=false`. Preserve both changes.** Do not revert to the old APK, add another elapsed-session shutdown, impose an overall Worker deadline, or interrupt the Owner's retained preview without the Manager coordinating the current resource handoff. Finite measurement windows are observations, not runtime limits.

This supplement extends the old experiment's power-control boundary only for the named stock operations below. Other PAV-1 constraints remain. `main`, `v0.0.4`, protected articles, existing apps/data and completed issue outcomes are not changed by publication. The old browser effort is not restarted.

## 2. Evidence and hypothesis

The accepted [#70 report at the pre-#76 source](https://github.com/code2hack/EyeBrowse/blob/086a3f768f450f6c6a04965a30081b2a02a333a1/docs/experiments/fold6-rg-projector-av/results.md) records:

- Locked-but-Awake AV on the actual Fold6, including a ten-minute moving-content software measurement; this is not a new acoustic/optical claim.
- Ordinary sleep made the virtual display inactive/OFF/DOZE along with the physical display. Frames stopped; audio timestamps could advance while the owned source was not playing and the expected tone was absent.
- The then-temporary `keep_active=true` comparison did not repair continuation after sleep or startup while asleep. Its later restoration to the old source was historical; #76 subsequently made keep-active part of the accepted current host.
- Samsung reported physical and virtual displays in group 0 despite requested independent-group flags. That observation is a lead, not an established OEM root cause.

The [#66 feasibility report](https://github.com/code2hack/EyeBrowse/blob/086a3f768f450f6c6a04965a30081b2a02a333a1/docs/experiments/fold6-rg-projector-av/fold6-feasibility.md) also recorded stock MOD+o/MOD+Shift+o with continuing projected pixels, but no independent evidence of actual physical panel darkness. Its log strings did not establish a successful panel-off case.

**Hypothesis:** scrcpy's compositor-level physical panel power control may leave logical rendering active, unlike Android's ordinary sleep action. Physical darkness, logical wakefulness, virtual-display state, DeviceIdle and keyguard are distinct observations. A logical display still reporting ON does not by itself disprove compositor-level panel-off; a log saying OFF does not prove hardware darkness either.

## 3. Pinned mechanism, not a new capture stack

Keep the unmodified official scrcpy 5.0.1 server already pinned to source `a60891aea193d92e7e5c3942700eca63f9d19a5f`, JAR SHA-256 `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`. Check actual source/artifact bindings before testing.

Source references:

- [Controller](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/Controller.java): keep-active periodically calls userActivity. `setDisplayPower` selects physical display 0 when capturing a new virtual display, invokes the stock device helper and registers power restoration. In virtual-display mode it does not persistently force the panel back off after every later POWER/wake event.
- [Device](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/Device.java): the selected physical-power path uses physical display tokens and SurfaceControl, including multiple panels. The newer Android-15 display-power path is deliberately disabled upstream. Do not assume `cmd display power-off` is an equivalent replacement.
- [ControlMessage](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/ControlMessage.java) and [reader](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/ControlMessageReader.java): type 10, followed by one Boolean byte.
- [Stock device controls](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/doc/device.md): keep-active, panel off/on and ordinary sleep are different operations.

Pinned stock control bytes:

```text
0A 00   TYPE_SET_DISPLAY_POWER(false)
0A 01   TYPE_SET_DISPLAY_POWER(true)
```

These belong on the **existing local scrcpy control stream**, serialized with its other writes. They are not complete EyeBrowse TLS-protocol messages. A minimal fixed experimental control operation or startup option in the current authenticated host is allowed; no unauthenticated network endpoint, arbitrary Binder/shell command API, new permanent RG panel or gesture is needed. Verify the exact handler is reached rather than assuming a byte write is hardware success.

The first test may use a matched stock desktop client as a mechanism comparator in its own reserved session. The qualifying implementation must then exercise the same operation on the real **Fold6-hosted bridge to RG** without a desktop media/control relay. Never have competing clients own the same control stream or virtual display. Do not run a second capture server solely to manipulate the first server's power state.

## 4. Focused experiment

Keep codecs, virtual geometry, media fixtures, selected IME and network path unchanged unless a concrete unrelated incompatibility is recorded. Do not bundle this experiment with the known IME/layout problem or new audio-isolation work.

| Case | Action and discriminating observation |
| --- | --- |
| A — reference | On the actual matched pair, confirm changing virtual video, owned audio signal and a harmless remote native-input effect while Android is Awake. Record both physical panels and keyguard. Prefer the already-demonstrated locked-but-Awake state when available without unlocking. |
| B — proposed continuation | Keep `keep_active=true`; send stock SET_DISPLAY_POWER(false), not KEYCODE_SLEEP. Observe actual physical panels, logical/virtual states, target Activity, video content, audio signal and native input. Do not require logical display OFF as the success oracle. |
| C — reverse and repeat | Send stock SET_DISPLAY_POWER(true); verify restoration. Repeat a small declared on/off sequence to distinguish one transient result from a usable operation. Keep all failures; there is no fixed extra retry budget or run-until-green loop. |
| D — operating check | If B works, observe a ten-minute controlled direct Fold6-to-RG session, including a native tap/scroll segment, while panels remain physically dark and keyguard remains locked. A test observer may stop collecting at ten minutes; the host must not self-terminate. |
| E — start and recovery | Separately distinguish a session started while Android is Awake but panels are blanked from starting while Android is genuinely asleep. From genuine sleep, only an ordinary already-authorized wake followed by the stock panel-off sequence is in scope; no unlock. If the app must be explicitly relaunched, report that rather than claiming uninterrupted or automatic recovery. |
| F — normal sleep comparison | Existing #70 failures remain the primary comparison. Repeat ordinary sleep only if needed to distinguish a current changed result. A normal sleep failure is not itself failure of B, nor does B claim support for physical POWER-button sleep. |

Never send an arbitrary event into the physical lock screen to keep the device alive. Do not add a timer that repeatedly wakes a Phone the user explicitly put to sleep. The named keep-active feature is inactivity prevention during an explicitly active projection session, not an unlock or ignore-Stop policy.

## 5. Evidence and success criteria

A **demonstrated locked, panel-off AV/input result** requires all of the following in the same bounded observation, with actual device/firmware/source/server and codec bindings:

1. Physical panel darkness/power-off is independently established for the active Fold6 panel and the other panel's actual state is recorded. Accept appropriate compositor/hardware-level observations whose semantics are understood, or direct panel observation. A second panel already OFF, black screenshot, brightness zero, black overlay, request return code or scrcpy log alone is insufficient. If only software power-mode evidence exists, describe that precisely; do not claim optical darkness was observed.
2. Existing secure/showing keyguard remains locked. No PIN, credential, dismissal, disabled lock or changed security policy is involved. An unlocked-only positive is a separate limitation, not a locked-state PASS.
3. RG receives and displays changing target content, not a cached last image or initialized STREAMING label. Record meaningful frame/source progress and target activity state. Direct socket/process evidence still excludes a PC media relay.
4. The owned audio source is actually playing and its known signal reaches the RG decode/output path, not merely advancing PTS or silence. Audible playback may use an existing permitted harmless observation; absent acoustic access remains unmeasured and must not be reported as newly heard sound. Retain existing whole-device capture limitations.
5. A harmless remote native tap and scroll affect the owned virtual-display target while panels are dark; an ACK alone is not input proof. Do not turn this into a new Phone-IME qualification.
6. Stop, Disconnect, peer loss and explicit panel-on have safe, tested behavior. Preserve stock cleanup and confirm the physical end state rather than assuming process death restored it. Test-owned sessions may be ended; the Owner's retained preview/install from #76 stays under Manager's explicit current custody and final-state instruction.

Host tests should cover exact stock control encoding/serialization, authenticated access, the retained no-overall-lifetime behavior and relevant cleanup. Run affected host tests/build/lint for actual changed packages. Reuse public/high-level existing verification seams; do not introduce a generalized input-identity/fencing framework.

Report four outcomes separately: **mechanism submitted**, **software panel-power evidence**, **actual AV/input continuity**, and **physical/acoustic observation**. Finite-run results are not battery-endurance, indefinite uptime or arbitrary-app guarantees. Screen-off continuation and startup are independent rows. A source-level argument is not a Samsung runtime result.

## 6. If the stock route fails

Retain the exact failure and complete a narrow read-only diagnosis of requested versus effective display groups, actual group/virtual wakefulness and the stock power operation's outcome. The pinned server already requests own-display-group/always-unlocked/own-focus flags; adding identical flags again is not a new mechanism.

**Do not** patch Samsung/framework policy, change display flags or keyguard handling, introduce partial/full wake locks or global timeout/Doze/plugged-in settings, force secure/DRM/audio-opt-out content, replace the capture stack, or disable the lock to obtain a green result. A concrete alternative requiring those changes returns as a separately scoped authority question. Failure of this route is not universal Android impossibility; a qualified negative may close this investigative objective without accepting production screen-off support.

## 7. Delivery and operating boundaries

Deliver the minimal experimental host/control change if needed and `docs/experiments/fold6-rg-projector-av/panel-power-results.md` with the case table, exact source/APK/server binds, observed physical/logical/keyguard states, AV/input outcomes, on/off restoration and current limitations. Link original #66/#70 evidence without overwriting it. Mark unobserved physical conditions unavailable; no repeated Owner question is required merely to start in-scope source work.

Manager assigns directly from the linked canonical issue, verifies current resource ownership and installed preview before mutation, and coordinates separate worktrees/independent exact-candidate review. Only granted Fold6/RG resources and owned benign test content may be used. Do not reset shared ADB/SSH/Tailscale, alter accounts, copy private screen/audio into Git, reinstall old app snapshots, reactivate canceled v0.0.4 work or expose raw control ports. Preserve current no-local-menu/two-tap-Back/three-tap-Disconnect behavior. No new user-facing panel-off button is mandated by the diagnostic control seam.

This publication creates an actionable follow-up, not device execution, new agent startup, a change to protected articles, a production migration or a main merge. Previous evidence remains valid for its original scope; no-go for production remains until the actual remaining limitations are resolved and the Owner makes that separate decision.
