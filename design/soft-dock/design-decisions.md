# EyeBrowse Soft Dock — design study v0.1

Selected by the Project Owner on 2026-09-08: displayed direction 2, Soft Dock.

## Authority and scope

The supplied MVP Design Brief v0 remains the authority. Soft Dock selects rounded browser control groups, a rounded composer, right-aligned user bubbles, open assistant text, and explicit rounded keyboard targets. It does not approve new features, production code, or final ergonomics.

Browser chrome retains Agent, URL, new tab, tab count, and reserved menu. Agent retains Browser, reserved menu, history, and composer. Neither gains a sidebar or a third permanent surface. The two menus and detailed tab management remain reserved. A new tab is an empty page.

## Device targets

| Target | Study viewport | Meaning |
| --- | --- | --- |
| Fold6 cover | 360 × 884 | Illustrative logical viewport for the tall, narrow cover-screen layout |
| Fold6 inner | 760 × 884 | Illustrative logical viewport for the near-square unfolded layout |
| Rokid Glasses | 480 × 640 | Exact CSS study canvas for the requested physical-pixel target |

The browser may scale the canvas to fit the review window. This is not an Android device emulator. Android window density and insets must be measured later. Phone text wraps and the conversation has a comfortable maximum width on the inner screen. Resizing preserves the active surface and drafts.

## RG states

| Surface | Normal | Reading |
| --- | --- | --- |
| Browser | Taskbar, webpage, pointer | Same webpage, fullscreen |
| Agent | Taskbar, conversation, composer, pointer | Conversation only |

The sample webpage has light/dark presentation authored for this study. Reading never extracts, inverts, or otherwise restyles website content. Text and formatting remain the same across Normal/Reading.

## Established interactions

- Head yaw/pitch conceptually controls pointer X/Y in Normal. Mouse movement stands in for head input in the study.
- Short tap activates the current target in Normal. It is not the Agent shortcut.
- Double tap toggles presentation across both surfaces without activating the underlying target. The study defers singles for 300 ms; this is a tunable test value.
- Forward swipe scrolls down and backward swipe scrolls up in both modes. The study buttons scroll 180 px.
- In Reading, upward pitch scrolls up, downward pitch scrolls down, neutral stops, larger displacement increases speed. The external slider uses arbitrary relative units, not measured head angles. A ±2-unit dead zone and 190 px/s maximum are illustrative only.
- RG keyboard is in-app, QWERTY, with Shift, Backspace, Space, punctuation, symbols and Done. The keyboard occupies 300 px of the 640 px RG canvas. The ten-key row has 42.8 px-wide cells after gaps and margins, and needs hardware testing. A field context stays above it.
- Phone fields use the template's Android keyboard presentation; typing uses the computer keyboard in this review environment. That keyboard image is not a complete tap-operable Android IME.

## Explicit study assumptions — not approved MVP decisions

- Entering Reading dismisses the keyboard and clears focus while retaining drafts. Returning to Normal does not reopen the keyboard automatically.
- A Reading short tap performs no action while its actual behavior remains unresolved. Double tap remains the defined exit.
- Swiping returns the simulated pitch slider to neutral; arbitration of simultaneous physical head-scroll and swipes is not decided.
- Browsing, agent replies and transient voice feedback are fixtures. An open command loads the Field Notes fixture and keeps the Agent surface until the user returns via the globe.
- Voice-command and dictation activation signals are not assigned. The external transient selector is only a review control; it holds the selected state for inspection. Submitted sample prompts advance through simulated states automatically.
- Pointer motion has no dwell activation. Exact pointer gain, jitter filtering, neutral acquisition, acceleration, hit targets and taskbar height require real RG testing.

## Fidelity corrections

The generated board has approximate frame proportions. The study corrects these to explicit target windows instead of stretching the phone and RG canvases to reproduce image-generation distortion. All chosen visual structure and key controls are retained.

Icons use the [Phosphor family](https://phosphoricons.com/), chosen for its rounded robot/globe and keyboard symbols. The target has no content imagery requiring raster asset generation.

## Next design review

Prioritize RG text size, toolbar and key targets, and uninterrupted Normal/Reading transitions. Decide the unresolved interactions only after inspecting the study and, for physical comfort, testing on RG. Production implementation and a refined specification follow those decisions.
