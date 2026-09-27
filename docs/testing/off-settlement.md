# Bounded pre-admission OFF settlement (#11)

Authority: Planner ruling5856613738, continuation
MGR3-T02-I11-SETTLEMENT-IMPLEMENTATION-ROUTE-20260927-01.

`PrivateDisplayHost.resizeProfile` retains one existing profile request and its
original absolute elapsedRealtime deadline. Only a valid, owned, focused request
whose display is OFF can wait. `ProfileOffSettlement` posts one Main check every
20ms (or the remaining budget), independently of animation/draw. Existing timeout
ownership remains; no deadline, profile epoch, capture lease, or transport attempt
is renewed by a check. Reader allocation and native mutation require ON. All other
guards still fail; native display state is never changed to force readiness.

Original window/display/WebView/document/reader identity and wait-entry focus-loss
serial remain fenced. The controller additionally binds transfer/epoch/host and
document ownership; the publisher captures original authenticated session/control
context and native host/window ownership. Retirement is propagated separately from
current failure, so late false/timeout callbacks cannot trigger successor teardown.
Native allocation, resize and surface replacement recheck ownership/state/deadline;
completion at or after D fails. Reader-retirement bookkeeping only clears its own
reader. A state change after native staging is governed by existing failure cleanup;
no partially applied native operation is retried.

14 deterministic `ProfileOffSettlementTest` cases execute the production admission
wait driver with injected observations, clock and queue: OFF->ON, already-ON,
persistent OFF, ON at/after D, late wake, admission before D with completion at/after
D, every non-state guard category, guard loss while waiting, focus ABA, ownership/
identity retirement, dequeued cancellation, scheduling failure, timeout collision
coalescing, and ON returning to OFF before native staging. Allocation/mutation counters here are admission-callback evidence,
not real Android resize/frame evidence. Existing HostingPresentationProfileTest
covers epoch identity independently. The Android caller wiring and capture path
also require the changed-head device checks below; compilation does not establish
hardware settlement or every platform interleaving.

Device request consists of three bounded slices on the same four-APK candidate:

1. Both ReviewRecoveryJourneyTest companions (editor/recovery/navigation, then
   Stop-mid-reconnect), with all unchanged effects, old-intent and cleanup oracles.
2. Paired ReadingJourneyTest and KeyboardJourneyTest for the affected #10 consumer
   and capture/profile paths.
3. Phone HostingInstrumentedTest methods
   `delayedCommitFromSupersededProfileCannotPublish` and
   `retainedOldReaderCallbackDuringDelayedSettlementCannotUnblockReadiness`.

Each slice uses the existing65s per paired command (or single instrumentation),
180s qualified guard, checked USB setup, exact APK/identity/trust binding, RG wake,
owned fixture27341/27342 and Phone reverse27341, no retry. Stop on the first failed
slice. No display/surface/focus intervention is used to manufacture OFF->ON.

Retain `EyeBrowseProfileSettlement` records: initial native state and D, ON
observation, native admission, first-frame publication and capture timestamp,
terminal callback and old-reader retirement timestamps. These events use Phone
elapsedRealtime; observation/cleanup may be later than D and are labeled separately.
An all-ON pass is not an OFF-wait pass. Persistent OFF that cannot settle without
an extra platform intervention returns to Manager/Planner; the deadline and guards
must not be relaxed. Prior failed/successful receipts remain bound to their heads.
