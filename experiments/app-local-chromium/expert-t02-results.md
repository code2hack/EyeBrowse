# Expert T02 repair investigation

I57-T02 remains **unconcluded / NOT_PASSED**, attempt2/failure1/lastConcluded1,
assignment revision2. This is an investigation checkpoint, not
`EXPERT_HANDBACK_READY`, acceptance, adoption or a source handoff. The retained
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
TEST source including the observer and optional diagnostics is
`9c6ab28ce13d2d32e74d9aba8924de2d92cfcc04`, tree
`09b91e0908dc3515e10f764ea65bc0c183d10fa5`.

| Artifact | SHA256 |
| --- | --- |
| APP | `5eb93e460987b9279c13770777058bd9a2bd96c22dc29e71888b91ca849f4720` |
| TEST | `d359cc0e07dccf27306dbbb4dc92f94eec177ac1fc08fc455d0089ef907c741f` |
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
| Corrected current158-focused-002 | Access lost; bounded completion monitor ended without a terminal receipt; exact device outcome and dependent TLS/routing reruns remain pending |
| Anonymous public roots | Separate earlier Paseo pass and later failures retained; no stable site/account PASS |

The controlled request-without-response failure is distinct from the earlier
confirmed premature HTTP-error publication. Native public transport timeouts
also occur outside renderer execution. Host default curl uses an existing
loopback proxy; explicit host DIRECT ChatGPT access timed out. These observations
do not establish one cause for all remaining failures. No public root was
preloaded or made successful by a cached/fabricated result, UA substitution,
polyfill, longer deadline or trust bypass.

Temporary CAMERA permission was confined to the separate prototype's existing
controlled scanner/native group. Both completed granted-camera groups restored
the actual captured denied permission and exact flags after closure, including
failed runs. Granted-camera results do not qualify denied-permission dialog
behavior or optics. No new QR/photo capture, account login, pairing, offer,
credential, personal session or original-app private-data operation occurred.

## Retention limitation and pending resource state

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

RG window04 remains active. The5555 route stopped answering bounded shell reads;
the specifically authorized41877 fallback did not establish authenticated access.
The exact current invocation has not been replayed. Its existing620s completion
monitor ended without receiving a device exit or JUnit terminal. Physical
readiness, that original outcome and current process/socket state require
recovery. Last verified CAMERA restoration preceded this access loss. After the
finite command bounds expired, owned host fixture PID2142813 was requalified
by start time/CWD/command/source and stopped; PID absence and39030 free were
verified at16:55:39UTC. This is host-fixture return only. Actual RG
Awake/displayON and device resource return remain unverified. Source,
experimental app/data, evidence and workspace cleanup remain deferred.
