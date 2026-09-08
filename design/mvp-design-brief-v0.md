# EyeBrowse MVP Design Brief v0

**Status:** Pre-SPEC design brief
**Purpose:** Input to Product Design and early interaction prototyping
**Target platforms:** Android phone + Rokid Glasses
**Product name:** EyeBrowse

---

## 1. Product vision

**EyeBrowse is a minimal, AI-native browser designed around a built-in agent rather than conventional browser chrome.**

It should feel like a browser in which:

- the web remains the primary visual surface;
- an embedded Pi-like agent can understand and operate the browser;
- voice can replace much manual navigation and configuration;
- networking freedom and private connectivity are built into the product;
- phone and glasses editions share the same product model while using interaction methods appropriate to each device.

EyeBrowse should not attempt to reproduce all of Chrome.

The central philosophy is:

> **Keep the browser visually minimal and let the agent absorb complexity.**

---

# 2. Product structure

EyeBrowse has two editions built on one shared platform:

```text
                    EyeBrowse Platform
                           │
          ┌────────────────┴────────────────┐
          │                                 │
   EyeBrowse Phone                    EyeBrowse RG
     phone APK                         glasses APK
```

These should **not be separate product forks**.

They share:

- browser model;
- agent;
- networking;
- ASR;
- tab/session state;
- settings model;
- browser-agent control layer.

They diverge mainly in:

- interaction logic;
- physical input;
- UI density;
- device lifecycle;
- device-specific capabilities.

---

# 3. Shared EyeBrowse platform

The shared platform conceptually contains four major systems:

```text
SEE       Browser / WebView
THINK     Pi-like Agent
HEAR      ASR
CONNECT   Mihomo + Tailnet
```

More explicitly:

```text
EyeBrowse Shared Core

├── Browser Core
│   ├── Android System WebView
│   ├── tabs
│   ├── navigation state
│   └── semantic browser-control runtime
│
├── Agent Core
│   ├── Pi-like agent loop
│   ├── LLM API
│   ├── browser tools
│   └── session/chat history
│
├── Speech Core
│   ├── ASR client
│   ├── agent-command speech
│   └── normal dictation
│
└── Network Core
    ├── Mihomo
    ├── Tailscale/tailnet
    └── routing/connectivity policy
```

---

# 4. Browser philosophy

EyeBrowse should begin from a **bare Android WebView**, not from a conventional full browser.

The browser engine should preferably be Android System WebView rather than a bundled Chromium fork.

The MVP should avoid conventional browser features unless they are required for the core experience.

The website itself should dominate the viewport.

---

# 5. Agent/browser relationship

The built-in agent is not an external automation client controlling a browser remotely.

It is intended to feel like a **native cognitive component of EyeBrowse itself**.

Conceptually:

```text
User
 │
 ▼
Pi Agent
 │
 ▼
EyeBrowse Browser API
 │
 ▼
WebView + semantic page runtime
 │
 ▼
Web
```

The agent should preferably manipulate the WebView through a small semantic browser API rather than CDP.

The desired conceptual control loop is:

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

The exact implementation is an engineering concern rather than a visual-design concern.

For Product Design, the important fact is:

> Pi can understand the current page and perform browser actions on behalf of the user.

---

# 6. Primary information architecture

EyeBrowse MVP has only **two primary surfaces**:

```text
Browser
   ↕
Agent
```

There should not be conventional multi-level application navigation in the MVP.

---

# 7. Browser surface

Current conceptual layout:

```text
┌─────────────────────────────────────────────┐
│ 🤖   [ URL / location ]   +   [tabs]   ⋮   │
├─────────────────────────────────────────────┤
│                                             │
│                                             │
│                 WEB CONTENT                 │
│                                             │
│                                             │
└─────────────────────────────────────────────┘
```

Top bar elements:

### 🤖 Agent button

Replaces the conventional browser Home button.

Action:

```text
Browser → Agent
```

---

### URL / location field

Displays the current URL/location.

It remains available as a fallback manual navigation mechanism, but it is **not intended to be the user's primary navigation interface**.

The preferred navigation model is:

```text
"Open Hacker News"
        ↓
       Pi
        ↓
     Browser
```

rather than manually typing URLs.

---

### `+`

Creates a new tab.

---

### Tab count

Displays the number of tabs and eventually provides access to tab management.

Detailed tab-management UX is not yet defined.

---

### `⋮`

Reserved for Browser configuration.

Eventually this menu should contain only EyeBrowse-relevant configuration, especially:

```text
Network
├── Mihomo
└── Tailnet
```

It should **not reproduce Chrome's verbose settings menu**.

For MVP, this menu does not need to function.

---

# 8. Agent surface

Current conceptual layout:

```text
┌─────────────────────────────────────────────┐
│ 🌐                                      ⋮   │
├─────────────────────────────────────────────┤
│                                             │
│                                             │
│                CHAT HISTORY                 │
│                                             │
│                                             │
├─────────────────────────────────────────────┤
│ [               input area               ]  │
└─────────────────────────────────────────────┘
```

### 🌐 Browser button

Returns to Browser:

```text
Agent → Browser
```

This creates a symmetric relationship:

```text
             🤖
Browser ─────────► Agent

Browser ◄───────── Agent
             🌐
```

---

### Agent configuration `⋮`

Eventually contains:

```text
LLM
├── provider
├── endpoint
├── API key
└── model

ASR
├── provider/endpoint
└── model
```

For MVP, none of this needs to be implemented as UI.

---

### Chat history

Displays the active Pi conversation.

---

### Input area

Used for normal text prompts when needed.

Voice will also be an important input mechanism.

---

# 9. Phone edition interaction

EyeBrowse Phone should use normal Android interaction conventions.

Primarily:

- normal touchscreen interaction;
- normal Android scrolling;
- normal software keyboard/IME;
- standard touch activation of Browser and Agent controls.

Phone should not imitate the unusual RG interaction system merely for visual consistency.

The product model is shared; the physical interaction model is not.

---

# 10. Rokid Glasses edition

EyeBrowse RG uses a glasses-specific interaction system built around:

```text
head movement
+
Rokid touchpad
+
voice
```

The core browsing experience has two persistent modes:

```text
NORMAL MODE
READING MODE
```

These modes apply to **both Browser and Agent surfaces**.

---

# 11. RG Normal Mode

Purpose:

> **Interact with content.**

In Normal Mode:

- head movement controls a visible pointer;
- top taskbar is visible;
- cursor is visible;
- user can interact with WebView content and EyeBrowse UI.

Conceptually:

```text
head yaw   → pointer X
head pitch → pointer Y
```

The head-pointer concept is inspired by GazeMou for Rokid Glasses, but EyeBrowse only needs to control its own application/WebView.

It does not need to behave as a system-wide mouse.

---

# 12. RG Reading Mode

Purpose:

> **Consume content with minimal visual obstruction.**

In Reading Mode:

- cursor disappears;
- taskbar disappears;
- content becomes effectively fullscreen;
- vertical head movement controls scrolling.

The head acts as a one-dimensional analog scrolling controller.

```text
head tilted upward
        ↓
continuous scroll upward

neutral head position
        ↓
STOP

head tilted downward
        ↓
continuous scroll downward
```

Scrolling speed should conceptually scale with pitch displacement:

```text
large up    → faster scroll up
small up    → slow scroll up
neutral     → stop
small down  → slow scroll down
large down  → faster scroll down
```

A comfortable neutral dead zone is necessary.

The exact sensitivity and velocity curve must eventually be tested on physical RG hardware.

---

# 13. Agent Reading Mode

Reading Mode also exists on the Agent surface.

### Agent Normal Mode

Displays:

```text
taskbar
chat history
input area
cursor
```

### Agent Reading Mode

Displays:

```text
chat history only
```

Specifically hide:

- Agent taskbar;
- input/composer;
- cursor.

Head-pitch scrolling operates on chat history.

This keeps the same conceptual rule across both surfaces:

> **Normal Mode = interact.**
> **Reading Mode = consume.**

---

# 14. RG MVP touchpad bindings

Current MVP bindings:

### Short Tap

```text
Short Tap → normal tap/click
```

In Normal Mode, this activates the target at the current head-pointer location.

It is not reserved for agent invocation or mode switching.

---

### Double Tap

```text
Double Tap
    ↓
Normal Mode ↔ Reading Mode
```

This behavior is universal across Browser and Agent.

The implementation must distinguish single tap from double tap without accidentally activating the target during a double-tap sequence.

Exact timing is an implementation/ergonomic tuning issue.

---

### Swipe Forward

```text
Swipe Forward → scroll down
```

---

### Swipe Backward

```text
Swipe Backward → scroll up
```

Swipe scrolling should remain available in both Normal Mode and Reading Mode.

Therefore RG has two complementary scrolling mechanisms:

```text
Head pitch
= continuous / analog / hands-free

Touchpad swipe
= discrete / manual / precise
```

---

# 15. RG text entry

YodaOS/Rokid Glasses should not be assumed to provide a useful system software keyboard.

EyeBrowse RG therefore includes its own **in-app text-entry keyboard**.

This is not necessarily a system-wide Android IME.

It exists primarily for text fields inside EyeBrowse/WebView.

Conceptually:

```text
WebView input receives focus
        ↓
EyeBrowse keyboard appears
        ↓
head pointer selects key
        ↓
short tap activates key
```

Initial MVP keyboard can be conventional QWERTY.

The keyboard should prioritize:

- sufficiently large targets;
- correction;
- short text;
- passwords/private input;
- names;
- symbols;
- situations where ASR is unsuitable.

It does not need to compete with a phone keyboard for high-volume typing.

---

# 16. Voice and ASR

EyeBrowse includes ASR as part of the shared platform.

Voice has at least two distinct meanings:

```text
VOICE → Pi command

VOICE → normal dictation
```

These will eventually be distinguished by different interaction signals.

Those signals are **not yet defined** and should not be invented during this design phase.

Example command:

> "Open Hacker News."

Example dictation:

> "I'll meet you tomorrow."

The visual system should leave room for transient states such as:

```text
listening
transcribing
agent thinking
agent acting
```

without permanently consuming large portions of the display.

---

# 17. Networking

Networking is a first-class part of the shared EyeBrowse platform.

It includes both:

```text
Mihomo
+
Tailscale / tailnet
```

Their conceptual purposes differ:

```text
Mihomo
= public Internet / proxy routing

Tailnet
= private machines and services
```

Both Phone and RG editions should consume the same shared networking model.

In MVP, configuration UI is not required.

---

# 18. Spark / remote services

EyeBrowse may communicate with services running on DGX Spark, including:

- LLM endpoints;
- ASR endpoints;
- web services;
- Paseo;
- future personal/context services.

Connectivity may use:

- tailnet;
- Tailscale Funnel;
- ordinary HTTPS;
- Mihomo-routed Internet.

This is infrastructure context rather than a major UI concern for MVP.

---

# 19. Hard-coded MVP configuration

To avoid premature settings UI, MVP may hard-code or developer-configure the minimum working values for:

```text
LLM endpoint
LLM model
LLM credentials

ASR endpoint
ASR model

Mihomo configuration/subscription

Tailnet configuration

Spark/service endpoints
```

Therefore:

### Browser `⋮`

May be visually present but non-functional or minimal.

### Agent `⋮`

May be visually present but non-functional or minimal.

The purpose of MVP is to prove the **core browsing + agent + glasses interaction experience**, not settings management.

---

# 20. Shared state model

Avoid creating separate duplicated modes for every possible combination.

At minimum, model these as independent dimensions:

```text
Surface
├── Browser
└── Agent

PresentationMode
├── Normal
└── Reading
```

This creates:

```text
Browser + Normal
Browser + Reading
Agent + Normal
Agent + Reading
```

Additional temporary states can exist independently:

```text
AgentState
├── Idle
├── Listening
├── Thinking
└── Acting

TextEntry
├── Inactive
└── Active
```

This avoids state explosion.

---

# 21. MVP screen/state inventory

The visual design should primarily address:

### Browser / Normal

Taskbar + WebView + RG cursor when applicable.

### Browser / Reading

Fullscreen WebView.

### Agent / Normal

Taskbar + chat history + input.

### Agent / Reading

Chat history only.

### RG Text Entry

Browser/Agent context plus EyeBrowse keyboard.

### Transient Voice State

Minimal listening/transcribing indication.

### Transient Agent State

Minimal thinking/acting indication.

No additional permanent screens should be invented unless clearly justified.

---

# 22. Product principles

## Content first

Web content and conversation content should dominate the display.

---

## Agent over configuration

If Pi can reasonably perform an operation, avoid building a complicated settings workflow for it prematurely.

---

## Familiar where useful

The Browser top bar intentionally resembles familiar Android Chrome structure.

Do not redesign familiar concepts merely to appear novel.

---

## Different devices, same product

Phone and RG should visibly belong to the same product but should not be forced into identical interaction mechanics.

---

## Minimize persistent UI

Especially on RG, every permanent pixel must justify itself.

---

## Interaction before decoration

Ergonomics, readability, mode switching and head-control behavior matter more than visual flourish.

---

## Browser and Agent are peers

Neither should feel like a secondary modal dialog.

They are EyeBrowse's two primary surfaces.

---

# 23. Explicit MVP non-goals

Do not design or prioritize:

- conventional browser Home page;
- bookmarks system;
- download manager UI;
- history management UI;
- desktop-site controls;
- extensions;
- sync;
- password manager;
- verbose privacy/settings screens;
- full Mihomo configuration UI;
- full Tailscale configuration UI;
- LLM provider configuration UI;
- ASR provider configuration UI;
- sophisticated tab management;
- system-wide RG mouse;
- system-wide Android keyboard;
- local LLM inference;
- elaborate visual branding or animations.

These may be reconsidered later.

---

# 24. Important unresolved areas

Product Design should not silently invent permanent answers for these:

1. Exact visual style and typography.
2. Exact taskbar height on RG.
3. Exact URL-field behavior.
4. Detailed tab-management experience.
5. Exact RG cursor appearance.
6. Exact transition feedback between Normal and Reading Mode.
7. Exact head-scroll dead zone and acceleration.
8. Exact keyboard layout beyond an initial QWERTY assumption.
9. Voice-command activation signal.
10. Dictation activation signal.
11. Pi listening/thinking/acting transient presentation.
12. Behavior of short tap in Reading Mode where no visible pointer target exists.
13. Whether the two `⋮` buttons should remain visible but inactive in MVP or simply be omitted until implemented.

These should be explored or tested rather than prematurely frozen.

---

# 25. First Product Design assignment

Product Design should **not implement production UI yet**.

First:

1. Play back/confirm this brief.
2. Identify the essential user journeys and product states.
3. Preserve the deliberately small information architecture.
4. Design for both:
   - Android phone;
   - Rokid Glasses at approximately 480×640.
5. Produce exactly **three substantially different visual directions**.

All three directions must preserve:

```text
Browser ↔ Agent
Normal ↔ Reading
```

and the established interaction constraints.

Do not add conventional browser features merely to fill space.

---

# 26. Flows that visual exploration must cover

At minimum:

### Flow A — Browse normally

```text
launch
↓
Browser Normal
↓
head/touch interaction
↓
navigate/click/scroll
```

---

### Flow B — Browser Reading Mode

```text
Browser Normal
↓
double tap
↓
Browser Reading
↓
head-pitch scrolling
↓
double tap
↓
Browser Normal
```

---

### Flow C — Browser → Agent

```text
Browser
↓ 🤖
Agent
↓ 🌐
Browser
```

---

### Flow D — Agent Reading Mode

```text
Agent Normal
↓
double tap
↓
Agent Reading
↓
read/scroll chat
↓
double tap
↓
Agent Normal
```

---

### Flow E — RG manual text entry

```text
Normal Mode
↓
editable field
↓
EyeBrowse keyboard
↓
head pointer
↓
short tap
↓
text entry
```

---

### Flow F — Voice → Pi

```text
voice-command signal
↓
ASR
↓
Pi
↓
browser action
↓
visible result
```

The activation signal itself remains unspecified.

---

# 27. MVP design success criteria

A successful design should make the following feel immediately plausible:

### On phone

EyeBrowse feels like an unusually clean Android browser with an AI agent built into its foundation.

### On glasses

A first-time user can understand:

```text
move head → point
tap → activate
swipe → scroll
double tap → focus/read
```

without a conventional browser UI tutorial.

### Across both

The product should clearly communicate:

```text
Browse the web
        ↕
Talk to the agent
```

with minimal visual machinery between the user and the content.

---

# 28. Core product statement

The design should ultimately express this idea:

> **EyeBrowse is not Chrome with an AI button added to it. It is a minimal browser environment in which the browser, agent, voice input and networking stack are designed as one system.**

And for Rokid Glasses specifically:

> **Normal Mode is for manipulating the web. Reading Mode is for consuming it.**
