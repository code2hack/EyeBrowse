# T02 reproduction and evidence map

This is the controlled issue57/T02 Expert handback guide, not production engine
adoption or a new device reservation. Use the exact assigned branch candidate
and its private handback manifest. The implementer remains Expert-#57-T02 until
Manager verifies the handback; Worker57's laterT03 is not transferred here.

## Inputs and source identity

The assigned baseline is3e2600b7a273307bfd5f4f8269a941a3565a9ed0/tree3b58bf7f.
T01's RendererInstances implementation and12 host controls remain byte-identical
to that baseline. Protected governance/spec/design/plan/ADR inputs are read-only.
Vendor Java stays unchanged; maintained Kotlin replacements and exclusion/mapping
are documented in [replacement-map.md](loader/replacement-map.md).

Original Cromite153.0.8010.37 archives, native inputs and all failures are retained.
Its first fixed-input controlled-TLS result remains NOT_PASSED. The separately
qualified unmodified original-upstream WebView input is158.0.8107.0, snapshot
1719582/source a296091d1ed08d4dde44b8a427477fe570911472, immutable GCS generation
1792071892816325. It is a development snapshot, not stable156 equivalence or
production/security/redistribution approval. The private qualification record
binds its original ZIP/APK/signer/ABI/minSDK29/complete standalone dependencies
and licensing. Runtime native ABI is ARM64 on physical RG API32/user0.

| Input | SHA256 |
| --- | --- |
| APP, actual compiled cb39bf49 | 5eb93e460987b9279c13770777058bd9a2bd96c22dc29e71888b91ca849f4720 |
| TEST, current fixture compiled a8c92e98 | 9c9537f364b86a6526cc31b8f43cb5dd810249e7bc01408cd27bd40c74d0a6e1 |
| Upstream158 unmodified APK | 8daa731574cf1d0e7f24996dd18c8d51554dc7f27806f2619528021674b78710 |
| ARM64 libwebviewchromium.so | 4a2cd075e018ef3827d21f5e721ee1b4a28ed03429ce19b03bea3ff8931cfad2 |
| ARM64 crashpad trampoline | ba112c5254fa29c239341f3f610f0c3d3495268a6f4849a102b76adf2f872d32 |

APP/TEST use signer e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963,
UIDs10076/10077. Original public EyeBrowse UID10073/APKd6a9145b and system WebView95
UID10047/APK54401e4d are preservation inputs, never private-state targets.
Engine evidence is not based on UA/version strings alone: APK/native hashes,
actual executable mappings, process/UID/SELinux observations and controlled
private-file results are separately bound. Startup's declared-isolation label
alone is not actual renderer isolation proof.

## Build and original method map

Use the qualified retained loader-source tree and JDK17/SDK35. Builds serialize
through the existing shared flock, sample available RAM after locking and retain
fresh invocation-specific logs. No shared cache purge is part of reproduction.
The affected fixture build ran assembleChromiumAndroidTest and lintChromium with
no-daemon/two workers/Xmx2g/GOMAXPROCS2. It does not imply a new94-JVM execution;
retained JVM/ordinary/experimental checks keep their source/cache/metadata limits.

| Criterion | Exact retained evidence |
| --- | --- |
| T01 identity/lifetime/rebind/unrelated/exhaustion contracts | Original12 host controls; original implementation preserved. Physical exhaustion or intentional renderer-crash recovery is not claimed. |
| Real named instances, modern fixture,257 core requests | usb-focused-001, f809/APP5eb/TESTd359, complete4/7.844s. |
| Complete unchanged routing class | usb-routing-full-001, same inputs, complete2/57.291s;30 protocol stages, correlated native/browser external-tailnet nonce receipts, live DIRECT rules/dummy controls and restoration. |
| First-origin HTTPS/WSS | usb-https-first-001, same inputs, complete1/6.332s. |
| Four-live-tabs/link/history/hidden404/ordinary404/keyboard/native/recreation | usb-browser-group-001, same inputs, complete10/138.112s. Missing-only read observation retries use the separately authorized one3000ms deadline; original assertions/outer waits/actions are unchanged. |
| Actual renderer boundary | usb-routing-renderer-boundary-001.json: main10076/untrusted_app, real child22666/99147/isolated_app, matching native inode60648, kernel Binder UID verification, owned private dummy read denied; bounded evidence, not production security adoption. |
| Paseo anonymous root/UI | Original f809/d359 phase checks and separately compiled2c/TEST956 full45s document plus qualified current-frame004; actual anonymous welcome/connection/pairing/settings UI, no interaction/account claim. |
| ChatGPT normal native response | w07-native-status-001, doc-only694/TEST888 compiled43; actual HTTP200/defaultTLS1480ms, JUnit measurement1/1.513s. |
| Original ChatGPT address/Open method | w07-browser-acceptance-001, a8c92e98/TEST9c95; original assertion/window unchanged, complete1/10.122s and qualified current anonymous UI frameeb2ed491. |
| Separate ChatGPT document observation | w07-browser-document-001, same inputs,45000ms/84delivered, diagnostic measurement1/48.166s; not an added acceptance pass. |

All browser runs use fresh application processes; retained cache/profile is
explicit and is not described as cache/profile-cold. No log-in, pairing, message,
credentials, real subscription, personal session or original-profile migration
is permitted by this guide. Qualified software frames are not optical evidence.

## Private route and safe continuation

The device route is USB on u4090 for serial1906092617103125; source/build/Paseo
remain Spark. The private prototype comparison used RG127.0.0.1:39072 through
USB reverse to a mode600 Unix socket in the assigned700/u1000 remote directory,
then one precisely owned SSH Unix reverse to existing Spark127.0.0.1:7897.
It creates no public TCP proxy or global network/trust/provider setting. Shared
proxy configuration/nodes/credentials are not read or changed.

The window07 route is returned and its one create is consumed. Do not reuse its dead PID/socket,
helper endpoint or intent as a fresh allocation. Any later physical comparison
requires Manager's actual exclusive resource disposition with fresh paths and
physical/installed/source/signer/input/Owner-use guards; this is shared-resource
coordination, not a new approach/ticket-plan acknowledgment stage.

Before every stock call, qualify actual existing server PID/start/UID/binary/
loopback socket and a separate framed version41. Use numeric-H127.0.0.1/P5037
and the exact serial for launch, sampling, export and return callers. Missing,
changed or mismatched server stops the stock call. The source-backed missing-
server behavior and separate precheck retain version-mismatch/TOCTOU limitations;
no atomic no-start or exact installed-source equivalence claim is made.

A new authorized one-purpose client sends only the fixed host:version, exact
serial selection and single allocated explicit-localhost/norebind create.
Require complete normal statuses, canonical row targeting the owned Unix socket,
and actual one127.0.0.1 LISTEN/UID2000/pinned inode without same-port wildcard or
IPv6 before requests. Retain control bytes, source/staged hashes and one-create
intent; uncertain delivery is reconciled read-only, never replayed. Never grow
this into a generic dispatcher or patch/repopulate the server's security filter.

Do not test removal before traffic. The device normalizes its stored name to
tcp:39072, while host bookkeeping uses the literal create name. A failed literal
remove can revoke host permission even when the device listener survives. The
recorded006sequence/fatal supports that diagnosis; it does not show Unix targets
or ordinary creation are generically unsupported. The implicit replacement
server incident remains an operational violation, not unchanged/read-only state.

The current Kotlin fixture's two fixed entrypoints invoke the existing original
acceptance or document method. It preserves original DIRECT rules, DNS/listeners,
normalTLS and actions/windows, adds only a credential-free owned HTTP test node
and final MATCH group, checks actual core selection/provider completion, and
restores captured recovery/config/profile/file absence. Opaque baseline backups
remain private; do not expose or reconstruct them from this guide.

At final closure, all owned requests/scenarios/runtime/camera traffic must be
closed and the mapping becomes permanently ineligible. The specifically granted
order was canonical device removal first, verified original list/no39072, then
literal-host-key revocation. The latter's remote listener-not-found remains an
actual failed device operation. Host revocation has source/request-order evidence,
not direct map readback. No remove-all, post-removal reuse, security bypass,
re-registration or new create. Return only exact owned SSH/control/Unix resources;
keep CAMERA granted by current Owner direction and verify actual scoped
process/socket/camera/public inputs/Awake/displayON. Defer general cleanup.

## Retention and Worker continuation

The private handback ledger supplies exact invocation/source/tree/compiled input/
APP+TEST+installed/native/signer/outcome/restoration pins and source archives.
All earlier negatives and partials remain. Shell exit0 is not JUnit PASS;
measurementJUnit is not browser/account acceptance. Corrected metadata trees,
new helpers and later executions do not rename older source or recover missing
bytes. The historical ordinary9899B log/SHA576af517 and3065B binding/SHA47da886f
remain unrecovered with authentic partial support; no reconstructed replacement.
The exact attribution rewrite exception was consumed and verified; further pushes
are normal assigned-ref only.

Manager verifies the exact handback and returns laterT03 to Worker57. Worker
continues the result/adoption/review objective with these actual limits and
fresh resource coordination as needed. Independent whole exact-head review,
Owner account/adoption decisions, integration, cleanup and release remain
separate. This guide grants none of those actions or a new physical window.
