# RG restart, wrong-peer and native Forget slice

Worker: Worker-codex-astra-2

Base:5974b35 (B4 abort-before-TLS-close correction). These are instrumentation-only
rows; compile success is not device acceptance. Manager approval
MGR3-T02-I11-RGSLICE-GRANTED-20260927-01 covers native canonical RG Forget and real
QR repair, preserving identities and prohibiting trust-file restoration. An
exclusive S20/RG booking and fresh source/APK/device bindings remain required.

## Control and evidence

ADB/guard use explicit verified USB: S20 R5CN30NA8GX, RG1906092617103125. Pin the
qualified USB screen-off adapter34883faf... and helperb50beccb... per runtime
receipts. S20 ends screen-off/Dozing without owned operations; no keyguard-lock
requirement. Recheck current canonical trust baselines after the preceding #10
window. Hash raw trust bytes before parsing their non-secret metadata; use the
DEV.md transport recipes. Record fingerprints/locators and raw pre/post hashes.

All rows share a fresh UUID `missionId`; Phone also needs `fixtureBaseUrl`.
The host exchanges only the fixed phase strings below through app-private
`cache/i11-rg-<UUID>.phase` files. These are androidTest coordination, not product
protocol messages. Do not replay a failed row. Parent companion has a55s shared
work deadline plus cleanup; each instrumentation command is bounded65s, each
guarded group180s. Ordinary connection operations retain their10s product budget;
fresh RG-frame checks2s, absent-client capture retirement6s.

## Actual RG process restart (twice)

Phone: `RgRecoveryCompanionTest#liveHostSurvivesRgProcessRestart`.
RG preparation: `RgRecoveryInstrumentedTest#prepareActualRgProcessRestart`.
RG verification: `RgRecoveryInstrumentedTest#actualNewRgProcessReconcilesExistingHostAndOwner`.

Run once with `restartOwner=rg`, once with `restartOwner=phone`, on BOTH peers.
Phone loads a fresh controlled document, preserves its view/document/history/
synthetic form value and starts hosting. After Phone phase `ready`, start RG
preparation. It uses native Retry and explicit Use on glasses. When Phone writes
`prepared`, mirror that into RG's phase file. RG saves process/context/ordinal
metadata in targetContext and exits with terminal PASS. Record the prepared PID,
then app-scoped force-stop RG and verify PID exit; disclose if instrumentation
already ended it. Never force-stop Phone for this row.

Phone observes link loss/capture retirement, verifies continuity, and optionally
uses native Use on phone while RG is absent; phase becomes `resume`. Start the
new RG verification process only after that phase. It requires a different
process-start identity and the same RG Keystore identity/Phone host lifetime and
document. RG ownership may resume only in the retain-owner case, with newer
viewport/frame authority and a non-reused reserved ordinal. Phone takeover must
remain authoritative with disabled RG page input. Mirror Phone's `reconciled`
phase to RG; after terminal RG PASS write `verified` to Phone. The final handshake
keeps the session alive long enough for both peers' assertions, rather than
racing an immediately closed test client. Phone then reports PASS and cleans up.

## Same saved test locator, different peer

Phone: `RgRecoveryCompanionTest#authenticPeerPrecedesWrongPeerAtTestAlias`.
RG: `RgRecoveryInstrumentedTest#differentPeerAtPreviouslyAuthenticatedTestLocatorIsRejected`.

Reserve host HTTP/TLS27341/27342, Phone application39818, host forward27344 ->
Phone tcp39818, and RG reverse27343 -> host27344. The RG row uses existing
`RgPairingStore(File)` with mission-private metadata and the real AndroidKeyStore
signer. It first authenticates the actual Phone via the alias. At Phone phase
`authenticated`, write `disconnect` to RG. Wait for RG phase `disconnected` AND
Phone phase `swap-ready`, then remap only the owned RG reverse27343 to host27342
(the fixture's distinct TLS certificate), and write `swapped` to RG. The saved
locator and pinned identity stay unchanged. Expect WrongPhoneIdentity with no
CONNECTED, host status, browser state or frames; both canonical trust records
stay unchanged. After terminal RG PASS write `verified` to Phone.

This is an isolated test-profile/route negative, not normal QR acceptance of
loopback locators. No production fallback or certificate bypass is introduced.
The existing fixture TLS and ADB mappings avoid a new relay implementation.

## Native canonical Forget and authenticated repair (twice)

Phone: `RgRecoveryCompanionTest#nativeRgForgetAndAuthenticatedRepairPreservePhone`.
RG Forget: `RgRecoveryInstrumentedTest#nativeForgetRevokesOnlyLocalCanonicalTrust`.
RG repair: `RgRecoveryInstrumentedTest#freshPrivateQrRepairsCanonicalPairingWithoutIdentityReplacement`.

Run once `forgetMode=online`, once `forgetMode=offline`, on BOTH peers. Phone
preserves its live fixture and starts or stops only its application listener,
then writes `ready`. In the online case RG uses native Retry and observes an
authenticated HOST_INACTIVE before native Forget; offline Forget does not attempt
to contact the absent listener. RG asserts canonical trust absent, native forgotten
status, and unchanged signing identity. A separate production reconnect API call
must fail locally with InvitationInvalid and no connection/status events. Native
unpaired Retry is NOT used as this negative: it starts the scanner by design.

After RG Forget terminal PASS, capture the intermediate raw trust state (RG absent,
Phone unchanged), then write `forgotten` to Phone. Phone verifies its unchanged
trust bytes and live-page continuity, uses the native Pair RG button with an
ActivityMonitor to observe the real PairingActivity (no second task-clearing
ActivityScenario), presses
Generate QR and writes ONLY a private PNG to its app cache. Phase is `qr-ready`.
Transfer that file privately to the RG cache with the same basename. The QR bytes,
invitation payload and secrets must never enter prompts, stdout, public logs or
screenshots. The RG repair row uses the existing production QrDecoder and real
RgLinkClient authentication, verifies the original identities and Phone ownership,
and removes its private PNG/receipt. No raw trust-store restoration is permitted.

After terminal repair PASS write `repaired` to Phone. It verifies invitation
consumption, identity preservation, Phone ownership and page continuity after
finishing PairingActivity and observing the original browser resume, then
cancels residual invitations and deletes its PNG. Re-pair failure stops with the
observed local trust state explicitly reported; do not blind-retry. Preserve the
Phone trust/site data and avoid an extra Phone force-stop. Test-process teardown
and its effect on the synthetic page lifetime must be disclosed separately.

## Closeout

Stop/settle owned instrumentation before any replacement invocation. Remove only
owned phase/receipt/disposable trust/QR files and forward/reverse mappings; keep
legacy mappings and unrelated site data. On a failed preparation or repair,
identify retained metadata explicitly and perform only authorized scoped cleanup.
Verify no owned listeners/services and S20 screen-off. Keep source/APK hashes,
JUnit terminal results, phase timings, fixture observations, identity receipts and
scoped non-QR screenshots. Canonical hash advancement after real authentication
requires Manager acceptance; a different raw hash is never silently reset.

These rows do not provide lost-ACK/duplicate-effect or #10-integrated keyboard/
Reading/continuous-input acceptance; those follow separately. No wearer/optical
qualification or whole-ticket completion is inferred.
