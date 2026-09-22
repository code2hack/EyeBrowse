# I8-T03 outcome B — one prepared durable ordinal

Worker: Worker-v0.01. This implements the [binding Planner supplement 5774967990](https://github.com/code2hack/EyeBrowse/issues/8#issuecomment-5774967990) on the 99775e6 lineage. [R2 plan 5771447930](https://github.com/code2hack/EyeBrowse/issues/8#issuecomment-5771447930) otherwise continues; no Phone admission, v2 wire format, ownership, transport, viewport, keyboard or Reading behavior changes.

## Reason and boundary

The completed frozen-head study had 24 target measurements (12 ordinary and 12 busy-adjacent). Ordinary O02 took145ms from confirmedAt to finishedAt, including137ms wall/6msCPU in the original durable commit. The new observation localized a production-stage violation; it did not recover the unlogged historical failure or establish a particular filesystem/scheduler cause. All failed records remain under the issue8 mission's t03/isolation evidence root.

Preparation moves durable allocation before action eligibility. It does not return early from an accepted action and enqueue that action later. A successful action return still means synchronous admission to the existing authenticated session queue, with a previously confirmed durable ordinal. The100ms dispatch endpoint and separate recognition-wait measurement remain unchanged.

## Process owner and state

CommandSequence is one process-coordinated owner shared by RG controllers. It retains at most one ready number, one active IO job and one coalesced current demand. No coordinates, gesture, action, prebuilt message, history queue or range allocation is retained there.

    IDLE -> PREPARING -> READY -> consumed -> PREPARING
                 |         |
               FAILED    abandoned by lifecycle/authority change

A controller claims a consumer token. A new claim invalidates the previous ready slot and demand. Pause, link invalidation, explicit retry and close invalidate publication without pretending to interrupt an in-flight synchronous write. The physical job remains active until it returns; only then can the latest demand start another write. Old completion can advance the stored floor but cannot publish an ordinal into a successor consumer. Retired consumers cannot cancel, consume or refill the new owner.

The namespace remains lifetimeId + controlEpoch. Document, viewport and hosting changes in that namespace neither reset nor pre-bind the number. Successful preparation advances exactly one allocation; without abandonment the sequence is +1. An abandoned ready number stays burned. Reconstruction allocates above stored reserved-through high-water. A genuinely new authoritative namespace follows the existing reset contract; stale lower epochs in the current lifetime cannot roll storage backward.

## Store, failure and lock order

CommandSequenceStore keeps the existing browser-command-sequence SharedPreferences cursor. Its separate browser-command-sequence-integrity initialized marker is allocation metadata, not pairing/trust state. It makes missing expected cursor data fail closed across process reconstruction; existing cursor fields remain the upgrade floor. Malformed/missing fields, nonpositive sequence, invalid namespace, exhaustion, write failure or uncertainty never produce a sendable slot. The marker and cursor use checked commit results; cached preference changes are not treated as durability. No apply(), data clearing or identity reset is used.

All preference loading and commits run on the single process reservation worker. Store entry rejects Main-thread calls. A validated existing floor remains known even when its first preparation is cancelled or exhausted. Failed/uncertain attempts are conservatively burned within the live allocator; no confirmed high-water is lowered. Cancellation or timeout cannot start a competing writer while the old call can still finish.

Lock order:

- UI/controller operations may enter the allocator's short memory lock, release it, then enter the existing link/session queue locks.
- Store IO and preference-load waits hold none of those locks. The writer never enters a controller or link lock.
- Publication uses only the memory lock. Notifications are posted to Main after releasing it; controller updates coalesce there. Existing engine-held lifecycle callbacks remain marshalled to Main, preserving the T02 inversion fix.
- UI/Activity shutdown never waits for the IO worker.

The preparation failure guard is4000ms from the original demand, shorter than the allowed5s maximum. Frames and repeated demand calls do not renew it. A timeout invalidates publication and visibly reports unavailable controls; it does not terminate the write or permit a second writer. Explicit recovery may establish a new demand. Native Retry retries preparation even when the authenticated link is already healthy; it does not require forcing a disconnect. Actual preparation/readiness durations are reported separately;4000ms is not a normal latency allowance.

## Eligibility and original intent

Remote eligibility is every existing ownership/authentication/compatibility/frame/profile/loading/pending-action/handoff condition plus a ready slot in the current namespace. Preparation is proactive on authoritative RG-session demand; refill starts after consumption and can overlap the preceding action's acknowledgement wait. No gesture is retained awaiting readiness. Pointer drawing, Recenter and local recovery remain available without a slot; handoff is not an action-ordinal side effect.

The local snapshot now includes reservation revision. Validation retains the ORIGINAL expected snapshot through the synchronous controller dispatch, including the ready revision used for atomic consumption. Only then does the consumed number acquire a v2 ID using the validated full current context. Consumption burns the number even if queue admission fails. Refill neither sends an action nor revives a rejected intent.

The adapter also keeps DOWN availability/context through UP for scroll and capture. A background refill between refresh and capture cannot promote an unavailable start. Cancellation preserves the original physical sequence's remaining double-tap suppression window, so the tail of an interrupted double cannot become a new page tap. Idle readiness changes do not add a debounce delay. A targeted host regression failed before this narrow integration fix and passes afterward; no new gesture or Reading behavior is introduced.

Cancellation evidence now counts gesture-bound consumption, message construction and real queue admission, plus independent Phone effects. Proactive high-water advancement is not counted as a cancelled gesture or a sent command. Diagnostics are bounded counters/current snapshots; no replay cache is added.

## Verification contract

Preserve the baseline isolation, then freeze exact identities before the committed-head host gate. Deterministic tests cover slow loads/writes without a UI lock, failed/uncertain persistence, restart/abandonment gaps, duplicate consumers, stale publication, epoch transition and write ordering, queue rejection, corruption/exhaustion, timeout without write cancellation, coalescing and unavailable-intent turnover. Real-store instrumented tests use unique temporary preference names through the production store implementation and delete only those names afterward.

The complete paired T03 journey retains independent Phone geometry/effect oracles, explicit handoffs, all five action types, replay/policy negatives and interruption/recovery. The repeated post-fix matrix includes historical ordinary/busy schedules, cold preparation, rapid eligible actions, refill and same-namespace recreation. Separate delayed-store and unconfirmed-result fault cases prove local draw/control responsiveness, zero effect from unavailable gestures, bounded visible failure and explicit fresh recovery. Every declared positive must actually queue its command and produce its fixture effect.

Trace points include demand, worker start, actual store read/commit with thread/CPU, publication, consumption, canonical queue acceptance, original capture/confirmation, finishedAt, emission and later observation. Readiness intervals are separate from the100ms command metric. Final sample counts, hashes and actual outcomes belong in the mission's predeclared matrix and reports, not a claim of acceptance in this design note. T04 remains held until renewed independent T03 acceptance.
