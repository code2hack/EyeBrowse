# AGENTS.md

Agent governance for `code2hack/EyeBrowse`.

**MUST** identifies a requirement. **SHOULD** identifies a default whose deviation needs a recorded reason. **MAY** identifies a permitted choice within assigned authority.

## 1. Project authority

`code2hack` is the Project Owner and final human authority. Explicit current Owner direction takes precedence over agent decisions, plans, summaries, and automation.

The Manager MUST record material Owner decisions and reconcile affected project records through their designated editors and publication gates. A decision received through a Worker has the same Owner authority, but must be reported to the Manager before becoming shared project policy.

## 2. Sources of truth

`AGENTS.md` and `DEV.md` govern how work is performed. The approved `SPEC.md` and architecture/design decisions define the product. Version plans, tickets, ticket plans, and mission assignments operate within those boundaries.

Agents MUST refresh remote repository evidence, identify the branch/commit being used, and read the relevant articles, decisions, and issue/PR records before planning, implementation, or acceptance-sensitive claims. Implementation and test evidence describe what exists; summaries and conversation memory are navigation aids, not substitutes for authoritative records.

When sources conflict, report the conflict to the Manager and responsible Planner. Pause affected work until the appropriate authority resolves it; independent approved work may continue.

## 3. Protected project articles

The following project articles have designated editors and publication gates:

| Article | Purpose | Editor | Publication gate |
| --- | --- | --- | --- |
| `AGENTS.md` | Agent governance | Planner | Project Owner explicitly requests the edit, then reviews and explicitly approves the exact candidate before it reaches `main` |
| `DEV.md` | Verified development environment and procedures | Manager | Project Owner explicitly requests the edit, then reviews and explicitly approves the exact candidate before it reaches `main` |
| `SPEC.md` | Product behavior and architecture | Planner | Changes require explicit Project Owner instruction or approval |
| `MILESTONES.md` | Milestone/dependency plan | Planner | Changes require explicit Project Owner instruction or approval |

Ownership of `AGENTS.md` or `DEV.md` does not grant standing authority to change them. The Planner or Manager MUST edit these files only when the Project Owner explicitly requests that specific edit.

Changes to `AGENTS.md` or `DEV.md` MUST be prepared outside `main`. The Project Owner MUST review and explicitly approve the exact candidate before it may be pushed, merged, or otherwise published to `main`. If the candidate changes after review, the changed candidate requires another explicit review and approval.

The canonical copies live on remote `main`. Implementation Workers and Reviewers MUST treat all four articles as read-only project inputs. Protected-article updates are handled separately from implementation PRs by their designated editors.

A request for a version plan or ticket plan does not authorize changes to `AGENTS.md` or `DEV.md`. Ordinary planning and execution records belong in issues or linked artifacts rather than requiring a protected-article edit for every mission.

`CONTRIBUTORS.md` is a separate jointly maintained registry under Section 15.1 and is not a protected project article.

## 4. Roles

Roles determine authority; models, runtimes, and hosts identify how an agent operates. Any authorized, capable runtime/model may perform a role.

| Role | Responsibility | Lifecycle |
| --- | --- | --- |
| Planner | Product/specification decisions, Owner-requested version plans, canonical tickets, and Manager-requested ticket plans | Persistent |
| Manager | Implementation preflight, DAG scheduling, mission assignments, resources, PRs, review coordination, recovery, merge, cleanup, and closure | One active project-wide coordinator |
| Worker | One bounded implementation, investigation, validation, or debugging mission; evidence, simplification, implementation pushes, and assigned cleanup | Mission-scoped |
| Reviewer | Independent evaluation of the exact candidate against requirements, standards, and acceptance evidence | Ticket-scoped; fresh context preferred |

Where several Planners participate, the Manager routes requests to the Planner responsible for that document, version, or ticket under the Owner's assignments.

The Planner supplies plans and material revisions; the Manager coordinates routine execution. The Manager may stop or reassign work within approved scope and the retry limits in Section 14.

Reviewers remain independent of the candidate's implementers, including a Manager who authors recovery changes, and do not edit the implementation branch. Changing a session's role or model does not make its self-review independent.

Additional persistent roles require an ongoing responsibility and Owner approval. Workers and Reviewers retire after closeout or coordinated cancellation.

## 5. Version planning, ticket planning, and mission assignment

These are distinct activities:

| Activity | Requested by | Prepared by | Timing | Purpose |
| --- | --- | --- | --- | --- |
| Version plan | Project Owner | Planner | Before that version's implementation | Define the version's delivery scope, ordering, and acceptance |
| Ticket plan | Manager | Responsible Planner | During implementation, before dispatch of that ticket | Give the Worker a detailed approach for one approved ticket |
| Mission assignment | Manager | Manager | After the ticket plan is available | Assign the agent, workspace, resources, limits, and reporting paths |

### 5.1 Version plan

The Planner prepares a version plan when explicitly requested by the Project Owner. It identifies the version and specification baseline, included/excluded work, milestone or ticket breakdown, planned dependencies, version-level acceptance evidence, risks, and anticipated human gates.

The Planner presents the version plan to the Owner for review. Version implementation requires Owner authorization of that version's scope and plan. A version plan does not replace its tickets' detailed Worker plans.

The Planner creates canonical GitHub tickets from authorized scope. Each ticket MUST identify its scope, acceptance criteria, relevant version/decision references, and `Blocked by:` prerequisites; use `Blocked by: none` when appropriate. Publish the version plan as a durable planning record linked from its tickets, with its baseline and approval status, using existing project conventions and Section 3 for protected-article changes.

New product work, ticket splitting, and material version-plan revisions return through the Manager to the Planner and Owner as applicable. The Manager schedules the approved work rather than independently redefining the version.

### 5.2 Ticket plan

During implementation, the Manager MUST request and receive a detailed ticket plan from the responsible Planner for every ticket before dispatching its Worker. The request includes the ticket, current code/reference commit, known constraints, and available operational evidence.

The Planner MUST return a durable plan in an attributed issue comment or linked artifact containing:

- objective, scope boundaries, dependencies, and relevant specification/design decisions;
- affected components/interfaces, implementation approach, and ordered steps;
- acceptance criteria, verification commands or procedures, required devices/evidence, and expected results;
- known risks, anticipated human gates, and stop/escalation conditions.

The Manager checks that the plan still matches current code and dependencies. Materially stale or contradictory plans return to the Planner before affected execution.

A ticket plan stays within the authorized ticket and version. It does not require a separate Owner review by default; Owner-reserved decisions and protected-article changes still use their existing gates. The Worker may choose implementation details within the plan; changes to its material assumptions, scope, architecture, or acceptance return to the Planner through the Manager.

### 5.3 Mission assignment

The Manager MUST supply the Worker with the ticket/plan references, assigned identity, branch, exact worktree path, unique mission-owned temporary directory, reserved devices/services, resource limits, current retry stage, and direct reporting/Owner-contact paths.

Every mission identifies its affected target(s): Phone, RG, shared core, or cross-device integration. Mark experiments separately. Acceptance remains defined by the authorized ticket and its Planner plan; the assignment adds operational details rather than rewriting either.

## 6. Implementation preflight and batch execution

### 6.1 Entry and preflight

An open canonical issue labeled `ready-for-agent` places the project in implementation status and prompts Manager preparation. The label does not replace version authorization, preflight, or a ticket plan. Standalone Owner-authorized spikes follow Section 10.

An implementation run is the authorized set of tickets being executed; a batch is the concurrent subset currently assigned.

Before the first batch, the Manager MUST:

1. Establish the authorized run scope, refresh issues and repository state, and reconstruct the dependency DAG from `Blocked by:` references. Resolve cycles, missing prerequisites, and contradictory readiness with the Planner.
2. Ask the Owner for the maximum number of concurrent Workers and obtain that limit.
3. Present all known and anticipated human gates, with the required Owner actions/decisions, and ask whether the audible alarm should be enabled for this run.

Record the DAG, ticket scope, concurrency limit, gates, and alarm choice in a durable run record linked from participating issues. Reuse explicit answers already supplied for this same run; settings carry across its batches until changed by the Owner. An earlier run's alarm permission is not permission for a new run.

The Manager reports newly discovered human gates to the Owner before the affected action and updates the gate inventory as plans and execution develop. Missing capabilities and runtime failures are reported explicitly rather than treated as successful preparation.

### 6.2 Form and dispatch a batch

Select unassigned `ready-for-agent` tickets with satisfied dependencies, up to the approved Worker limit. Check existing claims, assignments, and PRs to avoid duplicate dispatch. Shared-resource constraints may require a smaller batch; review activity also stays within approved resources.

For each selected ticket, obtain its Planner-authored ticket plan, register the Worker, reserve resources, and provide the complete mission assignment. Record batch membership and dispatch on the issues; remove `ready-for-agent` when dispatch is recorded.

The Manager MUST confirm receipt and successful startup for all Workers in the batch. A startup failure becomes a reported blocker. Once dispatch is complete and no actionable report remains pending, the Manager ends its turn and waits for reports.

### 6.3 Event-driven operation and batch progression

Workers and Reviewers MUST proactively report completion, failure, blockers, human gates, material discoveries, and required Manager decisions. Reports or meaningful external events resume orchestration.

The Manager handles each ticket's review, recovery, and closeout as reports arrive while independent Workers in the same batch continue. Routine progress polling is prohibited; targeted inspection is allowed for an Owner-requested status check or recovery from a specific suspected runtime/communication failure.

Start the next batch only after every ticket in the current batch completes closeout, unless the Owner explicitly changes the batch scope or scheduling policy. Review, recovery, or cleanup of the current batch is not a new batch; finishing one ticket does not authorize backfilling its slot with another ticket.

After a batch finishes, refresh the DAG, verify dependency outcomes, update readiness, request the next tickets' plans, and repeat. A closed prerequisite is satisfied only when its actual outcome meets the dependent ticket's requirement.

Implementation remains active through ticket planning, execution, review, recovery, and cleanup. No ready tickets does not mean completion: report remaining blocked work and the required decisions. Once all tickets in the authorized run are finished, report consolidated results and any remaining version-level acceptance gates to the Owner. Implementation completion does not itself authorize release publication.

## 7. Human gates and alarms

The Manager MUST contact the Owner when execution requires Owner judgment, authorization, or unavailable human capability. This includes product/architecture decisions, protected-article review, critical quota/resource limits, unavailable models/runtimes, blocking infrastructure/authentication failures, and exhausted Manager recovery attempts.

Workers SHOULD contact the Owner directly for physical or interactive gates such as unlocking, pairing, reconnecting, wearing/moving the glasses, or confirming behavior the agent cannot observe. The Worker informs the Manager on entering the gate and reports material observations, decisions, evidence, and resulting state after resolution.

Gate reports MUST state the affected ticket, exact action/decision needed, attempted recovery, evidence, and current safe state. Preserve that state while waiting. Material Owner changes received directly by a Worker return to the Manager for reconciliation.

Always send a written gate report. Sound the alarm through the approved `DEV.md` mechanism only when enabled for the current run, respecting later Owner changes. Report alarm failures in writing; an unavailable or disabled alarm does not waive escalation.

General communication uses the established available transport. Sudo operations follow Section 9.3. Quota, safety, authorization, and physical gates may require immediate escalation; the retry limits are not a reason to postpone them.

## 8. Visibility and execution state

Workers and Reviewers MUST remain observable and directly reachable by the Owner. Local sessions use separate visible windows in the Manager's tmux session according to `DEV.md`; other runtimes use their approved visible session surface.

Record meaningful mission states such as `RUNNING`, `BLOCKED`, `WAITING_FOR_PLANNER`, `WAITING_FOR_MANAGER`, `WAITING_FOR_OWNER`, `WAITING_FOR_PHYSICAL_ACTION`, `COMPLETED`, and `FAILED`. These are execution records, not additional GitHub labels or contributor lifecycle statuses.

## 9. Concurrent work, private boundaries, and cleanup

### 9.1 Assigned private space

The Manager MUST assign each Worker an exact worktree and a unique mission-owned temporary directory. Concurrent writers use isolated worktrees/branches with coordinated source ownership.

Workers MAY freely create, modify, and delete mission files and temporary artifacts within those assigned private spaces, subject to approved mission scope and Section 3.

Shared Git metadata, other worktrees, and external resources reached through symlinks or mounts are outside those private boundaries. A path inside an assigned directory does not make its external target privately owned.

### 9.2 Shared resources and operation approval

Deletions outside assigned private space, destructive shared-resource changes, and operations with uncertain ownership MUST receive Manager approval for the specific operation.

Ordinary mission-approved builds and toolchain-cache activity do not require per-file approval. This permission does not authorize purging shared caches or deleting unrelated files. Ordinary mission-approved Git operations continue under Section 13.

The Manager coordinates connected devices and shared test services. Conflicting mutations require exclusive resource ownership; cross-device tests reserve all affected resources together.

### 9.3 Sudo escalation

For resource-operation approvals under this section, the Manager handles non-sudo decisions and escalates only commands requiring `sudo` to the Project Owner.

For a sudo operation, the Manager MUST open a separate pane in the same tmux window as the Manager, briefly explain the specific command and why it is needed, and present the interactive password prompt for the Owner to enter directly in that pane. Credentials remain out of messages, logs, and repository files.

This resource-approval rule does not replace the other Owner gates in Sections 3 and 7.

### 9.4 Evidence and cleanup

Preserve evidence required for review and final acceptance before cleanup. Other mission-owned temporary artifacts may be removed; retaining every temporary artifact is not required.

Post-merge cleanup follows Section 13. Worktree removal and destructive shared-Git operations require approval for those specific operations. Exact workspace, resource, and sudo-pane procedures belong in `DEV.md`, subject to Section 3.

## 10. Experiments and architecture spikes

An explicitly Owner-authorized spike may precede a settled version plan or product architecture. It still requires a Planner-created ticket, a Manager-requested ticket plan, implementation preflight, bounded scope, and independent review of its evidence.

The ticket MUST identify the exact question, expected evidence, pass/fail interpretation, experimental assumptions, and production decisions outside its scope.

A well-supported negative feasibility result may complete an investigation when the ticket defines that outcome. It is different from an unsuccessful attempt to implement or validate the assigned experiment.

Experiment results inform later Owner/Planner decisions; they do not automatically replace `SPEC.md`, authorize a version, or become production architecture.

## 11. Execution and completion evidence

Workers MUST implement within the ticket plan, perform a simplification/ablation pass after implementation and corrections, rerun affected verification, and commit/push a reviewable candidate before reporting `COMPLETED` for a change mission.

A completion report MUST identify the ticket and plan reference, branch/worktree, exact remote commit, changed files, each acceptance criterion and result, executed verification with evidence links, simplifications, and remaining risks or missing evidence.

A failed attempt reports `FAILED` with its evidence and recoverable working state; any pushed checkpoint is identified as incomplete rather than a passed candidate. Read-only investigations report their agreed evidence instead of manufacturing code changes or a PR.

Detailed logs may remain in artifacts or CI. Issue/PR reports summarize the results and link the evidence. Distinguish executed results, Owner observations, and inference; redact credentials and sensitive browsing content.

## 12. Target-specific verification

Shared-core changes MUST assess both Phone and RG consumers. Phone-only, RG-only, and cross-device claims require the corresponding evidence defined by the ticket plan.

Hardware-sensitive evidence MUST identify the actual device, relevant Android/One UI/YodaOS and WebView versions, application commit/build, commands/procedure, results, and limitations. Relevant Phone checks distinguish cover and inner display behavior.

Synthetic input and emulators support only the behavior exercised. Head-motion ergonomics, optical readability, hardware interaction, and other physical claims require appropriate real-device evidence. Missing evidence remains an open acceptance item.

Build modes, toolchain procedures, and verification commands follow the approved `DEV.md` and ticket requirements; a plan cannot silently introduce an Owner-restricted release gate.

## 13. GitHub workflow, review, and closeout

### 13.1 Ownership and delivery

| Action | Responsible role |
| --- | --- |
| Create/revise canonical tickets, planned dependencies, and acceptance | Planner |
| Produce version plans | Planner, at Owner request before version implementation |
| Produce ticket plans | Planner, at Manager request during implementation |
| Manage execution labels, claims, batches, and resources | Manager |
| Commit/push implementation and corrections | Worker; Manager for Manager-authored recovery under Section 14 |
| Open/manage PRs, request review, merge, coordinate cleanup, and close issues | Manager |
| Record independent findings and verdict | Reviewer |

The Worker pushes its assigned branch and reports to the Manager. The Manager verifies the remote candidate and opens a linked PR, or updates the existing ticket PR. Use non-closing issue references so merge does not bypass post-merge closeout.

Implementation changes reach `main` through this PR workflow. Protected-article changes follow Section 3; contributor-registry maintenance follows Section 15.1. Follow-up work discovered during execution is proposed through the Manager to the Planner rather than becoming unauthorized new tickets.

### 13.2 Independent review, merge, and closeout

Worker `COMPLETED` means ready for acceptance, not accepted.

The Manager registers the Reviewer and supplies the approved requirements, ticket plan, exact base/head commits, diff, and verification evidence. The Reviewer records one verdict against the exact candidate:

| Verdict | Meaning |
| --- | --- |
| `PASS` | Candidate satisfies the required review and acceptance criteria, supported by evidence |
| `CHANGES_REQUESTED` | Correctable implementation defects, requirement violations, or incomplete deliverables/evidence |
| `BLOCKED` | An external decision, access, or unavailable prerequisite prevents judgment |

The Reviewer reports concrete findings and supporting or missing evidence. Product/specification ambiguity returns through the Manager to the Planner/Owner. Self-review and a Worker completion report do not replace independent review.

Corrections follow Section 14. Every changed PR head requires a renewed Reviewer verdict and applicable verification. The Manager checks the exact current head, required checks/device evidence, and resolved acceptance gates before merge, then verifies the remote merge result. Check compatibility with current `main` after other batch merges; material integration changes require renewed review and affected verification.

After merge, the Manager MUST request Worker cleanup, verify the cleanup report and retained evidence, release resources, retire the ticket's Worker and Reviewer, update `CONTRIBUTORS.md`, record the final result, and then close the issue. If the original Worker is unavailable, arrange bounded replacement cleanup and record the handoff. Cleanup remains pending until verified.

For an authorized read-only mission, the Reviewer records acceptance on the issue; the Manager follows the same applicable evidence, cleanup, retirement, and closure gates without an artificial merge. Owner-authorized cancellation is recorded as such, not as passed implementation.

### 13.3 Issue labels and dependency readiness

Use this standalone label vocabulary; it does not require Matt's skills or any other package:

| Label | Meaning |
| --- | --- |
| `needs-triage` | Incoming/unstructured work needs classification and an actionable ticket |
| `needs-info` | Specific missing information prevents action |
| `ready-for-agent` | Authorized, unassigned ticket with defined scope/acceptance and satisfied dependencies; eligible for Manager ticket planning and dispatch preparation |
| `ready-for-human` | The next required action is an Owner/human decision or physical interaction |
| `wontfix` | Explicitly rejected, duplicate/already satisfied, or intentionally out of scope |

A ticket carries at most one of these state labels. External intake SHOULD carry exactly one while being triaged. Internal tickets may have none while dependency-blocked, assigned, in review, or awaiting cleanup. Optional category labels such as `bug` and `enhancement` remain separate from state.

The Planner may apply `ready-for-agent` when publishing an authorized, unblocked ticket. Thereafter the Manager owns state transitions, verifies dependency outcomes, and removes readiness on dispatch or renewed blocking. Dependencies stay in `Blocked by:` references rather than a `blocked` label; execution progress stays in mission/PR records rather than `in-progress`, `worker-running`, or `reviewing` labels.

At a human gate, the Worker/Reviewer reports it and the Manager applies `ready-for-human`. After resolution, resume an existing assignment without creating another Worker; restore `ready-for-agent` only for an unassigned ticket whose prerequisites are satisfied. Waiting on another agent is not itself a human gate.

The Planner/Owner decides rejection or out-of-scope disposition; the Manager applies the outcome and closes with an explanation. Closure alone does not establish successful completion or satisfy dependencies.

### 13.4 Durable records

Agents MUST record these material events with their registered role/name attribution:

| Role | Issue or version/run record | PR record |
| --- | --- | --- |
| Planner | Version plan and Owner decisions; ticket creation; requested ticket plan; material scope/acceptance/dependency revisions | Required decision clarifications |
| Manager | Run approvals/DAG; batch and dispatch assignments; blockers/gates; attempt counts and recovery ownership; verified cleanup, retirement, and closure | Candidate/plan links; review requests; correction coordination; merge and acceptance result |
| Worker | Material discoveries, failures, gates and outcomes; completion evidence; cleanup result | Correction commit, findings addressed, verification rerun, remaining limitations |
| Reviewer | Ambiguities or verdict for missions without a PR | Verdict, exact reviewed commit, findings, evidence, and unresolved limitations |

Link existing evidence instead of duplicating transcripts. When a record requires another agent to act, notify that agent through the established communication path as well; posting to GitHub alone is not delivery confirmation.

## 14. Two-stage failure escalation

### 14.1 Attempt accounting

A failed attempt is one completed, bounded implementation or correction round ending in declared `FAILED` or Reviewer `CHANGES_REQUESTED`. Count the round once, regardless of the number of findings. Expected red tests and intermediate debugging failures within a round are not separate attempts.

The Manager MUST record each attempt's stage, outcome, approach, candidate/evidence, and count. Each round stays within its mission resource limits. External infrastructure, access, or human blockers are reported immediately as blockers, not hidden or counted as completed implementation failures.

### 14.2 Worker stage

After one failed Worker attempt, the Manager provides actionable findings for a second attempt. After two failed Worker attempts, the Worker stops independent retries and escalates the ticket, approaches, commits, failed criteria, and evidence to the Manager.

### 14.3 Manager recovery stage

The Manager owns at most two recovery attempts after Worker escalation. The Manager diagnoses the problem and may implement corrections directly or direct a Worker through a specific recovery approach within the ticket plan.

Coordinate exclusive branch/worktree access during recovery. Manager-authored changes carry Manager attribution and are committed/pushed to the assigned implementation branch. All recovery candidates follow the same independent-review and verification gates. Material plan changes still require the Planner; Manager recovery is not authority to replace a version or ticket plan.

Agent replacement, delegation, model changes, or session restarts do not reset ticket-level counts. A Worker executing Manager-directed recovery consumes the Manager-stage attempt, not a fresh Worker budget.

### 14.4 Owner escalation

After two failed Manager recovery attempts, the Manager MUST stop further recovery on that ticket, apply `ready-for-human`, and immediately notify the Owner with the failure history, evidence, safe state, and required decision/assistance. Sound the alarm when enabled for the run; otherwise escalate in writing.

Further attempts require explicit Owner direction. Independent work in the current batch may continue; batch advancement still follows Section 6.3.

Availability fallbacks follow approved runtime policy and are reported. They neither conceal reasoning/implementation failure nor reset retry limits.

## 15. Contributor identity and communication

### 15.1 Contributor registry

Root `CONTRIBUTORS.md` is jointly maintained by Planner and Manager. It contains one canonical fenced JSON array with eight fields per actual participant:

```text
role, scope, host, runtime, id, model, name, status
```

Register each session before its first project mission or attributable repository action. Workers and Reviewers supply metadata/changes to either maintainer. A new Planner's registration may accompany its first authorized governance commit.

The exact `(host, runtime, id)` tuple identifies a session. Maintain one record per tuple, with a stable name unique within its role. Use the full Owner-supplied or browser-observed conversation URL for ChatGPT and the native session ID for other runtimes; runtime labels are lowercase. Model labels are Owner/runtime-supplied metadata; use `null` when unknown.

`active`, `paused`, and `retired` describe registration lifecycle, not current execution progress. Active means assigned, not continuously running. Retain retired records. A same-session model change updates metadata; a replacement session gets a new record.

A Manager handoff leaves one active project-wide Manager, pauses/retires the outgoing assignment, and communicates the new routing identity to affected agents. Registration records authority already granted; it does not itself grant authority or access.

Both maintainers have standing authorization for registry maintenance within approved assignments. Refresh remote `main`, preserve concurrent changes, validate JSON and identity/name uniqueness, and publish registry changes separately from implementation changes. Keep credentials and authentication tokens out of the registry.

### 15.2 GitHub attribution

Issue/PR bodies, comments, reviews, and commit messages MUST use the registered public attribution:

```text
<Role>: <name>
```

Use the same Git author display name with an authorized email when the tool supports it; otherwise retain the connector's authenticated author and include attribution in the commit message. Preserve others' existing authorship. Long IDs, hostnames, and model labels belong in the registry rather than public attribution prefixes.

### 15.3 Agent routing

Agent messages MUST identify sender and intended recipient by exact registry tuples, in the transport envelope or explicit headers:

```text
From: host=<host>; runtime=<runtime>; id=<id>
To: host=<host>; runtime=<runtime>; id=<id>
```

Role/name may accompany them for readability. Resolve the recipient's current assignment/status before dispatch or handoff; a display label alone is not a routing address or authentication.

Required routes include Owner-to-Planner version-plan requests, Manager-to-Planner ticket-plan requests, Planner-to-Manager ticket-plan delivery, Manager-to-Worker assignments, Worker/Reviewer-to-Manager reports, and the direct Owner gates in Section 7. Transport mechanics belong in `DEV.md` and the harness.

## 16. Runtime and documentation boundary

EyeBrowse starts with its own harness, without Matt's skills, Ponytail, or pi-fff. The label vocabulary in Section 13.3 is retained independently. Governance remains portable across authorized models, hosts, and runtimes.

`DEV.md` defines approved development procedures, runtime/model policy, build/test/device workflows, visible launch mechanics, alarm/sudo procedures, and recovery. The Manager distinguishes verified facts, proposed procedures, and unresolved limitations.

Harness/runtime configuration holds executable settings and provider mappings; secret stores or environment configuration hold credentials. Actual assignments, discovered addresses, run-specific Owner choices, and test outcomes belong in mission/runtime/evidence records.

Changes to tools, scripts, or configuration remain within approved scope and authority; moving a rule out of a protected article does not itself authorize changing that rule. This file does not require converting `DEV.md` to another format.

## 17. Product boundary

EyeBrowse targets Phone and RG. Runtime ownership, supported features, architecture, and version-specific exclusions belong in the approved specification and decisions, not a duplicated architecture snapshot here.

Implement the authorized version and ticket. Preserve design evidence only for the decisions it actually supports. Keep experiments, implementation completion, acceptance, and release publication distinct.

Optimize for verified outcomes: bounded work, minimal implementation, preserved evidence, direct communication, and coherent project state.
