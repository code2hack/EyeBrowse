# RG nested head scrolling — issue #61

Worker: Worker-#61

## Precise native wheel continuation

On `b2ee2db`, both new nested/modal methods completed OK (1 test), with complete
body and cleanup reports. Four of six original Edge methods also passed:
numeric presets/limits, the long native Tabs list, all 72 boundary/overshoot
cases, and the actual declared rates/gains. Two unchanged stop regressions
failed. The app callback stopped within 63.557552 ms after visible re-entry,
but native page Y then moved 168→175 over 120 ms. With the keyboard open, the
callback stopped within 54.503177 ms, but Y moved 113→122 over 150 ms. These are
real effect failures despite callback cancellation. All ten failed native
invocations remain failed; complete terminals and reports are retained.

The exact pinned engine's [event builder](https://chromium.googlesource.com/chromium/src/+/a296091d1ed08d4dde44b8a427477fe570911472/components/input/web_input_event_builders_android.cc)
and [default feature](https://chromium.googlesource.com/chromium/src/+/a296091d1ed08d4dde44b8a427477fe570911472/ui/events/features.cc)
identify mouse-tool wheel input separately from precise touchpad pixels. The
candidate changes only generated wheel tool metadata to `TOOL_TYPE_FINGER`,
keeping `SOURCE_MOUSE` and public native dispatch. This tests the hypothesis
that precise pixels remove the mouse-wheel animation tail; no engine, feature
flag, system provider, focus, touch gesture or timing assertion is changed.
The existing parent-chain first-movement assertion remains, with an additional
completed 60-native-pixel distance check using the normal await. Passive event
tool type and owned browser Chrome version metadata are also retained; no
field value, page text, private URL or complete user agent is exported.

Both new affected host invocations execute 91 tests/18 suites with zero skipped,
failures or errors, including the genuine native controls. APP/TEST assembly
and lint complete, retaining the same analyzer limitation and 8/11 warnings.
Their separate authentic XML, source inventories/archives/diffs, compiled hashes,
APKs/signatures, native evidence and terminal logs are preserved. Earlier host
sets retain their original counts and missing-history limits.

Current precision-candidate APP `2af9356d…` / TEST `1b9714d1…` are retained from
those exact inputs. Installation and Android validation of this change are
pending. Other connected browser/native utility checks, actual production
SENSOR/head use and the default pre-grant APP/TEST restoration and verified
return remain required. This is not a completed candidate or resource handoff.

## Owner route event and ancestor correction (b2ee history)

This is still an incomplete implementation checkpoint. The authenticated Owner
bootstrap41877 event allowed one bounded requalification. Existing primary5555
reconnected and matched RG serial/model/user/API, current source/target/TEST
hashes and UIDs, provider95 and all six retained engine/native objects. No
bootstrap, tcpip, pairing or server restart was needed. Four of five target
preference files remained byte-identical; the sole change was the app-defined
subscription refresh-attempt timestamp. Its exact writer is not attributed.
The current timestamp is preserved, with no old preference overlay or claim of
whole-store byte equality.

Measurement-corrected TEST `4fff6949…` was then installed against unchanged APP
`acddd513…`. Two additional readiness failures occurred after a private fixture
startup guard failed and my shell chain incorrectly continued without the
service. Both original failed invocations remain retained. A new uniquely owned
fixture and fail-fast PID/start/CWD/direct-HTTP-body checks corrected that setup.
With the fixture verified, `modalAndCoveredNativeMenuNeverScrollTheBackground`
completed **OK (1 test)** with body and cleanup complete. The nested method
failed later at the unchanged `ordinary auto chain inside panel` assertion:
right-panel movement, endpoint isolation, reversal and deepest-inner movement
had succeeded, but a real native wheel event still hit the exhausted inner
container while the selector had found available capacity in its parent.
These eight native failures remain failed; the later modal result is scoped to
that invocation and still uses RawPoseReplay, not physical head movement.

The correction now checks current browser hit testing at the selected
container's original point, visible center and inset corners. A point inside a
different scrollable child cannot represent its parent. Native wheel dispatch
therefore reaches an exposed part of the chosen parent when chaining. If none
of the bounded visible points belongs to that container, the tick is dropped;
fully covered ancestors have no qualified fallback. No page scroll assignment,
focus change, retained DOM target, iframe traversal, new input queue or changed
timing/assertion is introduced.

The source-bound host Chrome155 matrix passes 20 checks, including an explicit
selected-parent hit-point check. Both new affected host invocations execute
91 JVM tests/18 suites with **0 skipped, failures or errors** using the qualified
unmodified Linux Mihomo input. APP/TEST assembly and lint complete: ordinary
lint has 0 errors/8 warnings; Chromium has 0 errors/11 warnings, retaining the
Kotlin2.2/analyzer2.0 semantic-analysis limitation. Each invocation preserves
its authentic XML, signed APP/TEST, source inventory/archive/diff, compiled-class
hashes, native evidence and terminal log before another build. These are new
runs; the original 91-total/2-skipped sets and baseline retention gaps remain.

The ancestor-correction Chromium APP `59c02f50…` / TEST `e417fdce…` were initially retained
from those exact working inputs. The later b2ee native outcomes are recorded
above. Actual physical SENSOR/head-use, remaining
connected/native utility checks, default pre-grant APP `5eb93e46…` / TEST
`9c9537f3…` restoration and verified resource return remain required. Source
app/provider are untouched; no ticket acceptance or handoff is claimed.

## Earlier revision3 device history

This remains an incomplete checkpoint. Manager granted exclusive RG reservation
`V004-BATCH001-RG-W61-01` after verified both-app preservation and Worker59's
safe handoff. Fresh guarded checks matched physical serial/model/user/API,
installed source/target/TEST hashes and UIDs, provider95, target CE/DP member
sets and all 500 regular-file hashes. Only target APP/TEST were upgraded
data-preservingly to APP `acddd513…` / TEST `2e21f535…`; source app was untouched.

Both new native methods completed one-test/one-failure runs at the unchanged
10-second `owned nested fixture ready` assertion, before scroll dispatch. Their
Activity/raw-source, tabs and settings cleanup reports were all true. These
are retained native fixture-readiness failures, with no scrolling or physical
PASS. The original reports contain no callback trace establishing which inline
load callback failed, so no exact missing-callback diagnosis is asserted.

The fixture correction uses ordinary `LocalBrowserSession.open` navigation
through the granted HTTP fixture module at `100.92.81.33:39030`, with the
required `nestedFixtureBaseUrl` test argument. The module serves an explicitly
labeled private own-copy `keyboard.html` from `NestedScrollFixture.HTML`.
This copy is not an original keyboard/protocol test. All five original page
sources remain unchanged for their separately bound original tests. No
production READY/URL/visual barrier, wait, assertion, input code or error
handling was weakened. Native verification of this correction and actual
sensor/head use remain pending.

Normal HTTP navigation subsequently reached READY on `e7ce8d8`, but both native
effect assertions failed. A passive observer on `8dee36c` then recorded a real
wheel event (source8194, factor82, axis -0.9756098), valid fresh/focused/visible
input, and right-panel movement of 62.745098 CSS pixels. Native width480 divided
by CSS width376 converts that distance to 80.100125 native pixels; other panels
and the document stayed at zero. The test incorrectly compared CSS scrollTop
directly with the requested native80. Its measurement helper now uses the
existing `pageOracle` width conversion and integer truncation. Expected80,
±2-native-pixel tolerance and all waits remain unchanged. Original JUnit
failures remain retained failures; corrected exact-source runs are pending.

At the subsequent pre-install read, primary5555 reported the device offline
before any measurement-corrected TEST install. One bounded primary reconnect
and one authorized41877 bootstrap connect did not recover access. At the
06:05 UTC capability observation the exact Tailscale peer was offline (last seen
06:00 UTC), with both authorized ports timing out. The qualified Spark ADB
server/client remained unchanged; no `tcpip`, pairing, server restart or other
route was used. This is a concrete route-recovery gate, not a new product result.

Last verified installed target was APP `acddd513…` / observer TEST `ae8d3351…`.
Corrected TEST `4fff6949…`, built against `ab92ae5` test bytes, is signed and
retained but **not installed or run**. Native methods still have no complete
PASS. The owned fixture PID/start/CWD/argv were rechecked before SIGTERM;
the process and listener are absent and `100.92.81.33:39030` is bind-free.
Last completed test reports recorded Activity/raw-source, tabs and settings
cleanup, but current device processes/sensors/camera/power/data state cannot be
reverified while offline. The required pre-grant target APP `5eb93e46…` / TEST
`9c9537f3…` restoration is **pending**, not completed. Source app is untouched.
Manager received the concrete normal Owner wake/reconnection request. Resume
requires fresh route/physical/installed/Owner-state reconciliation; no mutation
may be replayed blindly and no actual resource handoff is claimed at this gate.

## Historical host checkpoint

This is a **host implementation checkpoint**, not ticket acceptance. Actual RG
reproduction, Android wheel calibration, sensor/head use and connected native
regressions remain **NOT_RUN**. Mission revision2 retains HOST_ONLY. The current
Owner route is Spark-local Tailscale TCP5555 primary, with already authorized
wireless41877 bootstrap; u4090 is not used for now. This supersedes the original
checkpoint's historical USB/access wording. The targeted host-followup assignment
records #59 preservation in progress/NOT_CAPTURED; this Worker still has no
exclusive device grant. No device, original-app data or account was accessed.

The contract is [issue #61](https://github.com/code2hack/EyeBrowse/issues/61),
[P4.2 §6](https://github.com/code2hack/EyeBrowse/blob/9442398f599232d08c42b852050c6aa580f104af/docs/plans/v0.0.4-plan.md),
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
| `ordinary-final-001` | 91 total JVM cases/18 suites: **2 skipped, 89 non-skipped**, zero failures/errors; APP/TEST assembly and lint complete. 19 tasks executed / 59 up-to-date; lint 0 errors / 8 warnings. |
| `chromium-final-001` | 91 total JVM cases/18 suites: **2 skipped, 89 non-skipped**, zero failures/errors; APP/TEST assembly and lint complete. 20 tasks executed / 100 up-to-date; lint 0 errors / 11 warnings. |
| `targeted-native-debug-002` | Only the two previously skipped Linux controls: 2 tests/2 suites, **0 skipped/failures/errors**, shell0. 1 task executed / 23 up-to-date. |
| `targeted-native-chromium-002` | Same two Linux controls: 2 tests/2 suites, **0 skipped/failures/errors**, shell0. 2 tasks executed / 39 up-to-date. |

The original report omitted the skip count. Both original final XML sets and
their immutable hash manifests are retained unchanged. The skipped methods were:

- `com.code2hack.eyebrowse.rg.proxy.RefreshPolicyTest.genuineCoreRejectsCorruptPartialWrongBehaviorAndSelectsOnlyCompletePair`
- `com.code2hack.eyebrowse.rg.proxy.MihomoHostTest.realCoreRoutesAndSelectsAcrossSourcesWithAllDownRecovery`

Manager requested only these methods for each variant. The unmodified Linux
ARM64 Mihomo v1.19.32 binary was copied into this mission's private space and
qualified by SHA256
`c80df2ca5159567804da883401e0118908fc1155e05df2baf3dbd4d3ecfc3754`,
actual ownership, executable permission, ELF architecture and version output.
The existing `EYEBROWSE_MIHOMO_HOST_BINARY` and a unique
`EYEBROWSE_MIHOMO_HOST_EVIDENCE` directory were supplied. Both Gradle invocations
used only the two exact `--tests` selectors, the existing shared flock/fresh
memory gate, JDK17 and the established resource limits. JVM temporary storage
used its verified default `/tmp`; no long `java.io.tmpdir` override or changed
fixture, assertion, assumption or wait was introduced.

Each targeted invocation retains its own source archive/inventory/diff, authentic
XML, log, terminal result, compiled-class hashes, native core log and fixture
observations before the next build. These runs supplement the original
89 non-skipped cases per variant; they do not turn the original 91-total sets
into zero-skip executions. They ran on product/test source `857e97f`; this child
changes this testing record only. Existing APP/TEST bytes are explicitly reused
with unchanged source and SHA256 bindings; no new APK compilation is claimed.
These are Linux controls, with no Android dispatch, RG or wearer result.

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
Their original assembly was a host check; later exact-source native outcomes
are recorded above, with every earlier failure retained.
Raw quaternion replay never counts as an actual sensor or wearer result.

The obsolete `nativePageScrollDelta` helper and its three model-only JVM tests
were removed with the old outer-only writer. Current unit totals therefore
start at 91 total; the earlier 94-total observations and original retention gap
remain, without reconstructing unavailable XML or its skip count.
Endpoint coverage now measures the actual browser boundary in host fixtures
and is prepared for native dispatch in the two Android methods above.

## Required continuation

After Manager verifies #59's coherent both-app preservation and grants a fresh
exclusive reservation, bind actual physical serial/user/OS, current APP/TEST,
signer, pinned engine/native inputs and source before any install or test.
Use the current mission's TCP5555/authorized41877 transport policy; this record
grants no connection or recovery operation. Preserve the original source app
and newer Owner state. No old window007,
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
