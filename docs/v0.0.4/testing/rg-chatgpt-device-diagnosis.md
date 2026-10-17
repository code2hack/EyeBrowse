# RG ChatGPT device diagnosis checkpoint

Issue #64 remains incomplete. A fresh-process native-input regression and a
production controlled page both passed focus/edit/remove checks. ChatGPT home
briefly answered JavaScript observations, then its renderer became unresponsive.
The previously working controlled page also stopped answering after the owned
ChatGPT tab closed. A second fresh production process reproduced the renderer
stall and persistent work after both owned tabs closed. Its cause and hardware
adequacy remain unresolved. Actual ChatGPT sidebar response, safe composer
editing and designated direct-conversation loading have **not passed**.

This checkpoint records the resumed run through 2026-10-17 07:26 UTC, including
the second genuine Owner wake event and later incomplete preservation. The
[previous checkpoint](https://github.com/code2hack/EyeBrowse/blob/0d1a7f861129376053d00c328dc61ec99150e735/docs/v0.0.4/testing/rg-chatgpt-device-diagnosis.md)
retains the earlier diagnosis, failed access and original restoration cut. No
production correction, installation or account submission was made in either
phase.

## Exact execution boundary

The diagnosis used Worker64 mission revision 2 and exclusive reservation
`V004-BATCH001-RG-W64-01`, on RG `1906092617103125`, user 0/API 32. The authenticated
primary transport was 5555. Authenticated Owner direction subsequently replaced
the 36967 bootstrap with 36687. Current issue64/P4.6 and the same-mission
operational addenda retain the existing scope and acceptance; old ports remain
historical.

Source during this resumed execution was doc-only checkpoint
`0d1a7f861129376053d00c328dc61ec99150e735`, tree
`fb1aacf694f6dc66ab245dbfa4f398125d277c52`. Production source remained identical
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

After closure, renderer RSS was 544372 to 546884 KiB and VmSwap was 376696 to
376660 KiB. A subsequent bounded numeric thread observation attributed 97.79%
to Chromium renderer main, with pool workers at 14.77%, 8.20% and 7.55%. The
initial PID-equals-TID label identified the Android service's initial thread;
classification was corrected using the same retained samples, without a rerun.
An earlier process-name predicate stopped before thread sampling and remains
negative evidence. In the second post-close sample, renderer RSS grew from
624832 to 627912 KiB and VmSwap from 773800 to 781608 KiB. No debugger pause or
usable stack was obtained; the controlled-page CPU profile remains private.

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

Current foreground, power, guard, camera/sensors and renderer termination are
unknown after loss. RG resource return and Awake/displayON are **not verified**.
The same reservation remains held; further device work needs a genuine restored
capability event and fresh current qualification/preservation. No routine device,
network or timer probe is scheduled.

The owned host fixture was stopped after PID/start/UID/executable/CWD/argv and
listener ownership checks. Its PID and every 39030 listener were absent, and the
exact IP could bind the port again. All source, artifacts and failures remain
retained; general cleanup is deferred.

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

I64-T01..T03 retain their original round1/failure0/lastConcluded0 history. No whole
CANDIDATE_READY, acceptance, resource handoff, integration or release is claimed.
