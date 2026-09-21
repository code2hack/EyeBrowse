# Issue 7 T01: authenticated control and presentation foundation

Worker: Worker-#6-r3-Codex-#7

Implements the T01 portion of Planner plan issue #7 comment 5759769926. Rendering,
WebP encoding, browser effects, and the user-facing handoff journey remain T02/T03.

## Wire and compatibility

Major stays 1; minor is 1. Pairing/status capabilities retain their original meaning.
PRESENTATION_V1, HANDOFF_V1 and BROWSER_ACTIONS_V1 are negotiated on every authenticated
connection, never inferred from remembered trust. Old peers retain the base link;
presentation reports update-required. The RG saves actual authenticated peer metadata,
not its own advertised capability set. There is no second application port.

Hello/challenge/auth use the existing bounded length-prefixed JSON. After AuthOk,
peers advertising all three presentation capabilities switch to records:
one kind byte (1 control, 2 presentation), four-byte unsigned big-endian body length,
then body. Legacy peers continue their original framing. JSON bodies stay at 32 KiB.
Presentation bodies are at most 1 MiB including the four-byte metadata length, up to
8 KiB of JSON metadata, and encoded pixels. Unknown kind, invalid length and missing
auth/owner permission are rejected before pixel allocation. Frame metadata includes
the browser lifetime, control/document/viewport context, host generation, frame sequence,
capture timestamp, dimensions and WebP/version declaration. Encoding pixels is T02.

One session owns one bounded control queue (16), one replaceable pending frame and one
in-flight record. Controls have priority between frame records. A blocked write closes
at 30 seconds. An incomplete read is terminal, not restarted at a body byte. Context
change clears pending frames; a receiver rejects an old context or nonincreasing sequence.
Disconnect destroys session queues; nothing replays across authenticated reconnect.

## Ownership and admission

The Phone owns the coordinator. Remote messages have no authoritative source-owner field:
the Phone adapter derives RG source from the authenticated receive path. Serialized handoffs
increment the control and viewport epoch once. Hosting must be active, the session authenticated
and compatible, and an actual measured RG profile supplied. Measurements outside the existing
4096-per-dimension / 4,194,304-pixel bound are rejected rather than silently resized.

The returned profile is immutable. Phone layout publications cannot replace it while RG owns.
Phone takeover requires a fresh Phone-local content measurement; peer-supplied Phone geometry
is ignored. T01 provides the validated profile and transition state; T02/T03 apply the physical
presentation attachment and same-WebView control transition. Admission alone never marks the
presentation ready. Readiness/staleness callbacks carry the complete context, including lifetime
and generation, so an old callback cannot revive a successor or a disconnected presentation.

Back/Forward/Reload/ActivateAt/ScrollBy are typed Kotlin actions. Admission reserves a command ID
before any later page effect. Duplicate IDs are rejected; uncertain effects are never replayed.
The bounded ledger fails closed at 256 commands per control epoch; it never evicts an old ID to
admit a replay. The caller receives COMMAND_LIMIT_REACHED and must not silently retry. A later
explicit handoff creates a new epoch and fresh ledger; all prior-epoch commands remain invalid.
No keyboard, script evaluation, page renderer or production test-control receiver is added.

## Verification and limits

Pure tests exercise invalid profiles, competing handoffs, inactive hosting, stale lifetime/
control/document/viewport/generation contexts, wrong owner, reconnect, stale callbacks,
command pressure, record size/malformed input/truncation and queue priority/coalescing.
Real TLS JVM tests exercise old-trust reconnect without a new invitation, negotiated
control/frame transport, missing capabilities and pre-auth presentation rejection.
Physical device work is not part of T01; no device or pairing state is changed.
