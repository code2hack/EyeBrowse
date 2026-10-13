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
| Android qualification | **Pending actual RG/API32 execution**, including extracted-file execution and WebView provider feature support. A compiler target is not a device-load result. |

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

The mixed listener binds only `127.0.0.1`; no LAN listener, VpnService, TUN or OS proxy setting is configured. Management is a filesystem Unix socket under the app-private files directory. Upstream Unix control does not enforce a bearer secret: isolation/authentication is the Android UID/filesystem boundary, not a fabricated HTTP-token claim. There is no TCP controller or webpage/native bridge. Subprocess diagnostics are discarded and the core log level is silent; node/config exceptions containing credentials are not copied into normal logs.

`RgProxyRuntime.openConnection(URL)` routes native HTTP(S) clients explicitly through the running loopback core, otherwise `Proxy.NO_PROXY`. It has finite connect/read timeouts and does not auto-follow redirects. #44/#45 clients must use that entry point, validate any redirect deliberately, disconnect in `finally`, and preserve credentials privately. It is not a global `ProxySelector` or whole-process guarantee.

Core startup failure leaves direct operation. Child exit clears the WebView override; normal foreground stop cancels health work, closes its control request and terminates the owned child. Rapid recreation waits for the preceding child before starting another. A WebView network error triggers a bounded control-readiness check; a dead controller clears proxy routing without automatically replaying the failed request. Retry/navigation remains an explicit user action.

Mihomo's redir-host resolver uses DNS servers obtained from the active Android network, with the OS resolver as fallback. There is no DNS listener or DNS interception. DNS uses existing OS connectivity; this does not promise remote-node DNS privacy or alteration of Tailscale/VPN routing. WebRTC, UDP/QUIC, mDNS discovery, arbitrary third-party native clients and unsupported browser flows are not qualified by an HTTP proxy flag. Native clients outside the explicit entry point remain outside the claim.

## Host evidence and preserved failures

All Gradle commands use JDK17/SDK35, the Manager-assigned host-build flock, `--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g`. Available host RAM was approximately 19–20GiB at the runs. The original 70 RG JVM tests, debug APK, instrumentation APK and lint task completed; the later native-core qualification raises the total to 71, all passed. Lint emitted the pre-existing AGP/Kotlin metadata incompatibility diagnostics; successful task completion is not an assertion that those diagnostics disappeared.

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

After simplification and exact tunnel-source correlation, `host-cn-ip-gates-04` completed all 71 RG JVM tests with zero failures/errors/skips, both APK tasks and lint. Its `native-cn-ip-host-04` evidence records fourteen owned-origin cases (the original twelve plus both healthy controls), the separate successful public CN-IP oracle and genuine health/fallback/recovery snapshots. Lint still reports zero errors/eight warnings with the existing Kotlin metadata analysis limitation. Production app inputs and its APK are unchanged; the instrumentation APK contains the new shared probe. Earlier `da9d4dd`/`e6bf0ed` evidence and APKs retain their original source attribution.

## RG verification pending exclusive handoff

`MihomoRoutingInstrumentedTest` is compiled for the actual RG. It checks the exact core version, actual provider `PROXY_OVERRIDE`, native HTTP/HTTPS, real WebView HTTP/HTTPS and WS/WSS, the enabled-source union, private IP, known tailnet name and bundled China domain DIRECT, the public CN-IP combined oracle above, the four distinct fallback states, recovery, retained live form state, ordinary recreation and cleared foreground routing. It saves controlled test destinations/health records and the public probe's rule/TLS/response/node receipts to app-private `files/mihomo-test-results.json`.

**No RG or external fixture query/mutation has been performed for this candidate.** API32 load, actual RG provider behavior and device destinations are **NOT YET VERIFIED**. Actual tailnet DIRECT evidence requires the Manager-reserved owned external fixture and its request/observation contract. Current reservations, transport addresses and queue position remain in the private run records; they are not reusable instructions in this document. The public CN-IP probe is qualified on Linux only; execution of its combined rule/TLS/node oracle on actual RG remains pending. These limits are not converted into PASS by YAML, Linux loading, a compiled APK or bitmap evidence.

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

Only after an explicit exclusive grant, physical serial/model and current transport verification, install the exact recorded app/test APKs without clearing app data and execute the named instrumented test through the approved ADB route. Preserve the terminal JUnit result, source/tree/APK hashes, actual API/provider versions, app-private test JSON, listener/process observations and required screen cleanup. External tailnet and controlled CN-IP evidence remain separate from the loopback test's deliberate mapping.
