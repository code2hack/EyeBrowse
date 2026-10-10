# RG built-in keyboard and native editor qualification

Worker: Worker-#32 · issue #32 · assignment revision1 · I32-T01 attempt1/failure0.

Authority is canonical [issue #32](https://github.com/code2hack/EyeBrowse/issues/32),
SPEC D2.2, HUD H1.2, approved HUD-G1, and the
[accepted native password echo](https://github.com/code2hack/EyeBrowse/issues/29#issuecomment-6072154841).
Source begins at accepted integration `9e2b72270c587a52bd37936d0e80e2b584b8ec79`.
The external candidate manifest binds the exact pushed head/tree, contemporaneous
source snapshots, host tasks, APKs and native proposal. This guide describes the
implementation and tests; it does not claim device execution or acceptance.

The existing `NativeRgInputTarget` remains the single native writer. It asks the
current shown focused View for its InputConnection at each key, commits printable
text/Space, and sends ordinary Backspace/Enter/caret keys. Done dismisses the
keyboard without sending Enter. No field value mirror, editor identity, queued
edit, remote controller or additional recognizer is introduced. Native selection,
constraints, Unicode deletion and form/newline behavior remain provider behavior
that must pass the actual fixture tests.

`RgKeyboardKeys.localLayout()` now supplies the actual rendered key meanings and
rectangles to both `LocalBrowserActivity` and its host controls. The historical
remote keyboard's `rows()` remains unchanged. Keys are black with light outlines
and text. The complete local map is:

| Row | Letters | Number/symbol layer |
| --- | --- | --- |
| 1 | `qwertyuiop` | `1234567890` |
| 2 | `asdfghjkl` | `:-@_?&=#%` |
| 3 | Shift, `zxcvbnm`, Backspace | Shift, `+,;!'"()`, Backspace |
| 4 | 123/ABC, Space, `.`, `/`, Enter/Open, Done | Same actions |

Shift toggles English case and retains its state through a layer change. Enter
is labelled Open for the address editor. Both Enter/Open and Done remain visible
at once. JVM controls exercise both cases/layers, every required visible symbol,
all command keys, four row positions, non-overlap and reachable interior bounds.
They test the map actually consumed by rendering, not the historical remote map.

The toolbar remains `(0,0,480,48)`. The native address editor occupies
`(192,0,128,48)` and follows the full draft's caret horizontally. Its independent
star stays `(320,0,48,48)`; counter and More retain their fixed trailing slots.
Invalid Open preserves draft and page. Done preserves the draft for correction
and never deliberately navigates. The star uses the committed current page.

Showing the keyboard changes only the content viewport to `(0,48,480,392)`;
keys occupy `(0,440,480,200)`. Existing native WebView resize/focus handles field
reveal. There is no script-driven or repeating field reveal to oppose deliberate
edge scrolling. The pointer retains the full 480×640 rectangle and full glyph
clamp. The existing edge callback scrolls only the active page at the physical
screen top/bottom, including while the keyboard stays fixed. The divider, lateral
edge and pad swipes do not introduce vertical scroll triggers. Normal tab
selection/swipes dismiss the old keyboard and preserve surviving live pages.

Three new methods in `LocalBrowserInstrumentedTest` use the production Activity:

| Method | Required effects |
| --- | --- |
| `fourNativeEditorsPreserveUnicodeSelectionCaseAndDone` | Native text/password/textarea/plain editable; static dummy `aé中🙂z`; actual Backspace over supplementary Unicode; native selection replacement/caret; one recognized native key tap and zero double-tap constituent edits; built-in case, symbols, Space, Backspace, Enter/newline/form, Done; document/focus/value preservation. |
| `actualAddressKeysKeepDraftAndFixedToolbarUntilOpen` | Every visible letter/case/digit/required symbol through native key controls; long horizontal draft; separate Open/Done; invalid Open, star saves the committed page during editing, preserved draft on reopening, and valid Open navigation. |
| `nativeFieldRevealSettlesOnceWithoutReloadOrFocusChange` | Activate a lower field before resize; selected field visible within the smaller viewport; edit, dismiss, reopen and edit the same live native field without reload/submission. |

The fixture adds only a fixed `local-keyboard.html` route with declarative dummy
seeds, harmless intercepted form submission and counters. Existing routes and
fixture privacy remain unchanged. Tests never start a server or connect a device.
Native Ctrl+A/caret events establish selection using the platform; they are
labelled setup, not evidence of a nonexistent built-in caret button. Positive
editing uses current shown/laid-out built-in keys. JS observes only owned fixture
state; it never assigns values, selections or scroll outcomes.

Reports retain each method, invocation/source labels, PID/UID, monotonic interval,
reached observations, key counts/dispatch timings, failure and cleanup status.
Failure observations/capture are attempted before scene teardown; their own
failures are retained as suppressed errors. Original failures are not replaced by
a cleanup exception. The existing scene closes its Activity/raw pose source and
restores only the prior `local.tabs` key. Wrapper checks verify that restoration. The address method owns only its fixed fixture bookmark key, requires that key absent, and removes it through the existing failure-preserving cleanup helper; unrelated bookmark entries remain in place.
The host guard must independently verify unrelated preferences and opaque trust.

Password captures are fixture-only early/later native 480×640 window copies after
frame/visual-state waits. Actual timing is recorded; an early frame may already
be masked. Independent inspection must establish ordinary subsequent masking.
Accepted transient final-character echo is not a failure or a new approval gate.
No password value enters logs, reports or a custom masking layer.

Fresh native permission must bind source/APKs, actual physical RG, software,
provider/display/access, fixture/listener ownership, opaque canonical trust and
exact preference/artifact preservation before any device operation. The proposed
invocations and output names live in the external packet. Existing geometry,
bookmark, tab/pad/current-focus and keyboard-edge methods are the affected
regressions; their old source-bound results do not become changed-head evidence.
Harvest only reached originals; verify source/APK/PID/time independently, settle
only owned operations, restore exact scoped keys, and return resources to Manager.

Native focus/reveal/selection/Enter/masking, installed APKs, rendered pixels and
changed-head edge behavior require the separately granted device run. A compiled
test APK is preparation. Raw-pose/pad replay and native window copies support
software effects; physical movement, wearer direction, optics and ergonomics need
their own evidence when claimed. No Phone, QR, launcher cutover, HUD-G2 or release
acceptance is claimed here. Manager owns resource grants, independent review,
PRs, acceptance, merge and closure.

Correction02 adds field-free numeric admission observations immediately before
the existing element hit-point assertion: CSS rectangle/viewport, native page/root
bounds, scale, point, layout and focus flags. These are contemporaneous geometry,
not a reconstructed grant21 rectangle or evidence that reveal succeeded. Grant21
failed before activating the lower field; its exact target geometry and cause
remain unknown. The fixture, native actions and strict reveal assertions are
unchanged. A fresh granted run must establish the actual geometry and effects.
