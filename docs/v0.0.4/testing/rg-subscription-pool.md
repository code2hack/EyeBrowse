# RG subscription import and pool verification

Issue #59, current [P4.5](https://github.com/code2hack/EyeBrowse/blob/e64067f60bf575343a8387c163c7705e4dabf1a1/docs/plans/v0.0.4-plan.md). Original candidate `2ca9bcd7d09c8c440054c1010a5b41b29efa683c` was a documentation-only child of component `0dbe0375f52d46b16845904d39c2ceeb02b7c3c3`, over integration `03de921e2400147842ce6c56101b58d514dde77c`. Independent review1 requested two evidence corrections, R59-01 and R59-02. Mission7 makes these host-only corrections; earlier results below retain their original source/APK cuts. Changed code requires new affected verification and independent whole-head review. Main `8929241f348bae9005b9b6bc1a93fbabd47145c1` is unchanged.

The [Android JSON cache correction](rg-profile-cache.md) remains separately demonstrated. The identities of the two historical Owner-reported failed inputs, their relationship to either available source, and their original failure causes remain unknown. Neither later Owner success nor these executions retroactively identifies those inputs.

## Actual available-source execution

Both cases used the normal native More → Settings → Proxy → Add path, controlled raster/decoder delivery, production HTTPS download with normal TLS, genuine core validation, persistence and a new store reader. Raster delivery is **not optical** evidence. No fixture profile rows replaced import.

| Case | Actual result with APP `c5d1b20a…` / TEST `1bf0277a…` |
| --- | --- |
| Manager-selected original saved source, distinct from the current target source | Invocation `56f2d291-bab2-4d49-8b35-39204a845e9b`: JUnit 1 passed. HTTP 200; six usable nodes. Target profiles increased from one to two; the existing source, friendly name, ON state and cached nodes were retained. |
| Exact current target source reimport | Invocation `cc568808-eea4-44a4-8672-07f04ac6cbd8`: JUnit 1 passed. HTTP 200; 35 usable nodes. Exactly the same two source identities, names and ON states remained; the other source's cache was unchanged. The server also returned unchanged node data for the reimported source, so this execution does not demonstrate a changed server-side node set. |

Both enabled sources remain saved, contributing 35 + 6 = 41 nodes. Bookmarks and starting tab metadata were preserved; the original app was not launched, stopped, written or replaced. These import/store results do not establish runtime routing. The earlier controlled tests separately cover changed node sets, OFF state, budgets and store-only failed persistence. Production post-apply rollback was not covered by that standalone store probe; it is R59-02 below.

## Runtime startup defect

Three real saved-pool invocations failed before public routing checks. Two used TEST `d48f1c02…`; the second unintentionally reinstalled that same old artifact while preparing diagnostics. This artifact-selection error, its actual installed hash and both failures are retained. The subsequent correctly pinned TEST `19f36429…` identified `WAIT_CORE_WEBVIEW_READY`: the runtime reported core unavailable/direct fallback, with both native and WebView proxy ports absent. Two profiles and 41 nodes were readable; profiles, bookmarks and starting tab metadata were preserved. No minimum-latency or public-route PASS follows from these runs.

Android's network resolver list included a scoped IPv6 address. Passing its raw percent scope into the pinned core's DNS URL parser rejected the entire startup configuration. The non-secret example `fe80::53%wlan0` reproduces the rejection. The same address and interface scope encoded as `udp://[fe80::53%25wlan0]:53` passes. The pinned [v1.19.32 parser](https://github.com/MetaCubeX/mihomo/blob/v1.19.32/config/config.go) parses DNS entries as URLs.

`ProxyPolicy.configuration` now formats only scoped IPv6 resolver entries at that boundary. It keeps the same resolver, scope and order, and retains ordinary IPv4/unscoped IPv6 entries and the existing empty-list system fallback. It changes no global DNS setting, imported policy, trust, engine, dependency, subscription representation, size limit, refresh cadence or TLS behavior.

| Check | Actual result |
| --- | --- |
| Retained bundled and selected regional data | Four native host ruleset conversions passed. The first host configuration adaptation separately failed path confinement; its bad harness paths and original failures remain retained. Corrected adaptation isolated DNS rejection. |
| Pinned Linux core, isolated resolver inputs | Seven executions: retained original and controlled scoped IPv6 rejected; their scope-preserving URL forms passed; ordinary IPv4, unscoped IPv6 and system fallback passed. |
| Installed Android core, before APP correction | Four bounded `-t` executions: retained original rejected, normalized retained input passed, controlled scoped IPv6 rejected, normalized controlled input passed. Existing app configuration, profiles and bookmarks remained byte-identical; all owned native processes settled. These are config-validation results, not ordinary app routing. |
| New native regression before correction | One JVM test executed and failed on DNS format rejection. Original XML/log, source and compiled classes are frozen. |
| Unchanged regression after correction | One JVM test passed, exercising named and numeric IPv6 scopes, ordinary IPv6, IPv4 and empty-list fallback. Its compiled test class is byte-identical to the failed invocation's class. |
| Affected Chromium host gate | 94 RG JVM tests executed, 0 failed/skipped; APP/TEST builds and lint passed. Fourteen native destination observations. Lint: 0 errors, 11 warnings. Loader XML contains 12 passes from an UP-TO-DATE task; these are retained coverage, not 12 new executions. |
| Affected ordinary host gate | 94 RG JVM tests executed, 0 failed/skipped; APP/TEST builds and lint passed. Fourteen native destination observations. Lint: 0 errors, 8 warnings. No ordinary APK was installed over the original app. |

Both affected full JVM invocations were uninstrumented. The two older ordinary full-suite failures, diagnostic/traced results, Android cache red/green runs, setup negatives and original waits retain their original meaning. The Kotlin/lint metadata coverage limit remains explicit.

## Exact artifacts and current preservation

| Role | SHA256 |
| --- | --- |
| Earlier cache-fixed APP used for the two actual imports and failed pool runs | `c5d1b20a6a3e98831ea79c6ee16d6b1c4715f5580b12453f2b3a905d463b57a8` |
| TEST used for actual native imports | `1bf0277a8be9ea2f598bfa9f6e93cb1f0828dcd77848a765646b326835b58ccc` |
| Scoped-DNS fixed APP, installed and read back | `bbcb4b38801feac2adfa96b400cc5e430c1f737b059eda3746f13acdf58beeb7` |
| Unchanged diagnostic/pool TEST, also reproduced by the final build | `19f364295f027ca0b954770287e155c0c1a41ed92b4f5408ae1fa1543a6d4461` |
| Common signer | `e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963` |
| Installed Android core | `bb9afbd48717f92d59e07da35fb694b83d40d6edb63eb9033c2ee4174137d195` |
| Pinned Linux core used by host checks | `c80df2ca5159567804da883401e0118908fc1155e05df2baf3dbd4d3ecfc3754` |

The new APP was installed once with a verified data-preserving target-only update. Package, signing identity and versionCode 3 / versionName `0.0.3-chromium-spike` remain; version metadata belongs to #62. TEST bytes were unchanged. The original app, system provider and six pinned current/legacy engine objects were preserved.

Complete coherent current CE/DP snapshots of both apps were retained and reconstructed into private restore staging before mutations. Each full staging checkpoint ran 15 SQLite checks, including the target WAL state. No on-device restore was exercised. Static code references were accepted only after exact device/retained-byte and metadata qualification; mutable, unknown and cache data remained covered.

Across the fixed APP update, the entire target CE archive, including its metadata, stayed identical. The target DP store lost two `code_cache` objects during the package update; their complete pre-update bytes remain in checkpoint011 and the actual post-update DP store is captured in checkpoint012. No old state was overlaid. Source CE/DP stayed identical. Checkpoint012 was verified at 2026-10-17 03:53:38 UTC; it is a recorded cutoff, not a continuous state or power guarantee. All current account/profile/bookmark/pairing data stays private. Account E2E is not claimed.

## Actual fixed-APP verification

Invocation `6f8ef128-b23b-4cbf-b8ef-f37e85d71fe9` executed on the actual RG with APP `bbcb4b38…` and the **same TEST `19f36429…`** as the retained failing APP `c5d1b20a…` run. JUnit passed one test in 33.515417 seconds, with no timeout. The original 45-second awaits, production health bounds and genuine ten-second AUTO selection-cache wait were unchanged.

The post-run generated configuration contained two resolvers and no scoped entry. This pass establishes ordinary routing in the current environment; it is not a controlled same-network startup red/green result. The scoped-parser correction is separately bound to the retained Android four-case validation matrix and unchanged native regression. No resolver value or network policy was changed to obtain this pass.

| Observation | Actual result |
| --- | --- |
| Ordinary runtime startup | Running; native and Chromium WebView proxy ports ready and equal. |
| Combined real source membership | All 41 expected nodes from both enabled saved sources appeared in the real core's ENABLED provider. |
| Common-endpoint health and AUTO | 36 healthy candidates; selected delay 524 ms equalled the lowest measured delay, 524 ms. EGRESS selected AUTO; health interval remained 300 seconds. This is latency evidence at that cutoff, not a throughput or every-site guarantee. |
| Native public route | Normal TLS HTTPS response 200 from `example.com`, with an actual pooled core connection. |
| Chromium WebView public route | A distinct pooled core connection and the public Example Domain page reached READY. No private tab or account page was loaded. |
| Teardown and retained data | Profile cache and bookmarks unchanged; starting tab metadata restored; foreground runtime released. Actual subsequent state was HOME, keyguard false, with source/target/TEST and associated app processes absent. |

The test froze automatic refresh only while examining the current saved inputs, then restored the test connectivity seam. It used no seeded profiles, relay, reverse connection or fixture service. The original controlled tests demonstrated OFF/deleted exclusion and unhealthy fallback/recovery; the first source won only while the second remained unhealthy. The earlier assertion that those tests demonstrated switched healthy-source delays was unsupported (R59-01). This real execution selected one actual source at its observed latency.

Separate fresh-process invocation `4d7bc769-c400-47aa-b9ab-982fb31d3ac8` passed one JUnit test in a new Android process, PID 14140. Both `ProfileStore` and fresh `RgProfiles` recovered the exact canonical source, friendly name, enabled state and nodes for both profiles and all 41 enabled nodes. The private cache, complete profile XML and tab metadata stayed unchanged; this headless check did not start the core. Neither execution demonstrates account E2E or optical acquisition.

## Review1 correction round2

R59-01 extends the existing fixture with independently mutable delays, retaining both sources ON and healthy. The native check measures both at the same health URL, waits the genuine AUTO ten-second cache interval in each ordering, checks the measured minimum and native choice, and records fresh proxy and destination receipts. The original 90-second test bound is unchanged.

R59-02 exposes the unchanged `RgProfiles.applyAndSave` transaction as an internal companion seam used by ordinary imports, toggles, deletes and refresh. The shared controlled check uses that production body with a genuine core and an isolated dummy disk store. It observes a candidate's new route before the writer returns false, then verifies the rollback call, prior usable route, other-source data, exact disk bytes and fresh-reader state. A core-invalid but store-saveable candidate separately checks actual runtime refusal before any write. The standalone store-only probe retains its original narrower scope.

Both affected host gates passed with **94 actual RG JVM executions each, zero failures/errors/skips**, APP/TEST builds and lint (0 errors, 8 ordinary / 11 Chromium warnings). Ordinary invocation002 took 88.126341 seconds; Chromium invocation001 took 88.860249 seconds. Both source inventories contain 483 files and the 66 pinned inputs were checked before and after. Loader XML's 12 passes remain UP-TO-DATE reused coverage. Each native journal has 19 actual destination observations. Existing fallback, TLS, WebSocket and regional/direct checks still passed; no timeout, health bound or assertion was relaxed.

| Host gate / ordering | Measured first / second delay | AUTO and actual new route |
| --- | --- | --- |
| Ordinary, configured 180 / 15 ms | 181 / 17 ms | Second source, 17 ms; new `.3` proxy and destination receipt. |
| Ordinary, configured 15 / 180 ms | 17 / 183 ms | First source, 17 ms; new `.2` proxy and destination receipt. |
| Chromium, configured 180 / 15 ms | 182 / 17 ms | Second source, 17 ms; new `.3` proxy and destination receipt. |
| Chromium, configured 15 / 180 ms | 17 / 182 ms | First source, 17 ms; new `.2` proxy and destination receipt. |

Both rows remained enabled and both actual health entries remained alive with positive measurements in every evaluation. The common health URL was unchanged within each invocation, and each ordering used a genuine 10.1-second wait. These are controlled real-core results, separate from the historical single-minimum 41-node device observation.

Each gate also passed the production transaction check: a two-source last-good pool routed through `.3`; the candidate disabled only the second source, and a new `.2` route was observed inside the writer before it returned false. Rollback restored both live members and a new `.3` route; memory, other-source data, exact disk bytes and a fresh reader matched. A store-saveable invalid-cipher candidate was then refused by the native application adapter without another write, retaining both members and another new `.3` route. Live membership counts were 2 → 1 → 2 → 2. This executes the same production transaction body used by `RgProfiles`, with real native application and route receipts; it does not claim Android `RgProxyRuntime` execution.

The first ordinary invocation001 remains **94 executions / 1 failure**: its raw fixture node name was not normalized before seeding the dummy store, so the exact fresh-reader assertion found `same` versus `node`. Its partial swaps and accepted rollback are retained without promoting that failed run to PASS. Correcting the seed through `ProfileImport.nodes` preserved the equality assertion. The original PR-read EOF and a host process-audit shell-text false positive also remain recorded setup negatives.

The opt-in Android method `ProfileStoreInstrumentedTest#productionTransactionRestoresRuntimeAfterSaveFailure` is compiled into both TEST APKs, with arguments `transactionProbe=true` and a new invocation UUID. It shares the checks with the host test and uses actual `RgProxyRuntime`, an owned local fixture and isolated dummy storage, then restores the starting saved runtime sources and checks Owner preferences/tabs/bookmarks unchanged. **It has not executed.** Worker64 owns RG; mission7 performed zero device actions. Changed-candidate Android verification and renewed whole-head independent review remain explicit gates; no CANDIDATE_READY or whole-ticket PASS follows from these host corrections.

## Transport history and current preservation

Fixed-APP pool invocation `61ab2c03-76bc-4591-9bed-26433ded1bb3` was **not launched**: its first physical identity guard found no USB device. One authorized 5555 connection attempt and one 41887 bootstrap attempt each timed out; unknown outcomes were reconciled without replay, server restart, pairing or `tcpip` changes. USB briefly returned and authenticated, then disappeared again before current-preservation requalification013 could begin. No fixed-APP pool test or fresh-process persistence result is inferred from preparation, installation, host passes or brief transport recovery.

The later authenticated Owner event supplied wireless bootstrap port 36967 and expressly authorized enabling primary 5555. Existing 5555 was not authenticated. The worker authenticated the exact RG through 36967, persisted a single intent, invoked `tcpip 5555` once, then independently authenticated 5555 and verified physical serial/model/user/API/shell UID, TCP property 5555 and a real listening socket. No host server lifecycle, pairing, key extraction or global network policy change occurred. This actual recovery supersedes the old USB capability gate; the prior unknown outcomes remain unchanged.

Fresh current-state requalification found a changed target member set and correctly remained NOT_READY. Complete new checkpoint014 captured both current apps before the actual fixed-APP test: every member and all mutable/unknown/cache data were covered, static code references were freshly verified, independent repeated streams and final cross-store checks matched, restore staging passed and all 15 database checks passed. Final post-test checkpoint015 completed the same full checks with 15 fresh database passes at 2026-10-17 04:40:41 UTC. Original source CE/DP stayed byte-identical. The changed current target state was preserved, with no old overlay; complete profile XML and bookmarks matched before and after both new test passes. No on-device restore was exercised.

Actual resource-return observation completed at 2026-10-17 04:42:11 UTC: authenticated primary 5555, HOME, keyguard false, Awake, display ON, both app CAMERA permissions granted, empty active-camera client section, no owned app/associated processes or live app Unix socket paths, and all owned test/capture operations terminal. An initial camera-status reader failed to recognize the service's heading; that setup negative is retained, and a bounded current-status read reconciled the actual empty client list without capturing camera-history logs. No extra HOME, force-stop, install, clear or network change was used. These are cutoff observations, not continuous guarantees. The worker reported HANDOFF_READY and stopped device actions pending Manager transfer. Successful real imports and current APP/TEST remain installed; discretionary cleanup stays deferred.

Manager retains exact source/input inventories, 66 recovered input bindings, raw invocation/XML/class/JAR/APK records, private source bindings, all negatives and restore proofs under Worker59 phase `usb-multisub-r6-001`. Private URLs, response bodies, node credentials, actual resolver addresses and account/session contents are excluded from this record and Git. #60 migration is withdrawn, not passed; #61/#64 interaction work and #62 account journeys remain separate.
