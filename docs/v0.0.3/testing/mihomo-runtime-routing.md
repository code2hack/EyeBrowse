# Mihomo runtime and routing — issue #43

Scope: [SPEC D3.1](../../../SPEC.md) §§6.1–6.3, [HUD H3.1](../../design/v0.0.3-rg-hud.md) H3-C09 routing, [P3.1](../../plans/v0.0.3-plan.md), V3-A05–A06. Source baseline: `74798ae0d47e009a0a145c21b0ea116cb5fee94b`; target branch `work/v0.0.3-rg-input-proxy`. Phone: **REMOVED_FROM_SCOPE_NOT_PASSED**.

## Artifact and existing integration

The RG APK contains the unchanged official ARM64 Android command-line executable from [MetaCubeX/mihomo v1.19.32](https://github.com/MetaCubeX/mihomo/releases/tag/v1.19.32). Kotlin starts that executable with the existing `-d`/`-f` CLI and uses its existing filesystem-Unix-socket REST API. There is no custom JNI/Go bridge or imported VPN client. It is packaged as `lib/arm64-v8a/libmihomo.so`, with legacy installer extraction and symbol stripping disabled for that entry, so execution uses the installer-owned native-library directory rather than a writable app-data executable.

| Item | Pin / observation |
| --- | --- |
| Source | `88dcbf7f1614a67c3b36b848ee3592dfa92ada36` |
| Official asset | `mihomo-android-arm64-v8-v1.19.32.gz`, release asset ID `601415805` |
| Compressed SHA-256 | `42b1f2be8702484d3ae6238daab392622f54bd0d264966700f1f28f52596e326` |
| Packaged executable | 64,045,272 bytes; SHA-256 `bb9afbd48717f92d59e07da35fb694b83d40d6edb63eb9033c2ee4174137d195` |
| ABI / loader | ELF64 AArch64 PIE, `/system/bin/linker64`; `liblog.so`, `libdl.so`, `libc.so` |
| Upstream build | Go 1.26.8; `CGO_ENABLED=1`, `GOOS=android`, `GOARCH=arm64`, `with_gvisor`, trimpath; NDK r29-beta1 and `aarch64-linux-android34-clang` |
| Android qualification | Actual RG-glasses, API32/ARM64: installer-extracted binary hash matched, `-v` exited0, private control reported v1.19.32, and destination routing executed. WebView95.0.4638.74 supports `PROXY_OVERRIDE` and completed its callback. |

The [pinned upstream workflow](https://github.com/MetaCubeX/mihomo/blob/88dcbf7f1614a67c3b36b848ee3592dfa92ada36/.github/workflows/build.yml) contains the build command and toolchain setup. It downloads MetaCubeX's Go build and updates the embedded CA file before compilation. The official binary records the pinned source revision and `vcs.modified=true`; this is recorded rather than claiming a byte-reproducible clean-tree build. EyeBrowse does not patch the downloaded executable. Runtime checks its SHA-256 before execution. The test-only Linux executable is a different official artifact and is never packaged into the APK.

## Redistribution and source access

Mihomo's included upstream license is GNU GPL version 3. The original license, Go runtime license, linked-module notices and data licenses are shipped in `assets/mihomo/licenses`; machine-readable provenance is in `assets/mihomo/provenance.json`. Retain those notices when conveying this binary, preserve recipients' GPL rights, and provide access to corresponding source and the build instructions alongside the binary. This requirement applies to repository conveyance of the bundled binary as well as later APK distribution. The repository's overall license is not silently changed by this implementation.

The exact core source is available as a [fixed-revision source archive](https://github.com/MetaCubeX/mihomo/archive/88dcbf7f1614a67c3b36b848ee3592dfa92ada36.tar.gz), including `go.mod`, `go.sum`, native inputs and build scripts. The linked-module inventory records all 122 exact linked versions/replacements, Go sums and fixed-version source archive URLs. All 122 source content hashes were verified against the Go sums embedded in the official Android binary; 175 original license/notice files are preserved. Preserve this source-access information at the place an APK/binary is distributed; any subsequent binary modification needs matching source/build information. This record does not claim a new license grant for unrelated EyeBrowse files or a legal determination about every future distribution arrangement. [GPLv3 terms](https://www.gnu.org/licenses/gpl-3.0.html), particularly §§4–6, define the actual obligations.

## Bundled regional data

Both files are unchanged compiled `.mrs` snapshots from [MetaCubeX/meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat/tree/bf52d18cf9f047308cf3b1844e5e9211989d2e03), commit `bf52d18cf9f047308cf3b1844e5e9211989d2e03`, timestamp `2026-10-13T03:09:24Z`, release message `Released on 2026-10-13 11:05`.

| APK file | Upstream path | Bytes | SHA-256 |
| --- | --- | --- | --- |
| `cn-domain.mrs` | `geo/geosite/cn.mrs` | 540,748 | `e92bda4f0b9f05623663a882331a320ab4e266cada2046d3828a94572cdded46` |
| `cn-ip.mrs` | `geo/geoip/cn.mrs` | 37,538 | `d95b3d125f6c74ce3c15e19ef794f142de91e21c3b8fb7767e76aebfabff5149` |

The [observed generator](https://github.com/MetaCubeX/meta-rules-dat/blob/4178770badecb1b349fbcd62c737e0d7a2079729/.github/workflows/run.yml) derives the CN domain list from [blackmatrix7/ios_rule_script ChinaMax](https://github.com/blackmatrix7/ios_rule_script/tree/master/rule/Clash/ChinaMax), plus the generator project's direct-list additions/removals; the IP input is [Loyalsoldier/geoip](https://github.com/Loyalsoldier/geoip), which credits [gaoyifan/china-operator-ip](https://github.com/gaoyifan/china-operator-ip) for its CN IPv4/IPv6 data (MIT notice also included). The compiled snapshot does not publish all consumed input commit IDs, so none are invented here. The generator-project license is GPL3; the ChinaMax input's GPL2 and GeoIP input's CC-BY-SA-4.0 texts are also included. Credit those projects and retain applicable data attribution/share-alike requirements. EyeBrowse changes only the packaged filenames. These are maintained classifications, including names beyond `.cn`; they are not a guarantee of every domain/CDN's geography.

Bundled hashes are validated before first use. Existing app-private copies survive normal foreground stop/start so later #45 updates can retain last-good data. Mihomo validates the actual working files when loading configuration. A corrupted startup does not install a dead WebView proxy. Daily download/validation/replacement scheduling belongs to #45.

## Owned routing and health

`ProxyPolicy` accepts **node definitions only**, from independently enabled `ProxySource`s. It deep-copies them and replaces display names with a full source-identity SHA-256 plus index, so equal names from two sources do not collide and source URLs/display names do not enter normal diagnostics. Unsupported node protocols and routing/file-control options are refused. Raw subscription YAML, imported rules, DNS, controller, listeners and scripts do not enter the generated top-level configuration. The importer/production profile UI belongs to #44.

The one app-owned Rule configuration routes local names (`localhost`, `.local`, `.lan`, `.home.arpa`, single-label names, `.ts.net`), private/shared/loopback/link-local IPv4, applicable local IPv6, bundled mainland-China domain data and bundled mainland-China IP data DIRECT. The final rule uses EGRESS only when enabled-source union nodes exist. Otherwise it is DIRECT. A public name resolving to a private address also takes the DIRECT address path; the address rules do not use `no-resolve`.

All eligible nodes enter one inline Mihomo provider. The native URL-test group AUTO tests the common endpoint `https://www.gstatic.com/generate_204`, requires HTTP204, uses zero selection tolerance and selects by genuine core delay. DIRECT is absent from AUTO's candidates. EGRESS is a selector initially on DIRECT. A small Kotlin adapter calls the genuine provider health endpoint, reads per-node health/history for that same URL, then selects AUTO if any node is healthy or DIRECT if all fail. It never measures or sorts latency itself.

Only the Kotlin foreground runtime schedules these calls: once after pool changes and every 300 seconds while active. Native provider/group periodic timers are disabled. The upstream health endpoint deduplicates calls for one second; native AUTO caches selection for up to ten seconds. Tests wait beyond those native windows when flipping the controlled nodes. Those waits are test controls, not an additional application timer. Detection/recovery is bounded by the configured health cadence and native selection cache; established sockets are not migrated. Selection and source updates never reload tabs or replay form submissions.

## App and network boundaries

`LocalBrowserActivity.onStart/onStop` acquires/releases one singleton foreground runtime. It waits for core readiness before publishing a native proxy port and uses the public, feature-checked AndroidX `ProxyController` override. The callback records completion. A provider without `PROXY_OVERRIDE` is reported as unavailable; provider support is not inferred from an Android API number. Loopback bypass defaults are removed so HTTP/HTTPS requests also reach Mihomo's owned private-address rules.

The private `/version` response alone is insufficient readiness: this core starts its controller before its initial configuration finishes loading. Actual RG recreation exposed HTTP502 with a live controller while an OS-direct request to the same owned origin succeeded. Startup now waits for the allocated mixed listener to appear in `/configs`, then performs a synchronous reload of the same owned configuration before publishing either proxy path. In the pinned core, [initial startup](https://github.com/MetaCubeX/mihomo/blob/88dcbf7f1614a67c3b36b848ee3592dfa92ada36/hub/hub.go) starts control first; [configuration application](https://github.com/MetaCubeX/mihomo/blob/88dcbf7f1614a67c3b36b848ee3592dfa92ada36/hub/executor/executor.go) serializes loading and reaches `OnRunning` last. The existing synchronous [configuration API](https://github.com/MetaCubeX/mihomo/blob/88dcbf7f1614a67c3b36b848ee3592dfa92ada36/hub/route/configs.go) therefore provides the completion barrier. Checking the listener first prevents that reload from preceding initial application. There is no arbitrary startup sleep, new control API or automatic page replay. The device regression issues native/browser requests immediately after startup and recreation, before any test configuration reload can mask this race.

Android's `JSONObject` serializes optional slash escapes (`\/`), which this core's YAML file loader rejects. Startup and replacement files therefore remove only that optional escape while preserving paired backslashes, quotes and Unicode escapes. A regression test checks decoded JSON equivalence and idempotence, including node values containing backslash/slash sequences. Actual API32 validation rejected the original file and accepted a semantically identical normalized file; the corrected app then started and routed traffic.

The mixed listener binds only `127.0.0.1`; no LAN listener, VpnService, TUN or OS proxy setting is configured. Management is a filesystem Unix socket under the app-private files directory. Upstream Unix control does not enforce a bearer secret: isolation/authentication is the Android UID/filesystem boundary, not a fabricated HTTP-token claim. There is no TCP controller or webpage/native bridge. Subprocess diagnostics are discarded and the core log level is silent; node/config exceptions containing credentials are not copied into normal logs.

`RgProxyRuntime.openConnection(URL)` routes native HTTP(S) clients explicitly through the running loopback core, otherwise `Proxy.NO_PROXY`. It has finite connect/read timeouts and does not auto-follow redirects. #44/#45 clients must use that entry point, validate any redirect deliberately, disconnect in `finally`, and preserve credentials privately. It is not a global `ProxySelector` or whole-process guarantee.

Core startup failure leaves direct operation. Child exit clears the WebView override; normal foreground stop cancels health work, closes its control request and terminates the owned child. Rapid recreation waits for the preceding child before starting another. A WebView network error triggers a bounded control-readiness check; a dead controller clears proxy routing without automatically replaying the failed request. Retry/navigation remains an explicit user action.

Mihomo's redir-host resolver uses DNS servers obtained from the active Android network, with the OS resolver as fallback. There is no DNS listener or DNS interception. DNS uses existing OS connectivity; this does not promise remote-node DNS privacy or alteration of Tailscale/VPN routing. WebRTC, UDP/QUIC, mDNS discovery, arbitrary third-party native clients and unsupported browser flows are not qualified by an HTTP proxy flag. Native clients outside the explicit entry point remain outside the claim.

## Host evidence and preserved failures

All Gradle commands use JDK17/SDK35, the Manager-assigned host-build flock, `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`. Available host RAM was approximately 18–20GiB at the runs. The original 70 RG JVM tests, debug APK, instrumentation APK and lint task completed; native-core qualification raised the total to71, and the Android serialization regression raises it to72, all passed with the optional genuine host core enabled. Lint emitted the pre-existing AGP/Kotlin metadata incompatibility diagnostics; successful task completion is not an assertion that those diagnostics disappeared.

`MihomoHostTest` runs the unchanged official Linux ARM64 v1.19.32 core with the production `ProxyPolicy` and all-down adapter. Its binary SHA-256 is `c80df2ca5159567804da883401e0118908fc1155e05df2baf3dbd4d3ecfc3754`; compressed official asset SHA-256 is `9dd862e28b46ff7d775f169cceebc28deccaa0a9e804237d421cd2571e0caba0`. It starts two owned CONNECT nodes with deliberately different delays and TCP/TLS destinations that record actual peer addresses: `.1` DIRECT, `.2` first/slow source, `.3` second/fast source.

| Executed Linux case | Destination-observed peer |
| --- | --- |
| No sources | `127.0.0.1` |
| All sources OFF | `127.0.0.1` |
| Enabled source with empty usable set | `127.0.0.1` |
| Two healthy sources; second is faster | `127.0.0.3` |
| HTTPS with verified dummy test CA | `127.0.0.3` |
| Owned-origin controls before and after the public CN-IP probe | `127.0.0.3` each |
| Private IP DIRECT | `127.0.0.1` |
| Bundled China-domain match (`RuleSet`, `CN-DOMAIN`) | `127.0.0.1` |
| Tailnet-name match (`DomainSuffix`, `ts.net`) | `127.0.0.1` |
| IPv6 loopback DIRECT | `::1` |
| WebSocket upgrade/data | `127.0.0.3` |
| All enabled nodes unhealthy | `127.0.0.1` |
| First source recovers; second remains down | `127.0.0.2` |

The original nine cases passed in `native-host-02`; all twelve original routing cases passed in `native-host-05`, with 71 JVM tests total and zero failures/skips. The live matched-rule assertions distinguish China-domain and tailnet-name classification from incidental private-address routing. The earlier `native-host-01` had seven successful route observations followed by a failed all-down assertion: an immediate second provider-health call reused the positive result inside the core's one-second dedup window. Its original log, XML and destination observations are retained. The corrected control waits 1.1 seconds before an explicit subsequent health call; it does not change production health policy or hide the original failure. The later complete host run also records genuine per-node delay/alive and EGRESS snapshots (both healthy: first 183ms, second 18ms in `native-host-03`). Lint in `host-gates-03` also caught two missing `includeSubdomains` attributes in the new debug test XML. That original diagnostic is retained; the attributes are now explicit and `host-gates-04` and `host-gates-05` completed lint with zero errors/eight warnings, subject to the metadata diagnostic limitation above.

The test-only `route.test` and `health.test` hosts map to loopback. An explicit test-only `DOMAIN,route.test,EGRESS` rule exercises the proxy path despite that local mapping. Production private-address rules are separately tested. This proves actual transports/core-selection behavior, not real public-China-IP or external tailnet geography. The dummy TLS CA and private key are public test material; only debug builds trust that CA, scoped to `route.test`/`health.test`. Release builds retain normal system trust.

The additional `CnIpProbe` sends the fixed, non-secret [example.com A-record DoH query](https://223.5.5.5/dns-query?dns=AAABAAABAAAAAAAAB2V4YW1wbGUDY29tAAABAAE) through a real loopback CONNECT tunnel. The literal destination has no test host mapping or extra classification rule. Holding the tunnel before TLS captures its live `RuleSet / CN-IP` match and `DIRECT` chain from Mihomo, correlated to the tunnel's exact source port and destination. System certificate trust and HTTPS literal-IP endpoint identification then authenticate the response; DNS checks cover the query ID, question, response/opcode/rcode and positive answer count. The Linux run in `native-cn-ip-host-02` returned HTTP200 and a valid 72-byte response with two answers over TLSv1.3. Owned-origin requests immediately before and after observed the faster second node, with genuine two-node healthy/AUTO state retained. Actual dummy CONNECT receipts record both resolved-loopback controls and no `223.5.5.5:443` request.

This is a combined matched-rule, authenticated-response and dummy-node oracle. The public endpoint has no EyeBrowse-owned server logs; its response alone does not prove the route. The original owned-origin HTTP/HTTPS/WebSocket destination observations remain separate, and Linux success is not RG evidence. The first probe run (`native-cn-ip-host-01`) failed because an empty connection snapshot was JSON `null` before tracker creation. Its original XML/log/receipts are preserved. The bounded observers now treat that snapshot as empty and continue waiting; dummy receipts are checked against the core's actual resolved IP authorities. Partial rule/TLS/response evidence is retained if later probe checks fail.

At the `dc4d6a7` checkpoint, `host-cn-ip-gates-04` completed all71 RG JVM tests with zero failures/errors/skips, both APK tasks and lint. Its `native-cn-ip-host-04` evidence records fourteen owned-origin cases (the original twelve plus both healthy controls), the separate successful public CN-IP oracle and genuine health/fallback/recovery snapshots. At that checkpoint production inputs/APK were unchanged; subsequent API32 work changes configuration serialization and startup readiness. Earlier `da9d4dd`/`e6bf0ed`/`dc4d6a7` source/APK/check bindings remain separate.

During the granted RG window, `rg-window01/host-gates-03` passed all72 JVM cases, both APK tasks and lint. After the readiness correction, final `host-final-gates-12` again passed all72 cases with zero failures/errors/skips, both APK tasks and lint with zero errors/eight warnings, retaining the metadata analysis limitation. It completed in1m1s with78 tasks,14 executed and64 up-to-date. Its fresh `native-rg-window01-host-12` observations include fourteen owned-origin cases, the separate public CN-IP oracle and genuine health/fallback/recovery. No host gate is relabeled as device execution.

## Actual RG verification

The Manager granted an exclusive RG and owned external tailnet fixture window on2026-10-13. Physical serial/model were verified through the working primary transport before installation. The device is RG-glasses, Android API32, ARM64, provider `com.android.webview`95.0.4638.74. Normal `install -r` preserved app data. The extracted official core executed as the app UID. A live sample recorded exactly one core child of EyeBrowse, the mixed listener at127.0.0.1, no TCP controller, and the app-private filesystem Unix control socket. The device remained Awake/ON. Current reservation/transport addresses remain private operational records rather than reusable device instructions.

`MihomoRoutingInstrumentedTest` drives the production activity/runtime, genuine core and actual WebView. It saves controlled destinations, core health/rules, form snapshots and public probe observations to app-private `files/mihomo-test-results.json`. The owned fixture records actual origin peers and dummy CONNECT receipts. Final `rg-window01/instrumentation-correction09-10` completed terminal `OK (1 test)` in30.342s and JSON `PASS_EXERCISED_CASES`, with30 case records, nine fresh browser documents and78 owned-destination observations. There is no temporary debug-log sampler or diagnostic startup delay in this final source/test:

| Actual RG case | Observed result |
| --- | --- |
| Immediate no-profile startup | Native HTTP and fresh WebView HTTP/WS reached DIRECT peer127.0.0.1 before any test configuration reload |
| No sources, all OFF, empty usable set | Separate native and WebView HTTP/WS requests reached DIRECT peer127.0.0.1 |
| Both sources healthy; equal imported display names | Distinct hashed node identities, delays195/29ms, AUTO; faster second-source peer127.0.0.3 |
| Native HTTP/HTTPS | Owned destinations observed127.0.0.3 |
| WebView HTTP+WS, HTTPS+WSS | Unique completed documents, fetch and WebSocket data each observed127.0.0.3 |
| Private IPv4 / IPv6 | Owned destinations observed127.0.0.1 / ::1 |
| Controlled mainland-China domain / known tailnet name | Actual `RuleSet / CN-DOMAIN` and `DomainSuffix / ts.net`; owned DIRECT observations |
| Public literal mainland-China IP | Actual `RuleSet / CN-IP / DIRECT`, exact client/core source port46090, system-verified TLSv1.3, HTTP200 and valid72-byte DNS response with two answers |
| External owned tailnet, native and WebView | Actual `IPCIDR / 100.64.0.0/10 / DIRECT` plus two independent nonce-correlated server receipts |
| All enabled nodes unhealthy | Both genuine health states false, EGRESS DIRECT; new native and WebView requests observed127.0.0.1 |
| First source recovers, second remains down | Genuine AUTO resumes; new native and WebView requests observed127.0.0.2 |
| Live form | Same HTTPS URL and retained form through both selection changes |
| Ordinary recreation / stop | Immediate native/browser DIRECT requests succeeded after recreation; foreground close cleared native port and WebView override |
| Actual core child exit / restart | Forced exit of the test's actual child cleared both proxy ports; new native and WebView requests used DIRECT; normal recreation started a usable core and fresh browser request |

The public CN-IP probe had no test destination mapping or extra classification rule. Healthy faster-node owned-origin requests immediately before/after and two actual dummy-node receipts bracketed it; the public authority did not appear at either dummy proxy. This remains the combined rule/TLS/response/control oracle, without owned public server logs. External tailnet native nonce `19c8e493-5355-4264-af77-cf8f1793eb8d` and browser nonce `001c1484-d2bf-4e55-b658-ee602fae37ad` each matched the owned server's method/path/nonce/peer JSON. Their healthy controls reached the faster dummy node, with no external-authority dummy receipt. Both RG-local dummy egress and DIRECT can share a tailnet source address, so peer IP alone does not distinguish those routes. Unrelated WebView-provider CONNECT authorities observed in the test ledger are retained; the assertions identify the two controlled receipts by their exact authority.

The final installed APKs were read back and matched the retained files below. `compiled-inputs-correction09.json` records86 compiled source/build/asset Git-blob pins; the handoff compares these to the committed candidate. Documentation changes after compilation do not relabel earlier binaries.

| Final artifact | Bytes | SHA-256 |
| --- | --- | --- |
| RG debug APK | 35,338,490 | `f5cd2264701fd743b9cfbad877293fb8ea8d3139320ae0e1df3b93ef11d5323d` |
| RG instrumentation APK | 1,849,830 | `49495ed2f6bd274ba3a1bdf7579b8443d17125d849bcbb1f8bf2705e33eda7d4` |

Preserved device negatives in the same window are:

- The original retained APK/test failed before activity/core startup because API32's PKCS12 provider rejected the test keystore MAC key. Re-exporting the **public dummy test material only** with SHA1 MAC and PBE-SHA1-3DES preserved the certificate DER hash `86dd547deaa7e1d55a3a92473df21b066b3cfa5494ac34aa97d9837a1360cc56`. Actual API32 then loaded it. This is test-fixture compatibility, with no system provider/trust change; [OpenSSL's export options](https://docs.openssl.org/3.5/man1/openssl-pkcs12/) describe the encoding controls.
- The next run could not reach core readiness. Actual extracted-core `-t` rejected Android's original `\/` configuration with `yaml: found unknown escape character`, and accepted the normalized JSON with equal decoded values. The original file, stderr/stdout and corrected validation are retained; this was an observed serialization defect, not an inferred ABI blocker.
- The next run passed the routing probes but failed form retention because its HTTPS wait accepted datasets from the preceding HTTP document before navigation completed. That run's HTTPS/WSS label is not accepted as evidence. The driver now waits for a unique URL, complete document and fresh fetch/WebSocket results before editing, and records exact URL/form values through selection changes. The subsequent successful run verifies those values. Original partial JSON and destination traces remain intact.
- The earlier `instrumentation-correction03-04` passed21 bounded cases in27.729s, but did not test immediate first-use/recreated browsing. An expanded lifecycle run first used a test-only hostname after recreation had correctly removed its mapping; that driver error was corrected to a literal loopback origin. Later runs exposed the live-core502 defect with explicit native, browser and OS-direct controls. A temporary100ms debug-log observation made one diagnostic run pass; that delay was removed and is not evidence of a fix. The final run above tests the synchronous readiness correction without that delay. All original negatives and the bounded earlier pass remain separate.

Recovery-metadata preservation and cleanup have unresolved losses. Early runs omitted the repository's in-memory `local.tabs` save/restore pattern and could overwrite the original selected cold-recovery URL. Manager found no qualified pre43 backup; older device/agent metadata is not an exact substitute. Subsequent runs save and restore that key in memory without logging personal locations. During final cleanup, the Worker then attempted to remove one exact current-mission test entry while preserving an unrecognized entry. An incorrect piped-stdin `adb exec-out` write timed out and left `shared_prefs/LocalBrowserActivity.xml` empty. No prewrite byte copy or `.bak` exists. The unrecognized recovery entry and any other preferences in that file cannot be verified/restored. This additional cleanup error was promptly reported, and no guessed reconstruction, further preference write, data clear or cookie/form inspection followed. Neither loss is claimed as restored or passed cleanup.

The scoped resource return recorded the physical RG Awake/ON, no remaining owned app/core processes or TCP/TCP6/control sockets, and the exact owned external fixture stopped with its port bind-free. Own generated test configurations/results were removed after host preservation; rule files and other browser data were left in place. The resource-return record carries the preference-loss limitation. No device/fixture access follows return without a fresh Manager grant.

These device observations qualify the controlled HTTP paths described here. They do not expand DNS/unsupported-traffic claims, prove an arbitrary real subscription/node, or change OS/Tailscale/VPN configuration. Production profile import/UI and periodic rule updates remain #44/#45. Phone remains **REMOVED_FROM_SCOPE_NOT_PASSED**. Independent exact-candidate review and Manager acceptance remain separate.

Host rerun (the optional native qualification, including the public non-secret DoH request, is skipped unless the verified binary is explicitly supplied):

```sh
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
  ANDROID_HOME=/home/code2hack/Android/Sdk \
  EYEBROWSE_MIHOMO_HOST_BINARY=/absolute/path/to/verified/mihomo-linux-arm64-v1.19.32 \
  EYEBROWSE_MIHOMO_HOST_EVIDENCE=/absolute/mission-owned/evidence \
  flock /home/code2hack/.local/state/eyebrowse/v0.0.2/runs/run-001/host-build.lock \
  ./gradlew :app-rg:testDebugUnitTest :app-rg:assembleDebug \
  :app-rg:assembleDebugAndroidTest :app-rg:lintDebug \
  --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g
```

Only after an explicit exclusive grant, physical serial/model and current transport verification, install the exact recorded app/test APKs without clearing app data and execute the named instrumented test through the approved ADB route. Supply `-e tailnetOrigin http://<reserved-owned-fixture>:<port>`; the fixture serves `GET /route-probe/<nonsecretNonce>` with nonce/method/path/peer JSON and a retained corresponding server receipt. Preserve terminal JUnit, source/tree/APK bindings, actual API/provider versions, app-private JSON and listener/process observations. Stop only owned test operations and return the device under the current screen policy. External tailnet and public CN-IP evidence remain separate from the loopback test's deliberate mapping.
