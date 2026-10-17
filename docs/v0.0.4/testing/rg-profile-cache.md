# RG profile cache: Android slash escapes

This is the historical controlled diagnosis for issue #59. Implementation commit `a6353428c940de202650667849581e11f9571b07` (tree `c06be3be01e5972ee3f047859022b88d2c201537`) is based on `03de921e2400147842ce6c56101b58d514dde77c` and its accepted #61 integration. The two Owner-reported subscription inputs remained unacquired and unexecuted at this checkpoint. Their relationship to this defect is not established. This record does not claim issue acceptance or candidate readiness. Subsequent execution with two separately bound available sources, the additional DNS startup correction and the remaining device gate are recorded in [the current subscription/pool checkpoint](rg-subscription-pool.md).

## Cause and correction

`RgProfiles.applyAndSave` emits “Profile change could not be saved. Existing data kept.” for both failed `canSave` preflight and failed persistence after runtime application. The message alone cannot distinguish them or prove rollback.

A small supported YAML fixture containing a WebSocket path and `http/1.1` ALPN parsed successfully, then failed `canSave` on RG before any writer call. The exact fixture passed with desktop JSON and failed with Android JSON. Android serializes solidus as `\/`; the store reconstructed a JSON node document and passed it directly into SnakeYAML, which rejected that escape during fresh-reader validation.

Removing the slash-bearing values made the controlled candidate saveable. This was a 240–320 byte document, not a size-limit failure. The seven-variant isolation and the original failed Android invocation are retained separately from subsequent successes.

`ProfileStore.reload` now applies the existing escape-aware `ProxyPolicy.configurationText` helper before passing that internal document into `ProfileImport.nodes`. The core configuration path already uses this helper. It preserves literal backslashes and other JSON escapes; a blanket string replacement is not used.

The persisted JSON schema, supported subscription representations, cumulative node budget, total cache limit and fresh-reader check remain in place. Source identity, names, ON/OFF state, write-failure handling, runtime application/rollback and app routing policy are unchanged. The maintained production change is confined to the cache read boundary.

Fixed `ProfileStore.kt` SHA256: `1c44ae8e5f4e7677f028bfedbb6193235c318c646208c062774bb6b4f6f79cc6`.

## Evidence and limits

| Execution | Result |
| --- | --- |
| Original eight import/store JVM tests | 8 passed with desktop JSON; the same 8 passed with pinned Android 12 JSON source. These did not cover the new nested fixture. |
| Exact nested fixture before correction | Desktop suite: 9 passed. Android JSON suite: 9 run, 1 failed at save preflight. |
| Original RG store probe | 1 failed; candidate preflight rejected, writer calls 0, real profile value unchanged, owned fixture removed. |
| Fixed import and policy tests with Android JSON | 16 passed, including the unchanged nested fixture and an exact literal-backslash-before-solidus round trip. |
| Fixed Chromium JVM/build/lint gate | RG 93 executed and passed. The unchanged loader test task was UP-TO-DATE; its retained XML counts are 12 passed, not 12 new executions. APP/TEST build and lint passed. Lint: 0 errors, 11 warnings, with the existing Kotlin metadata coverage limit. 125 tasks: 19 executed, 106 up-to-date. |
| Ordinary full JVM gate, two invocations | Each ran 93 tests with 1 failure: the unchanged native China-IP live-connection observer. Both failures and their incomplete six-observation journals remain retained. |
| Isolated unchanged ordinary native class | 1 passed, with 14 destination observations. This does not erase the two failed full-suite invocations or establish their cause. |
| Ordinary full JVM suite with owned-core syscall tracing | 93 passed. Diagnostic execution only: tracing may perturb timing and does not substitute for an uninstrumented gate. |
| Subsequent uninstrumented ordinary JVM-only invocation | 93 passed, with all 14 native destination observations. Forced execution after separate build/lint completion; no traced/up-to-date result was reused. |
| Remaining ordinary TEST build/lint | Passed separately; this is not a JVM test pass. |
| Fixed RG store probe | 1 passed: preflight performed no write, actual preference commit succeeded, fresh reader matched exactly, oversized-candidate and injected-writer failures retained last-good data, real profile value unchanged, owned fixture removed. |
| Actual Owner source import/routing/fresh-process acceptance | Pending bound private input. Not passed by substitute fixtures. |

The ordinary check set has passing uninstrumented JVM and separate build/lint results. The two earlier combined invocations remain failed, with their cause unidentified. An independent bounded public-service request returned HTTP 200 with normal TLS verification, and the Chromium native gate passed its 14 observations. These different execution contexts do not establish why the ordinary pre-TLS observer missed its connection. Original assertions, the three-second observation deadline and the two-worker limit were not changed; this record does not introduce a project-wide test-ordering policy.

The failed Android probe used APP `5eb93e460987b9279c13770777058bd9a2bd96c22dc29e71888b91ca849f4720`, compiled from `cb39bf49bcbed32fe90dc2fe534926619073937e`. Its relevant import/store/controller sources were independently compared with the assigned baseline. The fixed probe used APP `c5d1b20a6a3e98831ea79c6ee16d6b1c4715f5580b12453f2b3a905d463b57a8`, compiled from the recorded working source over `03de921`. Both used the exact same TEST `1bf0277a8be9ea2f598bfa9f6e93cb1f0828dcd77848a765646b326835b58ccc`. All have signer SHA256 `e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963`.

All 306 recorded maintained code/test/build inputs matched the committed implementation content. Documentation is not a compiled input. This equivalence does not relabel the APK's producer cut or create a new APK/test execution.

The fixed APP retains package `com.code2hack.eyebrowse.rg.chromium`, versionCode 3 and versionName `0.0.3-chromium-spike`; version metadata is assigned to #62. No ordinary build was installed over the original app. Source app, system provider and pinned engine bytes remain separately guarded.

Post-probe checks found all six current target preference files and bookmarks equal to the fresh checkpoint, the source mutable store unchanged, all six static engine objects unchanged, CAMERA granted for both apps and no owned app/test/reader or active camera client. The actual resolved HOME was awake with display ON at the recorded cutoff. The exclusive reservation is retained; this is neither a resource transfer nor a continuous power/state guarantee.

## Private input and preservation

Fresh coherent CE and DP snapshots of both apps were reconstructed and restored into private host staging before the diagnostic mutations. All 12 database checks passed. The original source snapshots matched the prior checkpoint; newer target state was preserved without overlaying old preferences. No on-device restore was exercised. Backup/staging readiness is distinct from diagnosis or acceptance.

The transient original UI error was not independently captured. The fixed-message-only hierarchy filter returned no match but did not establish underlying dump completion; the authenticated Owner report remains the error evidence.

`ProfileStoreInstrumentedTest.preflightWriteAndFreshReadRemainDistinct` is an opt-in, headless, no-network probe. It writes a uniquely named preference fixture, preserves the real profile value and removes only its own fixture during teardown. The original failed report remains alongside the new successful report.

`RealProfileImportInstrumentedTest.selectedPrivateSourceUsesNativeAddAndProductionImport` is separate preparation for an actually selected private input. It binds an app-private input file by hash, uses normal Settings → Proxy → Add controls and controlled decoder delivery, and checks production import and fresh-store persistence. Controlled raster delivery is not optical evidence. This path has not yet executed against either Owner case.

Raw inputs, response bodies, credentials, account data and backups stay outside Git and model/public output. Reports use fixed categories, counts, booleans and opaque bindings. Actual source acceptance still requires normal core application/routing and a fresh process read; store-fixture success is not a substitute.

The original failed ordinary and Android runs, setup negatives, source/APK cuts and #44/#57 history remain retained. General cleanup is deferred; necessary fixture/process/camera teardown remains immediate. See [P4.2](https://github.com/code2hack/EyeBrowse/blob/9442398f599232d08c42b852050c6aa580f104af/docs/plans/v0.0.4-plan.md), [#44's retained acceptance/history](https://github.com/code2hack/EyeBrowse/issues/44#issuecomment-6151252318), and the [Chromium reproduction record](../../../experiments/app-local-chromium/expert-t02-reproduction.md).
