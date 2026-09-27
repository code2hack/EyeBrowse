# ACK loss and duplicate effects (#11)

`AckLossJourneyTest` runs paired Phone/RG companions against the existing
`/control.html?case=<mission UUID>` fixture. It requires canonical pairing,
exclusive USB routes, a checked setup and four-APK binding before entry. These
are executable procedures; compiling them is not device acceptance.

| Group | Phone method | RG method |
| --- | --- | --- |
| ACK matrix | `observesExactlyFourEffectsDespiteLostDelayedAndDuplicateResults` | `duplicateLostAndDelayedResultsDoNotRepeatEffects` |
| Recovery | `observesContinuityAndTwoEffectsAcrossUncertainReconnectAndStop` | `uncertainActionRetiresAcrossReconnectAndStop` |

Invoke each method with `am instrument -w -r -e class <package>.AckLossJourneyTest#<method> -e missionId <UUID> <package>.test/androidx.test.runner.AndroidJUnitRunner`.
Phone additionally receives `-e fixtureBaseUrl http://127.0.0.1:27341`.
Use a different UUID per group. Require terminal `OK (1 test)` on both devices;
no failed-row retry. Each command is bounded to 65 seconds; the guarded child
is bounded to 180 seconds. App link remains 39818; fixture ports 27341/27342;
only the owned Phone USB reverse for 27341 is needed. Preserve other mappings.

The host reads only exact mission `cache/i11-<UUID>.phase` files through the
respective debug `run-as`. Start Phone, wait for `ready`, then start RG.
For the matrix, forward RG `done` to Phone `cache/i11-<UUID>.ack`.
For recovery, forward RG `disconnected` to Phone, wait for Phone `reconnect`
and forward that to RG, then forward RG `stop` to Phone. Capture scoped
screenshots at ready and before forwarding done/disconnected; retain matching
`EyeBrowseAckLoss` logs, command transcripts and terminal results. These phase
files carry coordination only, not success or effect assertions.

The RG androidTest wrapper holds selected **real authenticated decoded**
results and duplicates current status/state/results. It does not simulate
packet loss or forge server admission. All requests use the production
sender. A repeated action uses exactly the same ID/context/ordinal; it must
receive `STALE_COMMAND_SEQUENCE`, while the fixture records one effect.
A lost result is discarded permanently. Delayed result A is released twice
while B is pending; only B's matching result may retire B. Production 5000ms
timeouts remain unchanged; surface-status observations have a predeclared
500ms scheduling allowance, measured from the explicit send. A surface
observer forwards every call unchanged, so frame/status coalescing is not
suppressed to manufacture an uncertainty receipt.

Lost handoff results reconcile through authoritative state. With both result
and state withheld, the ordinary handoff timeout must release the pending
request without retry. Duplicate accepted results/state must not advance
ownership again. Host `PendingHandoffTest` also checks an old accepted result
cannot clear a newer opposite request. The existing lifetime/epoch rejection
checks remain; no action-ID correlation is invented for handoff messages.

The recovery group interrupts only the EyeBrowse link after a real click but
before ACK delivery. It checks pending retirement, unchanged lifetime/document/
owner epoch, increased viewport epoch, current frame identity before input,
old-context rejection, no automatic action allocation, and actual Stop with
another result outstanding. Test-held callbacks are discarded on retirement;
calling an old listener directly after reconnect would bypass production
session fencing and is not used as evidence for that fence. Existing
`AuthPublicationRaceTest`, cancellation and stale-session ownership tests
retain the transport boundary evidence.

Phone independently reads fixture sessionStorage for exactly four/two click
events, checks the same WebView/document/history/unsaved field, then verifies
Stop releases the link and hosting resources. ACK `effectSucceeded=null` is
never evidence of website success. RG logs action IDs, context, frame sequence
and capture timestamp. Host `PresentationInboxTest` proves duplicate grants
cannot reset sequence/display fences; the existing transport tests cover
framing and bounded queues. No separate device frame-replay claim is made.

Finally restore the original listener/surface, stop owned app operations,
remove exact mission fixture/phase/ack state, remove owned reverse/fixture,
verify raw canonical hashes unchanged, and use the qualified USB screen-off
adapter (no keyguard lock operation). Preserve primary failures if cleanup
also fails. #10-integrated editing and continuous input remain a separate slice.
