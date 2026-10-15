# Expert T02 repair investigation

The scoped T02 correction/verification package is **ready for Manager handback**
under assignment revision7. Canonical attempt2/failure1/lastConcluded1 remains
unconcluded until Manager records the outcome; this is not ticket acceptance,
production adoption, account/session qualification or automatic source transfer.
The fixed153 TLS result remains NOT_PASSED. Separately qualified158 results and
all original cutoffs are distinguished below and in the
[reproduction/evidence guide](expert-t02-reproduction.md). The retained
[Worker partial handback](repair-results.md) and [original results](results.md)
remain unchanged. T01's instance/lifetime fix and controls are preserved;
T03 remains Worker57's later dependent objective.

## Corrections and separate diagnoses

The maintained Kotlin loader now uses the five non-exported Android isolated
services. A private Binder capability returns only the pinned APK and two
native libraries as read-only descriptors, after a connected Binder call;
no file descriptor is carried in the bind Intent and the child supplies no
path. The engine and a small public native-load bridge share a defining loader.
Public Android resource-loader APIs supply engine assets. Remaining vendor
Java is unchanged and excluded/replaced classes are recorded in the
[replacement map](loader/replacement-map.md).

Actual renderer observations include isolated UIDs and SELinux `isolated_app`,
executable pinned native mappings, and denial of the owned dummy private file.
They are distinct from the initial generic service probe. The main process
remains UID10076 / `untrusted_app`. Binder-reported child UIDs are checked against
the kernel caller UID. Repeated binding, distinct names, unrelated services and
process-death lifetime controls remain in place. Physical exhaustion, deliberate
renderer-crash recovery and production adoption are not claimed.

The fixed Cromite153 controlled-TLS comparison failed: Android's app/domain
trust accepted the fixture root while the native engine rejected it. The exact
upstream153 certificate-policy patch and original `net_error -202` are retained.
Manager's revision2 disposition permits a separately qualified upstream input;
it does not retroactively pass153 or permit a validation bypass.

The separate upstream Chromium158 snapshot uses ordinary Android trust. Earlier
158 runs passed the independent HTTPS/WSS case and complete routing class. The
new isolated pipeline also exposed a completed bodyless Unix HTTP request whose
zero-byte body write could raise `Broken pipe`. `CoreControl` now omits only that
empty write. Its native regression sends256 GETs and1 body-bearing PUT to a
fast-closing controlled server, with257 completions and no request replay.

The common RG browser waits for actual WebView proxy-configuration completion
before dispatching a queued address load or resuming a page. It coalesces the
pending address and cancels callbacks on destruction; ordinary failure/DIRECT
paths remain available. Main-frame HTTP errors are hidden immediately and
published after their document settles, so Refresh does not act on the preceding
history entry. Committed-frame presentation retains the original live styling
and visual-state barrier; READY still requires page completion.

The original single `evaluateJavascript` observation callback was sometimes
lost across real link/history or error-document replacement. The Manager's
separate bounded-observer disposition allows only audited read-only expressions
to be reissued when delivery is missing. They share one3000ms monotonic deadline,
one target and one first-delivery result. Wrong/false/null/undefined values are
returned unchanged. Listener installation/reset remains one-shot. All method
bodies normalize byte-for-byte to the preceding source after helper-call
renaming; the original outer10000ms wait, assertions, UI/input actions, fixture
MIME/protocol and expected values remain unchanged. Later passes qualify browser
behavior through this amended observer, not reliable single-callback delivery.

## Bound inputs and current evidence

Compiled APP source is `cb39bf49bcbed32fe90dc2fe534926619073937e`;
The TEST used for the complete browser/routing/focused acceptance groups was compiled at
original `9c6ab28ce13d2d32e74d9aba8924de2d92cfcc04`, tree
`09b91e0908dc3515e10f764ea65bc0c183d10fa5`. Its corrected metadata identity is
`019b31fb497292b8bfc3f8bcbc81d850040201cf`; this is an equivalent tree, not a new
compilation or execution. The [exact metadata map](expert-attribution-correction.md)
preserves all original invocation/source SHAs.

The separate document/cause-chain diagnostics use TEST compiled at
`2c309f68543f39b479681663ea2d68b961da4eef`, tree
`f6ec2e69e93036338774afd9f4357521c0c786a1`. The original anonymous, LocalBrowser
and Standalone acceptance files are byte-identical to compiled9c6; production,
loader, T01 and protected inputs are unchanged. APP output remains byte-identical.
The new TEST was separately staged and installed with equal source/remote/device
hashes and the same signer/UID. Its execution is supplemental measurement;
earlier full groups retain their actuald359 TEST bindings.

| Artifact | SHA256 |
| --- | --- |
| APP | `5eb93e460987b9279c13770777058bd9a2bd96c22dc29e71888b91ca849f4720` |
| TEST, original complete groups | `d359cc0e07dccf27306dbbb4dc92f94eec177ac1fc08fc455d0089ef907c741f` |
| TEST, supplemental2c diagnostics | `956bf84b8e06fd005aedfa4d0edbb0a587091cdd273ae73006d046244f72e148` |
| Original-upstream158 APK | `8daa731574cf1d0e7f24996dd18c8d51554dc7f27806f2619528021674b78710` |
| Upstream158 ARM64 executable | `4a2cd075e018ef3827d21f5e721ee1b4a28ed03429ce19b03bea3ff8931cfad2` |

APP/TEST signer is
`e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963`;
experimental UIDs are10076/10077. All62 upstream loader pins and54 staged,
unchanged Java sources were reverified. Seven maintained loader sources and
three actual compiled JARs are retained with exact hashes outside this worktree.

The158.0.8107.0 / code810700001 input is the unmodified standalone
SystemWebView.apk from official Chromium Android_Arm64 snapshot1719582,
source `a296091d1ed08d4dde44b8a427477fe570911472`, immutable GCS generation
1792071892816325. Its complete APK/native/ABI/minSDK29/dependency/signature/source/
license qualification is retained. It is a current CI development snapshot,
not Google stable156, a production signer or approved adoption. No engine
source build, repack, native patch or binary redistribution was performed.
Original153 archive/native/source and every result remain separate.

| Invocation / available verification | Actual outcome |
| --- | --- |
| Host loader / experimental / ordinary XML | 12 /94 /94 cases; zero failures/errors/skips; task cache distinctions retained |
| Affected APP/TEST builds and lint commands | Complete; Kotlin2.2 metadata versus analyzer2.0 coverage limitation retained |
| Earlier158 first-origin TLS and full routing at a206dcf | 1 /2 cases passed, separately bound earlier source |
| observer-browser-001 at ec0e136 | 10tests/1failure; later geometry pose check failed after denied-camera permission request |
| observer-browser-camera-001 at9c6 | 10tests/2failures; geometry passed, initial four-tab and recreation page failures retained |
| browser-resource-trace-003 at9c6, optional forwarding listener | 10tests/1failure; ordinary404 request had no main-frame response/error/commit within the original wait |
| Native public Paseo transport diagnostic | JUnit completed1 test; proxy root timed out, direct root200, direct script response then read timeout; not browser PASS |
| current158-focused-001 | 5tests/2 setup failures from omitted fixtureBaseUrl; original terminal retained, no modern/JSON body success |
| Corrected current158-focused-002, original recovered without replay | 4tests/1failure,25.513s; instance2/CoreControl1 pass, modern fixture presentation fails; original monitor loss retained |
| resume-https-first-001 at original2ef579f | Independent original HTTPS/WSS `OK (1 test)`,6.018s |
| resume-routing-full-001 at original2ef579f | 2tests/1failure,50.293s at native external-tailnet DIRECT before the later browser/HTTPS/form tail; post-JUnit log export went offline |
| usb-focused-001 at f8091cb | `OK (4 tests)`,7.844s; original instance2, genuine modern Web APIs1 and CoreControl1 |
| usb-routing-full-001 at f8091cb | `OK (2 tests)`,57.291s; all30 stages including correlated native/browser external-tailnet receipts, HTTPS/WSS, live form, recreation and core-exit recovery; exceptional restoration passes |
| usb-browser-group-001 at f8091cb | `OK (10 tests)`,138.112s; full LocalBrowser9 and original Standalone recreation1 together; granted CAMERA retained under current Owner direction |
| usb-https-first-001 at f8091cb | Original independent first-origin HTTPS/WSS `OK (1 test)`,6.332s; ordinary certificate validation and DIRECT |
| usb-anonymous-paseo-001 at f8091cb | Original presentation assertion passes,46.054s; READY after40.956s observation, but delivered DOM samples have zero text/buttons and retained screenshot is blank |
| usb-anonymous-chatgpt-001 at f8091cb | Original1test/1failure47.532s; actual ERROR `Page unavailable (-6)` and Chromium handshake `net_error -100` |
| usb-native-network-chatgpt-001 at f8091cb | Diagnostic completion only,17.511s; app-proxy SSLHandshakeException and DIRECT SocketTimeoutException, no successful native root response |
| usb-anonymous-paseo-trace-001 at f8091cb | Original presentation assertion passes,16.124s; passive metrics confirm22,147,384-byte script downloaded/status200, but delivered DOM samples still have zero text/buttons |
| Supplemental2c build/lint | Corrected chromium selector passes19s,97tasks/8executed89upToDate; initial wrong variant selector fails before compilation and remains retained; no new94-case execution claim |
| usb-paseo-document-001 at2c | Prelaunch engine-read SSH banner timeout after ordinary wake; no instrumentation/app-load invocation; original native output recovered exactly |
| usb-paseo-document-002 at2c/TEST956 | Separate45000ms document window,74observations, first nonzero text/button at21348ms; measurement completion47.348s |
| usb-paseo-document-003 at2c/TEST956 | Separate45000ms document window; measurement completion46.795s; external frame guard did not qualify focus, so no image was taken |
| usb-paseo-document-004 at2c/TEST956 | Separate45001ms observed elapsed,46.766s measurement completion; qualified in-window software frame shows anonymous welcome and connection/pairing-link/settings controls, untouched |
| usb-native-network-chatgpt-causes-001 at2c | First physical serial read fails at SSHconnect; no device mutation or instrumentation launch |
| usb-native-network-chatgpt-causes-002 at2c/TEST956 | Measurement completion7.480s; app-proxy SSLHandshakeException→EOFException5309ms, DIRECT ConnectException151ms; no HTTP response |
| Anonymous public roots | Separate earlier Paseo pass and later failures retained; no stable site/account PASS |

The controlled request-without-response failure is distinct from the earlier
confirmed premature HTTP-error publication. Native public transport timeouts
also occur outside renderer execution. Host default curl uses an existing
loopback proxy; explicit host DIRECT ChatGPT access timed out. These observations
do not establish one cause for all remaining failures. No public root was
preloaded or made successful by a cached/fabricated result, UA substitution,
polyfill, longer deadline or trust bypass.

The current ChatGPT failure also occurs in the separate native HTTP client.
Concurrent host-only root GETs record DIRECT connection timeout and HTTP403
through Spark's existing loopback proxy; these are separate host observations,
not device-browser outcomes. The [exact158 source](https://github.com/chromium/chromium/blob/a296091d1ed08d4dde44b8a427477fe570911472/net/base/net_error_list.h)
maps `net_error -100` to connection closure. The new exception chain confirms
an EOF inside the app-proxy handshake; a separate DIRECT connection also fails.
No TLS validation exception or egress/profile change is introduced. The
ChatGPT browser criterion remains NOT_PASSED; none of these measurements
establish a single cause or universally unavailable network.

A separate document diagnostic observes one45-second window without breaking
at READY. It reads only origin/readiness/counts/anonymous-or-challenge flags;
the original site method, assertions and deadline are unchanged. A diagnostic
JUnit completion is never promoted to anonymous-interface acceptance. The
native GET diagnostic additionally records bounded exception class chains,
without exception messages or server/credential content.

The qualified current software frame at2c/TEST956 shows Paseo's anonymous
welcome screen and its connection, pairing-link and settings controls. None
were used. The same run's delivered observations have no possible-personal-link
indicator and the actual foreground is the separate experimental Activity.
This resolves the identity of the rendered interface beyond a nonzero count;
it does not qualify accounts, pairing, optics, stable-site behavior or a first
HTTP-cache-cold load. The retained prototype cache/profile was not cleared.
An earlier frame guard failed without retaining its raw focus/count, so that
failure stays unqualified; the later display-focus capture has its own binding.

The remaining specific capability is a normally validated ChatGPT HTTPS path
on the same RG. Current permitted native/browser paths have no qualified
successful response. A Manager/Owner test-egress disposition is requested;
the current mission excludes global network changes and relay exposure.
An Owner-provided test network or separately scoped noncredential app-only
egress can be qualified before a new unchanged original browser invocation.
No such change is authorized or performed by this checkpoint. Without it,
the ChatGPT criterion remains unresolved and T02 stays unconcluded.

The two earlier temporary CAMERA fixtures restored their captured denied state
and exact flags after closure, including failed groups. Current authenticated
Owner direction in revision4 permits the separate prototype to retain CAMERA.
After fresh physical/installed/signature qualification, its denied permission
was granted with ordinary `pm grant --user 0`; actual before/during/after records
show unchanged flags and `granted=true` after the complete USB browser group.
Required permission end-state is `GRANTED_RETAINED_BY_CURRENT_OWNER_DIRECTION`;
scanner/camera clients must still close normally. Granted-camera results do not
qualify denied-permission dialog behavior or optics. No new QR/photo capture,
account login, pairing, offer, credential, personal session or original-app
private-data operation occurred.

The complete USB browser group has nine valid observer reports bound to its
invocation/head. Five missing callback deliveries were recovered in253.8–299.8ms
within the unchanged3000ms total deadline; no wrong value was discarded.
Standalone recreation uses its original one-shot helper. An extra collector
attempt for a nonexistent LocalBrowser recreation observer returned non-JSON
diagnostic text, which is retained and explicitly excluded from observer proof.
The actual complete JUnit terminal reports all10 methods passed.

Current full-routing security evidence separately binds main UID10076 to actual
child PID22666/UID99147, kernel-authenticated `isolated_app` and a matching
external OS sample. Its executable native inode60648 matches the separately
hashed158 input; the owned dummy private-file read is denied. This is actual
Chromium renderer evidence, not the generic isolated-service probe or complete
production-security approval.

## Retention limitation and actual resource state

Evidence lives under the assigned private `expert-t02-01/evidence` directory,
including immutable APKs, source archive, invocation manifests, terminals,
reports, observer delivery diagnostics, startup/UID/native observations and
qualified input records. Original failures, setup cutoffs, transport truncations
and aborted tails retain their own outcomes; shell exit0 is not JUnit PASS.

Two historical positive host client files were overwritten when the `ordinary`
run name was reused. The missing original log was9899bytes, SHA256
`576af51771bb0dcbb487ba90e158f0efbc668b279c533f82172d967fe1bf868b`;
the binding was3065bytes, SHA256
`47da886f729817844c482b77dfb865663e07666efe54a2214189bcd3bce76912`.
Authentic native excerpts,25 qualified raw daemon records and historical94 XML
remain, but exact original client-file bytes have not been recovered. The old
Manager record/hashes were not edited. Current9c6 files are separately preserved;
no rerun or reconstruction substitutes for the originals. The host runner now
refuses existing log/binding paths and requires a distinct invocation name.

RG window04 was returned. Subsequent private windows05 and06 were also returned
as recorded below. Owner moved the attached RG to USB on u4090;
USB ADB through noninteractive SSH is now primary for this operation. Source,
build and Paseo remain on Spark. The exact remote tool/USB identity, installed
APP/TEST/original public package/provider hashes, signers and engine were
independently qualified. Complete power/display snapshots show Awake/displayON
at their recorded cutoffs, not continuously.

Original `resume-routing-full-001` reports and scoped logs were recovered first
through USB into new host paths. Its saved terminal matches the original hash;
no invocation was replayed. Late log recovery is limited to the actual retained
buffer. Prior prototype process/TCP/TCP6/Unix absence and CAMERA denied with the
captured flags were then verified before further work. The earlier access/stream
failures and recovered4/1 focused outcome remain distinct.

Old fixture PID2142813 was returned at16:55:39UTC. Precisely owned Spark
fixture PID2239633 was requalified and stopped at18:31:25UTC, with PID absent
and39030 listener-free. Source/page/compiled JAR/process/start/CWD/argv and
receipt/log hashes remain retained. Manager independently verified the return;
39030 is now Manager-held with no active fixture grant.
The earlier failed native tailnet probe produced no retained fixture receipt;
the new complete USB routing invocation has its own nonce-matched native and
browser receipts and live DIRECT-rule evidence. The USB control route alone
does not establish that result or public-site success.

Recurring per-command SSH startup failures were retained as separate prelaunch
negatives. One task-owned local SSH multiplex client reused the same u4090/USB
route, with exact PID2276534/start/CWD/argv/private600socket qualification;
each device command still executed freshly. It created no port forwarding or
remote controller. Its original shim and per-invocation tool hashes remain.
Only that exact master/socket were closed after device work; actual PID and
socket absence were verified at19:04:33.728674UTC.

Necessary prototype teardown completed with fresh physical/installed/signer/
input/original-public/provider bindings. The owned runtime stopped; supported
process/TCP/TCP6/Unix headers and no owned rows were verified, and active camera
clients were empty. A strict postread initially rejected the IPv6 field label;
the original failure remains and a new correctly qualified read records the
actual `remote_address` header. CAMERA remains granted with unchanged flags.
Complete final power/display snapshots show Awake/displayON at
19:04:33.624478UTC. These are timestamped actual readbacks, not continuous
state or original-private-data equality claims. At the window04 return cutoff,
shared SSH/ADB/Paseo services were untouched. The later window06 shared ADB
incident is recorded separately below.

The specific ChatGPT network capability question remains with Manager; this is
neither `EXPERT_HANDBACK_READY` nor source relinquishment or a T02 conclusion.
The assigned private u4090 tool stage and all mission source/app/evidence/
workspace cleanup remain deferred to the recorded repair-end boundary.


## Private egress windows05 and06: transport results, no browser pass

The normal attributed source `43b799f593b9ef31811b5d78d06e45eb94bc038a`
adds only a Kotlin status probe. Its affected build/lint passed, APP remains
`5eb93e460987b9279c13770777058bd9a2bd96c22dc29e71888b91ca849f4720`,
and TEST is
`888c69d6287e1557e9f4e831a9992bead98e84e653a742af127e36be0d1bb9e3`.
Existing original browser methods, waits, assertions, engine, loader and T01
controls remain unchanged. No new JVM/full-group result is claimed.

| Invocation | Actual result and limit |
| --- | --- |
| Window05 CLI creation | One explicit-localhost request rejected with `Invalid source port`; no reverse mapping, HTTP, native or browser invocation. TEST888 was host-only at this cutoff. All owned resources returned. |
| Window06 normal service creation | Existing loopback server and separate framed `host:version` qualified first. One fixed explicit-localhost create completed all three statuses and resolved39072. Supported kernel reads showed one127.0.0.1 listener, UID2000/inode8906543. The device stored its name as `tcp:39072`; this was not a wildcard bind. |
| Window06 literal-name teardown attempt | `--remove tcp:localhost:39072` failed with listener-not-found. That original failure and the first literal-row guard STOP remain unchanged. Manager subsequently reconciled the normalized device row and authorized its canonical removal. |
| Window06 native status001, source43/APP5eb/TEST888 | JUnit completed one measurement in0.864s. Actual responseReceived=false, IOException→EOFException after845ms, no HTTP status. Legacy `window05` argument/report strings remain literal compiled-source fields; the invocation and route bindings identify window06. No HTTPS or browser PASS follows. |

The exact own-path shared ADB log records PID15009 fatally rejecting the006
reverse connection through `IsReverseConfigured`. Subsequent stock controller
sampling/report reads implicitly started replacement PID50651, sameUID1000
and exact SDK binary hash. The exact initiating client stderr was not retained;
the original fatal, before/after process/start/socket identities and raw native
outcome are retained. This implicit start violated the intended no-start
boundary. The original guard checked SSH ownership without checking ADB server
liveness on every post-failure read. No intentional start/kill/restart/version
command or security-filter override followed; the replacement was left intact
and is not described as the original unchanged service.

Independently read host reference `sockets.cpp` and `transport.cpp` support a
specific sequence hypothesis: reverse requests update host permission before
remote success; the failed literal-name removal could erase host permission
while the normalized device listener remained. This fits the later veto. It is
not exact installed binary/source proof, nor evidence that normal creation
intrinsically bypasses registration or Unix targets are generically unsupported.
Manager separately records the missed host-bookkeeping continuation precheck.
The veto was preserved. No new native retry, public-root request, browser wrapper or
original45s/nativeOpen invocation occurred after it, and no profile fixture was
created. First normally validated HTTPS response remains NOT_PASSED.

Necessary return used fresh physical/public/APP5eb/TEST888/UID/signer/input
bindings and the actual existing replacement server before every stock call.
These separate checks retain their TOCTOU limits and do not establish atomic
no-start enforcement. Transport death had already removed the mapping: the
original reverse list was equal again and supported TCP/TCP6 showed no39072,
so no canonical removal or recreation was issued. At20:19:36.305767UTC, owned
main/test/renderer/controller processes, TCP and app-private Unix rows were
absent, active camera clients were empty, CAMERA remained granted with its
original flags, and full power/display reads showed Awake/displayON. Original
public package/provider and158/dummy hashes matched. TEST888 remains installed.

After fresh exact PID/start/CWD/argv/UID/inode guards, one SSH master exit
returned only the006 bridge. Actual PID2305542 and local control were absent;
only its still-owned remote Unix inode171600 was unlinked. This completed at
20:20:31.560549UTC. Existing Spark7897 and shared SSH/Paseo services were left
intact; the shared ADB replacement incident remains explicit. No discretionary
file/app/workspace cleanup occurred.

Read-only guard negatives, including absent profile-file parsing, the window
subcommand's missing focus field, a generated return-script syntax error and a
camera-header layout assertion, remain retained. Successful corrected reads are
new records; authentic partial native tool outputs do not reconstruct missing
raw data. Detailed bindings are under `evidence/egress-window05` and
`evidence/egress-window06`, particularly the native invocation/outcome,
`private-bridge-hop-readonly-001.json`, `post-veto-server-readonly-001.json`,
`device-return-002.json` and `bridge-return-001.json`.

Private `next-operation-preparation-001/proposal.json` records the concrete
normal-registration sequence and final literal-host/canonical-device cleanup
only after all traffic closes. It also records the reference-backed numeric
server endpoint option, version-mismatch hazard, installed-runtime qualification
limit and precheck races. This is preparation only; window06's one creation is
consumed and no next window, route or request is authorized by that artifact.
The two historical ordinary raw-client gaps remain unrecovered. T02 remains
attempt2/failure1/lastConcluded1, unconcluded; no handback or T03 transfer.


## Window07: qualified response, original browser method and bounded document

Manager verified window06 return before allocating a fresh007 operation. Every
stock call used explicit numeric `-H 127.0.0.1 -P 5037`, the selected USB serial,
and a fresh check of existing server PID/start/UID/binary/loopback socket plus a
separate framed version41 exchange. These are bounded controls with explicit
version-mismatch and TOCTOU limits, not atomic no-start enforcement. No server
absence/version/kill experiment or security-map override was performed.

One normal explicit-localhost create completed all three statuses and resolved
39072. The canonical row targeted only007; the actual listener was
127.0.0.1/UID2000/inode8963913 with no same-port wildcard or IPv6. No pretraffic
remove was attempted, so normal host registration remained intact through the
checks. This supports the earlier failed-removal/permission-loss sequence
hypothesis; it does not establish installed-binary/source equivalence or a
universal cause for earlier failures.

| Invocation | Source / TEST | Actual result |
| --- | --- | --- |
| `w07-native-status-001` | doc-only69431b9, TEST compiled43 /888c69d6 | Normal Android TLS/hostname validation, HTTP200, responseReceived=true,1480ms. JUnit OK1/1.513s is measurement completion. Legacy window05 labels remain literal source fields; invocation/route identify007. |
| `w07-browser-acceptance-001` | a8c92e98, TEST9c9537f3 | Original complete address/Open method OK1/10.122s, within its unchanged45s maximum.9/9 observations delivered, finalREADY/shown/complete/style applied, originChatGPT,95text/42buttons, personal-links0. |
| `w07-browser-document-001` | a8c92e98, TEST9c9537f3 | Original separate diagnostic completed45000ms,84/84 delivered,75READY samples, finalshown/styled/complete/95text/46buttons, personal-links0 and no challenge flags. JUnit OK1/48.166s remains diagnostic measurement. |

The original focused480×640 screenshot was actually updated during the first
browser method, qualified against its delivered final personal-state guard,
and independently viewed. It shows ChatGPT's anonymous welcome/input interface
and login control, with native EyeBrowse navigation/address chrome. PNG33873bytes,
SHA256 `eb2ed491f5eb799a22449e31eba2e767e9ad84e622aaee81abd93622e8a2c1f0`.
This is software/UI evidence beyond DOM counts; no login, message, pairing,
CAPTCHA or account operation was attempted. Neither this frame nor45seconds
qualifies optics, conversations, enduring reliability or production adoption.
These were fresh application processes with retained browser cache/profile;
cache/profile-cold loading is not claimed.

The only implementation change after69431b9 is a153-line Kotlin TEST wrapper
with two fixed entrypoints calling the existing original methods. It holds the
existing runtime through a credential-free owned HTTP proxy fixture, preserving
all preceding DIRECT rules, DNS/listener settings, normal TLS and original
method bodies/actions/assertions/deadlines. It uses the actual core selection
and completed provider override, with no fake health/readiness. It snapshots
owned recovery/configuration and original file absence and restores them on
normal or exceptional closure. Opaque backup contents stay private.

Host build/lint at a8c92e98 succeeded19s/97tasks8executed89upToDate with the same
JDK17/SDK35/flock/no-daemon/two-worker/Xmx2g/GOMAXPROCS2 controls. No new94-JVM or
whole-group run is claimed. APP remains exact5eb/compiledcb39; TEST is
`9c9537f364b86a6526cc31b8f43cb5dd810249e7bc01408cd27bd40c74d0a6e1`,
compiled at `a8c92e98caff44bde41fb6a89fdb89996dc63a7f`, tree
`0983a6c682609c1cbca6bc02034bf6c8fd12debd`. Same e372 signer and UID10077;
only TEST needed installation. Original T01, production, acceptance and
ordinary sources are unchanged by this wrapper. Its complete original-body
results supplement the earlier full focused/routing/browser/first-TLS groups;
those retain their actual f809/d359 source and execution identities.

Both fixture restorations were independently read back: recovery string and
configuration bytes equal their captured baselines, profile and fixture files
absent, no owned process, camera clients empty and CAMERA granted. After all
traffic closed, one canonical `--remove tcp:39072` succeeded; original list and
no39072 were verified before one literal `--remove tcp:localhost:39072`.
The latter's exit1/listener-not-found is retained as an actual failed device
operation, not a deletion PASS. Ordinary request order/reference semantics
support host-key revocation; no direct release-server map readback is claimed.
No traffic/create/re-registration followed teardown.

Actual final RG return21:30:01.556973UTC binds unchanged public/APP/TEST/UID/signer
and APK/main-ELF/ARM64-crashpad/dummy hashes, original reverse list/no39072,
supported no-owned TCP/TCP6/Unix/process rows, controller PIDs5990/6596/7228
absent, empty camera clients, CAMERA granted with unchanged flags, and full
Awake/displayON snapshots. A copied environment key stopped incomplete return002;
its inherited never-created-fixture label is invalid for007. Source/partial
failure remain; fresh corrected003 records the actual two restored fixtures.

After exact ownership guards, one SSH exit returned only PID2324577/control
inode7495474 and guarded Unix inode171940. Actual absence was verified by
21:31:35.671120UTC. Existing shared server50651/start6726426 and proxy2767 were
left intact; the earlier006server violation remains separate. No discretionary
files/app/workspace cleanup, original-app private read, global trust/provider/
network action or production integration occurred. All required evidence and
source/remote/device artifact bindings remain in the private mission.
