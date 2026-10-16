# RG nested head scrolling — issue #61

Worker: Worker-#61

This is a **host implementation checkpoint**, not ticket acceptance. Actual RG
reproduction, Android wheel calibration, sensor/head use and connected native
regressions remain **NOT_RUN**. Manager's device queue disposition retains
HOST_ONLY: u4090 access is unavailable, #59's both-app preservation checkpoint is
NOT_CAPTURED, and no exclusive device grant has been issued to this Worker.
No device, original-app data or account was accessed by this checkpoint.

The contract is [issue #61](https://github.com/code2hack/EyeBrowse/issues/61),
[P4.1 §6](https://github.com/code2hack/EyeBrowse/blob/8178a5992899a6dde82d8e20f8c54f87ff81292f/docs/plans/v0.0.4-plan.md),
SPEC D3.1 §§3–4 and HUD H3.1. Source starts at
`cb603db8fc1bb74a3c0a4883e5dc044fc4c1c23c`, tree
`38445bd4b15557c45abda8bc70a98db12d0cccf0`; the assigned branch is
`work/issue-61-nested-scroll-v004`, targeting `v0.0.4`. Main is unchanged.
The Manager handoff binds the published source, APP/TEST hashes, exact commands,
JUnit reports, input maps and terminal results.

## Correction and selection rules

The old Activity checked the outer WebView's `canScrollVertically`, and the
input target used its `scrollBy`. Both miss an independently scrollable DOM
panel in a fixed document. A host browser fixture demonstrates that structural
failure. It does not establish the cause of the Owner's actual RG report.

Head scrolling now reaches the current browser without that outer-document
gate. One read-only `evaluateJavascript` expression uses current browser hit
testing and focus to find available scroll distance. It reads geometry, styles
and focus identity only, returning normalized coordinates and bounded pixels.
It installs no observer, global state, JavaScript interface or original-target
registry, and neither reads nor exports field values, page text or URLs.

The pointer's horizontal position selects the region. An off-screen vertical
strip projects to the middle of the current browser viewport. A pointed panel
takes priority; otherwise a currently visible focused panel can supply the
target. Hit testing is repeated for each admitted scroll, so no DOM element is
retained across close/reopen or document changes. Native
[ACTION_SCROLL](https://developer.android.com/reference/android/view/MotionEvent#ACTION_SCROLL)
dispatch performs the scroll, preserving browser hit handling and focus.

The innermost scrollable ancestor gets the motion. At its endpoint, authored
`overscroll-behavior: contain/none` stops chaining; ordinary `auto` can reach a
scrollable ancestor within the same panel. A panel endpoint never falls back
to the document behind it. A fixed non-scrollable cover is inert. Ordinary
document scrolling remains available when no nested panel or cover owns the
point/focus. Remaining distance caps each native pixel tick; conversion rounds
only the available distance, preserving small ticks at fractional CSS scale.

One geometry request per WebView can be outstanding. Busy or late distance is
dropped, with no deferred motion queue. Before native dispatch, the callback
must return within 50 ms and rechecks the current visible page, window and
caller-supplied current edge/tracking condition. The existing 24-pixel strips,
250-ms sensor-age cutoff, 50-ms integration cap and 100-ms visible-return target
are unchanged. Actual Android timing and pixel-rate qualification are pending.
Native utility ScrollViews retain their existing routing; More remains inert.

Iframe head scrolling is currently unsupported, including same-origin frames:
an iframe hit returns no target rather than scrolling the page behind it.
No frame-origin boundary is traversed. Ordinary native iframe interaction is
unchanged. Browser/OS popup windows outside the current WebView and unusual
scroll models such as reversed vertical layouts have not been qualified.
If the actual reported surface needs one of these, return that concrete finding
through the Manager; do not turn this checkpoint into a claim of support.

## Reproduction and regression evidence

All host browser pages are the non-private `NestedScrollFixture` or the retained
minimal fixture. Host Chrome is `155.0.8059.39`; it is **not** the pinned Android
158 engine, the RG device or physical head use. The private pipe-controlled
browser uses its own profile and no listening service, relay or device route.

| Evidence | Result and boundary |
| --- | --- |
| `host-dom-boundary-004.json` | Outer-only write leaves nested panel at zero; ordinary browser wheel moves it 80 pixels. Structural red, not Android reproduction. |
| `host-dom-boundary-005.json` | Unguarded wheel at panel endpoint moves the background 80 pixels. Retained negative; removing only the outer gate is insufficient. |
| `host-selector-matrix-003.json` | New fractional-scale case fails: a requested 3-pixel tick becomes 2. Other 18 cases pass. Original rounding negative retained. |
| `host-selector-matrix-004.json` | Source-bound selector/browser-wheel checks cover the corrected tick, fixed outer document, competing panels/focus, caret selection, nested auto/contain chains, endpoints/reversal, modal/cover isolation, reopen/replacement, ordinary document and opaque iframe handling. |
| Baseline host gate | Observed 94 JVM cases/19 suites and shell0; 52 executed tasks. Original XML and compiled APK bytes were overwritten before retention. The command/log and original native count observation remain; later XML/APKs do not replace these missing originals. |
| `ordinary-final-001` | 91 JVM cases/18 suites, zero failures/errors; APP/TEST assembly and lint complete. 19 tasks executed / 59 up-to-date; lint 0 errors / 8 warnings. |
| `chromium-final-001` | 91 JVM cases/18 suites, zero failures/errors; APP/TEST assembly and lint complete. 20 tasks executed / 100 up-to-date; lint 0 errors / 11 warnings. |

Both final JVM tasks executed. Cached/up-to-date tasks are not new executions.
Lint retains the Kotlin 2.2 metadata / analyzer 2.0 incompatibility, limiting
semantic coverage despite command completion. Final invocations each retain
their authentic XML, signed APP/TEST, log, source inventory/diff/archive and
hash manifest in a unique private artifact folder before another build.
Their production/test inputs match the published checkpoint; a subsequent
documentation-only edit records these results and the historical retention gap.
The earlier prototype invocations also lack separately retained original XML
and APK sets; only their authentic logs/observations and declared limits remain.

Early host browser startup failures, a stray unary-plus prototype and a host
observer failure after removing fixture nodes are retained. The observer was
corrected for removed owned nodes; assertions and product timing bounds were
not relaxed. No failed command is counted as a new canonical Worker round.

The permanent Android regressions are
`EdgeScrollInstrumentedTest#fixedDocumentNestedPanelsUseNativeWheelAndKeepEndpointsIsolated`
and `#modalAndCoveredNativeMenuNeverScrollTheBackground`. They use production
input, owned content and the existing raw quaternion source. They cover native
wheel displacement, two panels, nested ancestors, fractional conversion,
endpoints, modal native activation and the 100-ms More cancellation oracle.
Their assembly is a host check; their execution and assertions remain NOT_RUN.
Raw quaternion replay never counts as an actual sensor or wearer result.

The obsolete `nativePageScrollDelta` helper and its three model-only JVM tests
were removed with the old outer-only writer. Current unit totals therefore
start at 91; the earlier 94-case executions are retained, not rewritten.
Endpoint coverage now measures the actual browser boundary in host fixtures
and is prepared for native dispatch in the two Android methods above.

## Required continuation

After Manager verifies #59's coherent both-app preservation and grants a fresh
exclusive reservation, bind actual physical serial/user/OS, current APP/TEST,
signer, pinned engine/native inputs and source before any install or test.
Preserve the original source app and newer Owner state. No old window007,
39030, reverse, service, private account or relay grant is implied here.

Run the two new methods and affected existing Edge/NativeInput/LocalBrowser
methods on the exact candidate. Verify both endpoints and numeric rates,
100-ms visible return, 250-ms expiry, 50-ms integration/no backlog, tab/history,
navigation/replacement, pause/window focus, keyboard/caret/selection and long
Tabs/Bookmarks/Proxy utility lists. Record APP/TEST readbacks and actual terminal
completion separately from diagnostic observations and screenshots.

Then prepare the owned fixture with the **real sensor source**, arrange the
specific Owner wearing/head-movement action through Manager, and observe the
intended panel plus stationary background in both directions. Preserve an
unreproduced reported surface as such. A replay or software frame does not
satisfy that physical requirement. #60 migration and #62 signed-in account
journeys remain separate tickets. Prior #41 replay/physical limitations and all
#44/#57 failures, raw gaps and qualified acceptance limits remain historical.

No source or private input is deleted for this checkpoint. Owned browser/build
process teardown is immediate; evidence, backups and workspace cleanup remain
deferred until version end. Manager owns review, acceptance, integration and
closure. This record authorizes no PR, main merge or release publication.
