# Issue #64: host preparation checkpoint

Worker: Worker-#64

This is **HOST_CHECKPOINT**, not CANDIDATE_READY or ChatGPT acceptance. The
reported inert controls and direct-conversation failure have not been reproduced
on the actual RG. No production correction is justified by this checkpoint.
I64-T01 is incomplete; I64-T02 and actual I64-T03 verification remain pending.
The Manager retains the canonical attempt/history records unchanged.

## Authority and source binding

The execution contract is [canonical #64](https://github.com/code2hack/EyeBrowse/issues/64),
body SHA256 `17ce5312f0c29c14662427d85917ae5ba1686f6c1b3aa3f861c63b67139e3fdc`,
and [P4.4](https://github.com/code2hack/EyeBrowse/blob/7125378fe07dda1bd5cdb7e8399ff770884edb38/docs/plans/v0.0.4-plan.md),
file SHA256 `437b90e810a36b953ba8f0d0b76a09778607d0f8a4c152bef32c2f8b282579a2`.
Both were freshly read on 2026-10-17; #64 then had no comments.

Assigned branch: `work/issue-64-chatgpt-v004`. All executions below used
`aeb463ac9f90d80b8fcbdfb622fc23b288330e37`, tree
`204c4d136599048eb88cd1d0b3713dc5d5db0e02`, before this report was added.
That baseline includes Worker59's cache correction; it is not #59 acceptance or
integration. Refreshed integration remains
`03de921e2400147842ce6c56101b58d514dde77c`; remote main authority remains
`8929241f348bae9005b9b6bc1a93fbabd47145c1`. The governing AGENTS, DEV, SPEC D3.1,
HUD H3.1 and Kotlin ADR bytes matched remote main. Protected articles are unchanged.
Any eventual PR targets `v0.0.4`.

Startup independently bound the actual native header/current turn, effective
model/effort/access, tool CWD, private edit/read, Paseo native/project/workspace,
archive and permission state, GitHub read and assigned-ref push dry-run. The
Paseo-owned app-server's process CWD differs from its per-thread source/tool CWD;
the restricted startup record preserves this distinction. No separate runner or
helper was created.

Only permitted public artifacts were recovered into mission scratch: the 62
pinned loader main inputs plus its license, the recorded current APP/TEST, and
engine158. The maintained loader inputs are **six Kotlin files plus one
manifest**, alongside 54 unmodified vendor inputs used by compilation. The
Android core bytes in the source match the retained APP. These are host artifact
checks, not installed-device readback. No Worker59 private state, account inputs,
backups or source URLs were accessed.

## Source-supported stages

The six inspected input/session/activity/pointer/proxy files are byte-equivalent
to integration03de921. These observations do not identify the actual-site cause.

| Stage | What the current source establishes | What still needs actual evidence |
| --- | --- | --- |
| Pad admission | `RgInputRouter` requires active/window-focused/shown state and an available current pointer; one recognized tap calls the target. | Physical pad recognition and current tracking/focus during the reported failure. |
| Native delivery | `NativeRgInputTarget.activate` maps pointer through global/root matrices, checks root bounds, and sends touchscreen DOWN/UP through `root.dispatchTouchEvent`. Its Boolean is event acceptance. | Which native child receives the pair, WebView/page hit, coordinates and actual control response. |
| Native coverage | The clickable status TextView is above pages; utilities and the keyboard have their own covering surfaces. | Actual visibility and hit ownership. At canonical source geometry the status strip occupies x16..464, screen y60..112; this is not a device measurement or a ChatGPT selector. |
| Paint versus session state | `onPageCommitVisible` can make a styled frame visible while phase remains LOADING. Only the finished presentation callback promotes READY. | Current callback order, committed document, presentation completion and actual handler response. |
| Keyboard | The page-touch observer waits for native editor focus. State callbacks outside READY dismiss the keyboard; scrolling has a separate READY gate. | Native focus/edit results and their timing relative to state callbacks. |
| Script/presentation | JavaScript and DOM storage are enabled. The existing presentation script observes dynamic styling while preserving authored background alpha. | Actual required scripts, handlers, resource errors and presentation effects on the reported document. |
| Navigation/network | Native/browser loads wait for app-local proxy configuration. Certificate errors are cancelled; main-document and resource callbacks have different effects. | Normal current route, DNS/TLS/HTTP, applicable WS/WSS, redirects, resources and account/service capability. |

No UA, TLS/trust, engine/provider, proxy policy, presentation, input framework or
production code was changed. A recurring #57/#59/#61 cause remains unclassified;
any demonstrated overlap returns through the Manager with its existing history.

## Executed host evidence

`focused-native-jvm-001` ran the Chromium variant's existing pad admission,
gesture recognizer, keyboard-map, head-pointer and edge-motion JVM tests:
**37 tests, zero failures/errors/skips; 41 tasks executed**. The test task was
executed, not UP-TO-DATE. JDK17, SDK35, the shared build flock, fresh post-lock
memory above 6GiB, two workers, Xmx2g, GOMAXPROCS2 and `--no-daemon` were used.
There was no new APK or device test. XML, HTML, source hashes/diff and compiled
classes are retained. The evidence-copy runner raised a basename collision
after successful Gradle completion; retention was recovered without rerunning
the build or tests. This negative remains recorded.

`host-structural-006` used a fresh owned host Chrome155 profile with its normal
sandbox and the extracted, unchanged production presentation script. **20
checks passed**, including setup and privacy checks. This was a controlled
document, not Android WebView or ChatGPT:

- A completed document with painted controls received CDP touchscreen events and
  a click while its application handler was absent; the control had no effect.
- Installing the fixture's handler made the same touch affect its sidebar state.
- Touch focused a plain editable; host input inserted and removed a harmless
  draft without sending. The passive observer counted focus/input events.
- Dynamic black presentation and a transparent covering host remained usable.
- Observer teardown and a second controlled document's control response worked.
- Observations contained only fixed categories, Booleans and counts despite
  sentinel strings in the fixture's title, labels and link target.

The successful fixture used CDP `Page.setDocumentContent` and explicit fixture
handler initialization. It proves neither network hydration nor ordinary direct
navigation. **No direct-conversation, account, route or network acceptance is
claimed.** CDP touch is not physical RG pad evidence. The prepared passive
scripts are restricted scratch artifacts, not an installed Android diagnostic
TEST or a privacy-qualified actual-account runner.

Preserved setup/observer negatives:

| Invocation | Actual result |
| --- | --- |
| host-structural-001 | Snap rejected the assigned hidden profile directory before startup. |
| host-structural-002 | The same installed binary rejected a too-long Unix singleton socket path. |
| host-structural-003 | Browser started; the initial page-load stage did not complete. Its original failure detail was not captured beyond stage/class. |
| host-structural-004 | Page load-event observation timed out before presentation/input checks. |
| host-structural-005 | Navigation RPC timed out; the owned localhost fixture received zero requests. This host limitation was not diagnosed as an app defect. |

006 keeps these failures and narrows its claim to a network-independent document.
The shorter temporary pathname was a file-descriptor alias to the assigned own
scratch directory. No shared browser profile, sandbox disabling, TLS exception,
system network change or device fixture was used. The exact runner revisions,
production-script extraction, result files and hashes are frozen per invocation.

## Retained evidence pins

The Manager can resolve these paths beneath the assigned
`issue-64/worker-64-001/evidence/` directory. No raw account evidence exists in
this checkpoint.

| Artifact | SHA256 |
| --- | --- |
| `focused-native-jvm-001/result-recovered.json` | `4ba0ad5f15d4e822002389d9a06d06f6a2faf6771bdb60e06d1e09a76e7071ab` |
| `host-structural-006/result.json` | `cb97f22fa252ff332167b682dfa9d3a2c12c6a1393263c97e548ec447630dab9` |
| `host-source-input-binding-002.json` | `043aa61bc12536dc1a4d2d3821abb2ec68268b83014d3c04047c259b4b17cfbb` |
| `presentation-extraction-001.json` | `b140bad15625f6052c6f6aa3d2dc9564a5ea43ff38fa408845a90fd0adbdc147` |
| `input-recovery-001.json` | `6da1528792487d4099b5e0cf6975361908877d0ce68e77e0641cf8a42572acd7` |
| `host-teardown-001.json` | `47b3310190caea277b531ef52758590f78a0ea488284a2d36b91c0dea9577c9e` |

The original source-binding001 mislabeled seven maintained inputs as seven
Kotlin files; binding002 corrects the inventory and retains that negative.

## Pending device work and safe state

No ADB command, transport enumeration, device/private-account action, install,
capture, input, fixture39030 or remote service action occurred. Worker59's
exclusive device ownership was respected. Owned host browsers, fixture listeners
and source-CWD Java processes are gone; temporary profiles, inputs and evidence
remain retained for version end. No general cleanup occurred.

After verified Worker59 return and a separate exact device grant, required work
starts with fresh physical/source/APP/TEST/engine/core binding and coherent
current-state preservation of both apps. The next staged observations must
separate physical pad recognition, current pointer/focus, native overlay/root
hit delivery, renderer event receipt, actual handler response, resource and
navigation errors, ordinary routing/TLS and normal account capability. Existing
anonymous diagnostics/trace output are not automatically safe for current signed-in
pages; trusted restricted tooling must enforce the fixed output categories.

Actual acceptance still requires representative native controls/sidebar response,
an internally checked empty composer that can be focused/edited/cleared without
send or existing-draft loss, and a privately nominated safe conversation loaded
through ordinary native UI. Current sessions, pairing, bookmarks, profiles and
the original app must survive affected lifecycle/update checks. #62 owns the
later designated harmless send/response. No migration, old-state overlay,
re-pair/login ritual or rollback is authorized by this host checkpoint.
