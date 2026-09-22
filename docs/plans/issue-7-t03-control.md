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

## Round 2 measured geometry correction

Diagnostic on byte-identical5619694 production: command+160 viewport px moved native
WebView.scrollY0->160. CSS scroll moved0->74.666664; native WebView.scale was2.14732146
before/after, matching devicePixelRatio2.8125 * visualViewport.scale0.76349205.
RG profile density204 implied1.275 and incorrectly predicted125 CSS px; observed title75
was correct. Product dispatch is unchanged. The correction is fixture/test-only.

Each fixture reports live visual scale and DOM rectangle centers in title metadata; RG
tests parse this test-page data, while the product still uses ordinary BrowserState and
native viewport-pixel actions. Phone instrumentation independently measures/cross-checks
WebView.scale and asserts actual native deltas+160/-160. Correct-element checks and1s
effect bounds remain. A measured conversion regression plus invalid-geometry checks add
3 JVM identities:51 nonempty suites/328 total (325 retained). No production JS/test receiver.
The first diagnostic's title-handshake interference and the ordered diagnostic's original
oracle RED remain in evidence; neither is called a full-journey acceptance run.
