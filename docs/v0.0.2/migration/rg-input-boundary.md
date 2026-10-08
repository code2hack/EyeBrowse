# RG input/execution boundary — issue #29

Worker: Worker-#29

This is a behavior-preserving refactor from `86eb903ac7f94102e243e20d555dce3521954614`,
under SPEC D2.1, normative HUD H1.1, ADR-0001 and the
[Owner decision removing separate ticket planning](https://github.com/code2hack/EyeBrowse/issues/28#issuecomment-6065936978).
The future RG canvas is **480 pixels wide × 640 high, portrait**. HUD-G1 remains open.
This change does not install the production local browser/HUD, change gestures, fix pointer
overshoot/margins, add edge scrolling, remove recentering, or implement tabs/utilities.

## Execution and original intent

`RgInputRouter` receives an `RgInputTarget` and the actual browser root View. It owns the
existing single pad recognizer and confirmation timer. It has no controller, remote context,
image, frame, command ordinal, or editor-grant prerequisite.

| Contract | Responsibility |
| --- | --- |
| `RgInputState` | Immutable original execution identity and page readiness. |
| `RgInputGeometry` | Measured root-to-page mapping; no bitmap/profile requirement. |
| `RgInputIntent` | Original hit target, semantic key/control generation or mapped page point. |
| `RgInputTarget` | Snapshot, geometry, hit testing, immediate guarded dispatch and scrolling. |
| `RgInputCapture` | The existing confirmation fence: original hit equality and geometry generation before dispatch. |
| `RgKeyboardInput` | Native key rendering/availability/dispatch, independent of a concrete controller. |

State equality is authority, not a display-label comparison. A local implementation must
include browser lifetime, stable active-tab identity, selection generation, document, layout,
editor/session and readiness revisions as applicable. A→B→A tab or editor selection must
advance generation; equal coordinates, a reused tab index or restored readiness cannot revive
an old operation. Geometry and intent implementations must likewise have stable value equality
for an unchanged mapping/meaning, rather than allocating unequal identity objects each frame.

The execution owner revalidates the expected state at the actual effect boundary. Router hit
validation does not authorize a later unguarded queued effect. Returns retain their existing
meaning: local dispatch/queue admission, not proof of navigation, editing, rendering or submission.
There is no new replay queue, retry mechanism, second recognizer, sensor writer or browser framework.

## Legacy adapter and preserved guards

`MainActivity` binds `LegacyRgInputTarget` to the same controller and native listeners.
The adapter owns the legacy native IDs/consent labels and `PointerImageGeometry`. Shared
`RgViewGeometry` retains z-order, visibility/alpha, clipping, ancestor transforms, padding and
pointer-root hit coordinates. Bitmap dimensions must still match the measured profile;
density-scaled intrinsic dimensions still map to native presentation pixels. Margins and
covered/disabled controls remain rejected.

The existing `RgInputSnapshot` remains the remote state implementation: ControlContext,
owner, measured profile, input revision, page/handoff/history/address readiness and prepared-slot
revision all participate in equality. Controller frame/context/profile admission, namespace,
ordinal consumption, single pending command and authenticated queue are unchanged. Native Retry
still revalidates synchronously and reads trust outside the controller monitor.

Pending DOWN/UP/confirmation work still cancels on input-state or measured-geometry change,
pause, focus loss, stale sensor or modal occlusion. Same-context decoded frames retain their
existing behavior. The recognizer still consumes one physical sequence once, suppresses the
constituent single taps of doubles/composites, and rejects unrelated/modified/repeated/held input.
Legacy Reading is supplied through optional router hooks in `MainActivity`; it is absent from
the required local target and keyboard contracts. Legacy double-tap/swipe effects are preserved.

## Head input and local keys

`HeadPoseSource`, `SensorHeadPoseSource`, `RotationSample`, `HeadOrientation`, `HeadPointerModel`
and `PointerOverlay` were already independent of the remote controller. They remain unchanged.
A local Activity can acquire/render the existing pointer without creating a presentation receiver;
its optional sample callback does not require Reading or a Phone link.

`RgKeyboard<Editor>` now accepts the execution owner's editor identity type. The legacy
controller uses `EditorTarget`; a local owner can use its own immutable tab/document/element
binding. Neither `RgKeyboardView` nor the router requires that wire type. The existing printable
key/address bounds policy is retained. Field values/passwords are never copied into the model;
only the transient address draft is stored. Generation fencing, redacted key intents, Unicode
caret/backspace behavior, layers and Enter/Done semantics remain unchanged.

## Verification and limits

The host `RgInputTargetTest` uses an image-free local portrait viewport and tab-aware owner
to exercise actual `RgInputCapture` dispatch, original-target cancellation, tab-return fencing,
geometry changes and reentrant mutation checks. `RgKeyboardTest` exercises local editor identities;
the existing snapshot, image geometry, recognizer, pointer, ordinal, editor and consumer tests remain.
These host fixtures establish a usable interface, not engine/editor or real-device qualification.

Run the current DEV debug/unit/lint gate for both consumers under the Manager's serialized build
lock, JDK17/assigned SDK and `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`, plus
`python3 -m unittest discover -s tools/tests`. Preserve executed task identities, XML counts,
warnings, source/APK hashes and command results in the mission evidence.

Real-device legacy verification requires an explicit Manager reservation. Proposed affected
checks are `PointerInputInstrumentedTest`, `PointerGestureInstrumentedTest`, the paired
`PreparedOrdinalJourneyTest#preparedOrdinalMatrixUsesActualQueueAndFixtureEffects`, and the paired
keyboard journey including masked live pixels. Existing procedures and independent Phone fixture
observations establish effects; no expected value is assigned directly into a page. The synthetic
state/mapping cases in the standalone gesture suite remain labeled synthetic.

At source preparation, no device was accessed: RG belonged to Worker-#28 and Phone was unreserved.
Host checks or compiled instrumentation do not satisfy those device checks. The exact candidate's
executed evidence, unavailable resources, device/software/route binding, privacy observations and
cleanup must accompany the review handoff. Required missing evidence remains open.

Shared-core and Phone source are untouched; both consumer builds/checks are still required.
Material boundary/architecture/acceptance obstacles return to Manager. Protected articles and
historical R3 evidence remain unchanged; this report grants no new device/provider/security access.
