# RG ChatGPT device diagnosis checkpoint

Issue #64 remains incomplete. A fresh-process native-input regression and a
production controlled page both passed focus/edit/remove checks. ChatGPT home
briefly answered JavaScript observations, then its renderer became unresponsive.
The previously working controlled page also stopped answering after the owned
ChatGPT tab closed. A second fresh production process reproduced the renderer
stall and persistent work after both owned tabs closed. A third fresh process
produced an early source-bound profile, then reproduced the later runtime stall
and growing renderer work after its owned tab closed. Its cause and hardware
adequacy remain unresolved. Actual ChatGPT sidebar response, safe composer
editing and designated direct-conversation loading have **not passed**.

This checkpoint records the resumed run through 2026-10-17 08:25 UTC, including
the newest Owner endpoint event and later incomplete preservation. The
[previous checkpoint](https://github.com/code2hack/EyeBrowse/blob/0d1a7f861129376053d00c328dc61ec99150e735/docs/v0.0.4/testing/rg-chatgpt-device-diagnosis.md)
retains the earlier diagnosis, failed access and original restoration cut. The
[intermediate checkpoint](https://github.com/code2hack/EyeBrowse/blob/d3dd4d388b7e21971f1fd9263f9f7cf18c131438/docs/v0.0.4/testing/rg-chatgpt-device-diagnosis.md)
retains the second comparison and its access gate. No production correction,
installation or account submission was made in these phases.

## Exact execution boundary

The diagnosis used Worker64 mission revision 2 and exclusive reservation
`V004-BATCH001-RG-W64-01`, on RG `1906092617103125`, user 0/API 32. The authenticated
primary transport was 5555. Authenticated Owner direction subsequently replaced
the 36967 bootstrap with 36687, then replaced 36687 with 41061. The interim
35773 correction was never probed. Current issue64/P4.7 and the same-mission
operational addenda retain the existing scope and acceptance; old ports remain
historical.

The actual native header/current turn, SDK workspace and source/tool processes
resolved to the assigned isolated worktree. A later host check found that the
Paseo-owned Codex app-server controller's launch CWD was the home directory.
Both values are retained and reported for Manager qualification reconciliation;
a matching controller OS process CWD is not claimed. No controller lifecycle or
runtime-registry change was made.

Source during the first two resumed comparisons was doc-only checkpoint
`0d1a7f861129376053d00c328dc61ec99150e735`, tree
`fb1aacf694f6dc66ab245dbfa4f398125d277c52`. The third comparison used doc-only
`d3dd4d388b7e21971f1fd9263f9f7cf18c131438`, tree
`2e9059329fd3f5de1914b7d8b1271434e04b596c`. Production source remained identical
to `c7c04798b3c23e10563b061fd84fd5e22c7dd481`, tree
`048da3b21a547a34e017bfbaad2d0c7565a3346a`. That normally merged the assigned #59
foundation `2ca9bcd7d09c8c440054c1010a5b41b29efa683c` while preserving the
[host preparation](rg-chatgpt-host-preparation.md). Its only difference from that
foundation was documentation. LocalBrowserActivity, LocalBrowserSession and
NativeRgInputTarget also remained unchanged from the earlier host baseline.
This source alignment is not acceptance or integration of #59. The Manager
reported CHANGES_REQUESTED for two #59 evidence gaps; no later #59 candidate was
merged automatically.

| Installed input, freshly qualified before resumed launch | SHA256 |
| --- | --- |
| Chromium APP | `bbcb4b38801feac2adfa96b400cc5e430c1f737b059eda3746f13acdf58beeb7` |
| TEST | `19f364295f027ca0b954770287e155c0c1a41ed92b4f5408ae1fa1543a6d4461` |
| Original RG APP, unchanged | `d6a9145be20a2e671381d06b60ba8318db6bf1ae6089fab76c7c29a27a6a9952` |
| System provider95, unchanged | `54401e4da5ae93fe152b48e38771c734558e6dd10addb927e0d03e8e4586994e` |

The modern engine remained 158.0.8107.0/snapshot1719582, APK
`8daa731574cf1d0e7f24996dd18c8d51554dc7f27806f2619528021674b78710`.
APP/TEST signatures and the Android core were checked against the retained
source/APK map. Fresh executable-map device/inode checks bound the production
engine despite `/data/data` versus `/data/user/0` path aliases; the earlier false
literal-path predicates remain retained. These are the last qualified inputs
before the later transport loss, not assertions about an unreachable device.

## Observed native and renderer behavior

After genuine Owner connection information arrived, existing 5555 independently
authenticated the exact RG and its tcp.port/listener. Recovery required no
connect, tcpip or input at that point. Fresh current-state preservation preceded
the following product operations.

The installed, unchanged
`NativeInputInstrumentedTest#builtInKeysUseNativeWebViewFocusAndDoneDoesNotEdit`
executed once in a fresh process: remote exit 0, `OK (1 test)`, finished status 0
and instrumentation code -1. Original assertions and waits remained unchanged.
This exercises the native pad-replay/WebView/custom-keyboard path on a dummy
page with the same custom Chromium application/engine, without the production
browser presentation/proxy session. Native focus, character insertion/removal,
Enter, dummy password, Done-without-edit and scrolling passed. It establishes
neither physical pad/head/optical acceptance nor ChatGPT account behavior.

Six existing test-output files were staged by exact rename before this run.
After retaining the six newly generated outputs separately, the originals were
restored with identical bytes, mode, UID/GID, size, mtime, link count and inode.
The new owned artifact directory and changed parent-directory mtime are retained
and disclosed. Fresh engine startup was 5760 ms with PSS 99431 KiB.

A separate fresh production process loaded the same engine in 5039 ms with PSS
88193 KiB. An owned tab opened the existing `local-keyboard.html` fixture through
ordinary native address/Open and routing. JavaScript and production presentation
responded. Native WebView focus showed the custom keyboard; one character was
entered and removed, then Done hid the keyboard without editing. The field ended
empty, with two input events and zero submissions.

One additional owned tab then opened public ChatGPT home through ordinary native
address/Open. Scalar JavaScript initially answered; document state was complete,
presentation and router context were present, and an enabled sidebar toggle had
geometry. The observed composer predicate was absent. No inert ancestor, dialog
or challenge frame was observed in that limited initial view. Those predicates
do not establish full hydration or signed-in feature capability.

The partial network observer received 49 h2 responses: 47 HTTP200 and two HTTP202,
including one document response and script/fetch resources. Its pre-navigation
Network.enable response timed out, so navigation-start/resource/error coverage
is unknown. No captured failure is not proof that all required resources worked.
An earlier observer initialization stopped before any ChatGPT type/Open; its
detailed initialization output was not retained. That limitation remains explicit.

Before a fresh sidebar hit-test, scalar evaluation timed out while the native
HUD had no Loading status. One touchscreen DOWN/UP sidebar tap used the last
observed geometry in the same owned home document. A current DOM hit-test and
page-event response could not be obtained; accepted native input is not sidebar
success. No DOM click substitute was used.

Closing only that owned ChatGPT tab restored the controlled page, whose Inspector
connection answered but scalar evaluation now timed out. Closing the owned
controlled tab left the original two cold-recovery locations and current saved
selection 1. No original recovery location was replayed. No current safe direct
conversation was bound; the earlier reported conversation's identity remains
unproven. No Owner draft was edited or message sent.

After another authenticated Owner wake event, existing 5555 independently
authenticated again. Both installed packages and the engine objects were
requalified, and fresh complete current cut004 preceded a second fresh
production launch. Its owned controlled page answered scalar JavaScript with
complete document state and the expected production presentation. A bounded
2-second/5-ms CPU profile answered start, stop and disable: 413 samples over
2.791839 recorded seconds, with 73.16% sampled time idle and 26.84% program.
This is a limited diagnostic sample, not a benchmark or interaction acceptance.

An additional owned tab opened public ChatGPT home through ordinary native
address/Open. Post-load `Profiler.enable` timed out; `Profiler.start` was never
sent. A later Inspector connection answered while scalar JavaScript timed out.
Two intervening observations stopped before sending their intended device/page
commands because descriptive reservation-state text had changed. Those retained
pre-packet assertions do not prove process disappearance or renderer failure.
The helper now checks the same exact reservation and full actor/lifecycle
binding while rejecting explicit hold, waiting, return, release, closed,
transfer and pause states; server, physical, SDK and security guards remain.

One bounded native backtrace request returned exit 1 with an unclassified
failure and no usable frames. It was not retried or escalated. No debugger pause
or usable site CPU profile was obtained. Closing only the owned ChatGPT tab left
the previously responsive controlled page answering Inspector but timing out on
scalar JavaScript. Closing the controlled tab restored the original two cold
locations and saved selection 1 without replaying either location. No composer
or sidebar input was attempted in this second phase. Persistent renderer work
remained after both owned tabs closed.

After the newest authenticated Owner endpoint event, existing 5555 again
authenticated without a connect or tcpip. Complete cut007 and a subsequent
whole-store equality check preceded a third fresh process. PID8854/UID10076,
the pinned engine/loader and the executable-map device/inode were freshly bound;
startup was 4346 ms with PSS 102573 KiB.

Initializing `Debugger.enable` in the owned blank tab timed out before any site
Open or Profiler.start. The local watcher was reaped and its socket closed.
That result does not classify ChatGPT or hardware capacity. The same owned tab
then opened the existing public controlled page through native address/Open;
Inspector and scalar JavaScript answered, with complete document state, the
expected presentation, five inputs, an empty owned field and zero input/submit
events. Only the owned public address field was replaced.

On that responding control, the profiling client independently answered a scalar
preflight. Debugger/Profiler enable and start answered before native same-tab
ChatGPT home navigation. Stop, Profiler.disable and Debugger.disable all answered;
no pause or breakpoint was requested, and the child/socket were reaped/closed.
The same owned target reached the home route. The actual profile recorded
8.510954 seconds, 1602 samples and 2730 profile call-tree nodes. It does not
establish continuous coverage of the requested ten-second interval or the later
stall; the duration difference remains unclassified.

In the final captured two seconds, 53.98% of sampled time was in ChatGPT-origin
JavaScript, 6.69% in a fingerprinted public app presentation callback, and 39.33%
in anonymous/engine categories. The app callback starts at line16 of the retained
exact script template, the `Array.from` callback that reads computed backgrounds.
The anonymous/engine group includes 33.44% program/root and 4.37% garbage
collection. Function names, private URLs and sources remain private. This early
sample does not establish or exclude later app, style/layout, site or engine
causality.

After cleanup, Inspector answered while scalar JavaScript timed out again. No
interaction counter or composer observation executed in this phase. A distinct
persistent-state Profiler.enable also timed out; Profiler.start was never sent.
The browser timeline attempt yielded no useful task records: start was sent
without a response, one necessary cleanup End answered, and tracingComplete
confirmed completion with no data loss. Only metadata was collected. The request
specified a 1 MiB buffer, Chrome tracing and argument filtering, without system
tracing, heap dumps, screenshots or network capture; start acceptance was not
established. The retained target records
and public derivatives are separate.

One timeline process-name predicate and one pre-close persistence predicate
stopped before their intended trace/Close commands. Retained process data showed
the isolated renderer alive under the target app's subprocess name; source showed
tab metadata persists onStop. Both own helper corrections retain before/diff and
original negatives; they do not demonstrate device disappearance. One subsequent
native Close removed only the owned third tab. The original two cold locations,
selection1, hidden custom keyboard and absent WebView were verified, with saved
metadata equal to cut007. No original location or Owner draft was replayed/edited.

The captured presentation callback writes inline custom properties, including
transparent values already covered by its CSS fallback. Interaction with a
site's DOM reconciliation is a hypothesis for a bounded public structural
reproducer. No such reproducer, causal ablation or production correction has
executed.

## Bounded resource observations

The observations counted four logical CPU entries and MemTotal 1816600 KiB.
After the second Owner wake event, one bounded read identified these exposed
hardware fields:

| Field | Observed value |
| --- | --- |
| SoC manufacturer | `QTI` |
| Branded SoC model | Empty/unknown |
| Board platform / product board | `neo` / `neo` |
| Hardware property | `qcom` |
| CPU present / possible / online | `0-3` / `0-3` / `0-3` |
| Each processor's implementer / architecture / part / variant | `0x41` / `8` / `0xd05` / `0x2` |
| Policy0 related / affected CPUs | `0 1 2 3` / `0 1 2 3` |
| Driver-exposed `cpuinfo_max_freq` | 1996800 kHz |
| One `scaling_cur_freq` observation | 1996800 kHz |
| Configured `scaling_max_freq` cap | Inaccessible/unknown |

The cpuinfo Hardware field was absent. These observations do not establish a
branded SoC, physical layout, sustained clock, Qualcomm phone-generation
comparison or hardware adequacy. The maximum and instantaneous clock observation
are distinct from an unknown configured cap. No tuning or benchmark was run.

CPU percentages below normalize process/thread CPU ticks to one logical CPU:
100% means one logical CPU time equivalent; four logical CPUs give a summed
400% ceiling. This does not weight clocks or different core types.

| Original observation interval | Available memory, KiB | Renderer CPU, one-core % | Main CPU, one-core % |
| --- | --- | --- | --- |
| Fresh production controlled page | 594428 to 605200 | 35.53 | 87.23 |
| Actual ChatGPT home | 174224 to 164736 | 125.77 | 54.64 |
| All owned nonblank documents closed | 250056 to 243972 | 99.54 | 19.18 |
| Second fresh production, actual ChatGPT home | 192044 to 185732 | 126.85 | 9.23 |
| Second fresh production, both owned tabs closed | 145896 to 147076 | 128.54 | 10.55 |
| Third fresh production, after warm-navigation profile | 175340 to 175568 | 118.62 | 60.95 |
| Third fresh production, owned tab closed | 142888 to 142768 | 103.20 | 14.46 |

After closure, renderer RSS was 544372 to 546884 KiB and VmSwap was 376696 to
376660 KiB. A subsequent bounded numeric thread observation attributed 97.79%
to Chromium renderer main, with pool workers at 14.77%, 8.20% and 7.55%. The
initial PID-equals-TID label identified the Android service's initial thread;
classification was corrected using the same retained samples, without a rerun.
An earlier process-name predicate stopped before thread sampling and remains
negative evidence. In the second post-close sample, renderer RSS grew from
624832 to 627912 KiB and VmSwap from 773800 to 781608 KiB. No debugger pause or
usable stack was obtained; the controlled-page CPU profile remains private.

In the third post-close observation, renderer RSS was 665272 to 665692 KiB and
VmSwap 987336 to 991260 KiB. System SwapFree was 19504 to 13796 KiB, with
MemTotal 1816600 KiB and SwapTotal 1572860 KiB. These counters show persistent
growing renderer work and nearly exhausted swap at that cutoff. They do not
identify the cause of the allocation, establish a normal steady-state hardware
requirement or describe an unreachable device continuously.

These observations distinguish a working fresh native/production control from
later persistent renderer work. They do not isolate script, layout, garbage
collection, presentation or engine behavior, or prove RG hardware inadequacy.

## Preservation and return limits

Before launch, a fresh complete BOTH-app CE/DP capture was independently
repeated, restored on the host and checked, including 15 SQLite integrity checks.
All current mutable/unknown files and available WAL/SHM, metadata and exceptional
nodes were retained. Four large static objects were rebound only after fresh
device hashes matched exact retained bytes. Historical015 scripts were read as
references and were never executed or imported as64 authority.

After recovery, current cut002 preserved both apps before fresh-process testing,
including independent complete byte/header/member validation and 15 database
checks. Its newer saved selection 1 was retained; an initial comparison against
the older selection 0 failed and was preserved separately.

Cut003 captured post-site writes from 06:27:43 to 06:29:28 UTC. Two
independent fresh streams and a final cross-store comparison matched complete
bytes/membership/metadata. Independent host restoration checked every archive
header and regular-file payload; all 15 SQLite checks passed. Target CE was
1050183680 bytes/1601 members, target DP 419840 bytes; source CE/DP complete
archive hashes stayed unchanged. Both enabled profiles/41 nodes, bookmarks,
the original two saved locations/current selection 1 and all six engine objects
were bound unchanged. Target account/cache writes were retained without overlay.
Live target processes and exceptional socket metadata were recorded. This is a
coherent filesystem cut, not process-memory restoration or a quiescent lifecycle
result. No on-device restore occurred.

Fresh cut004 captured both apps from 06:52:34 to 06:54:55 UTC, before the second
fresh production launch. Independent complete header/payload/member validation
and all 15 SQLite checks passed. The four complete store archive hashes matched
cut003; both enabled sources/41 nodes, bookmarks, original two locations/current
selection 1 and all six engine objects remained bound. No source, target or test
UID process or live private socket was observed at that cut. The preceding
process termination cause is unknown; Worker64 issued no HOME or stop command.

After the second phase closed both owned tabs, new preservation005 failed a
physical-authentication guard before a target static-object hash. Its partial
source payloads remain retained. One targeted reconciliation reauthenticated
existing 5555 without connect or tcpip. New full capture006 then timed out during
its source CE transfer at 240 seconds, retaining 10436608 bytes and an unknown
read outcome. Neither attempt reached coherent cross-store, host-restoration or
SQLite readiness. They are not replacements for the last complete cut004, and
no old cut was overlaid. Dependent HOME/force-stop and the prepared fresh-process
source-attribution comparison remained unexecuted.

After the newest endpoint event, complete fresh cut007 ran from 07:48:27 to
07:50:40 UTC. Independent whole header/payload/member/metadata restoration and
15 SQLite checks passed. Source CE/DP complete archive hashes were unchanged.
Newer target writes were retained: CE 1050245120 bytes/1610 members, DP 440320
bytes. Both enabled sources/41 nodes, profile XML, bookmarks, original two saved
locations/selection1 and all six engine objects matched the earlier cut.

Cut007 observed one remaining target-UID process. A fresh pre-stop read later
found every source, target, test and associated process absent. Its cause is
unknown, and the expected-PID predicate stopped before a lifecycle intent. No
HOME or stop was issued. A complete new equality qualification at 07:56:40 UTC
proved all four current stores' tar bytes, full metadata and eligible static
objects still matched the freshly transferred/restored cut007, with all app
processes absent and HOME/Awake/displayON/keyguardFalse. A preceding partial
qualification and its physical-guard timeout remain retained. This established
the preserved input for the third fresh-process comparison.

After that comparison and successful owned-tab closure, new capture008 stopped
at 08:21:15 UTC before its first process-read guard/intent/result completed.
No mutable payload transferred; it is NOT_READY. Cut007 is the last complete
filesystem cutoff, preceding the latest site/account/cache writes. Those newer
writes remain primary on device, without an old overlay. Dependent target HOME,
stop and further tests remain pending fresh preservation after restored access.

At 06:36 UTC, the physical guard failed during necessary target teardown, before
any HOME or force-stop intent. The pre-HOME Owner-state reads had succeeded;
neither lifecycle command was issued. One targeted reconciliation found host
5555 metadata reporting device but independent physical shell authentication
timed out. Host metadata did not establish authenticated access.

The first selected 36687 connect under that capability event's bounded grant timed out
at 06:39 UTC, leaving server registration unknown; independent bootstrap physical
authentication failed. Its local client was killed/reaped. No primary-connect
retry, tcpip, stale port, USB, pairing or server lifecycle action followed.
The earlier unknown 36967/5555 connect outcomes were not replayed.

The later genuine Owner wake event restored existing 5555 authentication without
a connect or tcpip, enabling cut004 and the second comparison above. After the
capture006 timeout, a targeted reconciliation again found 5555 host metadata
reporting device while its exact physical identity read timed out. Existing
36687 authentication also failed. The first and only bounded 36687 connect in
this new wake event then timed out at 07:26 UTC, leaving its outcome unknown;
independent bootstrap authentication failed, and the local client was reaped.
No tcpip, primary connect, input, server lifecycle operation or blind replay
followed. These are separate retained outcomes from the earlier loss, not a
continuous authenticated-access assertion.

The newest Owner reply selected bootstrap41061, superseding unprobed35773 and
historical36687. Existing 5555 initially authenticated unchanged and enabled
cut007/the third comparison without a bootstrap connect or tcpip. After
capture008 failed, fresh host/client/server/SDK/assignment guards passed but both
existing selected routes failed physical authentication. The first and only
41061 connect under that genuine event timed out after 18 seconds at 08:25 UTC;
its outcome remains unknown, the local child was reaped, and independent
bootstrap authentication failed. No tcpip, primary-connect replay, stale-port,
USB, pairing, server-lifecycle or global-policy operation followed. The bounded
grant is consumed; no routine retry is scheduled.

Current foreground, power, guard, camera/sensors and renderer termination are
unknown after loss. RG resource return and Awake/displayON are **not verified**.
The same reservation remains held; further device work needs a genuine restored
capability event and fresh current qualification/preservation. No routine device,
network or timer probe is scheduled.

The owned host fixture was stopped after PID/start/UID/executable/CWD/argv and
listener ownership checks. Its PID and every 39030 listener were absent, and the
exact IP could bind the port again. All source, artifacts and failures remain
retained; general cleanup is deferred.

The third comparison conditionally reused the same three source-bound fixture
JARs after fresh ownership/free-port/exact-bind qualification. Fixture004 was
stopped at 08:05:41 UTC, with its process absent, all listeners absent and exact
bind free. A subsequent host-only observation again found no owned fixture or
scratch helper process and no 39030 listener. No new fixture framework or
APP/TEST build was introduced.

## Executed checks and retained evidence

The unchanged existing fixture source was compiled offline with JDK17, the
shared host-build lock, fresh 10.88GB available after lock, no daemon, workers 2,
Xmx2g and GOMAXPROCS2. Four Gradle tasks executed successfully. This is a fixture
compile, not an APP/TEST rebuild or a newly executed Android regression. Earlier
host checks remain scoped to the linked host report.

The resumed run executed the existing installed native-input method once as
described above. Production controlled-page focus/edit/remove passed; actual
ChatGPT interaction and direct conversation remain incomplete. No APP/TEST
rebuild or source regression was claimed. This checkpoint changes documentation
only; original assertions, waits and lifecycle contracts remain unchanged.

Restricted evidence root:
`/home/code2hack/.local/state/eyebrowse/v0.0.4/runs/run-001/issue-64/worker-64-001/device-r2-001/evidence/`.
Raw page, URL, network, app-store and capture content stays private. Public
derivatives contain fixed categories, booleans, counts, geometry and opaque
hashes. Principal retained records are:

| Record | Scope |
| --- | --- |
| `retained59-reference-cross-check-019.json` | Exact foundation/reference comparison |
| `fresh-preservation-001/` | Initial current full capture and restoration proof |
| `diagnostic-checkpoint-006.json` and its 31 pins | Earlier complete source/evidence/failure cut |
| `fresh-preservation-002/`, `recovery027-current-owner-store-binding-002.json` | Fresh pre-test state and newer selection retained |
| `existing-native-control-recovery027-001/report.json` | Actual one-test result and exact six-file restoration |
| `fresh-production-engine-map-inode-binding-003.json` | Engine path-alias/inode reconciliation |
| `fresh-production-controlled-observation-002.json`, `fresh-production-controlled-native-edit-002.json` | Production control readiness/native editing |
| `fresh-chatgpt-home-native-network-003.json` | Transient home JS and partial resource observation |
| `fresh-chatgpt-native-sidebar-003.json`, `fresh-chatgpt-stalled-native-close-004.json` | Stall before input, single native tap and post-close control stall |
| `fresh-production-own-control-close-004.json` | Original two tabs/current selection restored |
| `resource-*.json`, `renderer-thread-numeric-005.json`, `renderer-thread-classification-006.json` | Bounded resource samples and corrected classification |
| `fresh-preservation-003/`, `post-site-current-owner-store-binding-003.json` | First post-site full filesystem/restoration/15 SQLite checks |
| `own-stalled-target-settlement-005.json` | Guard stopped before HOME/force-stop |
| `primary-after-stall-reconcile-012.json`, `bootstrap36687-after-stall-012.json` | Earlier physical-authentication loss and bounded unknown connect outcome |
| `cpu-facts-retained-only-034.json` | Earlier cutoff before the hardware-identification call |
| `recovery035-001/primary-reconcile.json`, `cpu-identification.json`, `current-packages.json` | Second actual route qualification, hardware identification and installed input binding |
| `fresh-preservation-004/`, `recovery035-current-owner-store-binding-004.json` | Last complete fresh current filesystem/restoration/15 SQLite checks |
| `recovery035-001/control-bounded-profile.json`, `chatgpt-bounded-profile.json` | Answered controlled-page profile; site enable timeout/start never sent |
| `recovery035-001/chatgpt-after-profile-observation-002.json`, `owned-tabs-close-and-control-comparison.json` | Repeated site/control scalar stall and native restoration of original tabs |
| `recovery035-001/renderer-native-backtrace.json`, `native-backtrace-failure-classification.json` | Exit1/unclassified failure; no usable stack |
| `recovery035-001/guard-prepacket-operational-binding-016.json` | Exact changed guard pin and retained pre-packet negatives |
| `fresh-preservation-005/`, `fresh-preservation-006/`, `recovery035-001/post-preservation006-route-reconcile.json` | Incomplete new captures and latest bounded authentication loss |
| `bootstrap36687-current035-after-preservation-016.json` | Sole bounded connect under the second wake event; unknown outcome retained |
| `fixture-build-001/`, `fixture-running-002/teardown.json`, `fixture-running-003/teardown.json` | Source-bound compile and actual owned-host teardowns |
| `recovery039-001/primary-reconcile.json`, `current-owner-and-listener.json`, `current-packages.json` | Newest endpoint event, unchanged authenticated primary and fresh installed input binding |
| `recovery039-001/guard-authority-alignment/` | Preserved old helper and current039 authority-only adaptation |
| `fresh-preservation-007/`, `recovery039-current-owner-store-binding-007.json`, `recovery039-001/current007-post-exit-revalidation-002.json` | Last complete filesystem/restoration/15 SQLite cut and complete post-exit current equality |
| `recovery039-001/preloaded-chatgpt-native-comparison.json`, `preloaded-chatgpt-profile.json` | Blank Debugger timeout before site Open or Profiler.start |
| `recovery039-001/controlled-ready-observation.json` | Same fresh process's responding real controlled document |
| `recovery039-001/warm-control-chatgpt-native-comparison.json`, `warm-control-chatgpt-profile.json`, `profile-public-source-position-analysis.json` | Bounded source-fingerprinted early profile, actual cleanup and coverage limit |
| `recovery039-001/chatgpt-after-warm-profile-observation.json`, `chatgpt-persistent-stall-bounded-profile.json` | Later scalar and Profiler.enable timeouts; no later recording started |
| `recovery039-001/browser-timeline-persistent-stall-002.json`, `timeline-process-guard-correction/` | No useful task records; actual timeline completion and retained pre-command negative |
| `recovery039-001/own-diagnostic-tab-native-close-002.json`, `close-persistence-guard-correction/` | One actual owned-tab close, original state restoration and retained pre-tap negative |
| `resource-renderer039-after-warm-profile-008.json`, `resource-renderer039-owned-tab-closed-009.json` | Changed-condition CPU/memory observations |
| `fresh-preservation-008/report.json`, `recovery039-001/post-capture008-route-reconcile.json`, `bootstrap41061-current039-after-capture008-021.json` | New incomplete capture, physical gate and bounded unknown connect outcome |
| `fixture-running-004/teardown.json`, `checkpoint-022/host-process-listener-teardown.json` | Actual immediate fixture teardown and later host-only absence proof |

I64-T01..T03 retain their original round1/failure0/lastConcluded0 history. No whole
CANDIDATE_READY, acceptance, resource handoff, integration or release is claimed.
