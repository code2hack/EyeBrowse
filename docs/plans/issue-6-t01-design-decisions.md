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
- Heartbeat 10 s ping; liveness 30 s; connect 3 s/locator; auth window 5 s; cancel quiesce 2 s.

## D8 — Peer binding / replacement / Forget (pure BindingPolicy in core:link)
- One remembered peer per endpoint. Same-identity locator refresh allowed after TLS pin proof;
  different peer ⇒ PEER_REPLACEMENT_REQUIRED, explicit Forget first (both directions).
- Paired Phone receiving initial PairAuth from a different RG key ⇒ PEER_REPLACEMENT_REQUIRED.
- Second concurrent TLS client while a link is active: closed pre-auth, never receives status.
- Forget: close link, clear store, cancel local invitation; re-pair via fresh invitation.
- Store: app-private JSON, atomic tmp+rename, corrupt ⇒ fail closed (treated unpaired);
  secrets never written.

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
- **A3 (R2/B3, amends D7):** the in-flight raw/TLS socket is cancellation-owned from creation;
  `disconnect()` closes it at any stage; cancellation state is re-checked before sending the
  auth proof and before `onAuthenticated`/CONNECTED, so a cancel can never complete pairing or
  persist trust. Regressions: stalling-auth cancel (≤2 s quiesce, no trust commit, no CONNECTED,
  no connect-failure) and established-session cancel.
- **A4 (R3/B4, amends D7):** one absolute operation deadline is carried through TCP connect,
  TLS handshake and application auth; every blocking phase uses min(per-phase maximum, remaining
  budget). Regression: refused-locator + stalling-auth operation completes inside the configured
  budget although the per-phase auth timeout exceeds it.
- **A5 (R4/B5, amends D5):** both AndroidKeyStore identities explicitly request
  `secp256r1` via `KeyGenParameterSpec.setAlgorithmParameterSpec(ECGenParameterSpec)`; a
  pre-existing alias is validated against P-256 (`EcKeys.isP256`) and fails closed (explicit
  identity reset required) instead of being silently reused. Regression: `EcKeysTest`
  (P-256 accepts; secp384r1 and RSA reject).
- **A6 (R5/B6, amends D8/D9):** persisted trust reads are tri-state
  (`PeerTrustRead.Absent/Valid/Corrupt`). CORRUPT is never reported as unpaired: phone-side
  initial pairing and RG-side QR acceptance fail closed to PEER_REPLACEMENT_REQUIRED (explicit
  Forget required), reconnect fails closed to WRONG_RG_IDENTITY. Regressions: store tri-state,
  policy cases, engine-level corrupt-trust refusal (invitation not burned) + recovery after
  state clearing, RG wiring corrupt cases.
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
