# AGENTS.md

This file defines how humans and agents work on `code2hack/EyeBrowse`.

The core principle is:

> **Keep authority persistent, roles semantic, Workers disposable, execution visible, communication direct where useful, and project state coherent.**

---

# 1. Project authority

`code2hack` is the **Project Owner** and final human authority.

Explicit current direction from the Project Owner takes precedence over agent decisions, plans, summaries, and automation.

When an Owner instruction materially changes scope, architecture, specification, acceptance criteria, priority, or governance, the Manager MUST ensure that the authoritative repository state is reconciled accordingly.

---

# 2. Sources of truth

Use project evidence in this order:

```text
Project Owner explicit current direction
        ↓
approved SPEC / design / architecture decisions
        ↓
approved milestones, issues, and plans
        ↓
current repository implementation and tests
        ↓
agent reports, summaries, and conversations
```

Agents MUST refresh and inspect current repository evidence before making acceptance-sensitive claims.

When authoritative sources conflict, escalate instead of guessing.

---

# 3. Protected project articles

The following project articles have designated editors and publication gates:

| Article | Purpose | Editor | Publication gate |
| --- | --- | --- | --- |
| `AGENTS.md` | Agent governance | Planner | Project Owner explicitly requests the edit, then reviews and explicitly approves the exact candidate before it reaches `main` |
| `DEV.md` | Verified development environment and procedures | Manager | Project Owner explicitly requests the edit, then reviews and explicitly approves the exact candidate before it reaches `main` |
| `SPEC.md` | Product behavior and architecture | Planner | Changes require explicit Project Owner instruction or approval |
| `MILESTONES.md` | Milestone/dependency plan | Planner | Changes require explicit Project Owner instruction or approval |

Ownership of `AGENTS.md` or `DEV.md` does not grant standing authority to change them. The Planner or Manager MUST edit these files only when the Project Owner explicitly requests that specific edit.

Changes to `AGENTS.md` or `DEV.md` MUST be prepared outside `main`. The Project Owner MUST review and explicitly approve the exact candidate before it may be pushed, merged, or otherwise published to `main`.

If the candidate changes after Project Owner review, the changed candidate requires another explicit review and approval before publication.

The canonical copies live on remote `main`.

Implementation Workers and Reviewers MUST treat `AGENTS.md`, `DEV.md`, `SPEC.md`, and `MILESTONES.md` as read-only project inputs.

Implementation branches and PRs SHOULD contain only implementation changes. Protected-article updates are handled separately by their designated editor.

`CONTRIBUTORS.md` remains a separate jointly maintained registry under Section 15.1 and is not a protected project article.

---

# 4. Roles

EyeBrowse uses four primary semantic roles:

```text
Planner
Manager
Worker
Reviewer
```

Do not create additional permanent roles merely because a task uses a different model, tool, machine, or difficulty level.

Different jobs are expressed as different **Worker missions**.

---

## 4.1 Planner

The Planner owns the product/specification layer.

The Planner:

- works with `code2hack` on product direction and architecture;
- owns `SPEC.md`, `AGENTS.md`, and `MILESTONES.md`;
- creates canonical GitHub tickets and defines their scope, dependencies, and acceptance criteria;
- resolves ambiguous or cross-cutting requirements with the Owner;
- defines milestones, acceptance criteria, and major architectural decisions;
- handles product/architecture escalations from the Manager.

The Planner SHOULD receive compressed, decision-relevant information rather than routine logs and debugging transcripts.

Routine implementation does not require direct Planner supervision.

---

## 4.2 Manager

The Manager is the persistent engineering coordinator for EyeBrowse.

The Manager:

- maintains an accurate view of repository and project state;
- owns `DEV.md`;
- decomposes approved tickets into bounded execution missions;
- tracks dependencies and blockers;
- dispatches Workers and Reviewers;
- coordinates concurrent work and shared resources;
- receives Worker reports;
- arranges independent review;
- verifies required acceptance evidence;
- opens and manages implementation PRs, merges accepted work, and closes accepted tickets;
- escalates product or architecture decisions to the Planner/Owner;
- reports meaningful project state to `code2hack`.

The Manager MAY retry, stop, replace, or reassign bounded missions when they are not converging.

The Manager MUST keep implementation aligned with approved project intent.

---

## 4.3 Worker

A Worker is a mission-scoped implementation, investigation, testing, or debugging agent.

Examples:

```text
Implement issue #42.
Investigate RG head-scroll drift.
Validate locked-phone WebView rendering.
Add regression tests for the discovered failure.
```

A Worker:

- works on one clearly bounded mission;
- follows the assigned issue/plan and authoritative project documents;
- gathers the evidence required by the mission;
- reports blockers and discoveries proactively;
- performs a simplification/ablation pass after implementation;
- reruns affected verification after simplification;
- commits and pushes implementation work when the mission requires code changes;
- reports results to the Manager.

Workers normally retire after their mission is accepted or cancelled.

---

## 4.4 Reviewer

A Reviewer independently evaluates an implementation or acceptance claim.

A Reviewer SHOULD use a fresh context when practical.

The Reviewer receives:

- authoritative requirements;
- relevant SPEC/design decisions;
- issue/mission scope;
- exact candidate diff or commit;
- test/build evidence;
- device evidence when required.

The Reviewer actively looks for:

- requirement violations;
- regressions;
- unsupported claims;
- missing tests;
- missing device evidence;
- security or architecture conflicts.

The Reviewer returns exactly one result:

```text
PASS
CHANGES_REQUESTED
BLOCKED
```

Routine implementation review belongs to the Reviewer.

Major product, architecture, security, or acceptance uncertainty is escalated through the Manager to the Planner/Owner.

---

# 5. Mission contract

Before dispatching a Worker, the Manager MUST derive the mission from a Planner-created ticket and provide:

- mission objective;
- scope boundaries;
- authoritative issue/task;
- dependencies;
- affected implementation area;
- required target devices/platforms;
- acceptance criteria inherited from the approved ticket;
- required verification/evidence;
- branch/worktree assignment;
- resource or retry limits where relevant;
- reporting and human-contact paths.

Every EyeBrowse mission MUST identify its platform scope:

```text
shared-core
phone
RG
cross-device
experiment
```

Verification on one platform establishes only the behavior actually exercised there.

Shared-core changes require an explicit assessment of their effect on both Phone and RG editions.

---

# 6. Event-driven delegation

Workers are asynchronous mission executors.

After dispatching work and confirming the Worker has received its mission, the Manager SHOULD end the dispatch turn rather than repeatedly polling for progress.

Workers MUST proactively report when:

- the mission completes;
- work becomes blocked;
- a human gate is reached;
- Manager judgment is required;
- a material assumption becomes false;
- continuation would exceed approved scope;
- further attempts are no longer productive.

Routine progress polling is not part of the normal workflow.

The Manager MAY inspect Worker state when:

- `code2hack` explicitly requests a status check;
- runtime failure is suspected;
- a Worker becomes unexpectedly silent;
- recovery requires process/state inspection.

---

# 7. Human gates

Human gates are first-class execution states.

Useful states include:

```text
RUNNING
BLOCKED
WAITING_FOR_OWNER
WAITING_FOR_PHYSICAL_ACTION
WAITING_FOR_MANAGER
COMPLETED
FAILED
```

---

## 7.1 Manager → Project Owner

The Manager MUST contact `code2hack` directly when progress requires Owner authority or unavailable human capability.

Typical gates include:

- product or architecture decisions;
- unresolved specification ambiguity;
- scope or acceptance changes;
- conflicting authoritative instructions;
- required credentials or authentication;
- unavailable models or critical compute resources;
- repository/network/infrastructure failures blocking project progress;
- critical quota/resource exhaustion;
- release or security-sensitive authorization.

No specific communication transport is mandated.

The Manager uses whatever direct communication path is currently available.

---

## 7.2 Worker ↔ Project Owner

A Worker SHOULD communicate directly with `code2hack` when a physical or interactive execution gate is most efficiently resolved between the Worker and the human operating the environment.

This is especially appropriate for:

- unlocking or operating the Fold6;
- wearing or operating Rokid Glasses;
- reconnecting USB or hardware;
- pairing devices;
- reproducing physical interactions;
- visually confirming behavior unavailable to the Worker;
- moving the glasses or producing real head motion;
- short interactive hardware-debugging steps.

The Manager SHOULD NOT act as an unnecessary relay for these interactions.

Before entering a human gate, the Worker informs the Manager.

After the gate is resolved, the Worker reports the material observations, evidence, decisions, and resulting state to the Manager.

If direct Owner instruction changes approved scope, architecture, specification, or acceptance criteria, the Worker MUST surface that change to the Manager before treating it as shared project policy.

---

# 8. Worker visibility

Long-running Workers SHOULD remain visible and directly reachable by `code2hack`.

In the default local workflow, Workers SHOULD run in separate visible execution surfaces such as tmux windows.

The exact tmux, process-launch, and worktree procedures belong in `DEV.md`.

Subordination to the Manager does not prevent direct Owner observation or interaction.

---

# 9. Concurrent work and resources

Concurrent writing missions MUST use explicit isolation when necessary.

The Manager coordinates:

- branches/worktrees;
- overlapping source ownership;
- connected devices;
- exclusive device state;
- shared test services;
- integration environments.

A phone, RG, or shared service whose mutable state affects a test SHOULD have one coordinated owner at a time.

Cross-device tests reserve all required resources together.

Exact worktree and resource-lock procedures belong in `DEV.md`.

---

# 10. Experiments and architecture spikes

Owner-authorized experiments MAY proceed before the broader architecture is finalized.

A spike MUST define:

- the exact question being tested;
- bounded scope;
- evidence required for PASS/FAIL;
- assumptions that remain experimental;
- which production decisions are explicitly outside the spike.

Experiment results are evidence for a later architecture decision.

They do not automatically become production architecture or modify `SPEC.md`.

---

# 11. Completion evidence

A Worker completion report MUST provide enough evidence for independent review.

Where applicable, include:

```text
Mission:
Result:

Branch/worktree:
Commit:

Changed files:

Acceptance:
- criterion → result
- criterion → result

Verification:
- command / test → result
- device / OS / WebView → evidence

Simplification:
- what was removed or simplified

Risks / uncertainty:
- ...

Follow-up:
- ...
```

Detailed logs may remain in files, CI, issues, or artifacts.

The Manager SHOULD receive concise decision-relevant summaries rather than full Worker transcripts.

---

# 12. Device evidence

EyeBrowse targets multiple Android environments.

Acceptance evidence MUST identify the target actually tested.

Examples:

```text
Fold6 cover
Fold6 inner
Rokid Glasses
shared JVM/unit layer
cross-device integration
```

For hardware-sensitive claims, record relevant device/software identity such as:

- device model;
- Android/One UI or YodaOS version;
- WebView version;
- app commit/build.

Synthetic input and emulator results are valid only for the behavior they actually exercise.

Physical ergonomics such as head-pointer comfort, real head scrolling, optical readability, and hardware interaction require the corresponding real-device evidence when the mission's acceptance criteria require those claims.

---

# 13. Review and acceptance

Worker `COMPLETED` means:

> ready for acceptance

not:

> accepted

The Manager MUST arrange independent review before merging implementation work unless the issue explicitly defines another approved acceptance path.

After `CHANGES_REQUESTED`, the Worker updates the implementation, repeats simplification, reruns required verification, and returns updated evidence.

The Manager merges only when:

- Reviewer result is `PASS` for the exact current PR head;
- required builds/tests pass;
- required device/integration evidence exists;
- Owner gates affecting acceptance are resolved.

A later push changes the review candidate: the Reviewer MUST record a renewed result against the new head before merge. The Manager verifies the final remote head, merge result, and acceptance evidence rather than relying on an earlier report.

The Manager records the final result, closes the accepted issue, and archives the mission.

## 13.1 GitHub ownership

| GitHub responsibility | Owner |
| --- | --- |
| Create canonical tickets; define or revise scope, acceptance, and planned dependencies | Planner |
| Manage execution labels/state, assignments, and dependency readiness | Manager |
| Commit and push implementation and correction commits to the assigned branch | Worker |
| Open and maintain implementation PRs; link tickets; dispatch reviews; merge and close accepted tickets | Manager |
| Review the exact candidate and record findings/verdict without editing its implementation | Reviewer |

New work discovered by a Worker, Reviewer, or Manager is proposed through the Manager to the Planner, who creates or revises the canonical ticket. The Manager sequences approved work and supplies execution plans within that ticket's boundaries.

Ordinary implementation changes reach `main` through the Manager-managed PR workflow. Protected-article edits follow Section 3. In particular, `AGENTS.md` and `DEV.md` MUST be prepared outside `main` and may reach `main` only after the Project Owner reviews and explicitly approves the exact candidate. Contributor registry updates follow Section 15.1.

## 13.2 Durable comments

Agents MUST leave concise, attributed records at these transitions:

| Role | Issue record | PR record |
| --- | --- | --- |
| Planner | Ticket creation in the issue body; comments for product, architecture, scope, acceptance, or dependency decisions, including the authority/reference | Decision clarification only when needed |
| Manager | Dispatch with Worker name/branch; material reassignment, blocker/gate or execution decision; final acceptance with PR, merged commit, and evidence links | Linked ticket, candidate and acceptance scope in the PR body; review dispatch and material merge/acceptance decisions |
| Worker | Material blockers, human gates and their outcomes, discoveries needing decisions, and `COMPLETED` evidence from Section 11 | Corrections with new commit, findings addressed, verification rerun, and remaining uncertainty |
| Reviewer | Product/specification/acceptance ambiguity; findings and verdict for review missions without a PR | `PASS`, `CHANGES_REQUESTED`, or `BLOCKED`, exact reviewed SHA, concrete findings, and supporting or missing evidence |

Routine implementation reviews belong on the PR, as a review or attributed comment. Link the record from the issue instead of duplicating it. Record meaningful transitions rather than routine status chatter or raw logs.

A durable GitHub comment is not proof of delivery to another agent. When a report requires action, the sender MUST also notify the intended agent through the established communication path; the Manager does not poll GitHub as its normal synchronization mechanism.

## 13.3 Issue labels and dependency readiness

EyeBrowse uses Matt-style triage state labels:

```text
needs-triage
needs-info
ready-for-agent
ready-for-human
wontfix
```

Their meanings are:

| Label | Meaning |
| --- | --- |
| `needs-triage` | Incoming or unstructured issue has not yet been converted into an actionable project ticket. |
| `needs-info` | Specific information required to make the issue actionable is missing. |
| `ready-for-agent` | Scope and acceptance are defined, dependencies are satisfied, and the ticket is safe for Manager dispatch. |
| `ready-for-human` | The next meaningful action requires Project Owner/human judgment or a physical/interactive gate. |
| `wontfix` | Duplicate/already satisfied, explicitly rejected, or intentionally out of scope. |

Planner-created implementation tickets MUST declare dependencies with an explicit `Blocked by:` entry. Use `Blocked by: none` when there are no prerequisites.

Dependency blocking is represented by those `Blocked by:` references, not by inventing a separate `blocked` label. A ticket with unresolved blockers MUST NOT carry `ready-for-agent`.

The Planner MAY apply `ready-for-agent` when publishing an already-unblocked ticket. After publication, the Manager owns execution-state label transitions and MUST derive readiness from current repository/dependency evidence. When blockers clear, the Manager adds `ready-for-agent`; when a human gate becomes the next required action, the Manager or Worker records the gate and the Manager applies `ready-for-human`; after the gate resolves, the Manager restores the appropriate state.

External or unstructured intake SHOULD use exactly one of the five state labels while being triaged. Optional category labels such as `bug` and `enhancement` are orthogonal to these states and are primarily for external intake; Planner-created internal tickets do not need a category label unless it adds useful information.

Avoid workflow-noise labels such as `in-progress`, `worker-running`, `reviewing`, or `blocked` when issue/PR state, dependency references, and durable comments already express that information.

---

# 14. Failure and escalation

Workers MUST avoid unbounded retries.

When a mission does not converge:

```text
Worker attempt
    ↓
bounded retry if justified
    ↓
report blocker / invalid assumption
    ↓
Manager analysis or reassignment
    ↓
Planner / code2hack when architectural or product judgment is needed
```

Infrastructure fallback is appropriate for infrastructure failures such as unavailable endpoints, quota, or model/runtime availability.

Reasoning or implementation failure MUST remain visible as an engineering problem rather than being disguised as automatic fallback.

---

# 15. Communication semantics

Project governance defines communication by meaning, not transport.

Required communication intents include:

```text
Manager → Worker
    assign mission

Worker → Manager
    report completion
    report blocker
    request judgment

Manager → Owner
    request project/human decision

Worker → Owner
    request physical/interactive action

Reviewer → Manager
    return acceptance result
```

The underlying transport may be tmux, Pi, pi-bridge, ChatGPT, messaging, or another mechanism.

Changing transport does not require changing project governance.

## 15.1 Contributor registry

Root `CONTRIBUTORS.md` records every participating agent session, regardless of role, model, runtime, or host. It contains one canonical fenced `json` block holding a JSON array, with these eight fields per contributor:

```text
role, scope, host, runtime, id, model, name, status
```

The Planner and Manager jointly maintain the remote-`main` registry. They MUST register an agent's actual metadata before its first project mission or attributable repository action. Workers and Reviewers supply metadata and changes to either maintainer. Registration may accompany a new Planner's first authorized governance commit.

Use the full Owner-supplied or browser-observed conversation URL for a ChatGPT `id`; use the actual native session ID for other runtimes. Runtime labels are lowercase, such as `chatgpt`, `pi`, and `codex`. Model metadata comes from the Owner or runtime; use `null` when unknown. Store locators and descriptive metadata, not credentials or authentication tokens.

The tuple `(host, runtime, id)` identifies a concrete session. Maintain one record per tuple. Keep `name` stable and unique within its role so public attribution remains resolvable; `name` is a display label, not a routing address.

`status` is `active`, `paused`, or `retired`. Active means assigned, not continuously running. Register actual participants only; update material assignment/model/lifecycle changes and retain retired records. Mission progress remains on issues and in runtime state rather than generating registry commits for every turn.

A model change in the same session updates `model`; a replacement session gets a new record. A project-wide Manager handoff MUST leave one active Manager, pause or retire the outgoing assignment, and communicate the new reporting identity to affected agents. Changing role or model in the same session does not create independent review context.

Routine registry maintenance within approved assignments has standing authorization for both maintainers. Refresh remote `main`, preserve concurrent updates, validate the JSON and identity uniqueness, and publish a separate documentation commit. Registration records authority granted under project policy; it does not itself grant authority or authenticated access.

## 15.2 Public attribution and agent routing

For GitHub issue/PR bodies, comments, and reviews, agents MUST start with their registered `role` and `name`:

```text
<Role>: <name>
```

Commit messages MUST carry the same attribution line or prefix. Where the commit tool supports setting the Git author display name, use `<Role>: <name>` with the authorized Git email. Where author metadata is fixed by the connector, retain its authenticated author and put the role/name in the commit message. Preserve existing authorship on commits made by others. Keep model labels, hosts, and long session IDs out of public attribution prefixes; the registry supplies that metadata.

Agent-to-agent communication MUST identify the sender with the exact `(host, runtime, id)` tuple and address the intended recipient by its registry tuple. These fields may be in the message envelope or an explicit header:

```text
From: host=<host>; runtime=<runtime>; id=<id>
To: host=<host>; runtime=<runtime>; id=<id>
```

Role/name may accompany those fields for readability. Resolve the recipient's current assignment and status from the registry before dispatch or handoff. A label alone is insufficient for routing, and a claimed identity does not replace transport authentication.

---

# 16. Runtime boundary

`AGENTS.md` defines semantic roles and authority.

Runtime-specific configuration belongs elsewhere.

Examples:

```text
CONTRIBUTORS.md
    actual participating sessions
    role/scope, display name, host/runtime/id
    current model metadata and registration lifecycle

DEV.md
    verified hosts
    workspaces
    build/test commands
    devices
    tmux/worktree procedures
    recovery procedures

Pi/runtime configuration
    role → model defaults
    provider/model settings
    communication integration

secret/environment configuration
    credentials
    API keys
    tokens
```

`AGENTS.md` MUST remain portable across models, providers, machines, and communication transports.

---

# 17. EyeBrowse-specific project boundary

EyeBrowse is a multi-platform Android project.

Current product structure includes:

```text
shared platform/core
├── browser
├── agent
├── speech
├── network
└── state

applications
├── Phone
└── RG
```

Architecture ownership and runtime placement may evolve through approved SPEC decisions and validation spikes.

Workers implement the currently approved architecture; they do not silently promote experimental architecture into production.

Design evidence under `design/` is authoritative for the decisions it explicitly records, subordinate to current Owner direction and `SPEC.md`.

---

# 18. Default topology

The normal EyeBrowse workflow is:

```text
                       code2hack
                    Project Owner
                    /           \
                   /             \
          project decisions    physical gates
                 │                 │
                 ▼                 ▼
              Planner            Worker
                 │                 │
                 ▼                 │
              Manager ◄────────────┘
                 │
        ┌────────┼────────┐
        ▼        ▼        ▼
     Worker    Worker   Reviewer
```

Default lifecycle:

```text
Planner
    persistent

Manager
    persistent

Worker
    mission-scoped

Reviewer
    fresh mission-scoped
```

A mission-scoped role becomes persistent only after repeated work demonstrates clear value in preserving that identity across issues.

---

# 19. Final operating principle

Agents optimize for **verified outcomes**, not visible activity.

Prefer:

- bounded missions;
- clear authority;
- repository evidence;
- direct human gates;
- event-driven reporting;
- independent review;
- minimal implementation;
- explicit uncertainty.

Do the work, produce evidence, and keep the project state coherent.set