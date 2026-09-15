# Development and agent operations

Operational procedures for EyeBrowse. `AGENTS.md` is the authority for roles, approvals, acceptance, failure accounting, communication semantics and lifecycle. `SPEC.md` defines the product. This document implements those rules; it does not redefine them.

## Workspace, records and consumers

- Repository: `git@github.com:code2hack/EyeBrowse.git`; canonical articles are on remote `main`.
- Manager checkout: `/home/code2hack/Projects/EyeBrowse`.
- Runtime root: `/home/code2hack/.local/state/eyebrowse`.
- Per-version settings: `<runtime-root>/<version>/runtime.json`.
- Per-run record: `<runtime-root>/<version>/runs/<run-id>/run.json`; ticket evidence and assignments remain under that run.
- Private credentials belong in an owner-only secret store, not repository files or runtime/evidence records. Runtime may contain a private reference consumed internally by a trusted helper. Do not read the referenced secret into model context.

Refresh remote refs and identify the actual baseline before planning, dispatch or verification. Supply current canonical articles separately to old implementation/review worktrees; do not merge protected-article changes into a product PR merely to refresh instructions. Keep protected-article candidates outside `main` and use the editor/request/publication gates in `AGENTS.md` §3.

### One authoritative record, explicit consumers

| Information | Home / actual consumer |
| --- | --- |
| Governance and canonical agent-message semantics | `AGENTS.md`; agents and operational procedures follow its current revision |
| Verified operational recipes | This file and the installed skills/scripts; agents load the relevant skill before use |
| Provider catalog and executable model settings | `~/.pi/agent/models.json` and approved runtime configuration; Pi/provider loaders consume their own settings |
| Session identity/lifecycle | `CONTRIBUTORS.md` under `AGENTS.md` §15.1; Manager/Planner resolve it before routing |
| Run approvals, resources, canonical todo metadata and pending requests | Existing run/mission/runtime records; **Manager procedures consume these explicitly** |
| Local todo checklist | Installed `todo` extension's tool-result details in the selected native Pi session branch; its `session_start`/`session_tree` handlers reconstruct the checklist |
| Browser profile and tab ownership | pi-browser-harness's own configuration/session state, consumed by that extension |
| Device-screen policy and credential reference | Runtime `screenAccessPolicy`, read by the configured `pi-phone-use` helper |
| Feishu connection/configuration/routes/outbox | pi-feishu-link's own files, consumed by its daemon; not the project's run records or contributor registry |

General project runtime JSON is **not automatically loaded or executed by Pi**. Naming a field does not implement dispatch, deduplication, approval admission or migration. Record the actual procedure/adapter that reads and writes each field. Keep configuration, declared intent, executed results and unqualified capability distinct.

The Manager is the coordinated writer for shared dispatch, ownership, failure-history and pending-request decisions. Workers, Experts and helpers report changes through the canonical communication path rather than concurrently overwriting those decisions. For a record update, verify the expected prior revision, write a same-filesystem temporary file, validate it, and atomically replace the destination; preserve the preceding evidence/history. Re-read and reconcile after interruption before making another decision. This is a record-update procedure, not a new distributed todo engine or a guarantee about untested adapters.

## Runtime and capability preflight

Use the current Owner-approved profile, model, effort, concurrency and alarm settings from runtime and their decision references. Do not restore obsolete defaults from an old worktree, package configuration or conversation summary. Current local profile assignments require `max`; verify the effective setting rather than accepting silent clamping. An Expert or remote/helper assignment needs its own actual capability/resource check, not a guessed default model.

Model catalogs/authentication live in the configured harness/provider stores. A same-session model change is metadata, not a replacement identity or a fresh attempt budget. Availability fallback is allowed only under the applicable current Owner policy; distinguish it from implementation failure. Never silently substitute a provider, model or lower effort. A low quota warning or required-runtime outage is handled through the existing Owner gate, not provider hopping.

For a local Pi profile, inspect `pi --list-models <model-id>` and non-secret runtime metadata. Where a route/mapping is newly configured, make a bounded no-tool preflight and retain the effective effort field, HTTP/result outcome and limitations—never credentials or private reasoning. In prior checks, Pi display labels and upstream fields differed; record the actual mapping rather than inferring it from a label. Vision capability likewise requires an actual supported input declaration/check.

### Prepared integrations and their limits

Record installed versions/hashes and current results in the run/evidence record rather than copying mutable inventories into every article. The current preparation has established:

- **pi-chatgpt-use / pi-browser-harness:** selected agent profile; project-specific create/rename/archive; exact conversation URL IDs; correlated messages/replies; effort selection/restoration; and a real Manager → registered Planner → Manager request/return/ACK. Delayed/duplicate and restart behavior still need their stated qualification before automated reliance.
- **pi-phone-use:** one real guarded S20 normal PIN-unlock/relock cycle and further bounded setup use. This does not qualify every catastrophic-loss condition or make Wi-Fi continuously reachable.
- **Feishu:** verified private Owner identity/route, daemon replies, and direct installed-SDK text send/readback with Owner receipt. Owner subsequently reported fixing the notification problem. Do not reopen that resolved notification issue merely because its exact setting change was not supplied; do not claim an agent retest or infer unrelated capabilities from it.
- **Todo tool:** actual checklist operations and session-branch storage are available. Shared governance metadata, transition decisions and request routing are not supplied by the checklist tool itself.

Skill-loader success is not end-to-end orchestration acceptance. Keep the qualification status of notifier, inbound Owner control, exact-candidate local verification, Expert handback and restart recovery explicit. An unqualified capability blocks operations that depend on it; it is not permission to invent a replacement framework or resume an Owner-paused ticket.

## Canonical todos and the installed checklist

The installed tool is `todo`, with actions `list`, `add`, `toggle`, `clear`; `/todos` displays the current session branch in the TUI. Its source is currently `~/.pi/agent/extensions/todo.ts`. Inspect the actual installed implementation before depending on changed behavior.

It stores `{ id, text, done }`, the next numeric ID and action/error details in native session tool-result details. IDs and state are local to that session branch; `clear` resets IDs. `toggle` is not idempotent. The tool has no shared assignment, failure-history, candidate, helper, approval or request-correlation fields.

Use it as a **local checklist projection** where suitable. Put a stable canonical todo reference in its text and retain the owning session/item reference in the existing mission record. Reconcile with the canonical record on resume/branch changes. Do not treat a local numeric ID, cleared list, checkbox, renamed item, or old branch snapshot as the objective identity or a new failure allowance. Before repeating an uncertain toggle, list/reconcile state instead of toggling blindly.

For each approved objective, the Manager's existing run/mission record carries or references:

- stable todo ID, parent ticket/plan, objective, completion condition and dependencies;
- current implementer and write owner, helper pairing, reserved resources, and the **return Worker recorded at escalation**;
- round identity, approach, concluded outcome, attributable failure history and candidate/evidence references;
- current execution state and pending handoff/verification obligations;
- request IDs, recipient tuples, send/receipt/reply verification, processed result IDs and any request-bound approval.

Apply `AGENTS.md` §14 to these records. A helper report and review confirming the same round are correlated evidence, not separate failures. Do not spread a ticket-wide verdict across unrelated objectives or reset history through tool operations. Preserve uncertain attribution explicitly. Maintain one canonical record for an objective; the checklist projection is not a competing decision store.

No automatic escalation consumer is assumed. Until a specific adapter is qualified, the Manager reads the canonical history, applies the governance rule, records the decision and dispatches explicitly. Qualify any automation against the same records before enabling it.

## Planning, assignments and visible sessions

Use `AGENTS.md` §§5–6 for version/ticket/todo planning and batch authority. Before dispatch, refresh the DAG, claims, actual prerequisite outcomes, current canonical articles and ticket plan. Resolve stale/contradictory scope with the responsible Planner. Do not backfill a completed slot or treat Expert/helper activity as a new batch contrary to the approved scheduling policy.

Include the ticket/plan/todo, exact code baseline, actual recipient tuple, write ownership, worktree/scratch or verified remote capabilities, resources, current history, evidence location and reporting/Owner-contact paths in the assignment. Account for helper and Expert resource needs within the approved limit; serialize or obtain a necessary exception rather than bypassing the limit by renaming a role.

### Local launch

Keep local agents in separate visible windows in Manager's tmux session. Resolve explicit pane IDs and record them against registry tuples; labels alone are not routing/authentication. Use the installed interactive-shell tool for supervised/dispatch launches, not a hidden nested agent.

Manager-resolved template; quote all assigned values safely:

```bash
session=$(tmux display-message -p -t "$TMUX_PANE" '#{session_id}')
scratch=$(mktemp -d "/tmp/eyebrowse-${mission}.XXXXXXXX")
git worktree add -b "$branch" "$worktree" "$base"
printf -v launch '%q ' env "TMPDIR=$scratch" pi --provider "$provider" \
  --model "$model" --thinking max --session "$session_file" --name "$name"
tmux new-window -d -P -F '#{pane_id}' -t "$session" -n "$window" -c "$worktree" \
  "exec $launch"
```

Start a new session idle, obtain its native ID, and register the actual participant before project work. Supply the complete assignment only after registration and verify receipt plus actual `PI_SESSION_ID`, `PI_PROVIDER`, `PI_MODEL` and `PI_REASONING_LEVEL`. A created window is not startup confirmation. Do not fork/revive/replace sessions or reset work history implicitly.

### Remote implementer and local helper

Use `pi-chatgpt-use` for the intended authenticated project and exact registered conversation. Verify the selected session's actual editing, cloud-test, commit/push and return-message capabilities. Register its observed full conversation URL before assignment; do not infer a new session from a title or send bubble.

When `AGENTS.md` §5.4 requires a local helper, assign/register it explicitly and keep it visible. A suitable existing ticket Worker may take the helper assignment within approved resources. The remote implementer and helper refer to the same todo, round and candidate; supporting checklists do not create independent budgets.

On `CANDIDATE_READY`, verify the remote SHA before local verification. Record exact source/APK/device bindings and executed checks. Route findings to the current implementer. Before any helper-authored correction, explicitly transfer source ownership; never allow two writers on the same implementation assignment.

## Communication and handback

**Use the sole canonical envelope in `AGENTS.md` §15.3.** DEV, skills and adapters implement it by reference, not with an independently maintained normative format. Preserve visible registered identity, authoritative tuples and request/reply correlation through every transport. Check registry status and resolve contradictory identity before acting.

For tmux, send the complete prepared message literally, then submit Enter as a separate delivery action:

```bash
tmux send-keys -t "$recipient_pane" -l "$message"
# Separate submission after the literal text has reached the editor:
tmux send-keys -t "$recipient_pane" Enter
```

For ChatGPT, use the verified skill's DOM/AX path. Check the exact conversation, existing draft and in-flight generation before mutation. Submit once and verify the corresponding new turn. A long response can use bounded read-only observation/event notification; do not repeatedly ask an agent for progress. Preserve pending request and last verified message IDs across restart and reconcile possible success before resending. A local watcher, browser response, or GitHub post is not by itself acceptance of the requested work.

For each handoff, record send, receipt, returned evidence and verification separately. Retry the same logical request with its existing correlation identity. Reject duplicate/stale results as new dispatch, failure, approval or ownership changes. End the Manager dispatch turn after startup/receipt and actionable reports are handled; use meaningful reports/events to resume coordination.

### Expert handoff and Worker return

When the canonical record reaches the condition in `AGENTS.md` §14, resolve a capable Expert, its required helper/resources and the intended return Worker. Record the scoped assignment and source handoff, and notify the Owner using the informational channel. Do not invent an Owner approval gate for an otherwise authorized Expert handoff.

The Manager coordinates product findings; Worker/Expert owns diagnosis and correction. Manager may perform authorized operational setup/recovery, but does not become the product-code or failing-acceptance-test troubleshooter.

Verify the Expert's exact pushed candidate, todo-specific evidence, required local-helper result and continuation notes. Resolve required review findings under `AGENTS.md` §13. Reconcile workspace state without discarding uncommitted work, relinquish Expert write ownership, and explicitly return the later todos to the recorded Worker. Confirm receipt before resuming it. Retire only assignments that actually end; a ticket Worker returning from helper duty is not retired. This handback is not ticket closure or an extra whole-ticket review ceremony.

## Android connection and screen workflow

### Toolchain

Verified host references include SDK `/home/code2hack/Android/Sdk` (platforms 35/36 and corresponding build tools), JDK17, Node24, and approved ADB `/home/code2hack/.local/bin/adb`. Verify the current binaries/profile rather than assuming an old PATH still applies. Emulator executable: `$ANDROID_HOME/emulator/emulator`; the existing `dealer-api36` AVD is not disposable shared state.

Use the assigned project's wrapper and explicit **debug** tasks, with the ticket's verified JDK/Gradle/AGP settings and resource limits. The current implementation procedure uses `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`. Do not invoke aggregate/release helpers or add release acceptance gates without the applicable Owner request. An untracked `local.properties` or explicit SDK environment may configure the local SDK; no machine path or credential goes into product source.

The isolated `experiments/locked-webview-spike` has its own verified Gradle8.11.1/AGP8.7.3/JDK17 debug commands:

```bash
ANDROID_HOME=/home/code2hack/Android/Sdk ./gradlew --no-daemon \
  :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

These are experiment commands, not a replacement for production ticket plans. Do not use that experiment's release-building `verify-variants.sh` unless explicitly authorized. Earlier Glasseo/environment evidence is a reference only, not EyeBrowse acceptance or architecture authority.

### TCP5555 first, physical identity always

For the mission's reserved device, prefer an **already authorized, identity-verified TCP/IP5555** route on the current trusted LAN, then an identified tailnet route where available. Keep USB as fallback/bootstrap. Android's paired Wireless Debugging/random-port mode is a separate recovery option, not proof that the legacy5555 listener is on/off.

1. Inspect `adb devices -l` and current authorized discovery. Cached addresses/MAC-IP bindings are hints; verify the physical serial and model before mutation.
2. Try an identified current5555 endpoint with a bounded connection attempt. Fresh LAN/mDNS/tailnet evidence distinguishes an unknown address, an unreachable host, a closed port and failed authorization. Do not declare physical absence from one stale address or assume mDNS crosses the tailnet.
3. If necessary, use verified USB or an already-authorized Wireless Debugging endpoint. Do not scan broad unrelated ports, change trust or silently pair a new host. Physical pairing/connection remains an Owner gate when required.
4. Inspect the current listener/address after recovery. Only when needed and coordinated, enable5555 through the verified fallback, then verify a real connection. Do not restart a working `adbd`/ADB server merely to switch route, and do not run `adb usb` during routine cleanup.
5. Prefer the verified5555 route again when available. Preserve USB/power where useful; network-changing work must have a verified recovery route before it begins.

Templates, with values resolved for the reserved physical device:

```bash
adb devices -l
adb mdns services
timeout 10s adb connect "$TCP_ENDPOINT"
adb -s "$TCP_ENDPOINT" shell getprop ro.serialno
adb -s "$TCP_ENDPOINT" shell getprop ro.product.model
# Only if the listener actually needs enabling, using an authorized fallback:
adb -s "$ADB_TARGET" tcpip 5555
```

A stable address does not guarantee sleeping Wi-Fi reachability or TCP persistence across a phone reboot. In the verified S20 setup, a router restart required wake-only USB recovery before Wi-Fi/5555 became reachable again; normal screen lock was retained. Do not use an always-on screen, root/persistent-property trick, disabled authentication, or unapproved battery/network changes as a workaround. Legacy TCP ADB is unencrypted; keep it on trusted LAN/tailnet routes without public forwarding.

### Screen access and UI work

Use `pi-phone-use` and the recorded device-specific Owner grant under `AGENTS.md` §9.5. The present helper is S20-specific; do not apply its credential or PIN-entry assumptions to other phones, RG or a desktop. The grant applies to authorized included local agents with necessary reserved work, not just Manager.

Use the skill's **guarded bounded run** for normal PIN unlock/work/immediate relock. The helper consumes `screenAccessPolicy` and reads its private reference internally; do not fetch the secret into a prompt or command line. Verify actual keyguard state and the final-lock result, not merely a dark screen. Its child timeout is not a bound on every connection/unlock/cleanup operation; give the calling tool finite headroom and preserve mission limits.

Keep dependent transient UI operations in one guarded session when they require an open menu/dialog. After relocking, reconstruct the expected UI rather than assuming a popup survived. Use fresh resource IDs/package/bounds, not remembered screen coordinates. Keep raw hierarchy/notification/browser contents out of model/public evidence; retain only task-relevant, redacted derivatives and remove exact owned temporary captures.

If a network-changing input loses its transport, inspect through the verified fallback before any replay. If cleanup cannot verify the lock after interruption/total access loss, report the last state and obtain Owner assistance. Do not intentionally strand an unlocked device to test catastrophic loss; use clearly labeled safe simulations for that case.

### Target-specific verification and cleanup

`SPEC.md`, the approved plan and current Owner acceptance profile identify the required targets/evidence. Label an authorized S20 run as S20 evidence, not Fold6 cover/inner/hinge qualification. Record explicitly permitted unexercised conditions as limitations—not fabricated passes or reinstated hidden gates. Shared-core changes still assess Phone and RG consumers. Record actual Android/One UI/YodaOS/WebView, app commit/build, display conditions and limitations for the evidence being claimed.

Use exact expected test identities and terminal results. Shell/ADB exit0, compiled instrumentation, an incomplete stream, screenshots, or an observed callback are not interchangeable with JUnit/device acceptance. Preserve source/APK/device bindings and distinguish executed results, Owner observations and inference. If a client disconnects, device-side instrumentation may continue; inspect and stop the owned operation safely before cleanup or another attempt. No automatic test/input replay.

Keep logs scoped to the assigned application/process and required milestones. Do not clear shared logcat or capture broad private browsing/notification/UI data. Preserve app/site data and authentication; no uninstall/`pm clear`, OS/WebView upgrade, unrelated package manipulation or device-setting change without the applicable authorization. Preserve retained evidence before removing only explicitly owned fixtures, mappings and temporary files. Verify actual processes/listeners/services and final lock state; do not infer cleanup from a shell's exit alone.

Use the existing approved ADB/harness procedures. `agent-device` was evaluated as an option, not installed/qualified by that evaluation; do not adopt it, a new AVD or another package implicitly.

## Owner contact, alarms and sudo

Use `AGENTS.md` §7 for the distinction between information, action gates and request-bound approval. A human Owner does not need a fabricated agent tuple. The current authenticated Owner channel and permitted destination must be recorded and checked before accepting instructions or applying approvals.

### Feishu: notification is not automatic Manager control

Keep credentials and exact private destination outside repository/public evidence. Reuse the verified Owner route only after matching it to the authorized Owner identity/destination; never fall back to an arbitrary first session/chat or broadcast registered routes.

The prepared direct SDK send/readback and Owner receipt establish that narrow text path. The exposed `feishu_send_local_file` tool is daemon-session-only and is not a generic TUI Owner notifier; direct SDK sends did not traverse its persistent outbox. A reusable notifier must name its real consumer and qualify destination checks, request identity, send/result recording, bounded recovery and uncertain-send reconciliation before unattended reliance. Record API acceptance, stored-message readback and human receipt separately.

Owner reported fixing Feishu notifications. That observation does not qualify voice calling, urgency permissions, the TUI tool, inbound routing, or permanent reconnect behavior. Do not add phone-urgency permission or consume its allowance merely to obtain a notification. Discord is not part of the current adopted route.

Automated project Owner-control admission is **not qualified or activated** by the current preparation. Until it is, use the established authenticated Manager channel for decisions; a Feishu-created model session is not this registered Manager. Before enabling that path, validate sender AND permitted destination, duplicate/stale request handling, approval binding to the actual operation/candidate, routing to the current Manager tuple, and confirmed receipt. An Owner binding is not an access-control allowlist; inspect the actual bridge configuration/consumers rather than assuming it is.

Do not treat a cached `connected` status as live proof. The developer console previously showed a failed persistent connection while local status said connected; one controlled idle-daemon restart recovered it. No permanent reconnect/status fix was established. A narrowly scoped restart requires checking gateway ownership, active sessions and pending outbox, preserving configuration, and verifying recovery; no broad process kills or repeated restarts. The earlier inference from the API callback list to missing message events was retracted—verify actual event configuration before changing it.

### Audible alarm

Read the current run's explicit Owner alarm choice before use; prior-run permission is not reusable. The existing local mechanism is:

```bash
~/Music/play-super-mario-alarm-hdmi.sh
```

The script was checked with `bash -n`; it defaults to six seconds, reads `~/Music/super-mario-alarm.mp3`, and uses FFmpeg plus `aplay -D plughw:0,3`. Physical audibility needs Owner confirmation. Report alarm failure in writing without unbounded retries. Do not sound it for routine informational Expert handoff or use notification receipt as approval.

### Sudo pane

For an approved sudo escalation, use a separate pane in Manager's current tmux window, explain the exact command/purpose, and let Owner enter the password directly. Do not capture or request it in chat/logs.

```bash
manager_window=$(tmux display-message -p -t "$TMUX_PANE" '#{window_id}')
tmux split-window -h -P -F '#{pane_id}' -t "$manager_window" -c "$worktree"
```

Use interactive execution for the prompt. Record only the non-secret outcome and close only that operation's pane afterward. Resource approval does not waive other Owner gates.

## Qualification, document review and cutover

Keep an explicit matrix of verified facts, labeled simulations, proposed procedures and remaining gaps. Do not repeat browser creation/archive, screen unlock, or router setup ceremonially when existing evidence covers the unchanged capability.

Before activating the affected workflow, exercise small disposable setup cases—not product retries or fake production contributors:

- canonical todo identity/history and exactly one escalation at the governance condition;
- duplicate helper/reviewer results, recurring objectives versus distinct later todos, and identity mismatch rejection;
- exact-candidate cloud/local-helper handoff and verified return to the recorded Worker;
- pending reply, Expert assignment, local-verification wait and unacknowledged handback across restart;
- no duplicate dispatch, simultaneous writers, lost counters or stale approval consumption;
- scoped Owner notifier/admission failures and safe screen-cleanup failure reporting.

State the boundary of each check. Fresh-process record reconstruction is not a live multi-agent restart test; a tabletop/data simulation is not an actual Expert/device mission. New tracked harness functionality follows Planner ticketing and independent review; small operational preparation does not authorize a framework rewrite.

Stage legacy-history mapping without activating it. Preserve the exact candidate, current replacement participants, genuine access/security gates and explicit Owner pause. Ambiguous failure attribution stays explicit. Do not revive retired sessions, reassign source writes, clear labels, reset counts or resume an Owner-paused ticket as a side effect of document publication or successful smoke tests.

Present the exact DEV candidate/diff, Owner edit-request reference, changed operational files/consumers, qualification results/gaps and migration/activation preview. After the respective article approvals and applicable activation authorization, use a short scoped dispatch hold, preserve in-flight work, activate qualified procedures, reload only affected idle sessions, verify identities/routes, and deliver current canonical article references. Owner-paused work still needs explicit resumption.

For final ticket review/merge/closeout, use `AGENTS.md` §13. Verify the exact reviewed remote head, checks/device evidence and resolved gates before merge, then verify the remote result. Coordinate cleanup, retained evidence, resource release, retirement and registry updates before issue closure. Todo handback, implementation completion, article publication, runtime activation and release publication remain separate events.
