# Review correction: effect uncertainty and interrupted reconnect (#11)

These test/fixture-only rows address the three findings at8fc0d950. They reuse
`AckLossJourneyTest.DeliveryFaults`, raw-pose/native-pad journey helpers, the real
RG keyboard buttons, and the authenticated production sender. They are compiled
procedures, not acceptance until their paired invocation receipts pass.

| Group | RG ReviewRecoveryJourneyTest | Phone ReviewRecoveryJourneyTest |
| --- | --- | --- |
| R11-3/R11-7 | editorResultsLossRecoveryAndNavigation | observesEditorAndNavigationEffectsAcrossResultFaultsAndRecovery |
| R11-8 | phoneStopRetiresAnInFlightRememberedReconnect | nativeStopWhileRememberedReconnectIsInFlight |

Use the existing65s shared paired terminal deadline/180s guard. Start Phone with
`missionId` and `fixtureBaseUrl`, launch RG only on matching `PHONE_READY`. Relay
`cache/i11-review-<UUID>.phase` RG->Phone and `.ack` Phone->RG. Each distinct phase
is an independent Phone observation before acknowledgement. Stop on abort or
failed terminal result, without starting the next group or retrying.

The first group inserts through actual field keyboard buttons. Hold result A
past the unchanged5000ms uncertainty timer (500ms observer allowance); require
pending retirement and keyboard dismissal. Explicitly activate again, hold B,
then deliver old A twice: B and its target must remain pending/current. Release B,
repeat its exact authenticated request and require stale-sequence rejection.
Phone independently verifies the two-character value and exactly two input events.

Enter executes a real submit handler; discard its result, pass the same unchanged
timer, reject replay, and require exactly one submit with no second input effect.
A third key has a real executed effect with its result still held when the actual
app link is disconnected. Require keyboard/target/pending retirement, old captured
key rejection before/after reconnect, no auto reopen/replay, preserved Phone view/
document/history/value, fresh full context/profile/frame, and a new target only
following explicit field activation. A fresh fourth key is separately observed.

Finally hold a real Reload result through the document change. Require a fresh
matching frame/state while positively asserting the command remains pending and
input disabled; only after result release may action readiness return. Release it twice
late, and resend its exact original ID/context/ordinal: stale-context rejection,
no keyboard resurrection, one navigation only. An opt-in fixture sessionStorage
ledger (`recoveryLedger=1`, key `i11-loads-<UUID>`) stores only a load count, never
field contents. Phone requires loads=2 after initial load plus Reload, and unchanged
count after the duplicate. No exhaustive action/fault cross-product is claimed:
Insert uses lost/delayed/duplicate results, Enter lost result/repeated request,
Reload late/duplicate result/repeated request. ACKs never stand in for effects.
The RG action ledger requires exactly10 consumed/constructed/queued explicit
commands including four activations, five keys and one Reload; raw duplicate
requests use the same ordinal and are counted through Phone effects/rejections.

The second group starts with a real click whose result is held, then disconnects.
After prior-operation quiescence, the existing test-only TCP-connect hook pauses
an actual remembered-peer Retry with its raw socket owned by the engine. It emits
a monotonic receipt and signals a latch; isBusy must be true, input unavailable.
Only then may Phone press native Stop. The Phone acknowledges that barrier after
its original server engine has boundPort=-1, link down and hosting resources gone.
Only that acknowledgement releases the connector to attempt the real address;
its operation must settle without late authority/hosting/input restoration. The
page and pairing persist, the initial click count stays1, Phone UI owns the same
WebView and local Open is enabled. The hook waits at most5s, is restored in finally,
and does not replace authentication or change any product deadline. This proves
Stop during reconnect at TCP entry, not every possible handshake instruction.

Checked USB setup rebinds all4 APKs, verifies identities/raw trust, and wakes RG
with an Awake assertion. Same ports39818+27341/27342, owned Phone reverse27341.
Retain no-content IDs/context/ordinal/counters, screenshots of synthetic fixture
states and terminal receipts. Remove exact mission ledger/signals/images, owned
fixture/reverse and operations; restore RG Asleep and S20 screen OFF via the
qualified adapter. Independent review remains required on the changed candidate.
