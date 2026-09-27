# Issue 11 recovery checks

Worker: Worker-codex-astra-2

The Phone owns the existing WebView and control lifetime. Reconnect keeps the
owner and document, advances viewport authority, retires old editor targets and
requires a current frame before RG page input. Command high-water marks survive
reconnect; uncertain effects are not automatically resent. Cold recovery offers
the saved URL for explicit Open, without replaying POST or restoring page memory.

Stop's settled NOT_HOSTING transition ends the app link and listener after local
authority is restored. Pairing, the live Phone page and ordinary site storage are
retained. Explicit Phone Start reopens the remembered-peer listener. An unrelated
idle resource notification does not close an inactive-host pairing session.
This replaces the old #7 test oracle that expected TLS to remain linked after Stop,
per the Planner ruling recorded on issue #11.

## Host gates

Use DEV.md's JDK17, two-worker, 2 GiB debug gate. `AuthPublicationRaceTest` holds
real TLS output immediately after AuthOk. Both status and Forget must survive;
positive controls verify the same connection's delivery. Writer startup follows
server publication; AuthOk is always the first wire frame, before multiplexed
controls. Security/cancellation tests run in the full core-link suite.

`RecoveryContractTest` checks lost-result duplicate rejection, preserved ordinal
high-water after reconnect, disconnected Phone takeover and old-lifetime
rejection. Its callback counts establish dispatch behavior, not website effects.

## Reserved real-device journey

Run only in an exclusive Manager-granted S20/RG window. Use the DEV.md screen
guard, physical-identity checks and exact trust-byte hashing before and after.
Install both debug APKs and test APKs with data preserved. Record candidate SHA,
APK SHA-256, device/OS/WebView and fixture routing. Do not clear logcat or trust.

Run these companions with the same fresh UUID `missionId`; the Phone also needs
`fixtureBaseUrl` pointing to the mission's standard browser fixture server:

- Phone `RecoveryJourneyTest#livePageSurvivesLinkLossRecreationAndTakeoverUntilExplicitStop`
- RG `RecoveryJourneyTest#reconcilesLiveHostWithoutReplayingInputOrStealingPhoneControl`

Start Phone first, wait for `PHONE_READY mission=<UUID>`, then start RG. Forward
only matching `EyeBrowseRecovery` phase receipts (existing test-only ack-file
pattern): `PHONE_RELEASE ... phase=reconnect|recreate|takeover` writes that phase
to RG's `cache/i11-<UUID>.ack` using its debug `run-as`; `RG_RELEASE ... phase=stop`
writes `stop` to the equivalent Phone file. These files never enter production
logic. They prevent a retry from racing remote teardown. Each wait is bounded;
an expired wait fails the row rather than silently retrying it. Capture scoped
screenshots at the disconnect/takeover barriers before forwarding their receipt.

The Phone asserts same WebView, document, history and synthetic form value;
absence of capture demand while disconnected; reconciled authority; Activity
recreation; local takeover; and Stop resource/link cleanup. RG checks current
frames/epochs, both native Retry and consent controls, rejection of disconnected
input, recreation and no implicit takeover. `pause()` is the app-link fault;
ADB/network/debugging stay available. No real RG process restart or lost-ACK
coverage is claimed by these two companions.

## Graceful persistence and actual Phone process loss

The Planner hybrid ruling is recorded in issue #11 comment 5851875099. A value
written only to renderer memory immediately before abrupt loss is not required
to become synchronously durable. The abrupt-loss row instead uses storage that
has already survived the graceful path below; no product flush mechanism is added.

Run individually with a shared fresh `missionId` and `fixtureBaseUrl`:

1. `ColdRecoveryInstrumentedTest#gracefulBackgroundPersistsStorageBeforeProcessLoss`.
   It writes a mission cookie/localStorage value and verifies both in-session,
   then moves the actual Activity to CREATED, exercising normal `onStop` (including
   its ordinary cookie flush). The host waits for `cache/i11-<UUID>.persistence`
   to say `backgrounded`, then **reads only** the WebView LocalStorage journal.
   Only after a complete CRC-verified PUT for the exact fixture-origin mission key
   and expected value may it write `durable` to that same test-only barrier file.
   The row allows 20 seconds for ordinary persistence, then resumes/recreates the
   Activity and checks both values. A missing durable record fails the row; the
   host must not write the journal or acknowledge elapsed time as persistence.
2. `ColdRecoveryInstrumentedTest#prepareRealProcessLossAfterFixturePost` runs in
   a new actual process, proves both values survived that restart, and only then
   creates the harmless POST fixture. Receipt metadata records the baseline.
3. After its terminal JUnit PASS, record the prepared and current Phone PIDs,
   app-scoped `am force-stop com.code2hack.eyebrowse.phone`, and verify the prepared
   PID is absent. If instrumentation already ended the process, disclose that;
   do not claim force-stop killed a live PID.
4. `ColdRecoveryInstrumentedTest#newProcessOffersSavedAddressWithoutReplayingPost`
   requires a different process-start identity, a changed host lifetime, saved URL,
   remembered pairing, honest interruption UI, inactive hosting and no automatic
   POST replay. Native Open performs explicit GET recovery. The new document ID,
   exact URL/title, completed navigation and DOM readiness all precede storage
   queries; retained `lastCommittedUrl` alone is insufficient. Previously durable
   cookie/localStorage must survive, while transient JS/form state is not restored.
5. `ColdRecoveryInstrumentedTest#cleanupOwnedMissionState` is an independent
   cleanup row, **also run after a failed preceding row**, not a retry. Pass the
   explicit comma-separated UUIDs in `cleanupMissionIds` to remove only current
   and prior owned fixture keys/cookies and receipt metadata. Logs distinguish
   already-absent localStorage/receipts from entries actually found and removed.
   Never clear all app/site data. An uncertain still-running instrumentation must
   be settled before cleanup is launched.

Receipts are test-only targetContext app-private metadata, never passwords or
form drafts. The host journal decoder retains only mission results and file
hashes, not unrelated browsing data. A compacted table or incomplete/corrupt
record requires a supported decoder or an explicit inconclusive result, never
an invented durability PASS. The current mission uses the read-only decoder
and checked runner retained with its issue-linked evidence; neither modifies
production storage. Each instrumentation command is bounded at 65 seconds and
the guarded sequence at 180 seconds. POST setup uses WebView.postUrl; this is
HTTP/persistence evidence, not keyboard/native-submission evidence.

## Evidence limits

Compilation is not device acceptance. Preserve terminal JUnit results, exact
commands, phase logs, fixture observations, screenshots, trust hashes and cleanup.
A lost ADB client does not establish that instrumentation stopped. Preserve the
original failure while cleaning up owned operations, links, hosting and fixture
mappings; immediately relock Phone and verify lock state.

The complete #11 matrix also needs actual RG process restart; dropped/delayed
acknowledgements and duplicate effects on real apps; locator/identity/Forget
outcomes; and continuous-input expiry integrated with #10. Existing host/security
and prior device evidence must be bound explicitly before any carry is claimed.
These rows are not silently satisfied by the new journey. Physical wearer/Fold6
qualification remains NOT EXERCISED under the unattended profile, and #12 repeats
integrated keyboard/Reading/recovery acceptance.
