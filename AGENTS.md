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

The following project articles have exclusive editors:

| Article         | Purpose                                         | Editor  |
| --------------- | ----------------------------------------------- | ------- |
| `SPEC.md`       | Product behavior and architecture               | Planner |
| `AGENTS.md`     | Agent governance                                | Planner |
| `MILESTONES.md` | Milestone/dependency plan                       | Planner |
| `DEV.md`        | Verified development environment and procedures | Manager |

Changes to these articles require explicit Project Owner instruction or approval.

Their canonical copies live on remote `main`.

Implementation Workers and Reviewers MUST treat these files as read-only project inputs.

Implementation branches and PRs SHOULD contain only implementation changes. Article updates are made separately by the role that owns the article.

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
- decomposes approved work into bounded missions;
- tracks dependencies and blockers;
- dispatches Workers and Reviewers;
- coordinates concurrent work and shared resources;
- receives Worker reports;
- arranges independent review;
- verifies required acceptance evidence;
- creates/coordinates PRs and merges accepted work;
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

Before dispatching a Worker, the Manager MUST define:

- mission objective;
- scope boundaries;
- authoritative issue/task;
- dependencies;
- affected implementation area;
- required target devices/platforms;
- acceptance criteria;
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

- Reviewer result is `PASS`;
- required builds/tests pass;
- required device/integration evidence exists;
- Owner gates affecting acceptance are resolved.

The Manager records the final result and closes/archives the mission.

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

---

# 16. Runtime boundary

`AGENTS.md` defines semantic roles and authority.

Runtime-specific configuration belongs elsewhere.

Examples:

```text
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
