# RG direct input boundary — issue #29

Worker: Worker-#29

Current authority is **SPEC D2.2 / normative HUD H1.2** at
`6750d3d2eea6e2eebc9b5a3ffd4370fb4bbb2abd`, the revised canonical issue #29 and
assignment revision 2. The canonical canvas remains **480 pixels wide × 640 high, portrait**.
HUD-G1 remains open for later production HUD work. This preparatory refactor does not implement
the new toolbar, tabs/utilities, edge scrolling, margin fix or removal of legacy production modes.

## Local input

| Component | Local responsibility |
| --- | --- |
| `RgInputRouter` | One existing pad recognizer, ordinary Activity/focus/tracking lifecycle and immediate confirmed gesture dispatch. |
| `RgInputTarget` | Three direct operations: activate at current pointer coordinates, scroll the current visible View and send a built-in key. |
| `NativeRgInputTarget` | Current coordinate transform and normal Android touch dispatch, visible page `scrollBy`, current focused View's native input connection. |
| `RgKeyboardKeys` | Reusable key meanings, Shift/symbol state and key rows; no editor binding or field-value mirror. |
| Existing head source/model/overlay | Sensor acquisition, pointer calculation and local drawing, already independent of the remote controller. |

A local Activity supplies its current View tree and visible page, constructs `NativeRgInputTarget`
and exactly one `RgInputRouter`, routes its pad events there, and starts/stops the existing
`PointerOverlay`. The native router reads the pointer when a single gesture is confirmed. It
supplies no captured document/element/selection, expected state, per-action generation, ABA check
or original hit snapshot. Its confirmation timer is the existing double-tap recognition interval,
not a separate action-delivery queue. Android hit testing and focus own the actual recipient.

The local Activity supplies its horizontal-swipe/tab callback. The reusable local path does not
inherit legacy Reading or swipe-to-scroll policy. This issue leaves current production bindings
unchanged; later authorized work connects the new tab behavior and edge scrolling.

Built-in character/Space keys use the current native input connection; Backspace, arrows and Enter
use normal key events. Done calls the owner's keyboard-dismiss action without sending Enter.
Shift/symbol keys change only the app-local key layout. WebView handles focused fields, selection
and password presentation. The helper never reads or stores field values/passwords, keeps an
editor registry or queues a captured key. Address entry may use a normal focused Android editor
and the same key path; the production compact toolbar/keyboard is later HUD work.

Use current geometry, visibility and normal Activity/sensor cleanup. The visible-page supplier
must return only the current usable page (or null while a utility covers it). There is no second
recognizer, concurrent pointer writer, generic engine/plugin framework or remote session dependency.
Dispatch return values mean native event acceptance, not proof of an edit, submission or rendering.

## Temporary historical consumer

The old remote `RgInputRouter` is retained as `LegacyRgInputRouter`. Existing `MainActivity` uses
only that router until the authorized local cutover. Its original snapshot/admission, native
control meanings, delayed remote target handling, image matrix mapping and mode callbacks remain
inside that historical code. It does not implement the local interface. Existing remote keyboard
`RgKeyboard`/`RgKeyboardView` retain their remote-only editor/intent handling; only plain key types
and row construction move to `RgKeyboardKeys`. No remote protocol is rebuilt or generalized.

The old gesture and keyboard tests update those type names mechanically. Existing frame/context/
profile/ordinal processing remains in the controller/link code. It is a temporary compilation/
regression dependency, not a v0.0.2 feature, local requirement or new remote-parity gate. Final
local cutover removes or inactivates unused legacy wiring after tracing consumers.

Shared-core and Phone source are untouched. Head source/model, overlay and recognizer algorithms
remain unchanged. No protected article or historical R3 record is edited.

## Verification and evidence

`RgKeyboardKeysTest` checks the controller-free built-in layout. Existing affected JVM regressions
remain. Run the common debug/unit/lint host checks for both consumers through the Manager's
serialized build lock with JDK17, assigned SDK and
`--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`. Bind logs, executed/cached tasks,
JUnit/lint reports and APK hashes to the actual candidate.

`NativeInputTestActivity` is a debug-only owned regression fixture, not the product entry point
or a production HUD/reference design. It has no presentation controller, Phone link or image page.
`NativeInputInstrumentedTest` routes replayed raw poses and actual pad-device KeyEvents through
the production local router to native controls/WebView. It observes one button activation and
actual focused text/password/textarea edits through the built-in key controls. Page JavaScript
only reads the owned fixture's layout/focus/effects; it never assigns expected outcomes. Done
dismissal leaves completed input intact. A lossless live password capture is retained for actual
masking inspection; input-type metadata or a method return alone does not prove masked pixels.

A bounded RG-only reservation is needed for those two native-input tests and the existing
ordinary legacy activation/lifecycle checks appropriate to the renamed consumer. Preserve exact
source/APK/physical device/provider/route/input bindings, actual terminal test identities,
rendered evidence and cleanup. Replay and screenshot evidence do not establish physical gesture
ergonomics or optical comfort. Engine/HUD qualification remains with its authorized tickets.
No broad paired ordinal/keyboard matrix or unavailable Phone becomes a gate merely because
legacy code temporarily compiles here. Report a concrete additional touched-consumer need if one
is discovered.

Checkpoint `b480f6d3f4146bcfdc0b1382b717ecfa80009ce1` and its D2.1/H1.1 evidence remain historical.
Its generalized local original-intent/generation/ABA tests were removed to follow D2.2; this
requirements change is not a failed attempt or erased history. Current source/host/device results
and remaining resource gaps accompany the new exact-head review report. Until a reservation is
granted, devices and fixture/link services remain untouched; missing real evidence is not PASS.
