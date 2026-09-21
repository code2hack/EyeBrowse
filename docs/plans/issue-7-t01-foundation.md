# Issue 7 T01: authenticated control and presentation foundation

Worker: Worker-#6-r3-Codex-#7

R2a correction: Expert: Expert-#7 (preserves Planner duplicate-command-ID contract).

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

Back/Forward/Reload/ActivateAt/ScrollBy are typed Kotlin actions. Every action carries a
required positive commandSequence and a canonical commandId produced by BrowserCommandId:
`v1:<controlEpoch>:<commandSequence>:<lifetimeId>`. The two ordinals are canonical decimal
(no sign or leading zeroes), followed by the full, unmodified browser lifetime. Delimiters in
the lifetime cannot alias the two preceding numeric fields. There is no hash/collision tradeoff.
Document, viewport and hosting-generation changes do not create a new command namespace.
The shared helper is available to both senders; the wire constructor/decoder and Phone arbiter
validate the mapping. Reusing a resolved ID with a new ordinal or epoch/lifetime is structurally
invalid, not another admissible page action. True replay keeps the same ID/ordinal and rejects.
This preserves the Planner duplicate-ID rule while results remain keyed only by commandId;
delayed results from other control epochs/browser lifetimes have distinct keys.

The maximum ID length is 171 characters (version prefix, two Longs, separators, and the existing
128-character lifetime bound); request and result support that same bound. The authenticated
32 KiB JSON / 1 MiB presentation / 8 KiB metadata record limits are unchanged.

The owner sends increasing ordinals and never assigns a new ordinal to retry an uncertain effect.
The Phone reserves the ordinal only after validating identity, before any later page effect,
and stores only an O(1) high-water mark: duplicate or reordered ordinals at or below it are rejected
even for a malformed raw arbiter request. Gaps are allowed; wraparound and nonpositive ordinals
are rejected. Explicit ownership transfer starts a new control epoch and resets the mark;
old-context commands still fail before admission. Stable-page scrolling has no small
command-count or cache-capacity limit, and no ID cache is introduced.

The Phone adapter reads actual browser document identity on link start, authenticated-session
publication and each handoff/action admission, as well as on listener events. A missed notification
while the listener was detached cannot publish old document authority. Reconciliation that changes
the context invalidates any pending presentation grant/frame. The browser's document ID is volatile
for authentication-thread reads; all multi-step adapter operations use the authority monitor.
No keyboard, script evaluation, page renderer or production test-control receiver is added.

## Verification and limits

Pure tests exercise invalid profiles, competing handoffs, inactive hosting, stale lifetime/
control/document/viewport/generation contexts, wrong owner, reconnect, stale callbacks,
command pressure, duplicate IDs with same/new ordinals, canonical namespace/length validation,
delayed wire-result correlation across handoff/lifetime changes, record size/malformed
input/truncation and queue priority/coalescing.
Real TLS JVM tests exercise old-trust reconnect without a new invitation, negotiated
control/frame transport, missing capabilities and pre-auth presentation rejection.
Physical device work is not part of T01; no device or pairing state is changed.
