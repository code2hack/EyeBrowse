# Development and local agent operations

Approved development procedures and runtime configuration for EyeBrowse. Governance and authority remain in `AGENTS.md`; product requirements remain in `SPEC.md`. Actual run choices, assignments, addresses, and test outcomes belong in runtime/issue/evidence records, not this file.

## Workspace

- Repository: `git@github.com:code2hack/EyeBrowse.git`
- Local checkout: `/home/code2hack/Projects/EyeBrowse`
- Canonical branch: remote `main`.
- Local runtime root: `/home/code2hack/.local/state/eyebrowse`; keep credentials out of runtime records.
- Per-version runtime settings: `<runtime-root>/<version>/runtime.json`; per-run records: `<runtime-root>/<version>/runs/<run-id>/run.json`.
- Runtime records are consumed by Manager procedures, not automatically loaded by Pi. Provider configuration remains in `~/.pi/agent/models.json`.
- Production build tasks are established by the approved ticket plans and implementation; the existing experiment has its own wrapper and commands below.

## Owner-approved agent settings

| Role/profile | Runtime | Provider | Model | Thinking | Name and lifecycle |
| --- | --- | --- | --- | --- | --- |
| v0.0.1 Planner | `pi` | `openai-codex` | `gpt-6-astra` | `max` | Reuse the registered `v0.0.1 Planner` session through v0.0.1 closeout |
| Worker | `pi` | `deepseek` | `deepseek-v4.1-flash-expires-on-0910` | `max` | `Worker-#<issue>`; ticket mission through verified cleanup |
| Worker availability fallback | `pi` | `spark` | `qwen3.8-flash-next` | `max` | Same mission; record the actual model/session change |
| Reviewer | `pi` | `openai-codex` | `gpt-6-astra` | `max` | `Reviewer-#<issue>`; independent ticket-scoped session, reused for renewed reviews |

Use `--thinking max` for every assigned profile. Refresh current model metadata and verify the effective level at startup; do not silently accept clamping or substitute a lower level. Pi's display label and upstream parameter can differ: current mappings send DeepSeek `reasoning_effort=max` and Spark/Qwen `reasoning.effort=xhigh`.

A replacement session receives a new native ID and a unique name suffix such as `Worker-#42-r2`; retain the prior registry record. Replacement does not reset ticket-level attempt counts. Resolve the persistent Planner through `CONTRIBUTORS.md` and current runtime routing rather than starting another Planner for each ticket.

Use the Spark fallback only when the requested DeepSeek model is unavailable, not to conceal reasoning or implementation failures. Record and report fallback use. Do not silently substitute other providers, models, or thinking levels.

On any low weekly quota/limit warning or quota wall, notify the Owner immediately and await direction before continuing affected work or changing providers to evade that limit. No numeric warning threshold has been specified.

Configuration is local to `~/.pi/agent/models.json`. Never copy credentials into repository files, prompts, or logs.

### Model preflight

Use `pi --list-models <model-id>` and inspect only non-secret model metadata. A missing model/auth configuration is a startup blocker, not a successful fallback. Before relying on a newly configured mapping, make a bounded no-tool request through the configured Pi provider and record the outbound effort field, HTTP result, and whether a normal response completed; never record credentials or reasoning content. A successful request proves that route accepted the parameter, not a quantitative guarantee about internal reasoning or future availability.

The listed DeepSeek and Spark routes have passed this basic `max` request check; the Planner's Pi startup and receipt verified `openai-codex/gpt-6-astra` at `max`. Preserve detailed preflight results in the version runtime directory and repeat availability checks when dispatching or recovering a failed route. Each real mission still requires its own startup/receipt verification.

## Implementation operations

This section implements the lifecycle in `AGENTS.md` using the current local harness; it does not redefine its authorization, batching, retry, or acceptance rules.

### Run preparation and records

Keep `implementationHold=true` in the version runtime settings until configuration and required version/run authorization are complete. A ready label alone does not lift the hold.

For each authorized run, record the version/specification baseline, approved ticket set and version-plan reference, dependency DAG, Owner concurrency limit, gate inventory/alarm choice, current batch, resource claims, and links to durable issue/PR records. Store mutable details in `run.json`; record approvals and meaningful transitions in attributed comments linked from participating issues. An Owner limit of `unlimited` means no fixed count cap, not unlimited hardware/model capacity or exemption from exclusive device ownership and batch boundaries.

For each ticket record its Planner-authored plan reference, current code baseline, assigned registry tuples, live routing, branch/worktree/scratch paths, resource reservation, attempt stage/count, candidate/evidence references, and cleanup state. Do not put these changing assignments or test results in `DEV.md`.

### Planner requests and plan delivery

Use the registered v0.0.1 Planner for that version's requested plans. Owner requests version planning; Manager requests each approved ticket's detailed plan before Worker dispatch. Send the issue, exact reference commit, constraints, available environment/evidence, and required reply path. Planner publishes the plan as an attributed issue comment or linked artifact and directly sends its reference to Manager. Verify receipt and current-code/dependency compatibility before assigning a Worker. Existing document ownership and protected-article gates remain unchanged.

### Workspaces and visible launch

All agents run in separate visible windows of the **same tmux session as Manager**, including human waits. Workers use `Worker-<issue>` window labels; Reviewers use `Reviewer-<issue>`. Window labels are for people, not authenticated routing.

Resolve the session from Manager's current pane, create the exact assigned worktree and a unique scratch directory, and keep session/evidence files in a persistent per-ticket runtime directory. Set the child process's `TMPDIR` to its scratch directory. Existing branches/worktrees are inspected and reused only for their assigned mission; do not overwrite another assignment or clear predictable shared temporary paths.

Shell templates below require Manager-resolved, safely quoted values for `issue`, `branch`, `worktree`, `base`, `window`, `provider`, `model`, `name`, and `session_file`. Run TUI launch commands through `interactive_shell` in dispatch mode; the agent itself stays in the created tmux window.

```bash
session=$(tmux display-message -p -t "$TMUX_PANE" '#{session_id}')
scratch=$(mktemp -d "/tmp/eyebrowse-issue-${issue}.XXXXXXXX")
git worktree add -b "$branch" "$worktree" "$base"

tmux new-window -d -P -F '#{pane_id}' -t "$session" -n "$window" -c "$worktree" \
  "exec env TMPDIR='$scratch' pi --provider '$provider' --model '$model' --thinking max --session '$session_file' --name '$name'"
```

Start a new session idle, obtain its real native ID with `/session`, and register it before sending project work. Update the local tuple-to-session/pane routing record; do not invent session IDs or pre-register hypothetical agents. Supply the full mission and confirm its receipt plus actual `PI_SESSION_ID`, `PI_PROVIDER`, `PI_MODEL`, and `PI_REASONING_LEVEL`. A successful tmux-launch helper only confirms window creation, not mission startup or completion.

Resolve current `main` articles separately from the implementation baseline: an older issue branch may contain obsolete governance. Supply canonical read-only article paths/commit references without merging unrelated protected articles into the implementation branch.

### Direct communication and recovery

Match the recipient's active registry tuple to its current live pane before sending. Use captured pane IDs, not window-name parsing: names containing dots can be mistaken for pane selectors. Messages carry exact From/To tuples, a concise purpose/state, and issue/evidence references.

```bash
tmux send-keys -t "$recipient_pane" -l "$message"
tmux send-keys -t "$recipient_pane" Enter
```

Planner plan delivery, Worker/Reviewer receipts, results and blockers use this direct path in addition to required GitHub records. End the dispatch turn after startup is confirmed; do not poll other panes for routine progress. A queued message may wait behind an active long-running tool: if urgent cancellation is required, explicitly stop the owned operation and verify its safe state rather than assuming a queued message was acted on.

Removing an extension file does not unload it from an existing Pi process. Use `/reload` while idle, or resume the same saved session in its owned window when recovery requires a restart. Verify native identity/model, reconcile any interrupted command before retrying, and update routing. Never treat a restart as a fresh attempt budget.

### Sudo pane procedure

For an approved sudo escalation under `AGENTS.md` Section 9.3, open a separate pane in Manager's current window through the interactive execution tool:

```bash
manager_window=$(tmux display-message -p -t "$TMUX_PANE" '#{window_id}')
tmux split-window -h -P -F '#{pane_id}' -t "$manager_window" -c "$worktree"
```

Explain the exact command and purpose, run it in that pane, and let the Owner enter the password directly there. Never request the password in chat or capture it in logs. Record the non-secret result and close only that operation's pane when finished; do not use a different tmux session or close Manager's pane.

### Verification, review, and closeout

Finish implementation and simplification before freezing the candidate used for final evidence. Record source SHA, APK hash, install output, target/software identity, commands, raw results, and limitations together. Keep raw captures immutable; corrections to summaries are explicitly attributed. Historical runs remain tied to their original builds, and partial/timed runs state their actual intervals and interruptions.

Supply the Reviewer the approved ticket/plan, exact base/head, diff and evidence in a separate worktree. Verify the final remote SHA and renewed verdict before a guarded merge; for GitHub's merge API supply the expected `sha`. Use non-closing issue references so merge does not bypass closeout. Debug-only build policy below applies to every role and helper.

Record failure-stage/attempt accounting in the ticket/run record using `AGENTS.md` Section 14. Manager-directed recovery gets explicit source/resource ownership and the same independent-review/evidence workflow; model/session replacements do not reset counts.

After merge, request Worker cleanup. Preserve required APK/log/report provenance outside disposable scratch, approve exact worktree/shared-resource removals, verify the cleanup report and released resources, then retire the Worker/Reviewer and update the registry before closing the issue. Close only their verified tmux windows; keep the version Planner alive until version closeout. Retained artifacts and any cleanup limitation must have explicit locations/state, not an unverified 'done' claim.

## Android toolchain and device workflow

**Build policy:** Use debug builds by default. Do not build release APKs or treat release-build/test success as a hard acceptance gate unless the Project Owner explicitly requests the corresponding release build or gate. This restriction also applies to helper scripts and aggregate tasks that invoke release builds.

Sources: local `/home/code2hack/Projects/Glasseo/DEV.md` (checkout HEAD `5f9d23512359aaaef7d5e1ba203ea7322cae433b`) and fresh read-only host/device inspection. Glasseo architecture, package IDs, toolchain pins, unattended-only policy, concurrency limits, and alarm policy are **not** EyeBrowse policy. Its prior tests are environment references, not EyeBrowse acceptance evidence.

### Verified host inventory

- SDK: `/home/code2hack/Android/Sdk`; platforms 35/36 and Build Tools 35.0.0/36.0.0/36.1.0 are present.
- Java: OpenJDK `17.0.20`; Node: `v24.20.0`.
- ADB: `/home/code2hack/.local/bin/adb`.
- Emulator executable: `/home/code2hack/Android/Sdk/emulator/emulator`; AVD `dealer-api36` has been exercised with the isolated spike. Verify its actual configuration and serial for each mission; it has no secure lockscreen by default in the tested setup.
- Set an explicit SDK environment or an untracked `local.properties` for builds; never commit a machine-specific SDK path.
- The isolated `experiments/locked-webview-spike/` uses its verified Gradle 8.11.1 wrapper, AGP 8.7.3 and JDK 17. From that directory, the debug-only verification commands are:

```bash
ANDROID_HOME=/home/code2hack/Android/Sdk ./gradlew --no-daemon \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

These are experiment commands, not an automatic dependency choice for production v0.0.1. Its approved ticket plans establish the applicable production tasks. Do not run the experiment's release-building `verify-variants.sh` unless explicitly requested by the Owner.

### Real-device ADB connection order (Phone and RG)

Apply this procedure to the mission's reserved physical device. Prefer **USB → local LAN TCP → Tailscale TCP → human gate**. An emulator or another connected device is not a substitute.

1. **USB first.** Check `adb devices -l` for the intended device in authorized `device` state. Verify its identity, then enable legacy TCP ADB on port **5555** through that USB transport. Record its current LAN/Tailscale addresses and verify a TCP connection before a planned USB disconnect. If the listener is already enabled and verified, do not restart it unnecessarily.
2. **Without USB, try LAN, then Tailscale.**
   - **LAN:** discover the device’s current address using bounded local-network discovery. Cached addresses are hints, not authoritative. If an old address fails, continue discovery rather than declaring the device unavailable. Distinguish “address not found” from “device found but no authorized ADB connection.” Try port **5555** and run `adb mdns services` to discover Wireless ADB's current random port. Use the target's `_adb-tls-connect._tcp` endpoint, not its `_adb-tls-pairing._tcp` port. An already trusted host can reconnect without new human pairing; discovery alone does not grant authorization.
   - **Tailscale:** if LAN attempts fail, identify the device with `tailscale status --json`. Try its tailnet address on **5555**, then any known current Wireless ADB connection port. Do not assume LAN mDNS advertisements cross Tailscale.
   - If necessary, use bounded port discovery only against identified target-device addresses on the trusted LAN/tailnet. Use short timeouts and bounded retries. An open port is not success: require authorized ADB `device` state and matching physical-device identity.
3. **Human gate only when no authorized route works.** Inform Manager and ask the Owner to connect USB, or enable the device's Tailscale and Android Wireless debugging where supported. Complete any required pairing/authorization interactively. Respect the Owner's current availability and no-alarm instructions.

After recovering through **any** authorized transport, verify the device identity, enable legacy TCP ADB on **5555**, and verify reconnection. If the device rejects this mode, report the limitation rather than bypassing authentication or using root.

Command templates (`ADB_TARGET` is a verified authorized USB or network transport; `TCP_ENDPOINT` is the selected `HOST:PORT`, either 5555 or a discovered Wireless ADB connection port):

```bash
adb devices -l
adb mdns services
timeout 10s adb connect "$TCP_ENDPOINT"
timeout 10s adb -s "$TCP_ENDPOINT" get-state
adb -s "$TCP_ENDPOINT" shell getprop ro.serialno
adb -s "$TCP_ENDPOINT" shell getprop ro.product.model
# Enable the legacy listener through the verified authorized transport:
adb -s "$ADB_TARGET" tcpip 5555
```

- Use explicit `adb -s` targeting for every device operation. IP addresses can change or be reassigned; verify identity against the assigned device before mutation. Re-enabling TCP can restart `adbd`, so coordinate it with any active test.
- Keep legacy TCP 5555 available as the reconnection path. Do not routinely run `adb usb` during cleanup; disable TCP only when explicitly requested by the Owner. Still stop test apps and restore other temporary test settings.
- Retain ADB host authorization. Legacy TCP ADB is unencrypted on the LAN; use only trusted LANs and authorized tailnet access. Do not disable authentication or expose port 5555 through public forwarding.

### Rokid Glasses: available now

Known RG identity and inspected platform values; recheck them before device-sensitive verification:

| Property | Observed value |
| --- | --- |
| Serial / ADB state | `1906092617103125` / authorized `device` |
| Model | `RG-glasses` |
| Android / API | 12 / 32 |
| ABIs | `arm64-v8a,armeabi-v7a,armeabi` |
| Display | 480×640; physical density 240, override 204 |
| Active WebView | `com.android.webview` `95.0.4638.74` |

Always target the serial explicitly, especially once an emulator is running:

```bash
adb -s 1906092617103125 get-state
adb -s 1906092617103125 shell getprop ro.product.model
adb -s 1906092617103125 shell getprop ro.build.version.sdk
adb -s 1906092617103125 shell dumpsys webviewupdate
```

Reserve RG for one mission at a time before mutating device state. Preflight identity, authorization, app commit/build, foreground state, and mission prerequisites. Do not overwrite or manipulate Glasseo or unrelated installed applications.

Once EyeBrowse has an approved build, package ID, and launch component, use its committed wrapper to build and its exact APK to install (`adb -s SERIAL install -r APK`), launch with `shell am start -n COMPONENT`, and collect bounded logs (`logcat -d -t 500`), package/lifecycle state, and instrumentation results. These are procedural templates, not tested EyeBrowse commands. Do not clear shared logcat or reset device settings without coordinating resource ownership.

Prefer correlated native/WebView logs, test output, `dumpsys`, and input/sensor traces for behavioral claims. Screenshots support layout evidence, not timing, event identity, or physical optical readability. Redact secrets and browsing content. Record device/software identity, commit, command, exit status, and limitations with acceptance evidence.

WebView 95 requires real-RG feature qualification: exercise the APIs EyeBrowse actually uses rather than assuming modern browser compatibility. Glasseo's historical HTTPS asset-origin, encoding, Promise, IndexedDB, secure randomness, WSS, and bridge-origin tests suggest useful probe areas; rerun applicable checks for EyeBrowse. Do not weaken TLS or privileged bridge restrictions to make tests pass.

Synthetic input and emulator runs do not qualify real head motion, peripheral behavior, comfort, or optical readability. Follow EyeBrowse `AGENTS.md` for physical gates and independent acceptance.

### Phone and emulator procedures

The Fold6 target is `SM-F956N` / serial `R3CX70NHTHK`. Reserve only the targets authorized by the mission; a connected RG is not a substitute for a phone test. Identify cover/inner display conditions and current Android/One UI/WebView versions where relevant. Emulator results remain separate from real Fold6 acceptance.

Never request or record the Owner's unlock secret; the Owner operates secure lock/unlock directly. Coordinate physical unplugged-testing steps with the Owner and follow the real-device ADB connection order above. Preserve existing device/AVD state, avoid unrelated apps/data, and restore temporary test settings without disabling the configured TCP reconnection path.

Before emulator use, verify the intended AVD's configuration and reserve it; do not wipe or repurpose the existing `dealer-api36` AVD without approval. Launch long-running emulator processes visibly through the interactive execution tooling. Record the actual emulator serial and always target it explicitly.

### agent-device evaluation

Primary source reviewed: https://github.com/callstack/agent-device (upstream README on `main`; moving documentation, not a pinned installed version).

Upstream documents Android emulator/physical-device automation via ADB and a snapshot helper, with:

- accessibility snapshots, selectors/refs, taps, text input, scrolling, and assertions;
- screenshots/video plus target-dependent logs, traces, and diagnostic evidence;
- replayable `.ad` workflows;
- CLI, MCP, and typed Node.js API entry points;
- worktree-scoped sessions and host-local device claims for concurrent agents.

The CLI requires Node 22.12+ according to upstream; this host's Node meets that requirement. `agent-device` is not installed on the current PATH, and its helper/runtime compatibility has not been qualified. Existing ADB procedures do not require it.

Before adopting it, select and record an exact version, install deliberately, then run `agent-device doctor`, `agent-device help workflow`, and `agent-device capabilities --platform android`. Consult that installed version's help for exact device/session selection. Check `agent-device device status` before acquisition; never release a live mission's claim. Built-in claims supplement Manager ownership and do not protect against unrelated direct ADB commands.

Use fresh accessibility refs after state changes. Validate WebView accessibility coverage rather than assuming snapshots expose all browser content; use screenshots and appropriately scoped WebView diagnostics when needed. RG snapshot/helper compatibility remains unverified. Agent-device automation supplements, rather than replaces, build/unit/instrumentation tests and real-hardware evidence.

## Contacting the Owner

Always send a written gate report. Read the current run's Owner-approved alarm setting before sounding audio; later Owner instructions override it. When that setting is enabled and Owner attention is required, run:

```bash
~/Music/play-super-mario-alarm-hdmi.sh
```

Also send a concise written message explaining the gate, evidence, and required action. The alarm supplements communication; it does not replace it. Workers should contact the Owner directly for physical/interactive gates while informing the Manager.

The discovered executable script defaults to six seconds, reads `~/Music/super-mario-alarm.mp3`, and pipes FFmpeg output into `aplay -D plughw:0,3` (HDMI). Both executables are installed and the script passes `bash -n`. Physical audibility requires Owner confirmation. If playback fails, report the failure and the original gate in writing; do not enter an unbounded retry loop.
