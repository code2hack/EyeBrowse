# Development and local agent operations

Manager-owned article. Initial preferences recorded with explicit Project Owner approval. Governance remains in `AGENTS.md`; no product or architecture decisions are made here.

## Workspace

- Repository: `git@github.com:code2hack/EyeBrowse.git`
- Local checkout: `/home/code2hack/Projects/EyeBrowse`
- Canonical branch: remote `main`.
- Current repository is a specification/design scaffold. EyeBrowse build/test commands have not yet been established; host SDK and device inventory are verified below.

## Owner-approved agent settings

| Mission role | Provider | Exact model ID | Thinking |
| --- | --- | --- | --- |
| Worker | `deepseek` | `deepseek-v4.1-flash-expires-on-0910` | Highest available thinking effort for the selected model |
| Worker availability fallback | `spark` | `qwen3.8-flash-next` | Highest available thinking effort for the selected model |
| Reviewer | `openai-codex` | `gpt-6-astra` | `medium`; `max` for hard bugs |

For every Worker launch or model change, resolve the highest available effort from the selected model's current supported levels and provider mapping; do not hard-code `high`. Skip levels marked unsupported (`null`) and account for aliases: Pi's label is not necessarily the upstream effort name. Verify the effective runtime level and report clamping, rejection, or uncertain upstream support rather than silently accepting a downgrade. Resolve the fallback model independently.

Current local mappings expose Pi `max` for both models: DeepSeek maps it to upstream `max`, and Spark/Qwen maps it to upstream `xhigh`. These mappings were validated locally; the temporary DeepSeek V4.1 model's upstream Max support and the Spark endpoint's handling remain unverified. Issue #1's initial Owner-approved `high` run is historical evidence, not the default for future Workers.

Use the Spark fallback only when the requested DeepSeek model is unavailable, not to conceal reasoning or implementation failures. Record and report fallback use. Do not silently substitute other providers, models, or thinking levels.

On any low weekly quota/limit warning or quota wall, notify the Owner immediately and await direction before continuing affected work or changing providers to evade that limit. No numeric warning threshold has been specified.

Configuration is local to `~/.pi/agent/models.json`. Never copy credentials into repository files, prompts, or logs.

### Verified facts and unresolved checks

- The DeepSeek and Spark model IDs above are present in local `models.json`, with reasoning enabled.
- Installed Pi documents `--provider`, `--model`, and `--thinking`, including `max` and `medium`.
- `pi --list-models astra` lists `openai-codex/gpt-6-astra`. The Owner explicitly approved `openai-codex` as the Reviewer provider, resolving the initial provider discrepancy.
- Issue #1 launch verification: Pi 0.85.1 started `LockProbe-Worker` in tmux `work:Worker-1` (pane `%6`) with `--thinking max`, but the footer reports effective `high`. Pi's supported-level resolver requires an explicit model mapping for `max`/`xhigh` and otherwise clamps to a supported level. The Owner subsequently approved `high` for that initial run, resolving its launch gate. The later highest-available-effort policy above supersedes a fixed `high` default. This launch observation verifies the effective local setting, not the upstream model's maximum effort.

## Visibility and isolation

All subagents must run interactively in separate named windows of the **same tmux session as the Manager**. Do not replace these windows with hidden/headless workers or a different tmux session. Leave agents directly reachable by the Owner, including when waiting at human gates.

At initial inspection the session was `work` (ID `$0`) and Manager pane was `%0`. These identifiers are observations, not permanent constants. Resolve the Manager session from its current pane when dispatching:

```bash
tmux display-message -p -t "$TMUX_PANE" '#{session_id}'
```

Before launch, define the complete mission contract required by `AGENTS.md`, including branch/worktree, platform scope, evidence, resource limits, and direct reporting paths. Use isolated worktrees for concurrent writing missions. Name windows by role and mission, e.g. `Worker-42` and `Reviewer-42`.

Pi argument templates (not yet end-to-end launch-tested):

```bash
# Resolve these separately from current model capabilities before launching.
# Current mappings select max for both; these are not permanent model-independent defaults.
pi --provider deepseek --model deepseek-v4.1-flash-expires-on-0910 --thinking "${DEEPSEEK_THINKING:?Resolve highest available effort first}" --name Worker-42 @/absolute/path/to/mission.md
pi --provider spark --model qwen3.8-flash-next --thinking "${SPARK_THINKING:?Resolve highest available effort first}" --name Worker-42 @/absolute/path/to/mission.md
pi --provider openai-codex --model gpt-6-astra --thinking medium --name Reviewer-42 @/absolute/path/to/review-mission.md
```

For hard-bug review use `--thinking max`. Start each CLI in its assigned worktree and separate tmux window using the interactive launch tool. Verify the actual selected model/thinking and mission receipt before ending the dispatch turn. Establish proactive completion/blocker reporting before dispatch; routine progress polling is not the workflow. Exact launch and notification transport integration remains to be verified before the first mission.

## Android toolchain and device workflow

Sources: local `/home/code2hack/Projects/Glasseo/DEV.md` (checkout HEAD `5f9d23512359aaaef7d5e1ba203ea7322cae433b`) and fresh read-only host/device inspection. Glasseo architecture, package IDs, toolchain pins, unattended-only policy, concurrency limits, and alarm policy are **not** EyeBrowse policy. Its prior tests are environment references, not EyeBrowse acceptance evidence.

### Verified host inventory

- SDK: `/home/code2hack/Android/Sdk`; platforms 35/36 and Build Tools 35.0.0/36.0.0/36.1.0 are present.
- Java: OpenJDK `17.0.20`; Node: `v24.20.0`.
- ADB: `/home/code2hack/.local/bin/adb`.
- Emulator executable: `/home/code2hack/Android/Sdk/emulator/emulator`; `-list-avds` reports `dealer-api36`. An Android 36 system-image directory exists. Emulator boot and app automation have not been tested.
- `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `JAVA_HOME` are unset in the inspected shell. Use an explicit SDK environment or an untracked `local.properties` when establishing builds; never commit a machine-specific SDK path.
- Glasseo reports successful Gradle wrapper 9.1.0 / AGP 9.0.1 / JDK 17 builds with built-in Kotlin. This is a reusable compatibility reference, not an approved EyeBrowse dependency selection. No EyeBrowse wrapper or build tasks exist yet.

### Real-device ADB connection order (Phone and RG)

Apply this procedure to the mission's reserved physical device. Prefer **USB → local LAN TCP → Tailscale TCP → human gate**. An emulator or another connected device is not a substitute.

1. **USB first.** Check `adb devices -l` for the intended device in authorized `device` state. Verify its identity, then enable legacy TCP ADB on port **5555** through that USB transport. Record its current LAN/Tailscale addresses and verify a TCP connection before a planned USB disconnect. If the listener is already enabled and verified, do not restart it unnecessarily.
2. **Without USB, try LAN, then Tailscale.**
   - **LAN:** locate the intended device using known addresses, `ip neigh`, router/DHCP records, or bounded local discovery. Try port **5555** and run `adb mdns services` to discover Wireless ADB's current random port. Use the target's `_adb-tls-connect._tcp` endpoint, not its `_adb-tls-pairing._tcp` port. An already trusted host can reconnect without new human pairing; discovery alone does not grant authorization.
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

Fresh ADB inspection confirms:

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

### Phone: emulator and Fold6 authorized for issue #1

The Owner explicitly authorized validation of issue #1 on both the installed Android emulator and the connected real Fold6. Fresh ADB inventory identifies the authorized phone as `SM_F956N`, serial `R3CX70NHTHK`. Reserve both targets for the assigned validation Worker; use explicit serials in every ADB command and leave RG (`1906092617103125`) untouched for this phone-only mission.

This authorization covers the isolated locked-WebView spike, not unrelated phone apps, data, or production architecture changes. Never request or record the Owner's unlock secret; the Owner operates secure lock/unlock directly. Coordinate physical unplugged-testing steps with the Owner and follow the real-device ADB connection order above. Preserve existing device/AVD state; restore temporary test settings without disabling the configured TCP reconnection path. Emulator results remain separate from real Fold6 acceptance.

Before emulator use, verify the intended AVD's configuration and reserve it; do not wipe or repurpose the existing `dealer-api36` AVD without approval. Launch long-running emulator processes visibly through the interactive execution tooling. Record the actual emulator serial and always target it explicitly.

### agent-device evaluation

Primary source reviewed: https://github.com/callstack/agent-device (upstream README on `main`; moving documentation, not a pinned installed version).

Upstream documents Android emulator/physical-device automation via ADB and a snapshot helper, with:

- accessibility snapshots, selectors/refs, taps, text input, scrolling, and assertions;
- screenshots/video plus target-dependent logs, traces, and diagnostic evidence;
- replayable `.ad` workflows;
- CLI, MCP, and typed Node.js API entry points;
- worktree-scoped sessions and host-local device claims for concurrent agents.

The CLI requires Node 22.12+ according to upstream; this host's Node meets that requirement. `agent-device` was not found on the current PATH. No installation, helper deployment, emulator boot, or live compatibility test has been performed.

Before adopting it, select and record an exact version, install deliberately, then run `agent-device doctor`, `agent-device help workflow`, and `agent-device capabilities --platform android`. Consult that installed version's help for exact device/session selection. Check `agent-device device status` before acquisition; never release a live mission's claim. Built-in claims supplement Manager ownership and do not protect against unrelated direct ADB commands.

Use fresh accessibility refs after state changes. Validate WebView accessibility coverage rather than assuming snapshots expose all browser content; use screenshots and appropriately scoped WebView diagnostics when needed. RG snapshot/helper compatibility remains unverified. Agent-device automation supplements, rather than replaces, build/unit/instrumentation tests and real-hardware evidence.

## Contacting the Owner

The Owner explicitly re-enabled the music alarm when authorizing issue #1 validation. Whenever Owner attention is required, including physical gates and quota warnings, run:

```bash
~/Music/play-super-mario-alarm-hdmi.sh
```

Also send a concise written message explaining the gate, evidence, and required action. The alarm supplements communication; it does not replace it. Workers should contact the Owner directly for physical/interactive gates while informing the Manager.

The discovered executable script defaults to six seconds, reads `~/Music/super-mario-alarm.mp3`, and pipes FFmpeg output into `aplay -D plughw:0,3` (HDMI). Both executables are installed and the script passes `bash -n`. Physical audibility requires Owner confirmation. If playback fails, report the failure and the original gate in writing; do not enter an unbounded retry loop.
