# Development and agent operations

Operational procedures for EyeBrowse. `AGENTS.md` is the authority for roles, approvals, acceptance, failure accounting, communication semantics and lifecycle. `SPEC.md` defines the product. This document implements those rules; it does not redefine them.

## Workspace, records and consumers

- Repository: `git@github.com:code2hack/EyeBrowse.git`; canonical articles are on remote `main`.
- Paseo project: **EyeBrowse**. Manager checkout: `/home/code2hack/Projects/EyeBrowse`.
- Local Workers, Experts and helpers use their exact assigned isolated worktree cwds within that Paseo project. A common project does not mean a shared `main` cwd. Keep each mission's branch, source checkout and unique scratch directory explicit.
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
| Participant identity/lifecycle | `CONTRIBUTORS.md` under `AGENTS.md` §15.1; retain native session identity and resolve the actual registration before routing |
| Local process, project/workspace and archive state | Paseo daemon and its registry; inspect the actual agent rather than inferring state from its title |
| Native Pi session/history | Pi session storage; Paseo owns that session's one local RPC process/controller |
| Paseo transport binding | Existing runtime records map the native tuple to the verified Paseo host/server, agent ID, project/workspace, cwd and model/effort; Manager procedures resolve the binding before operations |
| Run approvals, resources, canonical todo metadata and pending requests | Existing run/mission/runtime records; **Manager procedures consume these explicitly** |
| Message dispatch evidence | The sender's retained intent/body/message ID and Paseo's request receipt; Manager separately verifies recipient acknowledgment, complete report and acceptance |
| Local todo checklist | Installed `todo` extension's tool-result details in the selected native Pi session branch; its `session_start`/`session_tree` handlers reconstruct the checklist |
| Browser profile and tab ownership | pi-browser-harness's own configuration/session state, consumed by that extension |
| Device-screen policy and credential reference | Runtime `screenAccessPolicy`, read by the configured `pi-phone-use` helper |
| Feishu connection/configuration/routes/outbox | pi-feishu-link's own files, consumed by its daemon; not the project's run records or contributor registry |

General project runtime JSON is **not automatically loaded or executed by Pi**. Naming a field does not implement dispatch, deduplication, approval admission or migration. Record the actual procedure/adapter that reads and writes each field. Keep configuration, declared intent, executed results and unqualified capability distinct.

The Manager is the coordinated writer for shared dispatch, ownership, failure-history and pending-request decisions. Workers, Experts and helpers report changes through the canonical communication path rather than concurrently overwriting those decisions. For a record update, verify the expected prior revision, write a same-filesystem temporary file, validate it, and atomically replace the destination; preserve the preceding evidence/history. Re-read and reconcile after interruption before making another decision. This is a record-update procedure, not a new distributed todo engine or a guarantee about untested adapters.

## Runtime and capability preflight

Use the current Owner-approved profile, model, effort, concurrency and alarm settings from runtime and their decision references. Do not restore obsolete defaults from an old worktree, package configuration or conversation summary, or hard-code one effort level for every role. Verify the effective provider/model/effort after create, import or restart; a sampled session-file preview can omit a later setting change. Correct a mismatch before project work and retain the evidence. An Expert or remote/helper assignment needs its own actual capability/resource check, not a guessed default model.

Agent active-work/wall-clock ceilings and clock-based work checkpoints are not applied under the current Owner policy. This does not remove scoped objectives, failure accounting, concurrency/compute limits, finite individual command/transport/test safety timeouts, or product timing requirements. A client wait timeout is not an agent failure or permission to repeat a possibly accepted operation.

Model catalogs/authentication live in the configured harness/provider stores. A same-session model change is metadata, not a replacement identity or a fresh attempt budget. Availability fallback is allowed only under the applicable current Owner policy; distinguish it from implementation failure. Never silently substitute a provider, model or lower effort. A low quota warning or required-runtime outage is handled through the existing Owner gate, not provider hopping.

For a local Pi profile, inspect `pi --list-models <model-id>` and non-secret runtime metadata. Where a route/mapping is newly configured, make a bounded no-tool preflight and retain the effective effort field, HTTP/result outcome and limitations—never credentials or private reasoning. In prior checks, Pi display labels and upstream fields differed; record the actual mapping rather than inferring it from a label. Vision capability likewise requires an actual supported input declaration/check.

### Prepared integrations and their limits

Record installed versions/hashes and current results in the run/evidence record rather than copying mutable inventories into every article. The current preparation has established:

- **pi-chatgpt-use / pi-browser-harness:** selected agent profile; project-specific create/rename/archive; exact conversation URL IDs; correlated messages/replies; effort selection/restoration; and a real Manager → registered Planner → Manager request/return/ACK. Delayed/duplicate and restart behavior still need their stated qualification before automated reliance.
- **pi-phone-use:** one real guarded S20 normal PIN-unlock/relock cycle and further bounded setup use. This does not qualify every catastrophic-loss condition or make Wi-Fi continuously reachable.
- **Feishu:** verified private Owner identity/route, daemon replies, and direct installed-SDK text send/readback with Owner receipt. Owner subsequently reported fixing the notification problem. Do not reopen that resolved notification issue merely because its exact setting change was not supplied; do not claim an agent retest or infer unrelated capabilities from it.
- **Todo tool:** actual checklist operations and session-branch storage are available. Shared governance metadata, transition decisions and request routing are not supplied by the checklist tool itself.
- **Paseo/Pi:** a disposable local Pi agent was newly created through the supported creation API without an initial prompt, registered before its correlated assignment, verified in its isolated cwd, and safely archived after its probe; runtime cessation and retained history were checked. Separate import/archive-restore and basic Manager↔Worker SDK steering evidence also exists. These results cover the exercised paths, not every provider, creation retry, migration, busy/permission/archive race, restart, parent-retirement or RPC UI path. Record the actual installed versions, invocation and case evidence in the run record.

Use the existing Paseo control plane and qualified SDK procedure for local pi-agent messages. Do not install another messenger/task engine, enable its autonomous workers, or expose additional lifecycle tools merely because they are available. Installed source code is not proof that a tool is injected into the current agent. Any proposed alternative returns through the applicable operational/implementation authority and qualification gates.

Skill-loader success is not end-to-end orchestration acceptance. Keep the qualification status of notifier, inbound Owner control, exact-candidate local verification, Expert handback and restart recovery explicit. An unqualified capability blocks operations that depend on it; it is not permission to invent a replacement framework or resume an Owner-paused ticket.

## Canonical todos and the installed checklist

The installed tool is `todo`, with actions `list`, `add`, `toggle`, `clear`. Use its tool interface in Paseo-backed Pi sessions; `/todos` is a TUI display, not the required management surface or a shared scheduler. Its source is currently `~/.pi/agent/extensions/todo.ts`. Inspect the actual installed implementation before depending on changed behavior.

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

### Local create/import in Paseo

Local project-agent instances live in Paseo, not tmux, standalone Pi TUIs, or hidden nested agent processes. Paseo is the sole controller of their Pi RPC sessions. Ordinary non-agent shell commands may still run as tools; they do not authorize launching an unmanaged agent.

Before create/import, the Manager:

1. Resolves the authorized role/profile, current registry/assignment, exact baseline, isolated branch/worktree and unique mission scratch path. Inspect the actual EyeBrowse Paseo project ID; do not trust a display name alone or create a new project merely because a worktree has a different basename.
2. Creates/selects an EyeBrowse workspace bound to that exact worktree cwd. Preserve the source checkout; do not move Worker operations into Manager's `main` directory for visual grouping. Paseo's explicit import workspace must match the import cwd.
3. For an existing native session, confirms a safe stopped former process and preserves its file/history and pending requests before import. Import resumes a process; it does not attach to an already-running TUI. Never import the same native session concurrently or blindly repeat an import with uncertain outcome.
4. Uses the installed, qualified Paseo create/import API with explicit project/workspace placement. For Pi import, its provider handle is the verified native session-file path, not a guessed UUID/path. Where the CLI cannot express the required placement, use the existing SDK rather than relabeling an incorrectly placed agent.
5. Obtains and checks the actual Paseo ID and native session ID, provider/model/effort, cwd/branch and lifecycle. Register a genuinely new participant before project work; a non-mutating setup exchange may establish otherwise unavailable identity. Reusing a native session does not create a fresh failure allowance. Record the binding in runtime and verify it after every change.
6. Sends the complete assignment only after registration/readiness and verifies a correlated startup receipt. A visible Paseo tab, API acceptance, imported transcript or `idle` flag alone is not startup/capability confirmation.

For a **new** local Pi agent, use the qualified supported creation API, not an import as a substitute. Persist the exact options and a fresh logical creation idempotency key before calling. In the inspected API, keyed creation requires sending the initial prompt separately; omit `initialPrompt`:

```js
const created = await client.createAgent({
  config: {
    provider: 'pi',
    cwd: assignedWorktree,
    title: assignedName,
    model: ownerSelectedModel,
    thinkingOptionId: ownerSelectedEffort,
  },
  workspaceId: verifiedWorkspaceId,
  callerAgentId: verifiedManagerPaseoId,
  env: { TMPDIR: assignedScratch },
  idempotencyKey: createIntent.idempotencyKey,
});
```

Inspect the returned actual native/Paseo identities and configuration, register the participant, then send the separate correlated assignment using the delivery procedure below. A native session file may materialize only on that first message; an API-supplied path is not an already-verified file header. Verify the header when it exists. Preserve creation options/results and distinguish a creation receipt from an assignment receipt and finished work. On an uncertain creation outcome, reconcile the same key/result rather than issuing another creation with a new key. The disposable check established one successful creation/registration/probe/archive path, not duplicate/crash-recovery behavior or every model/provider.

Reserve import for an explicitly authorized **existing-session migration**. The inspected SDK import shape is:

```js
// Existing verified Paseo connection; native file, workspace and lifecycle checked.
await client.importAgent({
  provider: 'pi',
  sessionId: nativeSessionFile,
  cwd: assignedWorktree,
  workspaceId: verifiedWorkspaceId,
});
```

Do not run this example against a live or retired session without the corresponding authorized migration/reactivation. Snapshot/reconcile unexpected existing imports rather than resetting/discarding state. Keep selected command paths, installed SDK/CLI versions and actual executable evidence in the operational record, not credentials in the document.

### Remote implementer and local helper

Use `pi-chatgpt-use` for the intended authenticated project and exact registered conversation. Verify the selected session's actual editing, cloud-test, commit/push and return-message capabilities. Register its observed full conversation URL before assignment; do not infer a new session from a title or send bubble.

When `AGENTS.md` §5.4 requires a local helper, assign/register it explicitly and keep it visible. A suitable existing ticket Worker may take the helper assignment within approved resources. The remote implementer and helper refer to the same todo, round and candidate; supporting checklists do not create independent budgets.

On `CANDIDATE_READY`, verify the remote SHA before local verification. Record exact source/APK/device bindings and executed checks. Route findings to the current implementer. Before any helper-authored correction, explicitly transfer source ownership; never allow two writers on the same implementation assignment.

## Communication and handback

**Use the sole canonical envelope in `AGENTS.md` §15.3.** DEV, skills and adapters implement it by reference, not with an independently maintained normative format. Preserve visible registered identity, authoritative tuples and request/reply correlation through every transport. Check registry status and resolve contradictory identity before acting.

### Local pi-agent delivery

Use Paseo's existing SDK, not `tmux send-keys` or a second direct Pi RPC controller. Resolve the installed connector/API from the qualified installation recorded in runtime; version changes require affected requalification. The following is an API pattern, not a claim that a routing/outbox service exists automatically:

```js
// Existing verified Paseo connection, after the checks below and intent save.
await client.sendAgentMessage(recipient.paseoAgentId, canonicalEnvelope, {
  messageId: intent.messageId,
  activeTurnBehavior: 'steer',
});
```

For each logical send:

1. Resolve the current registered role/native tuple and its exact Paseo host/server/agent binding, assignment and candidate. Inspect actual provider/native persistence identity and project/workspace/cwd. Stop on disagreement; names, prefixes or labels are not substitutes.
2. Check canonical lifecycle/assignment and actual Paseo archive/permission state. Do not send to retiring, retired, archived or otherwise ineligible actors as a way to wake them. Explicit reactivation is a separate assignment. Serialize send/retirement changes and reconcile manual changes. Defer routine delivery across pending Owner permission/authentication UI rather than clearing or denying it incidentally.
3. Persist the canonical Request-ID, recipient, exact message body and stable transport `messageId` before submission. Keep that same ID/body if reconciling the same logical send. Record API acceptance separately from a recipient acknowledgment, final report and acceptance.
4. Use explicit steering only on the qualified Pi/provider path. It enters at a supported boundary, not necessarily after the entire task settles. Non-steerable/slash-command inputs can fall back to interruption; ordinary peer messages are envelopes, not slash commands. If safe busy delivery is not established, defer it to a verified settled state/event. Cancellation/replacement is an explicit separate operation, not the default for a routine report.
5. On rejection, connection loss or unknown outcome, inspect the correlated receipt/history before another action. The SDK request journal can deduplicate an identical completed send, reject a conflicting body, or report an unknown crash-window outcome. Unknown is not permission to mint a new ID or replay the request.
6. The recipient proactively sends a correlated result through the same checked route. Do not synchronously wait on each other in a cycle. Use event-driven result handling; retain pending state across reconnect/restart and inspect only as required for recovery or an Owner status request.

In the inspected Paseo implementation, plain CLI `send --no-wait` changes waiting, **not** the default interrupt policy; it does not expose the stable message ID used above. Sending can also unarchive an agent and clear pending permissions. The public send path has no atomic fail-if-archived guarantee. These are reasons for explicit lifecycle coordination and receiver-side assignment checks, not claims of a security boundary against another process sharing the host account.

Built-in `send_agent_prompt` and finish notifications may be used only if actually injected and qualified with the required semantics. Their defaults must not be assumed equivalent to this SDK recipe. A finish notice may be truncated and its subscription may not survive restart. Obtain the full correlated result when needed; completion notices, RPC `agent_end`, and client wait timeouts are not proof of settled work or product success. Pi's `agent_settled` distinguishes final settling from automatic continuations.

### Remote conversations and handoffs

For ChatGPT, use the verified skill's DOM/AX path. Check the exact conversation, existing draft and in-flight generation before mutation. Submit once and verify the corresponding new turn. A long response can use bounded read-only observation/event notification; do not repeatedly ask an agent for progress. Preserve pending request and last verified message IDs across restart and reconcile possible success before resending. A local watcher, browser response, or GitHub post is not by itself acceptance of the requested work.

For each handoff, record send, receipt, returned evidence and verification separately. When a retry is authorized after reconciliation, retain the logical request's existing correlation identity and unchanged body. Include the applicable assignment revision and reject stale revisions at the receiver. Reject duplicate/stale results as new dispatch, failure, approval or ownership changes. End the Manager dispatch turn after startup/receipt and actionable reports are handled; use meaningful reports/events to resume coordination.

### Expert handoff and Worker return

When the canonical record reaches the condition in `AGENTS.md` §14, resolve a capable Expert, its required helper/resources and the intended return Worker. Record the scoped assignment and source handoff, and notify the Owner using the informational channel. Do not invent an Owner approval gate for an otherwise authorized Expert handoff.

The Manager coordinates product findings; Worker/Expert owns diagnosis and correction. Manager may perform authorized operational setup/recovery, but does not become the product-code or failing-acceptance-test troubleshooter.

Verify the Expert's exact pushed candidate, todo-specific evidence, required local-helper result and continuation notes. Resolve required review findings under `AGENTS.md` §13. Reconcile workspace state without discarding uncommitted work, relinquish Expert write ownership, and explicitly return the later todos to the recorded Worker. Confirm receipt before resuming it. Retire only assignments that actually end; a ticket Worker returning from helper duty is not retired. This handback is not ticket closure or an extra whole-ticket review ceremony.

### Restart, migration and retirement

Keep exactly one live local process/controller per native session and one active project-wide Manager. A stopped process, an imported session, its Paseo transport ID and a completed continuity check are separate facts. Preserve the actual session, candidate, source ownership, pending request/reply IDs and recovery evidence; do not infer uninterrupted execution from an imported transcript. Verify model/effort and tool availability after startup, including RPC-specific UI limitations. Do not revive old TUI routes as an implicit fallback.

Before Manager restart/handoff/archive, reconcile active child assignments and remote conversations. Browser-harness shutdown can close its owned tabs; preserve exact ChatGPT URLs and in-flight request state, and use an authorized, qualified ownership handoff or settled/recoverable continuation before closing them. Reopen the same conversation when needed, not a replacement conversation. Never acquire unrelated tabs, profiles or credentials by bypassing ownership checks. A prior one-off browser preservation exercise is not a universal restart guarantee.

Retirement is completed through these coordinated steps:

1. Block new dispatch to the retiring actor in the canonical runtime record; settle or explicitly cancel its assigned work safely.
2. Preserve final source/evidence and verify required cleanup, resources and device lock state. An idle UI does not prove that owned background/device operations ended.
3. Inspect Paseo parent/child relationships. Detach or explicitly transfer still-assigned children before archiving a parent/Manager; verify their continued assignments/operation and the successor's receipt/bindings when handing over a Manager. Keep one active coordinator. Runtime archival can cascade; UI parentage is not authority to cancel another assignment.
4. Archive the **exact Paseo agent**, through the qualified agent-archive operation, and verify its archived state and runtime cessation. Do not substitute agent deletion, workspace/project archival, or Git worktree removal.
5. Complete contributor retirement and closeout records while retaining native identity, transport/history references and required artifacts. An archive failure leaves retirement/cleanup unresolved. A paused/HOLD actor is not automatically retired.

Archive is not acceptance, a counter reset, a source handback or permission to delete data. Do not send a routine message to an archived actor: reactivation requires its own explicit authority/assignment. Historical retired sessions are not automatically relaunched/imported for cosmetic completeness. Legacy unmanaged instances require an explicit safe migration/retirement disposition, not continued project work in tmux. Empty old Paseo groups may reference a still-used worktree; do not destructively clean them up based only on an empty agent list.

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

Use `AGENTS.md` §7 for the distinction between information, action gates and request-bound approval. A human Owner does not need a fabricated agent tuple. Paseo is the local agent visibility/control surface; the current authenticated Owner channel and permitted destination must still be recorded and checked before accepting instructions or applying approvals. A peer envelope, Paseo finish notification, label or API delivery receipt is not Owner authorization. Preserve the human-origin/operation binding; do not claim that shared-account shell/API access provides independent authentication.

### Feishu: notification is not automatic Manager control

Keep credentials and exact private destination outside repository/public evidence. Reuse the verified Owner route only after matching it to the authorized Owner identity/destination; never fall back to an arbitrary first session/chat or broadcast registered routes.

The prepared direct SDK send/readback and Owner receipt establish that narrow text path. The exposed `feishu_send_local_file` tool is daemon-session-only and is not qualified as a generic Owner notifier for Paseo-backed Pi sessions; direct SDK sends did not traverse its persistent outbox. A reusable notifier must name its real consumer and qualify destination checks, request identity, send/result recording, bounded recovery and uncertain-send reconciliation before unattended reliance. Record API acceptance, stored-message readback and human receipt separately.

Owner reported fixing Feishu notifications. That observation does not qualify voice calling, urgency permissions, delivery from every agent surface, inbound routing, or permanent reconnect behavior. Do not add phone-urgency permission or consume its allowance merely to obtain a notification. Discord is not part of the current adopted route.

Automated project Owner-control admission is **not qualified or activated** by the current preparation. Until it is, use the established authenticated Manager channel for decisions; a Feishu-created model session is not this registered Manager. Before enabling that path, validate sender AND permitted destination, duplicate/stale request handling, approval binding to the actual operation/candidate, routing to the current Manager tuple, and confirmed receipt. An Owner binding is not an access-control allowlist; inspect the actual bridge configuration/consumers rather than assuming it is.

Do not treat a cached `connected` status as live proof. The developer console previously showed a failed persistent connection while local status said connected; one controlled idle-daemon restart recovered it. No permanent reconnect/status fix was established. A narrowly scoped restart requires checking gateway ownership, active sessions and pending outbox, preserving configuration, and verifying recovery; no broad process kills or repeated restarts. The earlier inference from the API callback list to missing message events was retracted—verify actual event configuration before changing it.

### Audible alarm

Read the current run's explicit Owner alarm choice before use; prior-run permission is not reusable. The existing local mechanism is:

```bash
~/Music/play-super-mario-alarm-hdmi.sh
```

The script was checked with `bash -n`; it defaults to six seconds, reads `~/Music/super-mario-alarm.mp3`, and uses FFmpeg plus `aplay -D plughw:0,3`. Physical audibility needs Owner confirmation. Report alarm failure in writing without unbounded retries. Do not sound it for routine informational Expert handoff or use notification receipt as approval.

### Owner-controlled privileged terminal

For an approved sudo escalation, explain the exact command and purpose and present a dedicated Owner-controlled interactive terminal. A Paseo terminal may be used only after its password-echo, input/output logging and Owner-access behavior are qualified; a terminal creation success alone is insufficient. If that path is unavailable, ask the Owner to perform the specific command directly in a trusted host terminal, or remain at the gate. This human terminal is not an alternative residence for project agents.

The Owner enters any password directly. Never request, type, capture, retain or relay it through agent messages, `send_terminal_keys`, tool arguments, screenshots or logs. Qualify with non-secret simulated input before relying on a new terminal adapter; do not use a real credential as a probe. Record only the non-secret outcome, verify the resulting state, and close only the operation-owned terminal. Resource approval does not waive other Owner gates.

## Qualification, document review and cutover

Keep an explicit matrix of verified facts, labeled simulations, proposed procedures and remaining gaps. Do not repeat browser creation/archive, screen unlock, or router setup ceremonially when existing evidence covers the unchanged capability.

Before activating the affected workflow, exercise small disposable setup cases—not product retries or fake production contributors:

- canonical todo identity/history and exactly one escalation at the governance condition;
- duplicate helper/reviewer results, recurring objectives versus distinct later todos, and identity mismatch rejection;
- exact-candidate cloud/local-helper handoff and verified return to the recorded Worker;
- pending reply, Expert assignment, local-verification wait and unacknowledged handback across restart;
- no duplicate dispatch, simultaneous writers, lost counters or stale approval consumption;
- scoped Owner notifier/admission failures and safe screen-cleanup failure reporting;
- correct EyeBrowse project/workspace/worktree binding on create/import, with no accidental `main` writes or duplicate native-session process;
- actual busy-receiver steering versus interruption/fallback, pending permission UI preservation, stable-ID deduplication/conflict/unknown-outcome recovery, and full-result correlation;
- retired/archived recipient rejection, safe parent/child retirement, verified archival, and non-secret Owner-terminal privacy checks;
- Paseo/SDK/Pi reconnect and Manager handoff with pending results, native identity/model preservation and remote-browser ownership/continuity.

These setup cases do not create an aggregate agent work-time deadline. Keep individual commands and recovery operations finite and scoped. An unavailable affected capability remains explicitly gated, not falsely marked qualified by documentation or API acceptance.

State the boundary of each check. Fresh-process record reconstruction is not a live multi-agent restart test; a tabletop/data simulation is not an actual Expert/device mission. New tracked harness functionality follows Planner ticketing and independent review; small operational preparation does not authorize a framework rewrite.

Stage legacy-history mapping without activating it. Preserve the exact candidate, current replacement participants, genuine access/security gates and explicit Owner pause. Ambiguous failure attribution stays explicit. Do not revive retired sessions, reassign source writes, clear labels, reset counts or resume an Owner-paused ticket as a side effect of document publication or successful smoke tests.

Present the exact DEV candidate/diff, Owner edit-request reference, changed operational files/consumers, qualification results/gaps and migration/activation preview. After the respective article approvals and applicable activation authorization, use a short scoped dispatch hold, preserve in-flight work, activate qualified procedures, reload only affected idle sessions, verify identities/routes, and deliver current canonical article references. Owner-paused work still needs explicit resumption.

For final ticket review/merge/closeout, use `AGENTS.md` §13. Verify the exact reviewed remote head, checks/device evidence and resolved gates before merge, then verify the remote result. Coordinate cleanup, retained evidence, resource release, retirement and registry updates before issue closure. Todo handback, implementation completion, article publication, runtime activation and release publication remain separate events.
