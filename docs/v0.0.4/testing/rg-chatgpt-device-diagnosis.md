# RG ChatGPT device diagnosis checkpoint

Issue #64 remains incomplete. Native browser controls responded, but renderer
observations stalled for both ChatGPT home and an owned ordinary HTTP page.
The cause has not been isolated. Sidebar response, safe composer editing and a
designated direct conversation have **not passed**. No production correction or
account submission was made.

## Exact execution boundary

The diagnosis used Worker64 mission revision 2 and exclusive reservation
`V004-BATCH001-RG-W64-01`, on RG `1906092617103125`, user 0/API 32. The authenticated
primary transport was 5555; the current 36967 bootstrap was reserved for recovery.

Source at execution was `c7c04798b3c23e10563b061fd84fd5e22c7dd481`, tree
`048da3b21a547a34e017bfbaad2d0c7565a3346a`. This normally merged the assigned #59
foundation `2ca9bcd7d09c8c440054c1010a5b41b29efa683c` while preserving the
[host preparation](rg-chatgpt-host-preparation.md). Its only difference from that
foundation was the host document. LocalBrowserActivity, LocalBrowserSession and
NativeRgInputTarget also remained unchanged from the earlier host baseline.
This source alignment is not acceptance or integration of #59. The Manager
reported CHANGES_REQUESTED for two #59 evidence gaps; no later #59 candidate was
merged automatically.

| Installed input, freshly qualified before launch | SHA256 |
| --- | --- |
| Chromium APP | `bbcb4b38801feac2adfa96b400cc5e430c1f737b059eda3746f13acdf58beeb7` |
| TEST | `19f364295f027ca0b954770287e155c0c1a41ed92b4f5408ae1fa1543a6d4461` |
| Original RG APP, unchanged | `d6a9145be20a2e671381d06b60ba8318db6bf1ae6089fab76c7c29a27a6a9952` |
| System provider95, unchanged | `54401e4da5ae93fe152b48e38771c734558e6dd10addb927e0d03e8e4586994e` |

The modern engine remained 158.0.8107.0/snapshot1719582, APK
`8daa731574cf1d0e7f24996dd18c8d51554dc7f27806f2619528021674b78710`.
APP/TEST signatures and the Android core were checked against the retained
source/APK map. No install, engine/provider change, network policy change,
TLS weakening or site workaround occurred.

## Observed native and renderer behavior

Native touchscreen input opened More, added an owned tab, focused the address
field, typed the public home address and activated Open. Native close/tab-list
selection also responded. The two original Paseo recovery tabs were retained.
These observations do not qualify physical pad/head use or optical behavior.

ChatGPT home reached a visible WebView with the native Loading status absent.
The page's diagnostic connection answered `Inspector.enable`, while
`Runtime.enable`, scalar `Runtime.evaluate` and `Network.enable` did not answer
within their individual observation windows. An independent installed Node
WebSocket client reproduced the scalar timeout. No DOM click or injected input
was used. In-page event counters and page/account readiness remained unavailable.
The native hierarchy exposed HUD controls and a WebView, with no page children.

The associated sandboxed renderer PID 17049 measured 100/133 percent CPU in the
two-sample observation; main PID 17020 measured 65.5/40 percent. After closing only
the owned ChatGPT tab, renderer CPU measured186/122 percent. This is evidence of
a responsiveness problem, not attribution to a particular source component.
Classified logs contained no observed fatal, ANR, renderer-exit or allowlisted
JS/DNS/TLS error. That absence establishes no network or script success.

A new owned tab then opened the existing `local-keyboard.html` fixture through
ordinary native address/Open and existing routing. The reserved fixture received
one actual page GET and zero submissions. The page remained native LOADING and
hidden; its diagnostic connection again answered Inspector but not scalar
Runtime evaluation. Ordinary HTTP receipt does not prove page interactivity or
ChatGPT HTTPS/account capability. A fresh-process comparison was therefore
prepared, but was not executed before transport was lost.

Both owned tabs were closed through native controls. Native count 2/selected 0
was restored. No ChatGPT conversation was bound from saved tabs/bookmarks; the
identity of the earlier failed conversation remains unproven. A safe current
conversation was not yet bound through the unresponsive page. No existing draft
was edited and no message was sent.

## Preservation and return limits

Before launch, a fresh complete BOTH-app CE/DP capture was independently
repeated, restored on the host and checked, including 15 SQLite integrity checks.
All current mutable/unknown files and available WAL/SHM, metadata and exceptional
nodes were retained. Four large static objects were rebound only after fresh
device hashes matched exact retained bytes. Historical015 scripts were read as
references and were never executed or imported as64 authority.

A second complete live filesystem cut preserved post-navigation writes. Both
independent streams and final cross-store bytes/metadata matched. Complete
archive headers and every restored regular-file payload were independently
compared; all 15 database checks passed. Source CE/DP archive hashes remained
unchanged. Target CE grew from 1681 to 1835 members; its newer state and DP store
were retained without overlay. Both enabled profiles/41 nodes, bookmarks and
saved tab locations/selection remained equal to the starting current state.
This is a coherent filesystem cut with live processes, not process-memory
restoration or a quiescent lifecycle result. No on-device restore occurred.

At 05:42:45 UTC on 2026-10-17, the first pre-HOME physical guard rejected primary
transport. Neither HOME nor target force-stop was issued. The pinned existing
host server/protocol remained qualified, but primary selection reported OFFLINE.
One bounded primary host-connect attempt and one current 36967 bootstrap attempt
timed out; neither restored authenticated physical access. No `tcpip 5555`, USB,
old bootstrap, pairing, server restart or further device input followed. A
connection timeout was retained as an unknown host registration outcome and was
not replayed as a product attempt.

The last native observation had the original two tabs/selection restored and no
custom keyboard. Current foreground, power, guard, camera/sensors and owned
renderer teardown could not be reverified after transport loss. RG resource
return and Awake/displayON are **not verified**. Required device work waits for
the exact granted device to be reachable again; no routine probe is scheduled.

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

The existing installed native-input control method was found in TEST DEX, but
was not run. It writes six artifact paths already present in current data; any
later execution needs bounded current-artifact preservation and restoration.
No original assertion, wait or lifecycle contract has been relaxed.

Restricted evidence root:
`/home/code2hack/.local/state/eyebrowse/v0.0.4/runs/run-001/issue-64/worker-64-001/device-r2-001/evidence/`.
Raw page, URL, network, app-store and capture content stays private. Public
derivatives contain fixed categories, booleans, counts, geometry and opaque
hashes. Principal retained records are:

| Record | Scope |
| --- | --- |
| `retained59-reference-cross-check-019.json` | Exact foundation/reference comparison |
| `fresh-preservation-001/` | Initial current full capture and restoration proof |
| `native-public-home-002.json` | Actual native address/Open dispatch |
| `public-home-observation-005.json`, `devtools-independent-control-006.json` | Renderer command observations and independent client |
| `target-process-cpu-verified-004.json`, `post-own-chatgpt-close-cpu-001.json` | Actual CPU samples |
| `controlled-native-open-001.json`, `controlled-client-001.json`, `fixture-observations-001.json` | Controlled native HTTP comparison |
| `controlled-own-tab-restoration-001.json` | Original two tabs/selection restored |
| `live-before-fresh-process-001/`, `post-navigation-current-owner-state-binding-001.json` | Newer data, full live cut, restoration and current-state comparison |
| `own-target-fresh-process-quiescence-001.json` | Guard stopped before any lifecycle mutation |
| `primary-transport-event-001.json`, `primary-connect-recovery-001.json`, `bootstrap-connect-recovery-001.json` | Transport loss and bounded recovery outcomes |
| `fixture-build-001/`, `fixture-running-001/teardown.json` | Source-bound compile and actual owned-host teardown |

I64-T01..T03 retain their original round1/failure0/lastConcluded0 history. No whole
CANDIDATE_READY, acceptance, resource handoff, integration or release is claimed.
