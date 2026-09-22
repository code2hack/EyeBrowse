# I8-T02 — safe pad gestures and explicit consent routing

Worker: Worker-v0.01. [Controlling R2 plan5771447930](https://github.com/code2hack/EyeBrowse/issues/8#issuecomment-5771447930), §§4–7. Baseline151e435. This todo adds the RG routing layer; actual paired Phone effects and final integration remain T03/T04. No Phone, wire, canonical v2 ID or command-ordinal change.

## Observed input path and recognition settings

The production adapter accepts only the observed `ROKID,PSOC-TP-R` InputDevice with SOURCE_KEYBOARD: ENTER66, OEM double291, forward292, backward293. Device IDs are resolved by Android rather than hard-coded. Unknown/system keys and other keyboards pass through normal handling. Recognized pad events are consumed so focused Button default handling cannot create a second click. No system input injection, accessibility/IME service, new MotionEvent gesture or production replay receiver.

[KeyEvent](https://developer.android.com/reference/android/view/KeyEvent) timestamps and the confirmation Handler use uptime; pose age continues to use elapsed realtime separately. On the real RG, [ViewConfiguration](https://developer.android.com/reference/android/view/ViewConfiguration) reports double-tap300ms and long-press400ms. Constructor adapters bound those to100–600ms and200–2000ms respectively, with no device-setting change. Events older than250ms at receipt, future/out-of-order timestamps or negative/inconsistent down times fail closed.

Recognition rules:

- A fresh DOWN/UP shorter than the configured long-press threshold captures one target at UP. Only confirmation after the double-tap window may activate it. Repeated DOWN, unmatched UP, repeat/long-press/cancel flags and held sequences do not activate.
- A second ENTER sequence inside the window or OEM291 consumes both singles and emits one internal mode-toggle intent. A duplicate OEM marker following recognized double ENTER does not emit another intent. Reading behavior is left for #10.
- OEM292/293 complete to one +160/-160 native-pixel scroll step, superseding any unconfirmed tap. Swiping targets the active browser region without requiring page hover.
- A double/swipe suppresses trailing ENTER constituents for one configured double window; overlapping triple/composite tails are dropped, not queued. This is a bounded fail-closed policy. The physical finger event timing/composite encoding is NOT EXERCISED; Android/framework replay and the documented platform codes are separate evidence.
- Confirmation clears its pending record before dispatch. There is one pressed sequence, one pending tap and one scheduled confirmation; no recovery/retry backlog.

## Snapshot and geometry contract

A completed short tap stores its original aimed root point, semantic target, measured geometry version, and `RgInputSnapshot`. Head movement during the wait cannot retarget it. The image map composes native view-to-root transforms, ImageView padding and its actual image matrix; intrinsic drawable coordinates are scaled into the current presentation profile's native pixels. Clipped or letterboxed margins are rejected without clamping. No density/480×640/480×405 assumption or production DOM/JavaScript geometry parsing.

Topmost visible native views are considered before the image. Disabled, unknown or covered controls block click-through. Native targets retain their View identity and label/meaning; an old Use on glasses intent cannot execute Use on phone. An additional visible utility layer above the browser owns input, and window-focus loss/modal/pause cancels pending work.

A small-tree geometry fingerprint checks actual rectangles, transforms, visibility, enablement and native button meaning. It excludes bitmap identity/frame sequence and irrelevant title/status text. Same-context decoded frames therefore do not cancel every tap. Real layout, control meaning, document/ownership/profile/readiness or sensor changes invalidate pending intent. Pose freshness is also checked synchronously at admission in case its timer was delayed.

## Narrow controller bridge

`RgPresentationController.inputSnapshot` captures full ControlContext/owner/measured profile, readiness and a local invalidation revision. This revision is ephemeral gesture state, not a protocol epoch or replay-cache replacement. It changes on policy/context/link invalidation and outgoing action/handoff reservation; routine same-context frame sequences do not change it.

`dispatchIfCurrent` compares the captured snapshot and invokes the existing reservation/send path under the same monitor. Main-thread view validation cannot be interleaved by another UI event; background controller updates cannot slip between validation and dispatch. All canonical command IDs, durable ordinals, Phone admission and effectSucceeded semantics are unchanged. Busy/unready capture is rejected rather than retained for acknowledgement or reconnect.

One pending handoff prevents duplicate consent requests and disables page input while that request is unresolved. A matching authoritative result/state, failed send, disconnect or bounded5s timeout releases it. Nothing predicts ownership or retries automatically. Only the deliberately selected native handoff action requests transfer; page/nav gestures while Phone owns do not acquire control. Phone-local recovery and existing INVALID_PROFILE handling remain as in #7.

The atomic bridge makes lock ordering explicit: core client lifecycle/auth/state callbacks can hold its operation monitor while RG dispatch holds the controller monitor before sendControl. Those callback entries are marshalled to Main before acquiring the controller monitor, preventing an inverse lock wait. Control-message updates remain serialized under the same controller monitor. This is RG adapter synchronization supporting the bridge, not a core transport/Phone rewrite. On-device synthetic callback/monitor tests exercise the boundary; actual paired transport follows at T03.

## Verification and evidence boundaries

New JVM classes: PadGestureRecognizerTest13, RgInputSnapshotTest4, PendingHandoffTest4, PointerImageGeometryTest5. Preserve all340 T01 identities: **56 nonempty suites /366 JVM identities**. Freeze exact names before the acceptance gate.

Nine new `PointerGestureInstrumentedTest` identities cover actual Activity-dispatched framework events from the observed pad ID/source, raw quaternion aiming, original-target confirmation, double/composite suppression, swipes/repeat/hold/cancel, changed consent meaning/context, pause/sensor/modal cancellation, measured geometry/occlusion, atomic controller validation and same-context decoded frame updates. The prior7 `PointerInputInstrumentedTest` identities are rerun as affected regression:16 declared Android identities in one final RG invocation.

Evidence labels are required:

- Native key events are constructed in instrumentation and dispatched through the actual Activity/adapter; this is not a worn finger gesture or privileged global injection.
- Controller-state fixtures for consent test the real typed request **attempt** while no socket exists, not successful authenticated ownership transfer.
- Synthetic decoded-frame fixtures retain compatibility=false so page commands cannot consume the real durable cursor. They test frame-sequence independence, not a genuine Phone stream. Mapping-only snapshots likewise dispatch no browser command.
- Native Recenter invocation and pointer movement are actual local effects; the confirmation-to-invocation bound is100ms, excluding and separately recording the300ms recognition wait. Eligible authenticated command enqueue/page-effect timing is completed with T03's paired journey.
- No Phone app/install/pairing mutation occurs in T02. RG trust/settings/command cursor are checked before/after. S20 is not freshly certified under the Manager's RG-only routing; full dual checks resume before T03.

The implementation uses existing native transforms, view hierarchy, controller and typed actions. No new transport, keyboard, Reading UI, head-scroll, search, process-recovery subsystem or pointer-position shortcut. Worn calibration/optical/finger mechanics and other approved unattended-profile limits remain explicit.
