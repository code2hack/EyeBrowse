# Soft Dock design QA

final result: passed

This pass covers the disposable design study. It does not approve native Android behavior, hardware ergonomics, or exact optical rendering on Rokid.

## Evidence and normalization

Source visual truth: `references/soft-dock-selected.png`, 1672 × 941 pixels. The supplied MVP brief remains authoritative for behavior and target geometry.

Browser-rendered implementation: `qa/*-full.jpg`; corresponding exact app crops are `qa/*.png` and observed app rectangles are recorded in `qa/*-bounds.json`. Browser screenshot viewport was approximately 1363 × 936 at density 1. App canvases rendered unscaled: cover 360 × 884 CSS px, inner 760 × 884 CSS px, RG 480 × 640 CSS px. Crop pixels equal CSS pixels.

The generated board contains approximate device proportions. Its individual source panels were resized for side-by-side comparison; the actual implementation uses the target aspect ratios. These comparisons assess hierarchy, component treatment and state completeness, not pixel-perfect equivalence to distorted generated frames.

Full-view source/rendered comparisons: `qa/comparison-phone.png` and `qa/comparison-rg.png`. Both were opened and visually inspected together with their source panels. Focused comparisons: `qa/rg-browser-normal-focus.png` and `qa/rg-keyboard-focus.png`, also opened and inspected.

## States captured

| Screenshot prefix | Viewport | State |
| --- | --- | --- |
| phone-cover-browser | 360 × 884 | Browser, sample article |
| phone-cover-agent | 360 × 884 | Agent, Thinking fixture |
| phone-inner-browser | 760 × 884 | Browser, sample article |
| phone-inner-agent | 760 × 884 | Agent, completed summary |
| rg-browser-normal | 480 × 640 | Browser Normal |
| rg-browser-reading | 480 × 640 | Browser Reading |
| rg-agent-normal | 480 × 640 | Agent Normal |
| rg-agent-reading | 480 × 640 | Agent Reading |
| rg-keyboard | 480 × 640 | Browser Normal, Name field focused |

## Findings and iteration history

The first pass was blocked by four P2 findings, recorded in `qa/comparison-rg-before.png` and `qa/comparison-phone-before.png`.

| Earlier finding | Correction | Post-fix evidence and assessment |
| --- | --- | --- |
| Excessive RG Browser spacing hid the continuation link | Reduced subtitle and paragraph gaps | `comparison-rg.png`: intro and continuation link fit in Normal; resolved |
| RG Agent spacing obscured the final summary point | Tightened user-to-Pi gap and response rhythm | `comparison-rg.png`: all three points fit above the composer; resolved |
| RG keyboard was too short at 248 px | Increased keyboard to 300 px; retained visible field context | `comparison-rg.png`, `rg-keyboard-focus.png`: lower half-display keyboard with four rows; resolved |
| Inner-screen typography looked too small | Increased article and conversation type on the wide preset | `comparison-phone.png`: clearer hierarchy, responsive wrapping; resolved |

The cover Agent was recaptured in Thinking to align the comparison state. No unresolved P0/P1/P2 visual findings remain for study scope.

Required fidelity surfaces:
- Fonts/typography: rounded sans-serif hierarchy, bold article headings, open Pi text, readable field and key labels retained. Exact font metrics cannot be recovered from generated art; small weight/size differences are acceptable P3 study differences. RG readability still needs device validation.
- Spacing/layout rhythm: Browser capsule, sparse Agent toolbar, right-aligned user bubble, open responses and bottom composer match the selected structure. Correct phone ratios intentionally reveal more content than the approximate board. Reading removes all persistent app controls.
- Colors/tokens: warm pale phone surfaces and restrained green accents; RG black canvas, light text and inverted focus targets. Differences caused by optical display behavior remain untested.
- Image quality: the design has no content imagery. Phosphor provides vector icons. Library icons approximate the generated robot/globe silhouettes; accepted P3. Browser automation cursor overlays in captures are environmental and are not app UI.
- Copy/content: reference article and summary examples retained; continuation copy provides a scrollable fixture. Reserved menu/tab-management controls remain reserved. Different hovered keyboard keys are an interaction-state variation.

## Interaction verification

Verified through the cloud browser:
- All three device canvases have the stated dimensions; switching Fold views retains the active surface.
- Browser/Agent switching works. A submitted sample prompt produces Thinking, a simulated action/reply and the sample webpage.
- RG double tap enters/exits Reading across both surfaces. Reading contains no toolbar, composer, keyboard or pointer.
- Double-clicking New tab changes mode without incrementing its count; delayed single activation is suppressed.
- Downward pitch advances content; neutral stops it. Two later reads held the same scroll position after neutral.
- Forward swipe advances the sample article by 180 px; backward returns it toward its previous position.
- RG Name input accepts keys; Backspace changed Hugo to Hug. Symbols exposes numbers and punctuation. Private input renders masked characters.
- URL submission returns to the fixture article and dismisses the keyboard.

`npm run build` passed after final code edits, including TypeScript and the integrity check for 28 protected runtime files. Runtime changes are scoped to documented additive device presets and app-only presentation wrappers.

Application warnings/errors after the QA reload: none. Earlier dependency hot-reload noise cleared after reload; unrelated browser-extension messages were excluded.

## Remaining design decisions

Real head-input comfort, pointer gain/filtering, neutral acquisition, pitch curves, keyboard hit-target comfort, native window density/insets, Reading short-tap behavior, voice activation and swipe/head-scroll arbitration are unresolved. Current simulator values are explicitly recorded as illustrative in `design-decisions.md`.
