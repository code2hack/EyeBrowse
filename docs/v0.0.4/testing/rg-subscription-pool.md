# RG subscription and pool checkpoint

Issue #59, current [P4.4](https://github.com/code2hack/EyeBrowse/blob/7125378fe07dda1bd5cdb7e8399ff770884edb38/docs/plans/v0.0.4-plan.md), Worker59 mission6. This is a component checkpoint awaiting device verification. It is **not CANDIDATE_READY**, independent review, acceptance or integration. The source extends `aeb463ac9f90d80b8fcbdfb622fc23b288330e37`, based on integration `03de921e2400147842ce6c56101b58d514dde77c`; main `8929241f348bae9005b9b6bc1a93fbabd47145c1` is unchanged.

The [Android JSON cache correction](rg-profile-cache.md) remains separately demonstrated. The identities of the two historical Owner-reported failed inputs, their relationship to either available source, and their original failure causes remain unknown. Neither later Owner success nor these executions retroactively identifies those inputs.

## Actual available-source execution

Both cases used the normal native More → Settings → Proxy → Add path, controlled raster/decoder delivery, production HTTPS download with normal TLS, genuine core validation, persistence and a new store reader. Raster delivery is **not optical** evidence. No fixture profile rows replaced import.

| Case | Actual result with APP `c5d1b20a…` / TEST `1bf0277a…` |
| --- | --- |
| Manager-selected original saved source, distinct from the current target source | Invocation `56f2d291-bab2-4d49-8b35-39204a845e9b`: JUnit 1 passed. HTTP 200; six usable nodes. Target profiles increased from one to two; the existing source, friendly name, ON state and cached nodes were retained. |
| Exact current target source reimport | Invocation `cc568808-eea4-44a4-8672-07f04ac6cbd8`: JUnit 1 passed. HTTP 200; 35 usable nodes. Exactly the same two source identities, names and ON states remained; the other source's cache was unchanged. The server also returned unchanged node data for the reimported source, so this execution does not demonstrate a changed server-side node set. |

Both enabled sources remain saved, contributing 35 + 6 = 41 nodes. Bookmarks and starting tab metadata were preserved; the original app was not launched, stopped, written or replaced. These import/store results do not establish runtime routing. The earlier controlled tests separately cover changed node sets, OFF state, budgets, failed persistence and last-good behavior.

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

## Remaining device gate

Fixed-APP pool invocation `61ab2c03-76bc-4591-9bed-26433ded1bb3` was **not launched**: its first physical identity guard found no USB device. One authorized 5555 connection attempt and one 41887 bootstrap attempt each timed out; unknown outcomes were reconciled without replay, server restart, pairing or `tcpip` changes. USB briefly returned and authenticated, then disappeared again before current-preservation requalification013 could begin. No fixed-APP pool test or fresh-process persistence result is inferred from preparation, installation, host passes or brief transport recovery.

Continuation requires stable authorized device access, fresh current-state/preservation qualification, then actual saved-pool minimum-latency selection, native/browser public routing and fresh-process persistence on the fixed APP. The two successful imported sources stay saved. No new Owner terminal, private-file or source-identity setup is required. Current power/camera/resource return is unavailable; exclusive reservation remains held pending a verified handoff. Necessary owned processes have settled at their recorded cutoffs; discretionary cleanup remains deferred.

Manager retains exact source/input inventories, 66 recovered input bindings, raw invocation/XML/class/JAR/APK records, private source bindings, all negatives and restore proofs under Worker59 phase `usb-multisub-r6-001`. Private URLs, response bodies, node credentials, actual resolver addresses and account/session contents are excluded from this record and Git. #60 migration is withdrawn, not passed; #61/#64 interaction work and #62 account journeys remain separate.
