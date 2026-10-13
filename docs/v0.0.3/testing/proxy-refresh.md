# Automatic proxy data updates — #45

Implements SPEC D3.1 §8/V3-A09–A10 and HUD H3-C09/C10 on the accepted
#44 integration baseline `75646e6ec068ff937adb3ac02bb72bc393ca9a75`.
Independent acceptance and version integration remain Manager-owned.

## Production ownership and scheduling

| Operation | Policy | Owner |
| --- | --- | --- |
| All saved subscriptions, including OFF | 1,800 seconds | `RgProfiles` serialized writer |
| Regional domain/IP pair | 86,400 seconds | Existing `RgProxyRuntime` worker |
| Enabled-node health | 300 seconds while active and pool changes | Existing genuine Mihomo URL-test integration |

`RgRefresh` coalesces launch, usable default-network callbacks, foreground due
wake-ups and `RgRefreshJob` into one in-flight due check. App-private attempt
timestamps survive process restart. Missing/overdue timestamps and a clock
moving backward become due. Offline checks retain due state; failed downloads
keep data and retry at the next cadence rather than polling failure repeatedly.

JobScheduler IDs **4501/4502** are persisted, network-constrained, ordinary
one-shot jobs with the remaining due interval as minimum latency. Their extras
record the 1,800/86,400-second policy and due timestamp. Completion schedules
the next due job with the same ID. This avoids a periodic flex window skipping
a due update until a second full period. Recreation/resume retain stable IDs.
While active, one callback waits for the next due time; it stops with the last
Activity user. Background jobs never acquire a foreground core merely to keep
a timer alive. No exact alarm, foreground/VPN timer service, WorkManager
dependency or manual control is added. See the platform's
[JobInfo scheduling API](https://developer.android.com/reference/android/app/job/JobInfo.Builder).

Android can defer work during Doze, missing connectivity, quotas or force-stop.
Persisted jobs resume under Android's rules; launch/usable execution checks
overdue state. Minimum latency and accelerated tests are not exact execution
guarantees or evidence that 30 minutes/24 hours elapsed on RG.

## Last-good and routing behavior

Subscription GETs retain #44's finite HTTP/redirect/size/UTF-8/parser budgets,
explicit app-local native routing and credential-safe errors. Fresh core health
is checked before a refresh batch, so an all-down pool selects DIRECT before
bootstrap requests. Background/initial downloads with no running core use
ordinary connectivity. The core validates each source, including OFF data,
before its cache is saved. One failed source does not abort later sources.

Refresh retains source identity, friendly name and current toggle. OFF stays
outside the eligible pool. Automatic refresh and ordinary edits use the same
writer; a Delete requested during a download is serialized after that operation.
The refresh-specific store operation only replaces an existing row and cannot
add a missing source. No update deliberately reloads a WebView, submits a form
or changes imported Rule/DNS/listener policy. DIRECT remains fallback.

Regional downloads use the maintained `meta` branch of
[MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat/tree/meta):
`geo/geosite/cn.mrs` and `geo/geoip/cn.mrs`. Existing bundled data, pinned hashes,
licenses and provenance remain in `assets/mihomo`; first use requires no network
download. Each download is bounded to 8 MiB with finite connection/read timeouts.
Both files are staged app-privately. The pinned v1.19.32 core's
[MRS converter/parser](https://github.com/MetaCubeX/mihomo/blob/88dcbf7f1614a67c3b36b848ee3592dfa92ada36/rules/provider/mrs_converter.go)
must parse a non-empty domain and IP set with the correct behavior before
activation. Converter children have a ten-second bound and no listeners.

The active core loads the complete validated staged pair, then one atomic
`regional-current` file selects that directory for subsequent starts. A failed
pointer commit rolls the live configuration back. HTTP, partial/corrupt or parser
failure leaves the previous pointer/files intact. Each accepted pair retains
source URLs, retrieval time and file SHA-256 values in private provenance.
These hashes identify the downloaded bytes; a mutable upstream branch is not
represented as an independently verified source commit. No rule-provider HTTP
loop competes with this updater. Core health provider timers remain disabled;
the existing 300-second owner invokes genuine core URL tests.

## Verification procedure and boundaries

Run with explicit JDK17/SDK35, shared build flock, `GOMAXPROCS=2`,
`--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`:

```text
:app-rg:testDebugUnitTest :app-rg:assembleDebug
:app-rg:assembleDebugAndroidTest :app-rg:lintDebug
```

Supply the qualified official Linux v1.19.32 ARM64 core to
`EYEBROWSE_MIHOMO_HOST_BINARY`. Use a short owned `java.io.tmpdir`: filesystem
Unix-socket paths have a length limit. `RefreshPolicyTest` checks due boundaries,
restart/clock handling, OFF/deletion behavior and genuine MRS rejection of
corrupt, partial, wrong-behavior and missing pairs. `MihomoHostTest` retains real
HTTP/HTTPS/WebSocket, selection and all-down/DIRECT qualification.

On reserved RG, install matching debug app/test APKs with `install -r`.
`ProxyRefreshInstrumentedTest` uses owned subscription/rule endpoints and real
TCP destinations. It drives an actual JobService, observes ON/OFF cache effects,
genuine core routes and changed regional rule matching, last-good HTTP/malformed/
timeout/endpoint-unavailable failures, due coalescing, deletion during a delayed
response, recreation/resume, actual job configuration and retained page/form/
native input controls. Clock jumps and connectivity injection are **test-only**;
endpoint disconnects are real client I/O failures and do not establish that
the device's entire OS network was offline.

Before controlled mutation, an outer app-level guard snapshots affected profile,
tabs, due preferences, job definitions and rule/config files. It drains owned
work and closes scenarios/runtime/fixtures/reports before unconditional restoration,
preserving primary and suppressed failures. An injected report-closure failure
exercises restoration. Fresh-process checks compare the retained one-source,
six-node Owner cache without launching, refreshing, rescanning, deleting or
logging that private source. Native PNGs contain controlled dummy content.

Execution results, source/APK/install/provider/core/data bindings and original
limitations are retained in the assigned mission evidence and candidate handoff.
Phone is REMOVED_FROM_SCOPE_NOT_PASSED; #42 optical repeats remain
DEFERRED_BY_OWNER_NOT_PASSED; historical #43 losses are not reconstructed.

## Execution record

Worker: Worker-#45 used the reserved RG (physical serial `1906092617103125`, model
`RG-glasses`, Android 12/API 32, native 480×640) and owned controlled endpoints.
The installed app was updated with `install -r`; subsequent observer corrections
replaced only its test APK. Package data was preserved throughout.

| Retained run | Actual outcome |
| --- | --- |
| `host01` | One genuine Linux test failed because the generated Unix-socket path exceeded the platform limit; the three new policy/MRS tests passed. |
| `host02` | All 93 JVM tests passed with the short owned temp path; Android test compilation exposed an incorrect test API name. |
| `host03`, `host04` | All 93 JVM tests passed, zero skips; genuine Linux routing/MRS checks, debug app/test compilation and lint task completed. |
| `host05`–`host11` | Affected Android test compilation and lint task completed after test-only corrections; production APK bytes stayed unchanged. |
| `native01`, `native03` | Fresh-process private Owner-cache checks passed, one source/six enabled nodes, unchanged cache/tabs hashes, no core or subscription request. |
| `native02` | Nine completed destination/failure cases, then the observer rejected a legitimate `connections: null` snapshot before its request appeared. Full test failed; restoration passed. |
| `native04` | Initial fixture-control connection hit its three-second test bound. Full test failed; restoration passed. Subsequent RG HTTP 200/tailnet ping qualified recovery without restarting ADB/network services. |
| `native05` | Reached genuine regional matching; failed on the observer's `IP-CIDR` expectation versus core API spelling `IPCIDR`. Restoration passed. |
| `native06`, `native07` | New lifecycle-count observer failed because Android `ps` exposed a basename rather than the assumed absolute executable path. External observation qualified one actual core/socket/listener; the observer now resolves `/proc/PID/exe`. Restoration passed. |
| `native08` | Fourteen routing/update/failure cases completed, then form retention failed. The test had not verified that its initial field assignment reached the committed controlled document; setup now requires native READY, exact URL and a verified initial value. Restoration passed. |
| `native09` | Verified initial and retained form value/sentinel, with one page request before and after all refreshes. Later native input setup failed because it assigned an address before requesting focus; normal focus entry restored the committed URL. The test now requests focus before assigning its dummy draft. Restoration passed. |
| `native10` | Full named test passed in 59.996 seconds, all 16 controlled cases. Actual ON/OFF updates, last-good failures, DIRECT fallback, regional activation, delayed Delete and retained page/form passed. Actual jobs and launch/recreate/background/resume counts were recorded; starting state restored. |
| `native11` | Named exception-guard test passed in 0.531 seconds, retaining the injected primary/report-closure failures while restoring affected state. |
| `native12` | Named fresh-process Owner-cache test passed in 0.316 seconds. Final comparison matched the original cache/tabs hashes, one source/six enabled nodes, without starting a core or logging private source data. |

The successful native executions retain their original source binding
`36e8c4dca1fedf0439a6e5348cffa72b1b89a2d3`, tree
`20a933e00cb7f18780e6019db43ecce72d08a9dd`. Subsequent documentation and the
Manager-authorized unpublished message-attribution correction do not change
compiled inputs. The handoff records equivalent old/new commits without
relabelling these executions.

`native10` observed one genuine core process, one kernel control socket and one
mixed listener at launch, recreation and resume; background had zero of each.
Only jobs 4501/4502 were present, persisted and nonperiodic, with required network
and policy extras 1,800/86,400 seconds. Their observed minimum latencies were
1,800,000/84,600,000 ms; the regional remainder reflects the test-only 30-minute
clock advance. `cmd jobscheduler run -f` invoked the real subscription JobService.
The health interval remained 300 seconds. These observations qualify configuration
and exercised execution, not autonomous elapsed-time scheduling during Doze.

Real destination peers changed from `127.0.0.2` to `127.0.0.3` after a valid
subscription update; failed updates kept the latter route, and all-down selected
DIRECT at `127.0.0.1`. The controlled regional pair changed actual core matching
from `IPCIDR` (`127.0.0.0/8`) to `RuleSet` (`CN-DOMAIN`). The verified initial form
value and script sentinel survived every refresh with exactly one page GET
before/after. Native settings/input/eight toolbar controls also passed. The
retained 480×640 PNG shows the later recreated Activity's expected interrupted
controlled page; form retention is established by the earlier live value and
request observations.

The successful full host gate used explicit JDK 17.0.19 and SDK 35, shared flock,
two Gradle workers, 2 GiB heap and `GOMAXPROCS=2`, with fresh available-memory
checks. `host04` took 62 seconds: 19 tasks executed and 59 were up-to-date.
Later test-only builds each executed seven tasks with 48 up-to-date. These are
incremental results, not clean-cache claims. Lint returned success and its XML
contains zero errors/eight warnings, while its analyzer also printed Kotlin
metadata 2.2.0/expected 2.0.0 incompatibility diagnostics. Its semantic coverage
has that retained limitation; compilation and the executed JVM/native tests
provide separate evidence.

The official Linux ARM64 v1.19.32 reference executable was qualified at SHA-256
`c80df2ca5159567804da883401e0118908fc1155e05df2baf3dbd4d3ecfc3754`.
The installed Android ARM64 core was executed with `-v` and independently hashed:
`bb9afbd48717f92d59e07da35fb694b83d40d6edb63eb9033c2ee4174137d195`.
Bundled domain/IP data retain their original pinned hashes and provenance.

Final retained and installed APK SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| Debug app | `ce7d100ce53d0e54c87a169e7092745c081ec7b137c1b648e1725630d9357a52` |
| Debug Android test | `b31129312f7ded69492f0463a7207d2d11819ef038448146cc5970a77e38eec6` |

The provider was `com.android.webview` 95.0.4638.74. The final test-install/readback
record completed 0.616 seconds after instrumentation started; pre-start hash
readback is not claimed. During the successful run, `/proc` maps identified the
exact newly installed test APK and its matching SHA-256. Original timing,
installation and loaded-APK records remain separate.

At **2026-10-13T18:32:25.716366Z**, RG identity was reverified and returned
Awake/StayOn/display ON. Owned app/test/core processes and kernel control sockets
were absent; restored starting job state had neither job 4501 nor 4502. Camera
clients were empty. The owned fixture process was gone and port 39030 had no
listener. The first handoff observation retained an idle test-package process;
after confirming no active instrumentation, teardown stopped only that package
and repeated the scoped observation. Source/evidence/workspace retention continues
until version end. Independent review and acceptance remain pending.

The simplification pass shares the ordinary profile apply/save/rollback path
with automatic refresh, uses two stable ordinary jobs and the existing runtime
worker/health owner, and adds no dependency, recovery framework or manual UI.
