# I7-T03 — explicit handoff and browser actions

Worker: Worker-#6-r3-Codex-#7

Baseline e12b07e; Planner plan5759769926. Reuses T01 authority/canonical IDs and
T02 measured-profile, same-TLS presentation. No keyboard/head input or test receiver.

RG exposes explicit Use on glasses / Use on phone and local Back/Forward/Reload.
ActivateAt and ScrollBy are typed controller APIs for instrumentation and later pointer
input. Controls require an authenticated compatible current frame/context, matching
measured profile, completed loading and no pending action. One durable ordinal cursor
(lifetime + control epoch + sequence) reserves before send and survives reconnect and
Activity/process recreation; full-context command IDs come from BrowserCommandId v2.
There is one pending action and no automatic retry. Definitive server policy rejection
still consumes the ordinal; an uncertain native dispatch stays effectSucceeded=null.
No transport acknowledgment claims website success.

Phone serializes admission and native WebView dispatch on main. Rejected actions never
invoke an effect; admitted Back/Forward/Reload use the existing browser methods;
ActivateAt dispatches native down/up events in viewport pixels; ScrollBy applies bounded
pixel deltas. No production JavaScript/DOM automation or second WebView is introduced.

Phone takeover first requires current Phone content measurement, invalidates the RG
grant, revokes capture, retires/detaches private presentation, then clears RG exclusion
and lets the current Activity reclaim the same view and token. Hosting remains explicitly
active; returning to glasses builds a fresh immutable RG profile. With no current Phone
surface, RG return rejects with the existing INVALID_PROFILE result and prompts opening
Phone; local Use on phone then works even after link loss. Foreground/reconnect does
not itself transfer control. Explicit Hosting Stop returns local authority and invalidates
old RG contexts, including when a hidden Phone has no fresh profile until it returns.

New tests: BrowserActionDispatchTest (5) + CommandSequenceTest (3); retain317 baseline
identities =>50 nonempty suites /325 JVM identities. Physical BrowserControlJourneyTest
companions exercise both handoffs, every action, stale/replayed/consumed-ordinal rejection,
fixture page effects and disconnected Phone recovery. Fixture-only script setup/observation
stays in instrumentation; the actual effects use authenticated production actions.

Evidence records must distinguish admission from effect, preserve prior timing-test
intermittency and lint diagnostics, bind exact APKs, and verify pairing/settings/lock
preservation. A compiled companion is not physical acceptance.
