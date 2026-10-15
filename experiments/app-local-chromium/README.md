# App-local Chromium experiment (#55, repair #57)

This opt-in RG variant asks whether a complete newer Chromium engine can run
inside EyeBrowse without replacing Rokid's WebView or the installed EyeBrowse
application. It is an experiment, with no production adoption or migration.
The production browser, native input and Mihomo paths are reused unchanged.

`com.code2hack.eyebrowse.rg.chromium` (label **EyeBrowse Chromium experiment**)
and `com.code2hack.eyebrowse.rg.chromium.test` have their own UIDs and website
data. The ordinary application ID/version and default build remain unchanged.
Do not copy cookies, credentials, subscriptions or old profiles into this app.
No system provider, trust store, VPN or network configuration is changed.

## Fixed inputs and security limit

[inputs.json](inputs.json) pins the original upstream sources, release asset,
archive/native hashes, certificate, SDK requirements and licensing references.
The tested candidate engine is Cromite **153.0.8010.37**, not the separate
Google156 staged release reported during the investigation. Its complete
standalone APK includes the ARM64 engine and does not need Trichrome or a split.
Optional Android libraries are recorded in the input manifest.

The loader starts from the verified `core/src/main` source from WebViewUpgrade commit
`5a5a7d11b5e67bba37787a697f45c9f5d45e6dda`, verified against
[loader/source.sha256](loader/source.sha256) at every build. The small Kotlin
DSL wrapper builds that external source with the project's AGP8.7.3/JDK17/SDK35
and its required AndroidX annotations. Repair #57 replaces the activity manager
hook and five stub classes with maintained Kotlin. Original sources are checked
intact before staging the remaining vendor Java for both compilers.
[The replacement map](loader/replacement-map.md) identifies every exclusion,
replacement and security limitation. Repaired runtime classes.jar hashes must
be recorded independently. It does not use the published0.1.0 artifact or claim
that artifact matches this source. Apache2.0 license text is
included in the experimental APK; upstream source and copyright notices remain
unchanged. The engine APK/native binaries stay outside Git and outside the
experimental APK. Cromite declares GPLv3 and has Chromium/component notice and
source obligations. Engine redistribution is not qualified by this local test.

**The upstream loader does not preserve Android isolated-UID renderer
sandboxing.** Its five non-exported stub services have ordinary app child
processes. `SandboxedProcessServiceDelegate.java` explicitly describes this
choice so those processes can read the app-private archive. Multiprocess
execution is not proof of a renderer security boundary. No real Owner sign-in,
authenticated Paseo pairing, credential/offer import or personal conversation
test is authorized in this stage. Account/session compatibility remains
unperformed pending an explicit disposition.

## Build and provision

Use an assigned RG reservation, isolated worktree, private scratch directory,
the current host build lock and the DEV resource limits. Resolve JDK17/SDK35
in the environment. Fetch the exact loader source archive and the original
Cromite release asset linked in `inputs.json`; verify the engine SHA256 and
APK signing certificate before provisioning. Do not install the engine APK as
a system/application package. Set `LOADER_CORE` to the unpacked upstream `core`
directory; it is an external input, not a machine path embedded in source.

```sh
./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
  -PchromiumLoaderSource="$LOADER_CORE" \
  :app-rg:testChromiumUnitTest :app-rg:assembleChromium \
:app-rg:assembleChromiumAndroidTest :app-rg:lintChromium
```

With no `chromiumLoaderSource` property, no experiment variant or loader module
is enabled. Run the ordinary `testDebugUnitTest`, `assembleDebug`,
`assembleDebugAndroidTest` and `lintDebug` tasks separately without that property
to verify the original build. Native host routing tests need the qualified
`EYEBROWSE_MIHOMO_HOST_BINARY`, their own evidence path and short JVM temporary
directory; otherwise their assumption skip is not native qualification.

Before installing either experimental APK, verify the physical serial/model,
user0, current provider and original installed APK/UID/signature. Inspect both
experimental package IDs, test target, signer and hashes. Install only those
side-by-side packages, then reverify the installed bytes and UIDs. Ordinary
`install -r` updates are restricted to these experimental packages. Keep any
already authenticated experimental state if unexpectedly present.

Provision the verified external archive as
`files/chromium-engine/engine.apk` inside the experimental package, using a
uniquely named mission-owned staging file and `run-as` for this debug variant.
The main-process `ChromiumApplication` verifies the complete archive hash,
extracts/verifies the two pinned ARM64 libraries and calls the pinned loader
before production Activity/provider creation. Child engine initialization remains
upstream-owned; the Kotlin stubs also report a process-lifetime Binder to the
instance allocator. A missing/changed input, initialized provider, wrong version or
missing native mapping fails startup and writes `files/chromium-startup.json`;
there is no success fallback to95. A fully cold app start is required; engines
cannot be swapped in a live process. Initial extraction and complete hashing
are synchronous experimental startup costs, not a production startup design.

Example provisioning after those checks and a **fresh** resource grant (the
recorded window is ended): resolve the approved `I57_ADB`, identity-verified
`I57_RG_ENDPOINT`, verified `I57_ENGINE_APK` and unique `I57_RUN_ID` from the new
assignment. Preserve all returned output and installed hashes.

```sh
"$I57_ADB" -s "$I57_RG_ENDPOINT" install --no-streaming -r \
  app-rg/build/outputs/apk/chromium/app-rg-chromium.apk
"$I57_ADB" -s "$I57_RG_ENDPOINT" install --no-streaming -r \
  app-rg/build/outputs/apk/androidTest/chromium/app-rg-chromium-androidTest.apk
I57_STAGE="/data/local/tmp/eyebrowse-i57-${I57_RUN_ID}-engine.apk"
"$I57_ADB" -s "$I57_RG_ENDPOINT" push -z zstd "$I57_ENGINE_APK" "$I57_STAGE"
"$I57_ADB" -s "$I57_RG_ENDPOINT" shell run-as com.code2hack.eyebrowse.rg.chromium \
  mkdir -p files/chromium-engine
"$I57_ADB" -s "$I57_RG_ENDPOINT" shell run-as com.code2hack.eyebrowse.rg.chromium \
  cp "$I57_STAGE" files/chromium-engine/engine.apk
"$I57_ADB" -s "$I57_RG_ENDPOINT" shell run-as com.code2hack.eyebrowse.rg.chromium \
  sha256sum files/chromium-engine/engine.apk
```

The last digest must match `inputs.json`. The engine archive is copied only;
neither installation command above installs it as a provider.

## Verification

`ChromiumInstanceInstrumentedTest` sends the same public API32 named-binding
requests to the original APP (focused red control) and repaired APP (green).
Distinct names must connect to distinct child Binders; a repeat name retains
its child, the caller's Intent stays intact, and an unrelated app-owned service
keeps its ordinary component. The original engine delegate owns child exit.
Host lifetime controls additionally cover no premature reuse, stale death
callbacks, definite/unknown bind outcomes and explicit pool exhaustion.

The opt-in `:experiments:app-local-chromium:fixture:installDist` builds the Kotlin
adapter for the unchanged fixed pages and existing nonce-receipt protocol.
Its launcher takes `bind port pages-directory evidence-directory`; use only the
assigned host address/39030 after checking that the listener is free. Qualify
its actual PID/CWD/listener and stop that exact owned process at resource return.
Do not start the old #55 Python fixture or write to its retained evidence.

Keep [the original #55 results](results.md) and their failure cutoffs unchanged.
The [#57 result map](repair-results.md) records the verified partial repair and
remaining failures; it does not claim complete browser readiness.
Repair evidence must identify the actual repaired source/compiled JAR/APP/TEST
and installed bindings; no native or browser PASS is implied by host checks.

`ChromiumQualificationInstrumentedTest` uses the real production Activity and
an owned fixture page. It records exact app/startup/native bindings and checks
`toReversed`, `AbortSignal.throwIfAborted`, `:has()`, container queries and
`structuredClone` in the actual renderer without polyfills or UA changes.
`files/chromium-qualification.json` remains incomplete when any assertion fails.
`ChromiumHttpsInstrumentedTest` independently starts with an owned HTTPS origin,
observes a real native DIRECT connection in Mihomo, and checks the production
WebView's HTTPS fetch and WSS response. This prevents an earlier cross-origin
failure in the full routing suite from hiding the basic TLS/WSS result; it
does not replace or relax that suite.

Use the existing `LocalBrowserInstrumentedTest` for native editors, tabs,
navigation and current controls, and `MihomoRoutingInstrumentedTest` for genuine
HTTP/HTTPS/WebSocket, DIRECT/failure/recovery and recreation. Supply the reserved
fixed-page fixture as `fixtureBaseUrl` and the existing nonce-receipt fixture
protocol as `tailnetOrigin`; do not use an Owner subscription. Observe actual
app/renderer/core process UIDs, loaded library paths and PSS separately. A
bounded sample is not endurance, optics, head ergonomics or CPU-percentage proof.

The inherited keyboard cases also require a unique `evidenceRunId` and the
actual source commit as `candidateHead`; missing bindings are setup failures,
not keyboard results. Instrumentation preference lookup follows the Activity's
real `getLocalClassName()` rule for both the original ID and the suffix variant;
existing persistence/restoration assertions are retained.

For an assigned fixed-page/nonce fixture, set `I57_FIXTURE_BASE` and
`I57_TAILNET_ORIGIN` to its qualified origins. Preserve the terminal JUnit result
as well as the command exit; `am instrument` can exit0 with failed tests.

```sh
I57_HEAD="$(git rev-parse HEAD)"
"$I57_ADB" -s "$I57_RG_ENDPOINT" shell am instrument -w -r \
  -e class com.code2hack.eyebrowse.rg.MihomoRoutingInstrumentedTest \
  -e candidateHead "$I57_HEAD" -e evidenceRunId "$I57_RUN_ID" \
  -e fixtureBaseUrl "$I57_FIXTURE_BASE" -e tailnetOrigin "$I57_TAILNET_ORIGIN" \
  com.code2hack.eyebrowse.rg.chromium.test/androidx.test.runner.AndroidJUnitRunner
```

Use the same bound invocation shape for the engine/native cases, selecting their
exact class/method names. The HTTPS first-origin case needs its own fresh
invocation and no external fixture. Do not backfill missing results after a
failure with an assumed pass.

Anonymous public `https://chatgpt.com/` and `https://app.paseo.sh/` rendering may
be observed without account commands/submissions. A welcome or pairing page is
not session/conversation success. EyeBrowse's existing forced-black styling
still erases Paseo's background-colored menu bars; engine success does not fix
that defect. ChatGPT's prior stylesheet problem was not isolated.
`ChromiumAnonymousSiteInstrumentedTest` accepts only `anonymousSite=paseo` or
`anonymousSite=chatgpt`, plus the actual `candidateHead`. Run each in a fresh
invocation. It uses the native address/Open controls and records presentation,
public-page booleans and callbacks, without sign-in or conversation inspection.

Preserve original failures, incomplete streams and the exact source/input/APK
bindings. Return only owned test processes/listeners and the reserved RG in its
required Awake/ON state immediately after testing; preserve the original app
and defer discretionary file/app/workspace cleanup until the experiment end.
The Manager owns PR, independent review, attempt outcomes and adoption decisions.
