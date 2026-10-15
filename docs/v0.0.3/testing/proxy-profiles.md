# Proxy profiles — issue #44

Worker: Worker-#44

Scope: SPEC D3.1 §7, HUD H3.1 H3-C05/import half of C06, P3.1 V3-A07–A08,
canonical [issue #44](https://github.com/code2hack/EyeBrowse/issues/44).
Dependency-integrated baseline: `4b2e3be2fbadeb4b43783c469d1b1b7f05596ae6`.
Phone: **REMOVED_FROM_SCOPE_NOT_PASSED**. Independent review and acceptance
remain Manager-owned. Subscription scheduling belongs to #45.

Settings preserves both numeric rows and adds Proxy. The Proxy utility shows
friendly names, independent ON/OFF and Delete controls, Add, short feedback and
Back. It reuses the existing toolbar, scroll owner and PROFILE camera callback.
The website scanner keeps preview/Open and does not import.

## Import and storage boundary

- Accept HTTP(S), or `clash://install-config?url=<percent-encoded-HTTP(S)>`.
  Canonicalization lowercases scheme/host, removes default ports/fragment and
  supplies `/`; it preserves escaped paths and the full ordered query.
  Different account queries remain different sources. User-info URLs and
  unsupported schemes are rejected.
- Fetch through the existing native app-local route. Empty pools use DIRECT;
  initial import never requires its own undownloaded nodes. At most five
  redirects, no HTTPS downgrade, fixed connection/read limits and a 2 MiB
  UTF-8 body limit. The subscription request advertises `mihomo/1.19.32 EyeBrowse/0.0.3`
  and YAML content types, so client-sensitive servers can choose Clash YAML.
  This does not convert encoded subscriptions. HTTP/format/schema errors have
  fixed, credential-free text.
- [SnakeYAML SafeConstructor](https://github.com/snakeyaml/snakeyaml/blob/master/src/main/java/org/yaml/snakeyaml/constructor/SafeConstructor.java)
  is pinned at 2.5, with duplicate keys rejected, collection aliases disabled
  and bounded size/depth. A cumulative 2 MiB conservative UTF-8 JSON budget
  counts scalar occurrences, nested data, escapes and framing before pool
  copies/core configuration. Bounded scalar reuse remains supported.
  Only a usable `proxies` list is extracted. Full
  profiles cannot supply routing, mode, DNS, listeners, controllers or scripts.
- The importer supports the existing core's HTTP/SOCKS5, SS/SSR, VMess/VLESS,
  Trojan, Snell, Hysteria/Hysteria2, TUIC and AnyTLS node types with explicitly
  permitted connection fields. Unsupported node types are skipped; no usable
  node is an error. Unknown or unsafe options on a recognized node reject the
  import. File, interface, route-mark and chaining options are not accepted.
  Node labels are discarded. The bundled core additionally validates every
  imported source with `-t`, including updates to OFF sources, without opening
  listeners or applying imported globals. Proprietary/base64/provider-only
  subscriptions are unsupported.
- SnakeYAML's Apache 2.0 license and dependency attribution are bundled in
  `assets/licenses/SnakeYAML-Apache-2.0.txt` and `assets/third-party.txt`.
- One app-private atomic SharedPreferences value stores source, generated
  `Profile N` name, independent toggle and validated node cache. An exact source
  updates in place, retaining name/toggle; a new source starts ON. The store
  refuses to overwrite unreadable data. A failed apply or save keeps last-good
  data; a failed save rolls back the pool. Source/node credentials and parser
  exception text are excluded from ordinary UI/logs. A failed preference commit
  also restores the prior in-memory preference view, which Android changes
  before reporting disk-write success or failure. The aggregate store remains
  capped at 8 MiB. Save preflight bounds node data before serialization, then
  validates the exact serialized candidate through a fresh store reader.
  The import owner requires preflight before runtime replacement, and save
  repeats it before writing. Accepted saves use the same restart schema and
  representation budgets.
- All ON sources enter #43's collision-safe union. OFF retains its saved cache;
  Delete removes just that source. The genuine core owns health/lowest-delay
  choice and all-unhealthy DIRECT fallback. These operations never reload a tab
  or deliberately close existing page sockets. No extra selector or timer is
  introduced. The serialized import worker survives Activity recreation and
  its thread expires when idle. It awaits the bounded core operation rather
  than abandoning an already applied pool after a shorter fixed outer timeout;
  core health work grows with the number of imported nodes.

## Verification procedure

Use the current exclusive RG/fixture grant and DEV procedure. Verify physical
serial/model through the primary5555 route before mutation, install recorded
debug APKs with `install -r`, and preserve starting controlled keys in-process.
The test's outer restoration guard runs after scenario/fixture/report teardown
and preserves primary/suppressed failures. It never logs personal starting
values. No manual preference/XML writes, deletion, reconstruction or data clear.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/home/code2hack/Android/Sdk \
flock /home/code2hack/.local/state/eyebrowse/v0.0.2/runs/run-001/host-build.lock \
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
  :app-rg:testDebugUnitTest :app-rg:assembleDebug \
  :app-rg:assembleDebugAndroidTest :app-rg:lintDebug

adb -s "$RG_ENDPOINT" shell am instrument -w -r \
  -e class com.code2hack.eyebrowse.rg.ProxyProfilesInstrumentedTest#actualImportsPoolFallbackLastGoodAndRecreation \
  com.code2hack.eyebrowse.rg.test/androidx.test.runner.AndroidJUnitRunner
```

Run `longListGeometryAndOrdinaryInputRemainUsable` and
`exceptionalTeardownAndReportKeepStartingControlledState` separately on the
reserved device. The first records native empty/long list/control bounds,
ordinary strip ownership and keyboard/utility behavior. The second exercises
the actual scenario/outer restoration path with injected body, teardown and
report failures. Test fixtures use only owned dummy source/node data.

The separate `RealProfileImportInstrumentedTest` is opt-in with
`realSubscriptionOptical=true` and a fresh UUID `invocationId`. It enters the
ordinary Settings → Proxy → Add flow through native input, writes scanner-ready
metadata after a real camera frame, and waits for the Owner's presentation.
It never injects a decoder result or captures QR pixels/payloads. An opt-in
in-process download observer classifies response status, whitelisted MIME/format,
node/protocol counts and static parser rejection categories. It never persists
the response document or source URL; the normal app leaves that observer unset.
Parser diagnostics contain only whitelisted field/protocol categories. It records
fixed import feedback, private-cache round-trip booleans, page/form retention,
actual native/WebView public requests and whether the core observed a pooled
node on those connections. Its outer guard restores starting tab metadata
after teardown; the deliberately imported real profile stays saved.

The route test fetches real YAML through the production importer, checks the
actual production-generated node pool, and uses the existing TCP destination
fixture. Only its reserved `.test` destination mapping is changed for routing
probes; imported nodes/storage/toggles come from production code. Peer `.1`
means DIRECT, `.2` source one and `.3` the faster source two. Native HTTP and
fresh WebView requests, node receipts, retained URL/form values and actual
private storage provide the oracles. Controlled bitmap decode/delivery to the
shared scanner callback is **CONTROLLED_RASTER_NOT_OPTICAL**.

## Current evidence and limitations

Results and immutable APK/source bindings are retained under the assigned
issue44 evidence directory. The final candidate handoff identifies the exact
commit/tree, actual named terminal results, native PNGs/sidecars, original
failures, lint/cache limits and scoped RG/39030 return. A shell exit0 or a
Boolean toggle is not an acceptance result.

Actual optical camera-to-working-subscription proof is separately required by
#44. Fresh initial host checks found no qualified unattended presentation
surface. The Owner subsequently selected their real subscription QR in
[OWNER-V003-I44-REAL-SUBSCRIPTION-QR-01](https://github.com/code2hack/EyeBrowse/issues/44#issuecomment-6148043260).
The first deliberately presented real QR produced an actual optical callback
in native07, then failed import with fixed unsupported/invalid YAML feedback
(1 failed test, 422.293 s). No source was saved or real routing tested. This
feedback does not identify the representation or establish a product defect.
The Owner subsequently replaced that input with another correct real QR.
The replacement uses a separately recorded normal PROFILE invocation.
Successful real import remains
available to the Owner. No URL transfer, raw-payload report or QR image capture
is needed. Optical success and actual routing must be reported separately from
the controlled dummy checks; neither is inferred from scanner readiness. #42's
twenty repeats remain **DEFERRED_BY_OWNER_NOT_PASSED** and are not revived here.
Both #43 historical losses remain **NO_QUALIFIED_BACKUP_NOT_RESTORED**; these
tests preserve only their own current starting controlled keys.

Activity recreation reloads persisted source/name/toggle data. This is not a
claim that unsaved form memory survives process loss. Phone, unrestricted
public proxy nodes, arbitrary subscription formats, and #45 scheduling are
outside these checks.

## Reviewed round 1 verification

These results belong to `bada97793f14413d9dca84126354655772b684c3`.
Independent whole review requested the corrections below; all earlier positive
and negative evidence retains its original source/APK attribution.

RG: physical serial `1906092617103125`, model `RG-glasses`, Android 12/API32,
WebView 95.0.4638.74, native portrait 480×640. Every mutation used the physically
qualified primary TCP5555 route and ordinary data-preserving installation.
The native evidence directories retain exact APK hashes and source pins.

| Final evidence | Actual result |
| --- | --- |
| Host13 | Full assigned debug gates succeeded in 71 s; 78 tasks, 19 executed/59 up-to-date. All 87 JVM tests passed, zero skipped; genuine Linux core test 17.759 s. |
| Native08 | Combined OK3, 35.848 s. Eighteen controlled import/route/storage cases, exact list geometry/strip/native-input checks and exceptional restoration passed. All three restoration markers true; 14 fresh native images/sidecars/results retained. |
| Native09 | Replacement real QR: actual optical PROFILE delivery, normal download/parse/core validation/save, OK1 at 26.725 s. One ON profile/six cached nodes; private round-trip and retained page/form true. |
| Native09 actual real routes | Core healthy and EGRESS=AUTO. Native public HTTPS returned 200; fresh public WebView loaded. Both actual connections were observed with pooled-node chains. |
| Native11 cold read | Separate fresh process, OK1 at 0.345 s. Canonical source identity, friendly name, ON state and six cached nodes reloaded; raw private cache and tab metadata unchanged. No Activity/core/fetch/import started. Earlier native10 cold read OK1 at 0.332 s remains retained. |
| Native11 retained controls | Separate OK1 at 5.806 s. Actual native keyboard, star, Bookmarks, tabs, add/close, Back, Forward and Refresh worked. Its initially absent owned bookmark was removed, starting tabs restored and real private profile unchanged. |
| Host15 | Final test-only addition compiled; affected APK/lint tasks succeeded in 16 s, 73 tasks, 7 executed/66 up-to-date. Production APK and controlled/optical behavior unchanged. |

The replacement response was HTTP200, `text/yaml`, 3,378 UTF-8 bytes, a usable
YAML `proxies` list with six nodes (one SS, five VLESS). Those are static format
categories/counts, not published node details. No private source URL, downloaded
body, node names, credentials or QR pixels enter evidence. The expected normal
app-private source/node cache remains deliberately saved for the Owner. No
further destructive profile regression ran after the successful real import.

App APK SHA-256:
`2862df3c986023200502e6247cdd0cee840c8728a85d1b89a6a08c5c09bf6984`
(35,708,166 bytes), unchanged across native08/09/11. Native09's optical test APK
was `effa77c9513e11e8a3d114524a2e5cc3164b3fd3bfc4e8e7d0953bcccd98389d`.
Final test APK SHA-256:
`1da348c788de1c55e30d69c06ba1999b2b67302c9631eda8a397673eceb0a9f7`
(2,269,152 bytes). Only added read-only persistence/retained-input verification
methods and privacy-safe assertions differ in that test class; its optical
method is unchanged. The final binding has 85 module input pins. Installed APK
hashes were read back and matched both retained final artifacts.

The 26.725 s measurement is the whole optical/import/routing method, not a
five-second clear-code acquisition measurement. Lighting/distance were not
instrumented. This does not revive or pass #42's deferred repeat trials.
Android disk exhaustion/faults were not forced; storage write failure is an
injected JVM check. Unsupported schemas/encoded/provider-only formats remain
explicit failures rather than empty successful rows.

Necessary resource handoff finished on 2026-10-13: the exact owned fixture
PID/script was verified and stopped; no 39030 listener remained and a bounded
bind without listening succeeded at 15:48:42 UTC. Exact assigned app/test UID
processes, instrumentation, active core control endpoint and active camera
clients were absent at 15:50:12 UTC. RG remained Awake/stay-on true. The exact
owned host Linux binary also had no active process. These are timestamped
observations, not continuous guarantees. The deliberate real profile remains
saved. Return evidence is `resource-return-window01.json` plus its exact-package
follow-up `resource-return-window01-exact.json`; other file/state cleanup and
workspace archival remain queued until version end.

## Retained execution history

Original outcomes remain in the assigned private evidence; later passes do not
replace them. These are observations within the ongoing assigned round, not
canonical attempt conclusions.

| Record | Actual outcome |
| --- | --- |
| Native01 | Import test failed at 7.723 s: an Android-posted click had not settled before the toggle assertion. Early direct/first/faster-second probes passed; the run was incomplete. |
| Native02 | Test APK install failed during ADB access loss; tests NOT_RUN. |
| Native03 | Three separate named tests each OK1 (29.036 s, 3.950 s, 2.447 s). |
| Native04 | Combined 3 tests, 1 layout-capture failure at 32.971 s; old bottom image excluded. All three starting-key restoration records true. |
| Native05 | Combined 3 tests, 1 bottom-list wait failure at 77.241 s. All starting-key restorations true. |
| Native06 | Corrected combined OK3 at 34.061 s; all restorations true; 14 native images/bounds retained. |
| Native06 first live preparation | Failed at 46.588 s before opening the camera: the URL observer compared JSON-escaped text incorrectly. Tabs restored. Its old constant delivery label is an intended mode, not optical proof. |
| Native07 first real QR | Actual camera callback followed by rejected, unsaved import; FAILED1 at 422.293 s. Tabs restored; no real routing test ran. Owner subsequently replaced the input; its format/cause was not independently diagnosed. |

Native04/05 observer corrections preserve the original geometry, strip and
native-input oracles. The final list test traverses the entire long list using
raw bottom-strip input and observes the current ScrollView after each render;
it does not substitute fullScroll for that traversal. Captures require the
current laid-out, committed frame and fully visible target bounds. Production
scroll restoration also captures its particular rendered target, so a pending
post cannot scroll a subsequently opened utility.

Host08 ran 87 tests: 86 passed, the optional Linux core check skipped. Its
original Gradle log and observed count are retained, but the original XML was
overwritten before archival; the folder with that label contains a later
filtered skip and an explicit provenance note. Host10 genuinely executed all
87 tests successfully, including the real Linux Mihomo check (17.619 s).
A first confined rerun failed because the Linux Unix-socket pathname exceeded
107 bytes; it did not complete routing cases. With a shorter temporary directory
inside the assigned worktree, the same existing check passed at 17.775 s
(45 s Gradle command, all 24 actionable tasks executed). No product source
change addressed that host pathname limitation.

The original optional-binary skip, partial download timeout and Unix-path
failure remain recorded. The completed official Linux ARM64 v1.19.32 binary
was independently hash-verified before execution. No partial download was run.
The Manager also qualified matching retained dependency bytes; those originals
were not modified. Host10 used default host temporary space; the later focused
short-path run and current gate explicitly confine their core/config/evidence
to assigned space.

Lint tasks exit successfully and report eight warnings, zero errors. Kotlin
2.2.0-versus-2.0.0 metadata diagnostics limit the analysis; this is not a claim
of complete clean lint coverage. Gradle task counts distinguish executed work
from up-to-date work; these are incremental builds, not clean rebuilds.

## Simplification and handoff

One persisted value, one serialized import owner and the existing node-pool
runtime cover this feature. Profile rows use the existing utility/input paths;
there is no extra selector, polling framework, camera stack or scheduling
implementation. The import owner waits for the genuine bounded core operation;
a shorter outer timeout that could abandon applied-but-unsaved state was
removed. The worker thread expires when idle. Fixed public errors and discarded
node labels avoid accidental credential display.

Final commit/tree, APK/source bindings, terminal results, native images and
scoped resource-return readback are identified in the candidate handoff. The
Owner's deliberately successful real import, if any, is retained. Necessary
scenario/core/camera/fixture teardown and current controlled-state restoration
are immediate. Other file/state cleanup and exact workspace archival remain
queued until version end. Independent review and acceptance remain pending.

## Correction02: expanded data and reload compatibility

The [round 1 review](https://github.com/code2hack/EyeBrowse/pull/50#pullrequestreview-5506778748)
identified R44-01 scalar reuse bypassing collection alias limits, and R44-02
escape-heavy cache JSON exceeding its cold-reload limit after a successful save.
The original findings were static source/pinned-dependency observations.
Together they conclude one T01 round: attempt2/failure1/lastConcluded1 now.
T02/T03 retain attempt1/failure0/lastConcluded0. Manager owns further conclusions.

Against the reviewed product, seven selected JVM cases genuinely ran with two
regression failures in 20 s / 24 executed tasks. The bounded scalar probe is
roughly 38 KiB on wire, reusing 32 KiB across 80 nodes (about 2.5 MiB conceptually
expanded); it checks rejection before pool copying/core validation. Nested
header reuse also exercises the cumulative budget. No exhaustion run or
materialization of the review's roughly 2 GiB example was performed.
The save-to-fresh-store regression covers bounded escaped data and 1,100 KiB
of backslashes: successful saves must reload exactly; rejection preserves
preceding source/name/OFF/cache/disk data. Direct-store preflight additionally
proves non-reloadable candidates cannot write or replace last-good.

Simplification keeps one budget in the existing data walker, reused before
store serialization, and the existing fresh-reader path for compatibility
validation. The conservative estimate covers Android/JVM JSON escaping and
UTF-8. Existing raw input, node count, schema/depth, aggregate store and runtime
limits remain bounded. No new dependency/provider/framework/cache schema/core
selection policy was introduced. No blanket limit enlargement or OOM catch.

Fresh correction evidence is in `issue-44/correction02-state-evidence`, logs in
`correction02-state-scratch`. Original round 1 and intermediate red evidence
remain separately retained.

| Corrected-source evidence | Actual result |
| --- | --- |
| Host green01 | Assigned JVM/debug APK/test APK/lint gates succeeded in 1m12s with `--rerun-tasks`: all 78 tasks executed. All 90 JVM cases passed, zero failures/errors/skips. Genuine qualified Linux core test 17.509 s, using the short owned Unix-socket path. |
| Native01 controlled | Combined OK3 / 38.247 s, invocation `ec3db153-a4df-482d-acb3-7ddb50398780`. Twenty production import/storage/route cases include both new limits with exact last-good cache and actual native/fresh-WebView routes retained. Existing geometry/strip/native input and exceptional restoration oracles remain. Three starting-key restorations true; 15 fresh native artifacts retained. |
| Native01 real-cache cold read | Separate fresh-process OK1 / 0.354 s, invocation `6abb7098-1387-4714-8989-d245e3dc28e8`, PID16569. Original real profile/six enabled nodes reload with canonical identity/name/ON/cache equality; private cache/tabs unchanged. No Activity/core/fetch/import/refresh/rescan. |

Corrected app APK SHA-256:
`2fa64ace3031ff3cfb5f049e446c183424b2e33c8bc1bf1ecce08ead0bda00f2`
(35,432,594 bytes). Corrected test APK SHA-256:
`6a0d4a89f9b99ec12395f3db51c95627b1893f8d99817c376b0f86eedfc5443b`
(1,709,379 bytes). Both ordinary `install -r` operations succeeded; installed
hash/size readbacks match retained artifacts and 85 compiled module input pins.
Earlier real optical/public-route and retained-toolbar results still belong
to their earlier APKs; they were not replayed or relabeled. The corrected
real-cache check is non-destructive; no new Owner QR presentation was needed.

Lint retains eight warnings/zero errors and the Kotlin metadata analysis limit.
Reexecuting tasks is not a clean-cache/rebuild claim. Original negatives,
native07's undiagnosed rejection, optical timing limits, injected rather than
physical disk-failure checks, Phone removal, #42 deferrals and #43 losses stay
unchanged. Independent whole review and Manager acceptance remain pending.

Window02 was explicitly returned at **2026-10-13T16:51:13.297504Z**: exact
app/test UIDs10067/10068 had no process, active instrumentation, camera client
or core control socket; RG was Awake/stay-on with display ON. No new host39030
fixture was started; no listener remained and bounded bind without listening
succeeded. The owned Linux binary had no active process. These are timestamped
scoped observations. Controlled restoration preceded return; the successful
Owner profile remains saved. Evidence: `resource-return-window02.json` and
`native01/` bindings, install readbacks, named terminals and results. Further
device/service access needs a new coordinated grant. Other file/state cleanup
and exact workspace archival remain queued until version end.
