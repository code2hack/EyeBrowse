# #57 partial renderer-instance repair

Worker: Worker-#57

**PARTIAL_REPAIR_HANDOFF_READY; browser integration: NOT_PASSED.** Do not adopt
this candidate. The Kotlin
repair fixes the demonstrated instance collision and permits the formerly
stalled cross-origin HTTP/WebSocket path and four-live-tabs case. It does not
resolve all browsing failures or restore Android isolated-UID separation.
This is a stable, reviewable partial-repair checkpoint, not whole-ticket
`CANDIDATE_READY`, acceptance, integration or release. Manager owns attempt
conclusions, independent review and the next Worker/Expert disposition.

## Source and input bindings

Branch: `work/issue-57-chromium-loader-repair`, based on
`8f7735e67aae39044b7758da7ac68dedda1b85e9`.
Current main/regression authority remains
`8929241f348bae9005b9b6bc1a93fbabd47145c1` (D3.1/H3.1/P3.1 and Kotlin ADR).
APP functional code is at
`83fe6e2b6a2328fdf2ad9a8739c266e92ef5a1a7`, tree
`06bef9202929df4625607ac7ab720b8b907ad63a`.
Final TEST sources are at
`951f56f77a3b19964e710fd94f97f554ca64e40f`, tree
`eb51d6f5b9ebede92afd95d381fc75cb4fcbad66`; that commit adds only an owned
JSON document diagnostic. Later result/provenance documentation does not
change compiled sources.

| Retained and installed artifact | SHA256 |
| --- | --- |
| APP | `e91bf3b7c6bf16e7da9f7763ce14c09f6065bb3426f5804e233c673887d3b940` |
| Final TEST | `9e8364ccd838800e4cfb389005b91f559a1ec701e4ad07f96b1fc65b41b45080` |
| Actual repaired loader runtime classes.jar | `294da201be8585a38f9978d90ab385ffa58302cfa1ed618d2ee9ef11e9a727f7` |
| Unchanged external engine153 APK | `dcb233a86d58ca922f8aa08dd4216c9158450866286d727a1358e502e935baa3` |
| Actual ARM64 engine ELF | `79fc039e9c48b1ff0f9693050e47e66252c4ad8437d69a0feb9f2cbd402aabe1` |

APP/TEST signing certificate is the qualified debug certificate
`e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963`.
Installed byte hashes matched retained, apksigner-verified artifacts before
each invocation and at return. Experimental APP/TEST UIDs were10076/10077.

The fixed engine is Cromite153.0.8010.37, not Google156. All62 original
WebViewUpgrade inputs remain verified at commit
`5a5a7d11b5e67bba37787a697f45c9f5d45e6dda`; original-to-Kotlin exclusions and
compilation are listed in the [replacement map](loader/replacement-map.md).
The repaired JAR is not the original JAR or a claimed published0.1.0 binary.
The engine/native archive remains external to Git and APP; redistribution and
GPL/component compliance are not qualified by this local experiment.

## Actual comparison

The same new API32 named-binding test and TEST
`778f028ad499fe6b7a115ddb42ea722c0598730801ec3a190dbe1e1d148a0694`
were used against the retained original APP and initial repaired APP.
The original control completed2 tests/1 failure: two names reached the same
Stub0/Binder, and the caller Intent was mutated. Its unrelated owned-service
control passed. The repaired control completed `OK (2 tests)`: distinct child
Binders, stable repeated binding, intact caller Intent and unrelated binding.
These exact assertions were repeated successfully on the final APP/TEST.

Actual child callbacks identify component/name/connection, PID, UID and slot.
Observed death preceded later slot reuse. Host controls additionally cover
multiple connections, unbind without death, death with live bindings, stale
callbacks, rejection, uncertain outcomes and explicit bounded exhaustion.
No modulo collision, tab reduction, single-process flag or sandbox-disabling
flag was introduced. Physical exhaustion/stress is not claimed.

| Executed verification | Result |
| --- | --- |
| Loader allocation/lifetime host contracts | 12 tests, zero failures/errors/skips |
| Experimental application JVM suite, genuine Linux Mihomo included | 94 tests, zero failures/errors/skips |
| Ordinary debug JVM suite, genuine Linux Mihomo included | 94 tests, zero failures/errors/skips |
| Both APP/TEST build modes and applicable lint | Completed; Kotlin2.2/analyzer2.0 metadata coverage limitation retained |
| Final focused bindings plus real modern-engine API case | `OK (3 tests)`,3.575s |
| Complete unchanged original routing class | 2 tests/1 failure,44.528s |
| Independent HTTPS/WSS first-origin case | 1 failure,25.161s |
| Full nine native browser cases plus recreation case | 10 tests/3 failures;7 passed |
| Owned JSON receipt diagnostic | `OK (1 test)`; literal body JSON and nonce parse correctly |
| Anonymous Paseo root, native address/Open | 1 failure,47.553s;88 observations, empty hidden LOADING document |
| Anonymous ChatGPT root, native address/Open | 1 failure,47.931s;89 observations, blank/null-origin hidden document |

The modern API case executes actual renderer `toReversed`,
`AbortSignal.throwIfAborted`, `:has()`, container queries and `structuredClone`.
There are no UA substitutions or polyfills. Original source assertions and
waits were retained throughout.

In the native suite, four live tabs now pass. Address keys, field reveal,
Unicode/selection/case/Enter/Done, D-pad admission, bookmarks and controlled
recreation also pass. Geometry-case link/history and hidden-page HTTP404
replacement callbacks still fail. The ordinary HTTP404 history/refresh/address
case, which passed in #55, failed in this ten-case invocation at
`current ordinary main-frame HTTP404`; this regression is retained and its
cause is not isolated. Three correctly bound keyboard cases pass; the geometry
wrapper remains failed. Missing arguments were not disguised as keyboard results.

Routing now loads loopback HTTP/WebSocket and then `route.test` HTTP/WebSocket,
including empty/all-OFF/empty-pool cases and genuine native proxy/AUTO/China/
private/local/IPv6 controls. It later fails at
`external tailnet browser destination receipt` (line229/await383).
The native tailnet receipt passes; the owned server also recorded the browser
request. The same-origin empty-pool JSON diagnostic proves literal parsing
works, falsifying the tentative JSON-viewer explanation. No fixture MIME or
protocol was changed to force a pass. Later HTTPS/WSS, forms and recovery
sections behind that assertion aborted and remain unpassed.

The independent TLS case still observes native HTTPS through the actual core
with DIRECT, but the browser remains `about:` and sends no fixture page request.
Scoped Chromium errors include `net_error -202`; no certificate bypass, global
trust change or assertion relaxation was attempted. Public-root failures and
remaining navigation callbacks have not been proved to share that cause.
Observed child termination/error messages are retained; no intentional renderer
crash/recovery acceptance test was performed or claimed.

## Security, resources and preservation

Physical target:1906092617103125/RG-glasses/user0, Android12/API32, ARM64,
with system WebView95 unchanged. Main and renderer executable maps independently
showed the pinned ELF (same inode/path and qualified digest).
Both were UID10076 with SELinux `untrusted_app`; the child could read an owned
dummy private file. **Android isolated-UID separation is not restored.**
Changing the manifest alone does not qualify private APK/native/asset access
for an isolated UID. No Owner private profile data was used to test this limit.

A live routing snapshot measured main/renderer/core PSS at
146,024/101,592/82,686KiB. These are bounded samples, not CPU percentages,
endurance, optical or production memory/startup guarantees. Startup reports
retain measured input-verification/startup times; filesystem caches were not
cold-boot controlled.

Original installed APP UID10073/hash
`d6a9145be20a2e671381d06b60ba8318db6bf1ae6089fab76c7c29a27a6a9952`
and system95/hash
`54401e4da5ae93fe152b48e38771c734558e6dd10addb927e0d03e8e4586994e`
were unchanged at return. Original private cookies, profiles, credentials,
bookmarks, tabs and preferences were not inspected, copied or mutated.
No real account sign-in, pairing/offer import or personal-session operation
was performed. No possible personal links were observed in the anonymous
checks. No account compatibility, optical or Phone PASS is implied.

RG was returned at13:20:17UTC, Awake/displayON, with no owned UID/prefix
processes or observed owned TCP/TCP6/Unix entries. Only the experimental APP
was force-stopped. Exact host fixture PID2100258 was stopped and39030
verified free at13:22:01UTC. No RG or39030 work followed that return.
Necessary process/executor/socket teardown is complete; new source, evidence,
staging files, experimental APP/TEST/data, c57 and workspace cleanup remains
deferred to Manager's repair-end boundary. No selfarchive occurred.

## History, evidence and handback

[All original #55 negative results](results.md) remain unchanged. Initial
compile redeclaration and pre-install digest-check failures were setup cutoffs,
not hidden device passes. Two ADB streams truncated; actual failed TestRunner
outcomes were recovered separately, and original incomplete terminals remain
retained. An unsupported instrumentation dump query was not treated as cessation
evidence. Prior operation settlement used actual owned-process absence before
any repeat.

Owner supplied41877, which was physically verified and used. Only stale exact
host transports were reconnected; ADB server/adbd/listeners/network/provider/
trust were not restarted or altered. Later ordinary `am instrument` commands
used unique device terminal/exit files under a bounded nohup/timeout process;
launcher PIDs, exact arguments, completion and full JUnit terminals are retained.
This changed capture mechanics, not assertions or product retry accounting.

The last TEST adds only the owned JSON diagnostic; unchanged class sources
and identical APP bytes are explicitly reused where earlier executions are
reported. Host logs likewise retain executed versus up-to-date tasks. The final
APP host suite ran at83fe6e2; TEST-only951f56f compiled/linted and was physically
verified. Ordinary debug checks used unchanged ordinary sources separately.
No all-cold-cache or all-new-execution claim is made.

Raw invocation manifests, source/APK/JAR/input inventories, terminal streams,
71 task-generated report/PNG files, process/maps/PSS snapshots and scoped return
records are retained in assigned private evidence outside this removable
worktree. Manager receives its exact path, final remote head/tree and evidence
index. Simplification kept the pinned engine delegate and remaining vendor Java
unchanged, replaced six classes in Kotlin and confined bookkeeping to the loader;
no general updater/plugin or styling redesign was added.

The confirmed identity defect is repaired. Complete functional browser repair,
security/adoption and account acceptance remain **NOT_PASSED/NOT_RUN** as scoped
above. This handback does not conclude canonical counters or authorize merge,
release, cleanup or a new source-writing assignment. It does not cancel or
complete the Owner's repair objective. Window03 has ended; this Worker is
host-only for finalization, then idle/read-only pending Manager disposition.
