# EyeBrowse SPEC v1

**Status:** MVP product and engineering specification, v1 draft

**Product:** EyeBrowse

**Primary targets:**
- Android phone, primarily Samsung Galaxy Z Fold6 cover and inner displays.
- Rokid Glasses (RG), Android 12 / API 32, approximately 480 × 640 physical display target.

## 0. Authority and interpretation

This specification defines the intended EyeBrowse MVP behavior and architecture.

It is derived from:
- `design/mvp-design-brief-v0.md`;
- the selected Soft Dock direction;
- `design/soft-dock/design-decisions.md`;
- `design/soft-dock/design-qa.md`;
- Project Owner decisions made after the design study.

Where the design study explicitly marks a value or behavior as illustrative or unresolved, this specification does not silently promote it to a requirement. Such items are listed under **Open validation items**.

The words **MUST**, **SHOULD**, and **MAY** are normative.

---

# 1. Product definition

EyeBrowse is a minimal, AI-native Android browser in which the browser, an embedded Pi-like agent, speech input, public-Internet proxying, and private tailnet connectivity are designed as one system.

EyeBrowse is not intended to reproduce Chrome's full feature set.

The product has two primary surfaces:

```text
Browser ↔ Agent
```

The central product principle is:

> Keep the browser visually minimal and let the agent absorb complexity.

For Rokid Glasses, the primary interaction principle is:

> Normal Mode is for manipulating content. Reading Mode is for consuming content.

---

# 2. MVP goals

The MVP MUST prove all of the following:

1. A minimal Android WebView browser can serve as the visible web surface on both phone and RG.
2. A built-in on-device agent loop can understand and manipulate that WebView through a semantic browser-control API while using a remote LLM endpoint for inference.
3. RG can be operated through a head pointer, Rokid touchpad gestures, a Reading Mode with head-controlled scrolling, and an in-app keyboard.
4. Phone can use normal Android touch and IME behavior while sharing the same browser, agent, state, speech, and networking platform.
5. Mihomo and tailnet connectivity can coexist in the same app architecture without competing for two Android `VpnService` slots.
6. EyeBrowse can consume remote LLM, ASR, web, Paseo, or other Spark-hosted services through configured HTTPS/tailnet endpoints.
7. The product can remain visually sparse: Browser and Agent are the only permanent primary surfaces in the MVP.

---

# 3. Explicit MVP non-goals

The MVP does NOT require:

- a conventional browser home page;
- bookmark management;
- history-management UI;
- download-manager UI;
- desktop-site controls;
- browser extensions;
- account sync;
- a password manager;
- a verbose privacy/settings hierarchy;
- full Mihomo configuration UI;
- full Tailscale configuration UI;
- LLM-provider configuration UI;
- ASR-provider configuration UI;
- sophisticated visual tab management;
- a system-wide RG mouse;
- a system-wide Android keyboard/IME;
- local LLM inference;
- video support;
- elaborate branding, animation, or onboarding;
- a Chromium fork bundled into EyeBrowse.

These are not prohibited future features; they are outside MVP scope.

---

# 4. Repository and product architecture

EyeBrowse MUST be one repository with one shared platform and two Android application shells.

It MUST NOT be maintained as two divergent code forks.

Conceptually:

```text
                         EyeBrowse
                            │
                    Shared Platform
                            │
        ┌──────────┬────────┼────────┬──────────┐
        │          │        │        │          │
     Browser      Agent   Speech   Network     State
        │          │        │        │
        │          │        │    ┌───┴────────┐
        │          │        │    │            │
      WebView    Pi-like   ASR  Mihomo      Tailnet
        │
        └───────────────────────┐
                                │
                     ┌──────────┴──────────┐
                     │                     │
                 app-phone              app-rg
```

The intended source boundaries are:

```text
app-phone/
app-rg/
core/browser/
core/agent/
core/speech/
core/network/
core/state/
```

The shared platform defines what EyeBrowse means. Device application modules define how the user physically interacts with it.

Device-specific input rules MUST NOT be scattered through shared browser or agent logic.

---

# 5. Android platform requirements

## 5.1 Common

- EyeBrowse MUST support Android 12 / API 32 because RG runs that platform.
- The implementation MAY target a newer Android SDK, but runtime behavior required by RG MUST have an API-32-compatible path.
- Production browsing MUST use Android System WebView unless an explicit later decision replaces it.
- EyeBrowse MUST NOT bundle a full Chromium browser engine for MVP.

## 5.2 Phone

The phone APK MUST be responsive rather than designed against one fixed pixel canvas.

The primary physical target is Samsung Galaxy Z Fold6:
- cover display: tall/narrow phone layout;
- inner display: near-square foldable layout.

The Product Design study used 360 × 884 and 760 × 884 logical review viewports as representative layouts. These are design-study viewports, not normative hardware pixel resolutions.

The app MUST adapt to the actual Android window size, density, system insets, folding state, and orientation exposed at runtime.

## 5.3 RG

The RG edition MUST treat 480 × 640 as the MVP design canvas target.

Actual window insets, density behavior, and optical readability MUST be measured on hardware before ergonomic values are frozen.

---

# 6. Shared state model

At minimum, EyeBrowse MUST model the following independent state dimensions.

```text
Surface
├── Browser
└── Agent

PresentationMode
├── Normal
└── Reading

AgentState
├── Idle
├── Listening
├── Thinking
└── Acting

TextEntryState
├── Inactive
└── Active
```

`Surface` and `PresentationMode` MUST NOT be collapsed into duplicated states such as `BrowserReadingAgentThinking`.

The RG edition exposes both `Normal` and `Reading` modes.

The Phone edition MAY keep `PresentationMode = Normal` for MVP; it MUST NOT imitate RG's head-controlled Reading interaction merely for parity.

Switching Browser ↔ Agent MUST preserve:
- current browser tabs and active tab;
- page state and scroll position as far as WebView naturally permits;
- agent conversation state;
- unsent agent draft text.

Switching Normal ↔ Reading on RG MUST preserve the underlying page or conversation content and its logical position.

---

# 7. Browser Core

## 7.1 WebView

Each active browsing session MUST be backed by Android System WebView.

The Browser Core is responsible for:
- navigation;
- current URL/title state;
- back navigation;
- tabs;
- page lifecycle;
- browser-agent semantic observation and action;
- screenshots when required by the agent;
- communication between native code and the page runtime.

The Browser Core MUST NOT depend on the phone or RG physical input scheme.

## 7.2 Browser chrome

In Normal presentation, the Browser surface is based on the selected Soft Dock direction and contains the following controls:

```text
[Agent] [URL / location] [+] [tab count] [reserved menu]
[web content]
```

The Agent control replaces the conventional browser Home role and switches to the Agent surface.

The URL/location field:
- MUST show the current location;
- MAY allow manual URL entry;
- is a fallback, not the intended primary navigation mechanism.

The `+` control MUST create a new empty tab.

The tab-count control MUST display the number of tabs. Detailed manual tab-management UI is not required in MVP.

The Browser Core MUST nevertheless expose programmatic tab operations sufficient for the agent or future UI to list, switch, create, and close tabs.

The reserved menu slot MAY remain visually reserved while its settings workflow is omitted from MVP.

## 7.3 Reading presentation

Browser Reading Mode on RG MUST:
- show the same webpage, not an extracted reader copy;
- preserve webpage styling/content;
- hide EyeBrowse browser chrome;
- hide the head pointer;
- use the full available content viewport.

Reading Mode MUST NOT automatically invert, simplify, reflow, summarize, or otherwise rewrite arbitrary webpages merely because Reading Mode is active.

---

# 8. Semantic browser-control runtime

## 8.1 Principle

The built-in agent MUST manipulate EyeBrowse through a small semantic browser-control API.

CDP is NOT a required production interface for the MVP.

CDP MAY be used for development/debugging or a future fallback, but the agent MUST NOT depend on a WebView debugging socket for ordinary operation.

The intended control loop is:

```text
observe
  ↓
reason
  ↓
act
  ↓
settle
  ↓
verify
  ↓
diff
```

## 8.2 Page runtime

EyeBrowse SHOULD inject or otherwise host a small page runtime that can:
- inspect visible/actionable DOM semantics;
- derive roles, labels, text, state, and geometry;
- create model-visible element references;
- observe page mutations;
- execute or coordinate page-level actions;
- report structured results and failures to native code.

The implementation MUST keep privileged Android capabilities outside the trust boundary of arbitrary webpage JavaScript.

A webpage MUST NOT be able to call agent, networking, credential, or device-privileged capabilities merely because the semantic runtime is present.

## 8.3 Observation

The agent-facing observation SHOULD prioritize semantic information rather than raw HTML.

A typical observation may contain:
- document identity;
- URL;
- title;
- viewport information;
- visible text excerpts;
- actionable elements with compact references;
- semantic role/name/state;
- relevant capability or limitation flags.

The observation SHOULD omit irrelevant DOM noise to reduce token cost and improve reasoning quality.

Sensitive fields, especially password inputs and explicitly protected values, MUST be redacted from model-facing observations.

## 8.4 Element references

Agent-visible element references MUST be scoped to the current document/frame identity.

A reference MUST fail explicitly after the referenced document or element becomes invalid, detached, replaced, or navigated away.

The agent SHOULD be required to re-observe rather than blindly reuse stale references.

## 8.5 Standard browser tools

The MVP agent-facing browser profile SHOULD remain small. It MUST support the semantic equivalents of:

```text
observe()
open(url)
back()
activate(ref)
type(ref, text)
scroll(...)
extract(...)
screenshot()
```

Tab operations MAY be exposed separately.

The agent-facing API MUST NOT expose arbitrary WebView internals as its normal interface.

## 8.6 Actionability

Before performing a user-level action such as activation or typing, EyeBrowse SHOULD verify relevant actionability conditions, including where applicable:
- element exists;
- element is visible;
- element is stable enough to target;
- element is enabled;
- element can receive the intended event;
- editable targets are actually editable;
- the target is not blocked by another visible element.

Failures MUST be structured and informative rather than silently ignored or blindly retried.

## 8.7 Settle and verification

After an action, the browser controller SHOULD wait for the relevant navigation/DOM churn to settle within bounded limits.

The result SHOULD report a concise post-action state change or diff when possible rather than forcing a full page observation after every action.

The agent MUST be able to distinguish:
- action succeeded and page changed;
- action succeeded with no meaningful page change;
- action failed;
- action result is uncertain and requires re-observation.

---

# 9. Agent Core

## 9.1 Runtime role

EyeBrowse MUST contain an on-device agent orchestration loop.

LLM inference itself is remote in MVP.

The agent implementation MAY be:
- an embedded Pi runtime;
- a native Android implementation preserving the required Pi-like agent semantics;
- another implementation explicitly approved later.

This specification constrains agent behavior, not the internal programming language/runtime.

## 9.2 Agent responsibilities

The agent MUST be capable of:
- receiving text prompts;
- receiving ASR-derived command text;
- maintaining the active conversation;
- calling the configured remote LLM API;
- dispatching typed browser tools;
- dispatching approved networking/settings tools;
- observing tool results and continuing until completion or bounded failure;
- streaming assistant output to the Agent surface where supported by the provider.

The agent MUST NOT require a separate external automation process merely to manipulate the EyeBrowse WebView.

## 9.3 Agent surface

The Agent surface is the peer of Browser, not a modal overlay.

Agent Normal presentation contains:

```text
[Browser]                         [reserved menu]
[conversation history]
[input/composer]
```

The Browser control returns to the Browser surface.

The selected Soft Dock direction uses:
- sparse top controls;
- a rounded composer;
- right-aligned user messages;
- open assistant text rather than enclosing every assistant response in a bubble.

The exact typography and color tokens may be refined without changing the information architecture.

## 9.4 Agent Reading Mode on RG

Agent Reading Mode MUST:
- hide the Agent taskbar;
- hide the input/composer;
- hide the head pointer;
- show conversation history only;
- permit Reading Mode scrolling using the RG interaction rules.

---

# 10. Speech Core

EyeBrowse MUST include a shared ASR subsystem.

The speech architecture MUST distinguish two semantic destinations:

```text
voice command → Agent
voice dictation → focused editable field
```

The ASR provider/model MAY be remote and SHOULD support a configured Spark-hosted endpoint.

Provider endpoint/model settings MAY be developer-provisioned for MVP.

The exact physical signals that start:
- agent-command speech;
- normal dictation;

are not defined yet and MUST NOT be invented by implementation without a Project Owner decision.

The UI MUST have a minimal way to present transient states such as listening, transcribing, thinking, and acting without permanently consuming large screen area.

---

# 11. Network Core

Networking is a first-class shared subsystem used by both Android editions.

It contains two distinct capabilities:

```text
Mihomo  = public Internet proxy/routing
Tailnet = private machine/service connectivity
```

They MUST be modeled separately even if a higher-level routing policy selects between them.

## 11.1 Mihomo

EyeBrowse MUST embed a Mihomo-based proxy core for MVP.

Mihomo is the preferred owner of EyeBrowse's Android `VpnService` slot when TUN/VPN operation is enabled.

The shared Network Core SHOULD provide abstractions for:
- subscription/config loading;
- proxy groups/nodes;
- route/rule configuration;
- connectivity/health state;
- selecting the active proxy route;
- diagnostics.

Full user-facing configuration UI is not required in MVP.

## 11.2 Tailnet

EyeBrowse MUST support built-in private tailnet connectivity.

The tailnet implementation MUST NOT require a second simultaneous Android `VpnService` if Mihomo already owns the system VPN slot.

The preferred architecture is userspace Tailscale/tsnet-style connectivity embedded in the app.

Exact library/binding choices are implementation details provided they preserve:
- private tailnet identity;
- tailnet ACL/Grant semantics at the network layer;
- direct access to configured private services;
- coexistence with Mihomo.

Tailnet provisioning/login MAY be developer-driven in MVP; a polished user-facing pairing/settings flow is not required.

## 11.3 Routing classes

The shared Network Core SHOULD expose route intent rather than forcing higher layers to understand low-level interfaces.

At minimum, it should be possible to distinguish:

```text
PUBLIC_DIRECT
PUBLIC_PROXY
TAILNET
AUTO
```

Configured Spark services MAY use an `AUTO` policy that prefers private tailnet reachability and falls back to an explicitly configured public HTTPS/Funnel endpoint when available.

The exact fallback policy MUST be deterministic and observable through diagnostics.

## 11.4 Coexistence requirement

The MVP network architecture passes only if:
- Mihomo can be active for public Internet traffic;
- EyeBrowse can simultaneously reach a configured private tailnet service;
- enabling one does not disable the other through Android's one-VPN limitation.

---

# 12. Spark and remote services

EyeBrowse MAY consume remote services hosted on DGX Spark, including:
- OpenAI-compatible LLM endpoints;
- ASR endpoints;
- normal websites/content services;
- Paseo;
- future personal context and agent services.

Spark exposure may use:
- tailnet/private addresses;
- Tailscale Funnel / public HTTPS;
- another explicitly configured HTTPS endpoint.

EyeBrowse MUST treat these as configurable remote service endpoints rather than hard-coding assumptions about one transport into Browser or Agent logic.

A public Funnel endpoint does not itself provide application authorization. Any sensitive public service exposed through Funnel MUST add appropriate application-level authentication outside this MVP client specification.

---

# 13. MVP configuration policy

The MVP does not require complete settings screens.

The following MAY be developer-provisioned or supplied through local build/device configuration:
- LLM endpoint;
- LLM model;
- LLM credentials;
- ASR endpoint;
- ASR model;
- Mihomo configuration/subscription;
- tailnet provisioning/configuration;
- Spark endpoint(s).

Secrets MUST NOT be committed to the repository.

Hard-coded MVP behavior means hard-coded/product defaults and local developer provisioning, not checked-in production credentials.

Eventually:
- Browser settings belong primarily to network configuration;
- Agent settings belong primarily to LLM/ASR configuration.

Those settings workflows are not part of MVP acceptance.

---

# 14. Phone interaction

EyeBrowse Phone MUST use normal Android interaction conventions.

At minimum:
- touchscreen taps interact directly with Browser/Agent controls and WebView content;
- ordinary touch scrolling is supported;
- Android system back behavior is handled sensibly by the current surface/browser history;
- normal Android IME/software keyboard is used for text entry;
- the user can switch Browser ↔ Agent through the same conceptual controls as RG.

The phone edition MUST NOT simulate head pointer or Reading-mode head scrolling.

The phone UI SHOULD preserve the same product identity and Soft Dock visual structure while adapting spacing and typography to cover vs inner windows.

---

# 15. RG interaction architecture

RG input is device-specific and MUST be implemented in the RG application layer or RG-specific interaction module, then mapped into shared semantic intents/browser actions.

The RG interaction stack conceptually is:

```text
Gyroscope / accelerometer / touchpad
                 │
                 ▼
      RG Interaction Controller
                 │
         ┌───────┴────────┐
         │                │
      Normal           Reading
         │                │
   head pointer       head scroll
         │                │
         └───────┬────────┘
                 ▼
       shared Browser/Agent state
```

The implementation SHOULD reuse proven head-tracking ideas such as smoothing, dead zones, recentering, and motion adaptation, but EyeBrowse does not require a system-wide AccessibilityService mouse because it controls its own surfaces.

---

# 16. RG Normal Mode

Normal Mode is for manipulation.

In Normal Mode:
- the EyeBrowse taskbar appropriate to the active surface is visible;
- a head-controlled pointer is visible;
- head yaw controls pointer X;
- head pitch controls pointer Y;
- pointer motion applies to EyeBrowse UI and WebView content;
- short tap activates the current pointer target;
- touchpad swipe remains available for discrete scrolling.

The pointer SHOULD be rendered by EyeBrowse itself rather than depending on a separate system-wide cursor overlay.

Pointer movement SHOULD include:
- smoothing;
- a noise dead zone;
- recenter/reset behavior;
- motion compensation or sensitivity reduction when strong body motion would otherwise create false input.

Exact gain/filter values are hardware-validation items, not fixed by this specification.

---

# 17. RG Reading Mode

Reading Mode is for consumption.

On entry to Reading Mode:
- the visible head pointer MUST disappear;
- the surface taskbar MUST disappear;
- Browser shows webpage content only;
- Agent shows conversation history only;
- an open RG keyboard MUST be dismissed;
- Reading-mode head-scroll state MUST be initialized/recentered to a neutral reference.

In Reading Mode:
- upward relative head pitch scrolls upward;
- downward relative head pitch scrolls downward;
- neutral head position stops continuous scrolling;
- larger pitch displacement SHOULD increase scroll speed;
- a neutral dead zone MUST prevent drift from causing constant scroll.

The implementation MAY use integrated/virtual pitch displacement rather than raw instantaneous gyro velocity so that a deliberate held displacement can produce continuous scrolling.

Exact dead-zone size, acceleration curve, maximum velocity, neutral acquisition, and drift correction are hardware-validation items.

---

# 18. RG touchpad bindings

The MVP touchpad contract is:

```text
short tap      → normal activation/click in Normal Mode
double tap     → toggle Normal ↔ Reading
swipe forward  → scroll down
swipe backward → scroll up
```

Swipe forward/backward MUST work in both Normal and Reading modes.

Double-tap recognition MUST NOT leak the first constituent tap as an unwanted single activation.

The implementation MAY defer single-tap dispatch for a bounded recognition window or use an equivalent gesture recognizer.

The exact double-tap timing threshold is tunable.

The behavior of an isolated short tap while in Reading Mode remains an open interaction decision because the pointer is hidden and no target semantics have been approved.

---

# 19. RG in-app keyboard

The RG edition MUST include an in-app keyboard for EyeBrowse text entry.

It is not required to be a system-wide Android IME.

The keyboard MUST be able to enter text into ordinary editable WebView controls and the Agent composer.

The initial MVP keyboard SHOULD use familiar QWERTY organization and MUST provide at least:
- letters;
- Shift/case control;
- Backspace;
- Space;
- Enter/Done;
- common punctuation;
- a symbols/numbers state.

Password/private fields MUST remain visually masked and MUST NOT expose their contents to the agent observation stream.

The keyboard is controlled through the RG head pointer plus short tap.

The selected design study used approximately 300 px of the 640 px RG canvas for the keyboard and approximately 42.8 px key widths in the ten-key row. These are study values, not frozen production dimensions.

Entering Reading Mode SHOULD dismiss the keyboard and clear active text-entry focus while preserving draft text where practical. Automatic keyboard reopening on exit from Reading Mode is not required.

Exact key sizes, dwell/selection comfort, and optical readability MUST be validated on real RG hardware.

---

# 20. Browser ↔ Agent surface switching

The Browser surface exposes an Agent control.

The Agent surface exposes a Browser control.

These controls form the primary application navigation model.

The application MUST NOT require a bottom navigation bar, side drawer, or third permanent primary surface for MVP.

Switching surfaces MUST be fast and MUST NOT destroy the inactive surface's state.

The agent MAY continue an in-flight remote request/tool sequence while the Browser surface is visible, subject to normal Android lifecycle constraints.

---

# 21. Persistence

For MVP, EyeBrowse MUST preserve state during ordinary surface/mode transitions and configuration changes where Android recreation can reasonably be handled.

At minimum, app-local persistence SHOULD cover:
- non-secret product settings;
- configured endpoint metadata;
- Mihomo configuration/subscription state;
- tailnet device/provisioning state as required by the selected library;
- active agent conversation state sufficient for ordinary use.

WebView cookies/storage SHOULD use normal app-private WebView persistence.

Full cloud sync or cross-device browser-session sync is not required.

---

# 22. Security and trust boundaries

## 22.1 Web content

Arbitrary webpages are untrusted.

The semantic page runtime MUST NOT expose privileged native application APIs directly to arbitrary page scripts.

Native ↔ page messages MUST be scoped, validated, bounded, and versioned or equivalently structured.

## 22.2 Agent observations

Page content is untrusted input to the model.

The Agent Core SHOULD treat webpage text as data rather than instruction authority.

Sensitive editable values MUST be redacted where feasible.

## 22.3 Credentials

LLM/ASR credentials, tailnet provisioning secrets, private keys, and similar secrets MUST NOT be stored in source control.

On-device secrets SHOULD use Android app-private secure storage/Keystore-backed mechanisms appropriate to their type.

## 22.4 Tailnet

A private tailnet connection MUST preserve normal tailnet identity/authorization semantics; EyeBrowse MUST NOT bypass ACL/Grant policy merely because tailnet networking is embedded.

---

# 23. Failure and recovery behavior

The MVP SHOULD fail visibly and recoverably rather than silently.

At minimum:

### Browser tool failure
Return structured failure to the agent, including whether a fresh observation is required.

### LLM failure
Keep the current conversation/browser state and show a compact recoverable error on Agent.

### ASR failure
Do not submit fabricated text. Preserve current input state and allow retry.

### Mihomo failure
Expose disconnected/error status to the Network Core and diagnostics; do not claim proxy connectivity.

### Tailnet failure
Allow public/fallback routes where explicitly configured, but do not silently treat a public route as private tailnet reachability.

### RG sensor failure
The app MUST remain exit-able and operable through available touchpad/manual paths to the extent possible. It MUST NOT enter uncontrollable continuous scrolling.

---

# 24. Visual direction: Soft Dock

Soft Dock is the selected MVP visual direction.

The following are design-level requirements unless real-device validation forces revision:
- rounded browser control grouping/capsule treatment;
- restrained persistent chrome;
- rounded Agent composer;
- right-aligned user messages;
- open assistant text;
- explicit rounded RG keyboard targets;
- RG Reading Mode contains no persistent app controls;
- Phone and RG should visibly feel like the same product while using device-appropriate density and interaction.

The current study uses warm pale phone surfaces with restrained green accents and a black/light high-contrast RG presentation. Exact tokens and font metrics MAY change during native implementation while preserving the selected hierarchy and product identity.

---

# 25. MVP acceptance gates

The integrated MVP is acceptable only when the following are demonstrated.

## 25.1 Build and launch

- Two installable Android APKs can be produced from the same repository: Phone and RG.
- RG build runs on Android 12 / API 32.
- Phone build works on both Fold6 cover and inner windows without clipped primary controls.

## 25.2 Browser

- Browser loads ordinary HTTPS pages through System WebView.
- URL/location state updates with navigation.
- New tab creates an empty tab and count updates.
- Browser ↔ Agent switching preserves browser state.

## 25.3 Semantic agent control

A configured agent can, using the semantic browser API rather than CDP as its normal path:
- observe the current page;
- navigate to a URL;
- activate an observed target;
- type into an editable field;
- scroll;
- receive structured success/failure and post-action state information.

## 25.4 Agent

- Agent accepts text input.
- Agent calls the configured remote LLM endpoint.
- Agent streams or displays the response.
- Agent can invoke browser tools and continue reasoning from tool results.

## 25.5 RG Normal Mode

On real RG hardware:
- head motion moves the visible pointer with usable stability;
- short tap activates the pointed target;
- pointer can operate both EyeBrowse chrome and WebView content;
- forward/backward swipes scroll in the specified directions.

## 25.6 RG Reading Mode

On real RG hardware:
- double tap enters Reading without triggering an unintended underlying click;
- cursor/taskbar disappear;
- head-pitch displacement produces controllable up/down continuous scrolling;
- neutral position stops scrolling without persistent drift;
- double tap returns to Normal;
- swipe scrolling remains available.

## 25.7 RG keyboard

On real RG hardware:
- focusing a supported editable field can open the in-app keyboard;
- head pointer + tap can enter and correct text;
- Backspace, Space, Shift, symbols, and Done work;
- password/private text remains masked;
- keyboard can be dismissed and does not survive into Reading Mode.

## 25.8 Networking

- Mihomo can provide the configured public proxy route.
- Tailnet private connectivity can be active at the same time without taking over a second Android VPN slot.
- A configured Spark/private service is reachable over tailnet while Mihomo is active.
- A configured public HTTPS/Funnel service can be reached through the normal public networking path when used.

## 25.9 Speech

Once Project Owner activation signals are defined:
- command speech can be transcribed and delivered to Agent;
- dictation speech can be transcribed and delivered to the focused editable target.

The activation-signal choice itself is not an acceptance criterion until explicitly specified.

---

# 26. Required validation before values are frozen

The following MUST be validated on real RG hardware and remain tunable until then:

1. head-pointer gain;
2. pointer smoothing/filtering;
3. adaptive motion/noise behavior;
4. pointer target comfort;
5. Reading neutral acquisition;
6. Reading dead zone;
7. Reading pitch-to-scroll velocity curve;
8. drift correction/recentering;
9. taskbar height and optical readability;
10. RG keyboard height/key sizes;
11. swipe vs simultaneous head-scroll arbitration;
12. native density/insets;
13. exact double-tap timing;
14. short-tap behavior in Reading Mode.

Simulator values recorded in the design study are evidence for prototyping only.

---

# 27. Open Product Owner decisions

The following decisions are intentionally unresolved in SPEC v1 and require explicit Project Owner input or hardware validation:

1. Physical signal that starts Agent voice-command ASR.
2. Physical signal that starts normal dictation ASR.
3. Reading Mode isolated short-tap behavior.
4. Whether swipe input temporarily neutralizes/suspends head-scroll state or composes with it.
5. Final RG cursor visual treatment.
6. Final tab-count interaction/manual tab-selection behavior.
7. Whether reserved `⋮` controls are visible-but-inert, hidden, or open a minimal status surface in the first shippable MVP.
8. Exact Pi runtime strategy: embedded upstream runtime vs native Pi-compatible implementation.
9. Exact tailnet Android embedding library/binding, provided the no-second-`VpnService` requirement is preserved.

These unresolved items MUST NOT be silently decided by implementation agents.

---

# 28. Reference projects and design evidence

The following projects are useful references but are not automatically dependencies or authorities:

- Android System WebView / AndroidX WebKit — browser substrate.
- Agentic WebView — semantic WebView agent-control reference.
- BrowserOS — semantic snapshot / ref-driven action / post-settle diff concepts.
- Stagehand — token-efficient observation and agent-oriented browser semantics.
- browser-use — model-visible element references and agent/browser state concepts.
- Playwright — actionability concepts.
- Lightpanda — close agent/browser integration and deterministic replay concepts.
- GazeMou / RokidAppMaker — RG gyroscope head-pointer and scroll interaction reference.
- Pi — agent-loop/product inspiration.

Actual code reuse MUST be evaluated separately for license, Android suitability, maintenance cost, and scope.

The selected Product Design evidence lives under:

```text
design/
├── mvp-design-brief-v0.md
└── soft-dock/
    ├── README.md
    ├── design-decisions.md
    └── design-qa.md
```

---

# 29. Core MVP statement

EyeBrowse MVP succeeds if one shared Android platform can produce:

- a normal-touch Phone browser/agent experience;
- a head-pointer/head-scroll RG browser/agent experience;
- one semantic browser-control substrate for the built-in agent;
- one speech layer for command and dictation;
- one networking layer containing both Mihomo and private tailnet connectivity;

while preserving the deliberately small product model:

```text
Browser ↔ Agent
```

and, on Rokid Glasses:

```text
Normal ↔ Reading
```
