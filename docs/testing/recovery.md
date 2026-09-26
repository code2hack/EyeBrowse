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

## Actual Phone process loss

Run individually with a shared fresh `missionId` and `fixtureBaseUrl`:

1. `ColdRecoveryInstrumentedTest#prepareRealProcessLossAfterFixturePost`.
2. After its terminal JUnit PASS, record the Phone PID and app-scoped
   `am force-stop com.code2hack.eyebrowse.phone`; verify that PID exited.
3. `ColdRecoveryInstrumentedTest#newProcessOffersSavedAddressWithoutReplayingPost`.

The test-APK receipt contains process/lifetime/fixture metadata, never form drafts
or secrets. The second invocation requires a different process start timestamp,
checks no fabricated live page or automatic hosting, saved URL and pairing,
then uses native Open. Fixture POST counts must remain unchanged, including
after the explicit GET recovery. A mission-specific cookie and localStorage key
must survive; transient JS and form state must not be claimed restored. Only the
owned fixture keys and test receipt are removed. POST setup uses WebView.postUrl;
this is HTTP/persistence evidence, not keyboard or native submission evidence.

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
