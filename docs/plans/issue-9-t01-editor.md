# Issue 9 T01: renderer editor and authority foundation

Controlling plan: [5780808159](https://github.com/code2hack/EyeBrowse/issues/9#issuecomment-5780808159),
clock reconciliation [5780823585](https://github.com/code2hack/EyeBrowse/issues/9#issuecomment-5780823585),
renderer disposition [5782588285](https://github.com/code2hack/EyeBrowse/issues/9#issuecomment-5782588285),
local-focus qualification [5785170815](https://github.com/code2hack/EyeBrowse/issues/9#issuecomment-5785170815),
and in-place profile supplement [5786061535](https://github.com/code2hack/EyeBrowse/issues/9#issuecomment-5786061535).
The raw InputConnection route failed the admitted-A/later-B isolation probe on S20+/WebView99;
that is retained round-1 RED evidence. This implementation is round 2, a renderer mechanism,
not native remote editing or completed RG-keyboard acceptance.

## Packaging and privilege boundary

`renderer-editor` uses the existing Kotlin compiler version, 2.2.21, with one Kotlin/JS whole-program
plain artifact. Phone's asset task packages the generated file as `eyebrowse-editor.js`. No webpack,
npm editing library, dynamic module loader, downloaded program, or separate browser engine is used.
Review the Kotlin source and emitted asset together; the exact asset SHA belongs in every candidate
receipt. External DOM declarations and object/typed-array allocation are the visible platform interop.

The Phone loads the fixed asset off the UI thread and outside controller/link locks. Public WebView
evaluation invokes one fixed entry, with the request serialized as a JSON **string**, including escaped
line separators. No wire script, selector, HTML, property path, reflection, native bridge or extra endpoint
exists. Page code can see/interfere with its own DOM and the page-realm helper; its observations/results
are not Phone authority or a security sandbox. No trust, storage, OS or Phone-control privilege is exposed.

## Exact target and mutation transaction

One document instance, one grant, one in-flight operation, one explicitly retained resize target. A grant
stores the actual top-level element, token, native context, renderer generation, selection and revision.
Input text/password, textarea, and plain contenteditable hosts with direct text/BR children are supported.
Frames, shadow editors and arbitrary rich markup fail explicitly. Initial production grant discovery
checks the accepted activation's measured hit against the actual focused editor; edits never resolve a
selector or use the latest activeElement as their destination.

Focus transitions revoke immediately. Mutation observation includes removals, ancestor disabled state,
target attribute ABA and plain-content changes. `takeRecords()` runs at the execution boundary, so delayed
observer delivery cannot revive a removed/reinserted or temporarily readonly/disabled/type-changed node.
Selection must still match the captured revision. New documents do not auto-install an old edit/grant.

The edit reserves its renderer ordinal and marks itself in-flight before page callbacks. It emits a
cancellable synthetic `beforeinput`, then revalidates the original node, grant, selection, pending mutation
records and relevant value state. The bound `setRangeText` primitive edits controls; a bounded Range edits
plain text/BR content. Neither calls InputConnection, defers mutation, writes innerHTML nor replaces the
entire field. Post-edit `input` is emitted once; post-input refocus retires authority while preserving the
honest original-node result. Nested edit attempts fail BUSY and never become an effect backlog.

Backspace deletes the selected range or preceding Unicode code point; beginning/empty is no-change.
Applicable maxlength blocks growth, not deletion of already-overlong content. Fields stay masked and
values remain transient in the renderer. A dirty episode is separate from executable authority and contains no value
journal. Reconciliation retires the affected binding and clears the due episode before calling change
listeners; reentry cannot notify it twice. Actual departure is required; selection/window ambiguity
alone cannot invent a change. A genuine native change consumes the due episode without suppressing the
native event. Done can cancel remote observation without forcing blur; the episode remains observable
by real focus events. The secondary50ms timer is not a history witness.

## Enter, cancellation and Done

Multiline Enter inserts one line break. Single-line Enter resolves only the editor's associated form.
The first default submitter in document order is used, including external form associations. Disabled
default means no fallback. The real default-button click preserves click handlers and normal browser
validation; the no-default case uses requestSubmit only with at most one implicit-submission blocking field.
A scoped submit capture guard revalidates after click/validation callbacks and cancels a stale/reassociated
submission. The fixture records total submit events separately from non-prevented submission requests;
page-authored click/blur/submit side effects are not a promise of website transaction control.
No form.submit, direct POST, validation bypass or fallback after uncertainty exists.

Done retires only its matching grant and never sends Enter or forces blur. Synthetic event `isTrusted`
is false. Trusted-event-only applications, arbitrary rich editors and composition are not silently claimed
as native-equivalent. The required ordinary/event-driven controls still require physical qualification.

## Native authority, ordering, clocks and wire freeze

Protocol major 1/minor 2; capabilities `TEXT_INPUT_V1` and `RG_VIEWPORT_UPDATE_V1`. Base #6 trust and #7
browsing continue for an older peer; keyboard admission explicitly reports unavailable. New effects remain
`BrowserActionMessage` with canonical injective v2 IDs and the existing prepared ordinal/high-water rules.
Insert is <=256 UTF-8 bytes, address <=4096 UTF-8 bytes, token <=128 characters, within unchanged 32KiB
control records. Malformed decode errors and object formatting never echo typed bytes.

Editor close and viewport transition are bounded state-only messages. Viewport transition IDs are monotonic
per authenticated connection, with one previous exact request/result retained for idempotence. Same-owner
resize increments viewport epoch while preserving owner/control epoch and action high-water. A dedicated
lease transfer closes old frame admission without normal release or surface-null teardown. It stages one
replacement reader, waits asynchronously for old capture callbacks, resizes the existing VirtualDisplay,
replaces its Surface directly with the non-null new surface, then closes the disconnected old reader.
The same Presentation/window/WebView attachment and continuous local focus must survive. Actual display,
window/container/WebView layout and renderer viewport are checked before rebind and capture; API return
or requested allocation is not completion. Matching current Image dimensions and a visual-state/committed
draw fence precede resized output admission. Old reader/sink/context tokens remain invalid even when
numerical dimensions recur. Ordinary Stop/expiry retain their normal teardown semantics.

Retained state is a non-executable original-target reference, not an old edit grant. Focus loss/departure,
node mutation or incompatible selection destroys it. Rebind/reveal mints a fresh token/generation while
preserving logical revision; old tokens never become executable. Native geometry and actual renderer CSS
viewport use the measured WebView scale, never DPI inference. Remote edits remain disabled until a fresh
profile frame establishes readiness. Same-size no-op requests do not revoke a usable editor. The wire
never selects a DOM node.

All 64-bit identity/ordinal/transition fields cross into the renderer as canonical decimal strings, never
JavaScript Number. A separate monotonic **native editor lifecycle** order fences late grant/revoke scripts;
it is not a replacement effect allocator. At most one effect is pending. No IO or blocking renderer waits
occur under controller/link/gesture locks. A current-context rejected effect consumes its ordinal. Missing
results are uncertain, never retried; a verified renderer revocation barrier is required before a fresh
grant, successor transition or completed Stop. Old callbacks/timers cannot clear or close successors.

Android scheduling/callback observations use elapsedRealtime. Renderer stages use performance.now within
that renderer only. Do not subtract these clocks or Phone/RG clocks; receipts retain separate intervals.
The existing <=100ms real enqueue endpoint and <=1s fixture effect target remain unchanged.

## Qualification scope

`RendererEditorQualificationTest` invokes the actual bundled production adapter on the S20+ private
presentation. It is explicitly substrate evidence, not fake RG authentication, a real key journey or
website server-transaction success. Fixture initialization/negative stimuli and independent boolean
observations are separate from adapter-generated positive edits. New editor/protocol/authority JVM tests
supplement the inherited identity inventory. The final receipt must enumerate actual rows, unavailable
conditions, hashes, timing stages and cleanup; this document itself asserts no executed device PASS.

T01's scoped independent source/security review remains mandatory before T02 release.

## In-place qualification boundary

The public local-focus arrangement retains NOT_FOCUSABLE|NOT_TOUCHABLE and adds LOCAL_FOCUS_MODE.
Only the current RG-owned attached private host requests local focus. Its readiness is separate from
Phone global focus and editor permission. Physical focus/dismiss/retirement events follow the current
host identity across geometry epochs; old reader/layout callbacks follow their own tokens.

The prior recreate-at-size checkpoint c3dbf26 is NOT_PASSED: physical shrink preserved the live page but
created a real focus gap and invalidated the retained target. The in-place path has no blur exemption,
no silent rebuild/readoption and no fallback surface variant. PR1/PR2 qualification is required before
PR3/PR4 and full integration acceptance; the source here does not assert a hardware pass. Full T01 and
both-app protected direct-LAN evidence, ordinary Phone IME return, cancellation/failure/lifecycle tests,
exact-head host inventory and independent review remain required. The explicit USB-conditional
screen-off/secure-lock disclosure remains separate from final cleanup lock verification.

The initial readiness diagnostic at cdcbdba measured native480x344 but CSS171.0222168x123.0222244
at actual DPR/WebView.scale2.8125, mapping to481x346. Chromium99 converts native pixels to
integer DIP with an upward rounding, then back to Blink pixels with another upward rounding
(ViewAndroid::OnSizeChanged; WidgetBase::DIPsToCeiledBlinkSpace). The renderer comparator models
those float conversions using measured DPR, without a generic tolerance or inferred display density.
Exact native/reader/image/encoded geometry, fresh-context barriers and the original2s deadline
remain separate requirements: nearby native dimensions can share the same logical bucket.
This correction is the Manager-authorized I9-T01-COMPARATOR-CORRECTION-ROUTED-GLMR2-20260923-01;
the setup failures remain retained, and no successful in-place physical qualification is asserted here.
