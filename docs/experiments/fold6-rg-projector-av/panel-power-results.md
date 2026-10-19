# PAV-PWR-01 stock physical-panel control investigation

Worker: Worker-#78. Canonical issue: [#78](https://github.com/code2hack/EyeBrowse/issues/78). Contract: [PAV-PWR-1](panel-power-followup.md). This report is **in progress, not candidate acceptance or demonstrated panel-off support**.

## Source and mechanism

Assigned baseline `b3c9aa95f344dd5be8601baa8efd2d4bec40dc02` adds only Planner Markdown over accepted product `ecb5b59ca152360ecaa6014ab1f815b58b62c6cd`. Main governance was refreshed at `8929241f348bae9005b9b6bc1a93fbabd47145c1`; the four protected articles match the worktree and remain unchanged.

The existing mTLS listener admits two fixed diagnostic requests after the TLS handshake: operation 3 is PANEL_OFF and operation 4 is PANEL_ON, behind the existing PAV1/version-2 request header. They act only on the active session. `PanelPowerMain <existing-peer-directory> off|on` is the explicit shell experiment client. It consumes the already trusted peer identity opaquely; there is no new trust, network listener, RG menu, or arbitrary shell/power endpoint.

The host writes exactly `0A 00` / `0A 01` on its existing local scrcpy control stream. Tap, scroll, navigation and panel writes share one synchronized writer. A response byte of 1 and the fixed SUBMITTED telemetry indicate only a completed local write. They cannot establish stock-handler success, panel darkness or media continuity. Closure rejects later control writes and retains upstream cleanup. No host lifetime timer was added; `keep_active=true`, `power_on=false`, mandatory output Opus, direct media routing and existing startup/write/audio safeguards remain.

Pinned unmodified scrcpy 5.0.1 source is `a60891aea193d92e7e5c3942700eca63f9d19a5f`; source checkout is clean. Verified master JAR SHA-256: `764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536`. The stock [Controller](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/control/Controller.java) selects physical display 0 for new-display capture and registers restoration after a successful OFF. Its [Device helper](https://github.com/Genymobile/scrcpy/blob/a60891aea193d92e7e5c3942700eca63f9d19a5f/server/src/main/java/com/genymobile/scrcpy/device/Device.java) selects physical display tokens and SurfaceControl. Source behavior is distinct from the Samsung runtime result.

## Checks and artifacts

On spark, the assigned build flock and two-worker/2 GiB JVM limits were used. Baseline protocol/host tests passed. The added encoding test first failed on the missing implementation. JDK17 affected checks then passed: 21 protocol/host unit tests, host debug assembly, host/protocol lint, and 36 receiver compatibility tests plus receiver debug assembly/lint. The no-overall-lifetime regression, per-session JAR preservation and exception categories remain covered. TLS tests exercise trusted and rogue identities for both fixed panel requests; the control-writer test proves a panel command cannot split a tap message and rejects writes after closure.

Host APK SHA-256: `6e83df5d9c8a6598721ccb9882aac86e8aa622e3086dd9d26731ebe0fb8d2dce`, 2,728,235 bytes. Debug signer SHA-256: `e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963`. Installed predecessor host hash matched `9dcc5388f78ace697552d9a3057420e2a76ad9aea9b7c60288390b80ac5cc44b`. Installed unchanged RG hash matched `e805103f4d445101439626a1ae678166a7f23bb79feca97cc7cfbe16f861b9c3`; no receiver update is required for the fixed host requests.

## Current device binding and cases

The assigned selected ADB server is `127.0.0.1:15037` via the retained Manager SSH forward to the unchanged u4090 server. The first Phone primary check returned device-not-found; the Manager restored its missing LAN entry with one bounded connect. That original failure remains in evidence. No Worker connect, reset, trust change or route switch occurred. Subsequent exact Phone identity matched.

Phone: Fold6 `R3CX70NHTHK`, SM-F956N, Android 16/API36, One UI property 80500, build `samsung/q6qksx/q6q:16/BP4A.251205.006/F956NKSS4DZH5:user/release-keys`, user0/shell2000. RG: `1906092617103125`, RG-glasses/API32, user0/shell2000. Current direct media addresses remain Phone `10.199.167.157` and RG `10.199.167.154`; debug uses Phone LAN5555 and RG USB.

Before any preview transition, inherited bridge PID6480/start234763921 was alive but DISCONNECTED. Its last session400927e9 ended CONTROL_IO after96 seconds; RG's matching status was STOPPED. The old preview was therefore not claimed to be continuously live. Phone was Awake, DeviceIdle ACTIVE/light ACTIVE, secure=true but showing=false/inputRestricted=false/deviceLocked0. The Worker performed no credential action. Owner was asked once to lock normally for the locked-state case. RG music was0; historical original4 custody is retained.

| Case | Mechanism | Panel / keyguard | AV / native input | Restoration |
| --- | --- | --- | --- | --- |
| A reference | Pending matched current session | Current inherited state was unlocked; locked case pending | Pending | Not yet changed |
| B stock OFF | Pending | Unmeasured | Unmeasured | Pending stock ON |
| C reverse/repeat | Pending | Unmeasured | Unmeasured | Pending |
| D ten-minute interval | Conditional on viable B | Unmeasured | Unmeasured | No measurement-end shutdown |
| E blanked-Awake start vs sleep | Pending | Unmeasured | Unmeasured | Pending |
| Stop / Disconnect / peer loss | Pending | Unmeasured | Unmeasured | Pending |

## Limits and retained evidence

Physical darkness and audible playback are not observed yet. Software frame/audio counters, source state, compositor power evidence, logical wakefulness, DeviceIdle, secure/showing keyguard and native target effects will be reported separately. No screenshot, black frame, successful write or prior stock log is promoted to physical PASS. No PIN/unlock, deliberate sleep substitution, wake loop, global power/Doze setting, wake lock, display-flag change or capture-server fork is authorized.

Original [#66 feasibility](fold6-feasibility.md), [#70 results](results.md) and accepted [#76 lifetime/startup record](https://github.com/code2hack/EyeBrowse/issues/76) retain their original scope and failures. The mission-owned raw evidence root is `/home/code2hack/.local/state/eyebrowse/experiments/fold6-rg-projector-av/runs/run-001/issue-78/worker-78-001/evidence`; private provisioning contents are excluded from reports and Git. Final preview custody and exact pushed candidate remain pending. Independent exact-head review and acceptance belong to the Manager.
