# AGENTS.md

Agent governance for `code2hack/EyeBrowse`.

**MUST** identifies a requirement. **SHOULD** identifies a default whose deviation needs a recorded reason. **MAY** identifies a permitted choice within assigned authority.

## 1. Project authority

`code2hack` is the Project Owner and final human authority. Explicit current Owner direction takes precedence over agent decisions, plans, summaries, and automation.

The Manager MUST record material Owner decisions and reconcile affected project records through their designated editors and publication gates. An authenticated Owner decision received by any agent has the same Owner authority and MUST be reported to the Manager before becoming shared project policy.

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

The canonical copies live on remote `main`. Workers, Experts, and Reviewers MUST treat all four articles as read-only project inputs. Protected-article updates are handled separately from implementation PRs by their designated editors.

A request for a version plan or ticket plan, an operational setup authorization, or a suggested Manager prompt does not itself authorize changes to `AGENTS.md` or `DEV.md`. The responsible editor records the explicit Owner request and exact-candidate publication approval. Ordinary planning and execution records belong in issues or linked artifacts rather than requiring a protected-article edit for every mission.

`CONTRIBUTORS.md` is a separate jointly maintained registry under Section 15.1 and is not a protected project article.

## 4. Roles

Roles determine authority; models, runtimes, and hosts identify how an agent operates. Any authorized, capable runtime/model may perform a role.

| Role | Responsibility | Lifecycle |
| --- | --- | --- |
| Planner | Product/specification decisions, Owner-requested version plans, canonical tickets, and Manager-requested ticket plans | Persistent |
| Manager | Implementation preflight, DAG/todo scheduling, assignments, resources, communication, Expert handoffs, PRs, review coordination, merge, cleanup, and closure | One active project-wide coordinator |
| Worker | Execute the ticket's approved todos, provide evidence, simplify, push implementation, and perform assigned cleanup; a local helper is also a Worker | Ticket/mission-scoped |
| Expert | Diagnose and fix the specifically assigned escalated todo, verify its result, and hand back to the Worker | Todo-scoped; no fixed attempt-count ceiling |
| Reviewer | Independent evaluation of the exact candidate against requirements, standards, and acceptance evidence | Ticket-scoped; fresh context preferred |

Where several Planners participate, the Manager routes requests to the Planner responsible for that document, version, or ticket under the Owner's assignments.

The Planner supplies plans and material revisions. The Manager coordinates execution and Expert assignment, rather than acting as the escalated product-code troubleshooter or authoring product recovery fixes. Within Owner-authorized operational scope, the Manager may configure and qualify installed tools, maintain routing and records, reserve resources, and perform bounded communication/environment recovery. This is not authority to repair product code or its failing acceptance tests. New tracked harness functionality follows Planner ticketing and independent review; no new framework is implied.

Reviewers remain independent of all candidate implementers, including Experts and local helpers, and do not edit the implementation branch. Changing a session's role or model does not make its self-review independent.

An Expert assignment covers the escalated todo, not the remainder of the ticket. After its verified handback, the Manager returns later todos to the ticket Worker under Section 14.4. Additional persistent roles require an ongoing responsibility and Owner approval.

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

### 5.3 Todos and mission assignment

Use these work-item terms consistently:

| Term | Meaning |
| --- | --- |
| Version | Owner-authorized delivery scope |
| Ticket | Planner-created GitHub issue with scope, dependencies, and acceptance |
| Todo | Bounded, verifiable objective within the approved ticket plan; the unit of Worker failure accounting and Expert escalation |
| Attempt | One implementation or correction round on a todo |

The Manager coordinates canonical todo identity, objective, completion condition, dependencies, assignment, and attempt history before execution. Workers and Experts maintain execution progress. Use the installed todo tool where suitable, with durable references in mission records where needed; one objective has one canonical record, not competing per-session failure counters. Commands such as inspecting a callback or running a test can be steps of the same todo rather than separate failure budgets.

Todos do not require new GitHub issues or another Planner approval ceremony. They stay within the existing ticket plan. Material scope, architecture, plan-assumption, or acceptance changes still return to the Planner; a local checklist cannot silently redefine the ticket.

The Manager MUST supply the implementer with ticket/plan/todo references, assigned identity, exact branch and baseline, workspace and unique mission-owned temporary space, reserved devices/services, resource limits, todo failure history, and direct reporting/Owner-contact paths. For local execution, specify the exact worktree and scratch paths. For a remote tool-only session, specify its repository/branch access and verify available capabilities rather than inventing local paths or tools.

Every mission identifies its affected target(s): Phone, RG, shared core, or cross-device integration. Mark experiments separately. Acceptance remains defined by the authorized ticket and its Planner plan; the assignment adds operational details rather than rewriting either.

### 5.4 ChatGPT implementers and local helper Workers

When ChatGPT is assigned as a Worker or Expert, the Manager MUST assign and start a separately registered local helper Worker for local builds, end-to-end/device verification, and debugging evidence. A suitable existing local Worker may take that explicit helper assignment; otherwise spawn one. A helper is a Worker assignment, not another permanent role or an independent owner of the same todo.

The ChatGPT implementer codes, runs the tests actually available in its cloud environment, commits and pushes its assigned branch, and reports `CANDIDATE_READY` with the exact SHA, executed checks, and unavailable checks. The Manager verifies the remote candidate and dispatches local verification of that SHA to the helper. Required local evidence is still required when cloud tests pass.

The helper reports actual source/APK/device bindings, test results, reproduction evidence, and limitations to the Manager. The Manager routes technical findings back to the current Worker or Expert. The helper's supporting checklist and reports refer to the same todo, attempt, and candidate; they do not create a duplicate retry budget for the same objective.

One implementer owns source writes at a time. The default helper assignment is verification/debugging; if the helper is assigned a correction, the Manager explicitly transfers write ownership and coordinates the remote implementer. A changed candidate requires affected verification again. Transport delivery, a cloud candidate, or a local test pass alone is not independent acceptance.

The Manager MUST verify the selected session's editing, test, GitHub, and return-message capabilities before relying on them. Use the intended authenticated project/conversation and observed model/effort controls. An agent-created conversation returns its exact full conversation URL before registration or assignment; a project URL, title, visible send bubble, or guessed ID is insufficient. Missing capabilities are reported and assigned to the appropriate environment; no agent claims unavailable tools, unverified unattended execution, or unperformed tests. Browser observation, bounded connection recovery, and helper launch mechanics belong in `DEV.md` and installed skills, using Section 15.3 for message semantics. Preserve private browser state and keep credentials or token-bearing diagnostics out of model context and shared records.

## 6. Implementation preflight and batch execution

### 6.1 Entry and preflight

An open canonical issue labeled `ready-for-agent` places the project in implementation status and prompts Manager preparation. The label does not replace version authorization, preflight, or a ticket plan. Standalone Owner-authorized spikes follow Section 10.

An implementation run is the authorized set of tickets being executed; a batch is the concurrent subset currently assigned.

Before the first batch, the Manager MUST:

1. Establish the authorized run scope, refresh issues and repository state, and reconstruct the dependency DAG from `Blocked by:` references. Resolve cycles, missing prerequisites, and contradictory readiness with the Planner.
2. Ask the Owner for the maximum number of concurrent Workers and obtain that limit. Account for local helper Workers and Expert resource needs in the run settings; do not silently bypass a limit by changing a role label. Reuse existing explicit authorization and serialize work or obtain an explicit resource exception when needed.
3. Present all known and anticipated human gates, with the required Owner actions/decisions, and ask whether the audible alarm should be enabled for this run.

Record the DAG, ticket scope, concurrency limit, gates, and alarm choice in a durable run record linked from participating issues. Reuse explicit answers already supplied for this same run; settings carry across its batches until changed by the Owner. An earlier run's alarm permission is not permission for a new run.

The Manager reports newly discovered human gates to the Owner before the affected action and updates the gate inventory as plans and execution develop. Missing capabilities and runtime failures are reported explicitly rather than treated as successful preparation.

### 6.2 Form and dispatch a batch

Select unassigned `ready-for-agent` tickets with satisfied dependencies, up to the approved Worker limit. Check existing claims, assignments, and PRs to avoid duplicate dispatch. Shared-resource constraints may require a smaller batch; review activity also stays within approved resources.

For each selected ticket, obtain its Planner-authored ticket plan, establish its todos, register the Worker and any required local helper, reserve resources, and provide the complete mission assignment. Record batch membership and dispatch on the issues; remove `ready-for-agent` when dispatch is recorded.

The Manager MUST confirm receipt and successful startup for all dispatched agents in the batch, including required local helpers. A startup failure becomes a reported blocker. Once dispatch is complete and no actionable report remains pending, the Manager ends its turn and waits for reports.

### 6.3 Event-driven operation and batch progression

Workers, Experts, and Reviewers MUST proactively report candidate readiness, completion, failure, blockers, human gates, material discoveries, and required Manager decisions. Reports or meaningful external events resume orchestration.

The Manager handles each ticket's review, Expert escalation/handback, and closeout as reports arrive while independent Workers in the same batch continue. Routine progress polling is prohibited; targeted inspection is allowed for an Owner-requested status check or recovery from a specific suspected runtime/communication failure.

Start the next batch only after every ticket in the current batch completes closeout, unless the Owner explicitly changes the batch scope or scheduling policy. Expert work, local-helper verification, review, handback, or cleanup of the current batch is not a new batch; finishing one ticket does not authorize backfilling its slot with another ticket.

After a batch finishes, refresh the DAG, verify dependency outcomes, update readiness, request the next tickets' plans, and repeat. A closed prerequisite is satisfied only when its actual outcome meets the dependent ticket's requirement.

Implementation remains active through ticket planning, todo execution, Expert help, local verification, review, and cleanup. No ready tickets does not mean completion: report remaining blocked work and the required decisions. Once all tickets in the authorized run are finished, report consolidated results and any remaining version-level acceptance gates to the Owner. Implementation completion does not itself authorize release publication.

## 7. Owner notifications, human gates, and alarms

The Manager MUST inform the Owner when a todo is escalated to Expert, identifying the ticket/todo, failed approaches, candidate/evidence, and assigned Expert. Mark this as informational: no approval or reply is required for the handoff or continued in-scope Expert work. A delayed acknowledgement does not turn notification into a permission gate.

The Manager MUST contact the Owner when execution genuinely requires Owner judgment, authorization, or unavailable human capability. This includes product/architecture decisions, protected-article review, critical quota/resource limits, unavailable required models/runtimes, and blocking infrastructure/authentication failures. Expert work has no failure-count escalation gate, but these existing human gates remain.

The Worker, Expert, or local helper performing physical work SHOULD contact the Owner directly when an action still requires human capability or authority, such as wearing/moving glasses, pairing, reconnecting, or confirming unobservable behavior. Device-specific standing authorization may permit guarded ordinary unlock/relock under Section 9.5 without a new Owner prompt. It does not authorize other devices or resume paused work. Inform the Manager on entering a genuine gate and report material observations, decisions, evidence, and resulting state after resolution.

Gate reports MUST identify the affected ticket/todo, exact action/decision needed, attempted recovery, evidence, and current safe state. Preserve that state while waiting. Material Owner changes received directly by an agent return to the Manager for reconciliation.

Owner messages MUST distinguish informational notifications from action/approval requests. Admit Owner instructions only from the configured authenticated Owner identity and permitted destination, and bind approvals to the actual request, operation, or exact candidate. An Owner contact binding, display name, bot reply, delivery receipt, or unrelated reply is not authorization. A messaging-created model session is not automatically the registered Manager: route authorized instructions to the current Manager identity and verify receipt. Unverified inbound control remains disabled or gated while independently verified outbound notification may be used. Report delivery failures through an available fallback and keep material decisions durably; successful text delivery does not establish banner, sound, voice-call, or phone-urgency capability or permission.

Always provide a written notification or gate report. Sound the alarm through the approved `DEV.md` mechanism only under the current Owner-approved alarm policy; routine Expert escalation is informational and does not itself request an audible alarm. An unavailable or disabled alarm does not waive communication. Sudo operations retain Section 9.3's password-entry procedure.

## 8. Visibility and execution state

Workers, Experts, Reviewers, and local helpers MUST remain observable and directly reachable by the Owner. Local sessions use separate visible windows in the Manager's tmux session according to `DEV.md`. ChatGPT and other remote runtimes use their approved registered conversation/session surface; a local helper does not replace the remote agent's identity.

Record meaningful mission states such as `RUNNING`, `BLOCKED`, `WAITING_FOR_PLANNER`, `WAITING_FOR_EXPERT`, `WAITING_FOR_LOCAL_VERIFICATION`, `WAITING_FOR_OWNER`, `CANDIDATE_READY`, `COMPLETED`, and `FAILED`. These are execution records, not new GitHub labels or contributor lifecycle values. Map them to the installed todo tool without assuming it supports these exact enum names.

## 9. Concurrent work, private boundaries, and cleanup

### 9.1 Assigned private space

The Manager MUST assign each local Worker, Expert, and helper an exact worktree and a unique mission-owned temporary directory. Remote assignments follow Section 5.3. Concurrent writers use isolated worktrees/branches with coordinated source ownership; an Expert or helper takeover transfers the specific write assignment before edits begin.

Workers and Experts, including local helpers, MAY freely create, modify, and delete mission files and temporary artifacts within their assigned private spaces, subject to approved mission scope and Section 3.

Shared Git metadata, other worktrees, and external resources reached through symlinks or mounts are outside those private boundaries. A path inside an assigned directory does not make its external target privately owned.

### 9.2 Shared resources and operation approval

Deletions outside assigned private space, destructive shared-resource changes, and operations with uncertain ownership MUST receive Manager approval for the specific operation.

Ordinary mission-approved builds and toolchain-cache activity do not require per-file approval. This permission does not authorize purging shared caches or deleting unrelated files. Ordinary mission-approved Git operations continue under Section 13.

The Manager coordinates connected devices and shared test services. Conflicting mutations require exclusive resource ownership; cross-device tests reserve all affected resources together. Verify physical-device identity separately from its current transport endpoint before mutation. A stable address, MAC/IP binding, or open port proves neither authenticated access nor continued reachability. Use the approved connection priority and recovery procedure without restarting a working listener solely to switch transport. Shared-user file permissions and advisory reservations do not establish isolation between agents using the same host account.

### 9.3 Sudo escalation

For resource-operation approvals under this section, the Manager handles non-sudo decisions and escalates only commands requiring `sudo` to the Project Owner.

For a sudo operation, the Manager MUST open a separate pane in the same tmux window as the Manager, briefly explain the specific command and why it is needed, and present the interactive password prompt for the Owner to enter directly in that pane. Credentials remain out of messages, logs, and repository files.

This resource-approval rule does not replace the other Owner gates in Sections 3 and 7.

### 9.4 Evidence and cleanup

Preserve evidence required for review and final acceptance before cleanup. Other mission-owned temporary artifacts may be removed; retaining every temporary artifact is not required.

Post-merge cleanup follows Section 13. Worktree removal and destructive shared-Git operations require approval for those specific operations. Exact workspace, resource, and sudo-pane procedures belong in `DEV.md`, subject to Section 3.

### 9.5 Device-scoped screen access

The Owner may authorize specified local agents to wake and normally unlock a specifically identified device for reserved, necessary test/debug work. Apply only the recorded device, agent, purpose, and operation scope. All authorized local agents may use that permission when included; it is not Manager-exclusive and does not grant concurrent use. Other devices, desktops, paused missions, and unrelated actions remain outside the grant.

Use the approved guarded helper to verify physical serial/model, user and keyguard/keypad state, perform ordinary authentication, and bound the associated commands. Submit a PIN at most once per authorized unlock attempt; unexpected UI, failure, lockout, or uncertainty stops further entry. A trusted helper may read the private credential internally; agents MUST keep the secret out of model context, messages, command arguments, logs, screenshots, artifacts, and repository files. Runtime holds only the private reference and non-secret policy. This grants neither security bypass nor removal of the normal lock.

Immediately relock when the reserved work finishes or fails, and verify actual lock state; a dark display is insufficient. Preserve cleanup on recoverable exceptions. After forceful termination, power loss, or total transport loss, report any unverified lock state and contact the Owner rather than claiming guaranteed remote cleanup. Device authorization and successful setup tests do not change product targets or acceptance requirements.

## 10. Experiments and architecture spikes

An explicitly Owner-authorized spike may precede a settled version plan or product architecture. It still requires a Planner-created ticket, a Manager-requested ticket plan, implementation preflight, bounded scope, and independent review of its evidence.

The ticket MUST identify the exact question, expected evidence, pass/fail interpretation, experimental assumptions, and production decisions outside its scope.

A well-supported negative feasibility result may complete an investigation when the ticket defines that outcome. It is different from an unsuccessful attempt to implement or validate the assigned experiment.

Experiment results inform later Owner/Planner decisions; they do not automatically replace `SPEC.md`, authorize a version, or become production architecture.

## 11. Execution and completion evidence

Workers and Experts MUST implement within the ticket plan and assigned todo, perform a simplification/ablation pass after implementation and corrections, rerun affected verification, and commit/push a reviewable candidate for a change mission. A cloud-to-local handoff uses `CANDIDATE_READY`; `COMPLETED` means the assigned work and required verification are ready for acceptance, not accepted.

Reports MUST identify the ticket, plan and todo, attempt and responsible agent, branch/workspace, exact remote commit, changed files, criteria/results, executed verification with evidence links, simplifications, and remaining risks or missing evidence. Distinguish cloud checks from local-helper checks and identify which candidate each actually exercised.

An unsuccessful completed attempt reports `FAILED` or `NOT_PASSED` with evidence and recoverable working state. Any pushed checkpoint is identified as incomplete rather than a passed candidate. Expected local checks still pending are a handoff/wait, not an already-concluded failure. Read-only investigations report agreed evidence instead of manufacturing code changes or a PR.

Detailed logs may remain in artifacts or CI. Issue/PR reports summarize and link evidence. Distinguish executed results, Owner observations, and inference; redact credentials and sensitive browsing content. Marking a todo done in a tool does not replace required verification or independent acceptance.

## 12. Target-specific verification

Shared-core changes MUST assess both Phone and RG consumers. Phone-only, RG-only, and cross-device claims require the corresponding evidence defined by the ticket plan.

Hardware-sensitive evidence MUST identify the actual device, relevant Android/One UI/YodaOS and WebView versions, application commit/build, commands/procedure, results, and limitations. Relevant Phone checks distinguish cover and inner display behavior.

Synthetic input and emulators support only the behavior exercised. Head-motion ergonomics, optical readability, hardware interaction, and other physical claims require appropriate real-device evidence when claimed. Apply the Owner-approved acceptance profile: required missing evidence remains open; explicitly permitted unexercised physical conditions remain documented limitations, not fabricated PASS or a reinstated hidden gate. An operationally authorized test device does not silently replace a specified product target.

Build modes, toolchain procedures, and verification commands follow the approved `DEV.md` and ticket requirements; a plan cannot silently introduce an Owner-restricted release gate.

## 13. GitHub workflow, review, and closeout

### 13.1 Ownership and delivery

| Action | Responsible role |
| --- | --- |
| Create/revise canonical tickets, planned dependencies, and acceptance | Planner |
| Produce version plans | Planner, at Owner request before version implementation |
| Produce ticket plans | Planner, at Manager request during implementation |
| Manage execution labels, claims, batches, and resources | Manager |
| Commit/push implementation and corrections | Assigned Worker or Expert; a local helper only under explicit write assignment |
| Open/manage PRs, request review, merge, coordinate cleanup, and close issues | Manager |
| Record independent findings and verdict | Reviewer |

The assigned Worker or Expert pushes its branch and reports to the Manager. The Manager verifies the remote candidate and opens a linked PR, or updates the existing ticket PR. Cloud candidates can enter a draft PR while required local-helper verification is pending; they remain unaccepted. Use non-closing issue references so merge does not bypass post-merge closeout.

Implementation changes reach `main` through this PR workflow. Protected-article changes follow Section 3; contributor-registry maintenance follows Section 15.1. Follow-up work discovered during execution is proposed through the Manager to the Planner rather than becoming unauthorized new tickets.

### 13.2 Independent review, merge, and closeout

Worker/Expert `COMPLETED` means ready for acceptance, not accepted. Todo handback under Section 14.4 does not itself merge or close a ticket.

The Manager registers the Reviewer and supplies the approved requirements, ticket plan, relevant todo records, exact base/head commits, diff, and cloud/local verification evidence. The Reviewer records one verdict against the exact candidate:

| Verdict | Meaning |
| --- | --- |
| `PASS` | Candidate satisfies the required review and acceptance criteria, supported by evidence |
| `CHANGES_REQUESTED` | Correctable implementation defects, requirement violations, or incomplete deliverables/evidence |
| `BLOCKED` | An external decision, access, or unavailable prerequisite prevents judgment |

The Reviewer reports concrete findings and supporting or missing evidence. Product/specification ambiguity returns through the Manager to the Planner/Owner. Worker/Expert self-review and a helper verification report do not replace independent review.

Corrections follow Section 14. Every changed PR head requires a renewed Reviewer verdict and applicable verification. The Manager checks the exact current head, required checks/device evidence, and resolved acceptance gates before merge, then verifies the remote merge result. Check compatibility with current `main` after other batch merges; material integration changes require renewed review and affected verification.

After merge, the Manager MUST coordinate cleanup with the ticket Worker and any remaining assigned Expert/helpers, verify the cleanup reports and retained evidence, release resources, retire ticket-scoped participants with no remaining assignment, update `CONTRIBUTORS.md`, record the final result, and then close the issue. Experts normally finish their scoped assignments at todo handback under Section 14.4. If an agent is unavailable, arrange bounded replacement cleanup and record the handoff. Cleanup remains pending until verified.

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

At a genuine human gate, the responsible agent reports it and the Manager applies `ready-for-human`. Expert escalation, Expert handback, and waiting for a local helper remain agent work and do not themselves receive `ready-for-human` or requeue the ticket. After resolution, resume an existing assignment without creating another Worker; restore `ready-for-agent` only for an unassigned ticket whose prerequisites are satisfied. Waiting on another agent is not itself a human gate.

The Planner/Owner decides rejection or out-of-scope disposition; the Manager applies the outcome and closes with an explanation. Closure alone does not establish successful completion or satisfy dependencies.

### 13.4 Durable records

Agents MUST record these material events with their registered role/name attribution:

| Role | Issue or version/run record | PR record |
| --- | --- | --- |
| Planner | Version plan and Owner decisions; ticket creation; requested ticket plan; material scope/acceptance/dependency revisions | Required decision clarifications |
| Manager | Run approvals/DAG; batch/todo assignments; human gates; todo failure counts; Expert escalation/Owner notification/Worker handback; verified cleanup, retirement, and closure | Candidate/plan links; review and local-verification requests; correction ownership; merge and acceptance result |
| Worker / local helper | Todo progress, material failures/gates/discoveries, candidate or verification evidence, handoff receipt, cleanup result | Candidate/correction SHA, exact local checks, findings addressed, remaining limitations |
| Expert | Assigned todo diagnosis, attempts, verified resolution or genuine blocker, and handback package | Expert commits, relevant findings, verification, and residual risks |
| Reviewer | Ambiguities or verdict for missions without a PR | Verdict, exact reviewed commit, findings, evidence, and unresolved limitations |

Link existing evidence instead of duplicating transcripts. When a record requires another agent to act, notify that agent through the established communication path as well; posting to GitHub alone is not delivery confirmation.

## 14. Todo-level failure escalation and Worker handback

### 14.1 Attempt accounting

Two completed unsuccessful Worker attempts on the same todo trigger Expert assignment. An unsuccessful attempt is one bounded implementation or correction round concluded as `FAILED`, `NOT_PASSED`, or Reviewer `CHANGES_REQUESTED` for that objective. A helper report and review of the same round are one outcome, not two attempts. Multiple findings in one round count once per affected todo; do not charge unrelated todos for a ticket-wide verdict.

Expected red tests and intermediate debugging failures within a round are not separate attempts. An established external infrastructure/access/human blocker is reported as blocked, not an implementation failure. An incomplete required deliverable without an established external cause remains not passed when its round concludes. Each round has declared scope and resource limits; it cannot be kept open indefinitely to avoid accounting.

The Manager records todo identity, attempted approach, outcome, candidate/evidence, and Worker failure count durably. Renaming, splitting, reopening, moving, reassigning, changing models, or restarting sessions preserves the history attributable to the same unresolved objective. Genuinely different todos have their own counts. Internal helper checklists do not duplicate the parent todo's budget.

### 14.2 Worker to Expert

After the first unsuccessful Worker round, the Manager routes the findings for a second round. At the second unsuccessful round, the Worker stops independent correction of that todo and reports its approaches, failed criteria, current code/evidence, and safe state.

The Manager MUST assign a capable Expert to that todo, register the session, and record the return Worker, candidate, evidence, and explicit source/resource handoff before Expert execution. Inform the Project Owner. This is an informational escalation, not an approval request. The Expert proceeds within existing scope and resources without waiting for Owner acknowledgement. An unavailable Expert runtime or genuinely missing authority remains a reported blocker, not a silent return to Manager troubleshooting.

Keep the original ticket Worker available for handback or local-helper work. Unrelated approved todos may continue only under existing dependencies, write ownership, concurrency, and batch rules. The Expert does not inherit all remaining todos merely because one escalated.

### 14.3 Expert execution

The Expert owns diagnosis and correction of the assigned todo with no fixed attempt-count ceiling and no per-attempt Owner permission gate. Its scope, resource/compute limits, command timeouts, security boundaries, evidence requirements, and Owner stop instructions still apply. The Manager coordinates; it does not become the technical recovery implementer.

The Expert commits/pushes its own corrections, verifies the todo's required outcomes, records meaningful findings and changes of approach, and uses a local helper under Section 5.4 when running in ChatGPT. It may adapt implementation within the approved plan; material plan, architecture, scope, or acceptance changes return through the Manager to the responsible Planner/Owner. Report genuinely missing access/resources promptly rather than repeating ineffective operations without new evidence.

### 14.4 Expert to Worker

When the todo is fixed, the Expert sends the Manager a handback package: exact candidate commit, diagnosis and correction, todo-specific verification (including required local-helper results), relevant review findings, remaining limits, and continuation notes. Missing required checks keep that todo unresolved; a cloud-only pass cannot stand in for required device verification.

The Manager verifies the handback against the todo's completion condition and resolves any required review findings with the independent Reviewer. This is a todo-scoped check, not a new whole-ticket acceptance ceremony. Final exact-head PR review remains mandatory under Section 13.

The Manager MUST return ownership of the ticket's later todos to its Worker, supply the accepted handback commit and notes, and confirm receipt and safe workspace reconciliation before the Worker resumes. Use the original Worker when available; otherwise explicitly assign/register a replacement. The Expert relinquishes implementation ownership after handback and does not continue later todos by default. No additional Owner permission is required for this return.

Preserve Expert evidence, clean up or transfer its assigned resources, and retire the Expert and any Expert-only helper when their assignment ends. Registry retirement alone does not authorize deleting conversation history, browser profiles, or credentials. A local helper serving as the ticket Worker returns to its normal Worker duties rather than being retired. Retaining an Expert for a further assignment requires an explicit Manager assignment within approved authority, not automatic takeover of the ticket.

Later, distinct todos use their own two-failure Worker threshold. If the same resolved defect recurs or its acceptance is rejected later, reopen its history and return it to Expert without manufacturing two fresh Worker attempts. Todo completion, successful handback, and ticket/PR acceptance are distinct events.

### 14.5 Cutover from legacy ticket budgets

At rollout, preserve existing ticket-level Worker/Manager counts and evidence as history; new decisions use todo-level accounting and Expert escalation. Map documented failures to the actual unresolved objective rather than automatically charging every todo or inventing fresh attempts. If attribution is ambiguous, retain that uncertainty and assign Expert assistance for the unresolved repeated failure instead of falsifying a count.

A hold solely caused by the superseded retry ceiling can transition to Expert work only under an authorized activation of this workflow. Record the change and notify the Owner; retain separate access, security, scope, explicit-stop, or other human gates. An explicit Owner pause requires explicit Owner resumption and survives document publication, capability checks, and history migration. Preserve current candidates and assignments, then apply the same Expert-to-Worker handback. Availability fallbacks remain separate from engineering failure accounting.

## 15. Contributor identity and communication

### 15.1 Contributor registry

Root `CONTRIBUTORS.md` is jointly maintained by Planner and Manager. It contains one canonical fenced JSON array with eight fields per actual participant:

```text
role, scope, host, runtime, id, model, name, status
```

Register each session before its first project mission or attributable repository action. Workers, Experts, Reviewers, and local helpers supply metadata/changes to either maintainer. A new Planner's registration may accompany its first authorized governance commit.

Register an Expert with `role: Expert` and its actual scope. Register a local helper with `role: Worker` and a scope/name identifying its supporting assignment. Keep todo IDs, pairing links, write ownership, and attempt counters in runtime/todo records rather than adding registry fields.

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

### 15.3 Canonical agent-message envelope and routing

Direct agent messages MUST include both registered readable identity and exact session identity for sender and recipient. This section is the sole normative definition; `DEV.md`, skills, and runtime adapters reference and implement it rather than maintaining another format definition.

```text
From: <Role>: <name> | host=<host>; runtime=<runtime>; id=<id>
To: <Role>: <name> | host=<host>; runtime=<runtime>; id=<id>
Request-ID: <unique-message-id>
In-Reply-To: <request-id being answered; omit for an initial request>
```

Equivalent structured transport fields are permitted only if all required identity/correlation fields are preserved and the visible message shows role/name. The `(host, runtime, id)` tuple is the authoritative session locator; role/name identifies its registered assignment, not extra authority. Resolve current registry state before dispatch or handoff. A disagreement between readable identity, tuple, or authenticated route MUST be reconciled before acting, not silently accepted under whichever field is convenient. Use the exact full conversation URL as a ChatGPT ID, without inventing IDs or treating formatting as part of the locator.

Use stable message IDs to correlate receipt and reply; retries of the same logical send retain its identifier. Replies reference the request being answered. Include applicable ticket/todo, attempt, candidate, and evidence references in the payload. Record send, receipt, reply completion, verification, and acceptance as distinct facts. A delayed or duplicate reply must not create another dispatch, attempt, approval, or source handoff.

One conversation has one coordinated request stream. Preserve pending-request and ownership state durably across restart and reconcile uncertain delivery before resending. Transport adapters may perform bounded observation/recovery; the Manager remains event-driven rather than repeatedly polling conversations for progress. An observed assistant reply proves neither execution of all requested work nor its correctness.

Required routes include Manager-to-Planner ticket-plan requests, Planner-to-Manager plan delivery, Manager-to-Worker/Expert/helper assignments, implementer/helper/Reviewer reports to Manager, and Expert-to-Worker handback via Manager. Owner-to-Planner version-plan requests and Owner notifications/gates use Section 7's authenticated human channel; a human is not required to have a fabricated agent-registry tuple. Delivery machinery and connection diagnostics belong in `DEV.md`, skills, and runtime. Text headers are not transport authentication.

## 16. Runtime and documentation boundary

EyeBrowse starts with its own harness, without Matt's skills, Ponytail, or pi-fff. The label vocabulary in Section 13.3 is retained independently. Governance remains portable across authorized models, hosts, and runtimes.

`DEV.md` defines approved development procedures, runtime/model policy, build/test/device workflows and connection priority, visible launch mechanics, and references to the installed todo, browser/ChatGPT, device-screen, and Owner-messaging skills. It implements cloud/local handoffs, alarm/sudo procedures, and recovery while referencing this file for authority, acceptance, escalation, and canonical message semantics. Commands and reusable operational detail belong in the verified skills/scripts; addresses, private credential references, and live assignments belong in local runtime. Avoid independently editable copies of the same rule or setting. The Manager distinguishes verified facts, proposed procedures, and unresolved limitations.

Harness/runtime configuration holds executable settings, installed integration versions, and provider mappings; secret stores or environment configuration hold credentials. Actual todo IDs/counts, assignments, Expert return targets, cloud/helper pairing, discovered addresses, request state, run-specific Owner choices, and test outcomes belong in durable todo/mission/runtime/evidence records. Reuse the installed todo tool where it preserves the required state; reference or supplement missing metadata in existing run records without creating a competing source of truth. The owning procedure or loader must consume the records: editing a configuration file alone is not execution.

Changes to tools, scripts, or configuration remain within approved scope and authority; moving a rule out of a protected article does not itself authorize changing that rule. A successful skill-loader check or one transport/helper smoke test is evidence only for what it exercised, not proof of end-to-end orchestration. Before activation, qualify the affected dispatch, escalation, local-verification, Expert-to-Worker return, and restart paths. Use labeled simulations where appropriate rather than product retries or fake contributor registrations. Stage changes, preserve in-flight operations, and reload affected sessions at safe points; remaining capability gaps block only dependent operations unless a genuine broader gate applies. Publication of governance does not itself activate a runtime or resume Owner-paused work. This file does not require converting `DEV.md` to another format.

## 17. Product boundary

EyeBrowse targets Phone and RG. Runtime ownership, supported features, architecture, and version-specific exclusions belong in the approved specification and decisions, not a duplicated architecture snapshot here.

Implement the authorized version and ticket. Preserve design evidence only for the decisions it actually supports. Keep experiments, implementation completion, acceptance, and release publication distinct.

Optimize for verified outcomes: bounded work, minimal implementation, preserved evidence, direct communication, and coherent project state.
