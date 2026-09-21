# I6-T01 design notes — protocol/crypto/trust decisions (per Planner plan §4–§8, §10-B)

Authority: issues/6#issuecomment-5742886185 (ticket plan). All decisions below stay inside it.

## D1 — Module layout
- New Kotlin/JVM module `core:link` (kotlin("jvm") 2.2.21, JVM 17), included in settings.gradle.kts.
  Contains ALL Android-independent protocol logic: invitation codec + lifecycle, fingerprints,
  challenge transcripts, framing, message codec (kotlinx.serialization JSON), auth/binding policy,
  TLS engines (JSSE — platform, Android-independent per plan §4.1), pinning trust manager,
  locator validation/ordering, file-backed peer trust store, bounded outbound queue.
- kotlinx.serialization JSON 1.9.0 + plugin (pinned; plan §4.4 names kotlinx.serialization JSON).
- app-phone / app-rg hold thin wrappers: AndroidKeyStore identities, context-file stores,
  HostingController status projection, process-scoped managers. UI/QR-render/camera = T02.

## D2 — Test strategy for Keystore/JSSE
- `LinkSigningIdentity` (sign/verify/spki) and `TlsServerIdentity` (SSLContext + SPKI fp) are
  core:link interfaces. JVM tests use software EC P-256 keys; self-signed certs for TLS engine
  tests generated with BouncyCastle **test-scope only** (bcpkix-jdk18on 1.81) — no product crypto
  dep, no custom cert code in production paths.
- AndroidKeyStore impls (app-phone/app-rg) exercise only on device (T02+), per plan §15 risk note.

## D3 — QR invitation payload v1 (codec in core:link; rendering is T02)
- `eyebrowse-pair:v1?pv=1&maj=1&min=0&cap=<bits>&iid=<b64url16>&sec=<b64url32>&fp=<64hex>&loc=<l>[|<l>]&ttl=<s>`
- loc form: `ip:port`, IPv6 `[addr]:port`. Strict query whitelist (unknown key ⇒ MALFORMED_QR);
  unknown capability bits explicitly ignored (forward-compat permitted by the v1 codec, documented).
- No SSID/password/hotspot/credentials fields. Payload length hard cap 1024 chars.
- Validation: rejects loopback/unspecified/multicast + IPv6 link-local (scope id unrepresentable);
  port 1..65535; candidate count ≤ 8. iids/secrets base64url-decoded with exact length checks.

## D4 — Invitation lifecycle (pure, fake-clock)
- States ACTIVE→{EXPIRED|CANCELLED|CONSUMED}; TTL 180 s on injected monotonic clock;
  one active invitation (generate cancels previous); consume(id,secret) is synchronized atomic
  one-shot; outcomes map 1:1 to plan §7 errors (INVALID/EXPIRED/CANCELLED/REUSED).
- Secrets: 256-bit SecureRandom (app layer injects random + clock); NEVER persisted (memory only).

## D5 — Crypto primitives (standard only; §4)
- TLS 1.3 only via platform JSSE (SSLContext/SSLServerSocket/SSLSocket); enabledProtocols pinned.
- Phone identity: EC P-256 (AndroidKeyStore, TLS server key; platform auto self-signed cert).
- RG identity: EC P-256 AndroidKeyStore signing key, `SHA256withECDSA`.
- SPKI pinning: SHA-256 over SubjectPublicKeyInfo.encoded, lowercase hex; compare via
  MessageDigest.isEqual. Client trust manager: rejects every cert whose leaf SPKI fp ≠ pin;
  DNS-name validation intentionally omitted for IP-literal locators (plan-sanctioned);
  NO accept-all manager anywhere.
- Nonces: 32 B SecureRandom, single-use per challenge; secrets compared constant-time.

## D6 — Challenge transcript v1 (canonical, length-prefixed)
`"EyeBrowse-Pairing-Challenge-v1" | u8 purpose(1=initial,2=reconnect) | u16 maj | u16 min |
 len||phoneSpki | len||rgSpki | len||nonce | len||invitationId (empty on reconnect)`
- Signed with SHA256withECDSA; verified against presented (initial) / stored (reconnect) key.
- Fresh server nonce each challenge ⇒ old-nonce replay fails transcript match.

## D7 — Wire framing + messages
- 4-byte unsigned BE length + UTF-8 JSON; decoded frame ≤ 32 KiB; zero/oversize/malformed
  rejected before allocation. Outbound queue cap 16; status messages coalesce to latest.
- Hello(maj=1,min=0,caps[PAIRING_V1,STATUS_V1]); major≠1 or missing required cap ⇒
  INCOMPATIBLE_PROTOCOL. Flow: C:Hello → S:Hello → S:Challenge(nonce) →
  C:PairAuth|ReconnectAuth → S:AuthOk|AuthErr(code) [+ initial Status on AuthOk].
- Status values HOST_INACTIVE/HOST_STARTING/HOSTING/HOST_STOPPING — observation only, sent
  strictly after auth (engine enforces; negative test covers pre-auth rejection).
- Heartbeat 10 s; liveness 30 s; TCP connect ≤3 s/locator. One user-triggered connect/retry
  operation carries one absolute monotonic ≤10 s deadline from `connect()` invocation across all
  locator attempts. After a TCP connection succeeds, TLS handshake + application authentication
  share one absolute ≤5 s auth deadline, additionally capped by the remaining operation deadline;
  timeout is recomputed before each blocking handshake/auth read. Because socket `SO_TIMEOUT` is
  per underlying read and JSSE may perform multiple internal reads, a deadline guard also closes
  the currently owned pre-commit TLS socket at that absolute deadline. Cancellation owns the raw
  socket before blocking TCP connect and closes current connection work within ≤2 s.
  Deadline/cancel teardown is abortive so TLS cleanup does not receive another timeout window.

## D8 — Peer binding / replacement / Forget (pure BindingPolicy in core:link)
- One remembered peer per endpoint. Same-identity locator refresh allowed after TLS pin proof;
  different peer ⇒ PEER_REPLACEMENT_REQUIRED, explicit Forget first (both directions).
- Paired Phone receiving initial PairAuth from a different RG key ⇒ PEER_REPLACEMENT_REQUIRED.
- Second concurrent TLS client while a link is active: closed pre-auth, never receives status.
- Forget: close link, clear store, cancel local invitation; re-pair via fresh invitation.
- Store: app-private JSON, atomic tmp+rename. Corrupt state is a distinct fail-closed trust state,
  never treated as unpaired; explicit Forget is required before replacement/re-pair where the
  endpoint can no longer establish its remembered peer identity. Secrets are never written.

## D9 — Locators
- Fixed port 39818 (LinkProtocol.LOCAL_PORT). Reachability metadata only; identity = pinned key.
- Ordering primitive: dedupe, cap 8, IPv4 global first then IPv6 global, link-local last;
  no SSID/BSSID/subnet concept anywhere in core:link. Address enumeration is app-layer (T02+).

## D10 — Errors/states
- Sealed taxonomy exactly per plan §7 (camera/scan subset defined now, used in T02).
- States per plan §7; FORGETTING modeled as transient on the forgetting side.

## T01 boundary notes
- RG manifest: add INTERNET now (RgLinkClient needs it); CAMERA lands with the T02 scanner.
- RG Kotlin build support (plugin 2.2.21, jvmTarget 17, unit/androidTest runner+deps) added now;
  MainActivity Java→Kotlin conversion is T02 per plan §9-C ("RG MainActivity Kotlin migration").
- No HostingController.start/stop/lease calls from link code — status() + Listener (read-only).

---

## Round-1 review amendments (I6-T01 correction, verdict I6-T01-SOURCE-VERDICT-137A0211-20260920-6AAA1B2A-01)

The scoped protocol/security review accepted the architecture (TLS 1.3-only, SPKI pinning,
ECDSA proof, replacement-before-consume, no-status-before-auth, bounded framing; D1/D2 as
written) and required the following corrections, all applied on this branch:

- **A1 (R1/B1, amends D4):** replacement cancels only a still-ACTIVE prior invitation. EXPIRED
  and CONSUMED are stable terminal classes; `stateOf`/consume outcomes preserve them after any
  number of replacements. Regressions: expired→replacement→consume and consumed→replacement→consume.
- **A2 (R1/B2, amends D4):** `PairingInvitationManager.generate`/`consumeForServer` share one
  monitor; the visible active surface is cleared only when the consumed id is still the
  displayed active id. Regressions: stale-consume surface retention + 50-iteration
  generate/consume interleaving loop.
- **A3 (R2/B3, amends D7):** each `LinkClientEngine.connect()` has a distinct
  cancellation/ownership token guarded by one operation lock. The raw socket is published to that
  operation **before** blocking `connect()`, ownership transitions raw→TLS only for that same live
  operation, and every failure/session-exit path clears matching ownership. `disconnect()`
  atomically marks the operation cancelled and takes/closes its owned socket. Owned-socket teardown
  is abortive (`SO_LINGER=0`) so cancellation/deadline cleanup cannot consume another socket-timeout
  window. The final local `onAuthenticated` trust commit and CONNECTED transition are serialized
  against Cancel under the same operation lock, removing the prior check→callback race. Regressions deterministically cover
  cancellation during blocked TCP connect, cancellation at the precommit boundary, stalling
  application auth, and established-session cancellation; deliberate cancellation quiesces within
  ≤2 s and cannot produce local trust commit/CONNECTED.
- **A4 (R3/B4, amends D7):** the client captures one absolute monotonic operation deadline at
  `connect()` invocation and carries that unchanged through all locator attempts. TCP connect is
  capped by `min(connectTimeoutMs, current remaining operation time)`. After TCP success, one
  TLS+application-auth deadline is derived as
  `min(operation deadline, tcp-success time + authTimeoutMs)`; remaining time is recomputed before
  TLS handshake and before each blocking application-auth read, so no explicit phase receives a
  fresh full auth allowance. Because JSSE can perform multiple underlying socket reads within one
  blocking TLS/application read, an independent deadline guard closes the currently owned
  pre-commit TLS socket at the same absolute auth/operation deadline. Deadline/failure teardown is
  abortive and receives no extra cleanup allowance. Regressions assert the configured aggregate
  deadline with bounded scheduling slack and include a materially slow TCP phase followed by
  stalled authentication, which would fail under the former pre-connect budget snapshot behavior.
- **A5 (R4/B5, amends D5):** both AndroidKeyStore identities explicitly request
  `secp256r1` via `KeyGenParameterSpec.setAlgorithmParameterSpec(ECGenParameterSpec)`; a
  pre-existing alias is validated against P-256 (`EcKeys.isP256`) and fails closed (explicit
  identity reset required) instead of being silently reused. Regression: `EcKeysTest`
  (P-256 accepts; secp384r1 and RSA reject).
- **A6 (R5/B6, amends D8/D9):** persisted trust reads are tri-state
  (`PeerTrustRead.Absent/Valid/Corrupt`); CORRUPT is never treated as unpaired.
  **Phone/server initial pairing + CORRUPT** fails PEER_REPLACEMENT_REQUIRED before invitation
  consumption; **Phone/server reconnect + CORRUPT remembered RG trust** fails WRONG_RG_IDENTITY.
  **RG local QR acceptance + CORRUPT remembered Phone trust** fails PEER_REPLACEMENT_REQUIRED
  without dialing; **RG local reconnect + CORRUPT store** also fails PEER_REPLACEMENT_REQUIRED,
  because no trustworthy stored Phone identity/locator remains and explicit local Forget is the
  recovery. Regressions cover store tri-state, binding policy, Phone engine refusal without
  invitation burn, recovery after explicit state clearing, and RG wrapper corrupt-store cases.
- **A7 (R6/B7, amends D8):** `sendForgetNotice()` requires LINK_UP (authenticated-only); no
  control frame can reach an unauthenticated peer during TLS/AUTHENTICATING. Regression:
  forget-during-auth is a no-op and the auth result follows the challenge directly.

## D11 — recorded non-blocking follow-ups (out of T01 scope by direction)

- Obsolete invitation-secret state (cancelled/expired/consumed entries and their secrets) is
  retained until process death; bounded cleanup/reaping is deferred to a later todo.
- The RG client does not pre-validate QR protocol major/capabilities before dialing; the server
  rejects incompatible hellos with INCOMPATIBLE_PROTOCOL pre-auth (authoritative for v1). Local
  pre-dial rejection is deferred as a UX hardening follow-up.
- `PeerTrustStore.isPaired()` reports usable trust only (`Valid`); callers needing the
  fail-closed distinction use `read()` directly.

## D12 — T02 device-evidence amendments (link identity + UI/camera surfaces)

- **Link identity key spec (device-evidenced):** the Phone TLS identity is generated
  SIGN-only (no VERIFY purpose) with explicit digests {NONE, SHA256, SHA384, SHA512} on
  secp256r1. S20+ KeyMint evidence: Conscrypt signs the TLS 1.3 CertificateVerify through a raw
  NONEwithECDSA upcall; VERIFY in the purpose set breaks that upcall; absent digest
  authorizations are treated as "none authorized". Existing aliases are validated EMPIRICALLY
  (`supportsRawEcdsa()` — a real raw signature attempt), because KeyInfo digest metadata proved
  unreliable on the device. An inadequate alias is regenerated automatically only when trust is
  definitively ABSENT. VALID and CORRUPT both fail closed without silent rotation; CORRUPT recovery
  requires explicit Forget, which clears trust first and only then permits alias repair/rotation.
- **Pairing UI is event-driven** (server link observers), not polling — required for reliable
  accessibility-tree instrumentation and lower power draw; expiry/link notes are computed on
  refresh events.
- **RG scanner surfaces:** one production `QrDecoder` (bitmap + Y-plane entries), CameraX
  `CameraQrScanner` with synchronous main-thread unbind on cancel (<=2 s), runtime CAMERA
  permission with denial/recovery UI. The instrumentation image-input seam feeds the SAME
  decoder + SAME production controller; no pairing shortcut exists outside engine callbacks.
- **Bounded server diagnostics:** the link engines log failure class + message only (no
  payloads, no key material) — this proved required for device-field diagnosis.
- **Device findings:** physical RG cold first analyzer frame measured **7911 ms**. Planner
  adjudication `I6-T02-CAMERA-ADJUDICATION-20260920-6AAA09E0-01`, option (c), revised the
  authoritative T02 camera contract to cold <=10 s / warm reacquisition <=5 s /
  cancel-unbind-release <=2 s; therefore the preserved 7911 ms cold evidence is PASS and is not
  rerun merely to chase the superseded 5 s cold target. Doze/light network restriction drops
  inbound SYNs to the phone listener when the app is backgrounded (pairing windows keep the phone
  UI foreground; a pairing foreground service is a T03 concern); Samsung Freecess kills
  backgrounded apps under the default 30 s screen timeout.

## D13 — T02 identity recovery and invitation-pin freshness

- Automatic repair/rotation of an inadequate Phone AndroidKeyStore alias is authorized **only**
  when the tri-state trust read is `PeerTrustRead.Absent`. `Valid` and `Corrupt` both fail closed;
  neither state silently deletes or replaces the Phone TLS identity.
- Phone pairing-surface/server construction is side-effect free with respect to key usability.
  It does not force an SPKI read or rotation, so an inadequate VALID/CORRUPT alias cannot prevent
  `PairingActivity` construction before the existing explicit Forget control becomes reachable.
- Listener start is the identity-use boundary: it applies the ABSENT-only policy and surfaces an
  inadequate VALID/CORRUPT alias through the existing recoverable Generate-failure path.
- Explicit Forget closes the link, clears trust, cancels the invitation, then performs best-effort
  identity repair. Only the post-clear ABSENT state can authorize rotation.
- `PairingInvitationManager` resolves the Phone SPKI fingerprint lazily for each generation,
  before invitation lifecycle mutation. A legal post-Forget key rotation therefore cannot leave
  the process-scoped invitation manager advertising an obsolete pin; the first new QR carries
  the same fingerprint as the current TLS identity.

## D14 — T02 CameraUnavailable production failure semantics

- `CameraQrScanner` reports provider-acquisition or bind failure through the production camera
  error callback as `CAMERA_UNAVAILABLE`, exactly once.
- Failure cleanup best-effort unbinds partial CameraX use cases and clears the provider reference,
  allowing a subsequent normal scanner acquisition rather than leaving a false camera-active
  surface.
- The deterministic no-match-selector instrumentation regression exercises this production
  failure path and then proves normal camera reacquisition. It does not create a fake pairing path.

## D15 — T02 camera timing contract (Planner adjudication)

Authority: `I6-T02-CAMERA-ADJUDICATION-20260920-6AAA09E0-01`, option (c).

- cold first real CameraX analyzer frame: **<=10,000 ms**;
- warm reacquisition: **<=5,000 ms**;
- cancel / unbind / release: **<=2,000 ms**.
- The preserved physical-RG cold measurement of **7911 ms** is PASS under this contract. It is
  retained as evidence rather than automatically rerun solely because the prior cold <=5 s
  requirement was superseded.
- Runtime camera instrumentation uses these same bounds. The Android 12 permission-denial flow
  remains a split invocation because host-side `pm revoke` kills the instrumented process; that
  procedural fact is not treated as a product camera failure.

## D16 — T02 completed authentication-negative evidence

- **Invalid invitation secret/token:** a structurally valid invitation with the valid invitation
  ID but wrong secret reached the production Phone authentication endpoint and returned
  `INVITATION_INVALID` before authentication, with zero protected status exposed.
- **Wrong RG proof:** the controlled negative client established TLS 1.3 to the Phone-role
  endpoint, verified the pinned Phone SPKI, completed the real Hello/Challenge exchange,
  presented the expected RG SPKI but signed with a different private key, and received
  `AUTHENTICATION_FAILED` with zero protected status.
- The Phone role for these correction-round negatives is explicitly **substituted emulator
  evidence** under the standing Owner decision; it is not relabeled physical KeyMint evidence,
  and it does not seed trust or bypass the production Phone authentication engine.
