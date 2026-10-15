# #55 controlled experiment results

**Recommendation: do not adopt this pinned loader/engine combination.** A genuine
newer native engine can initialize inside an isolated EyeBrowse package and run
the previously missing APIs. It did not satisfy the existing browser integration
checks. This is a negative feasibility recommendation for the tested candidate,
not a claim that every possible app-local Chromium approach is impossible.

No production merge, release, account/session acceptance or todo disposition is
implied. The Manager owns independent review and those decisions.

## Candidate and input binding

The implementation baseline is
`8929241f348bae9005b9b6bc1a93fbabd47145c1`. Functional source through the final
anonymous-site checks is
`6a7c725efea6279caf26bcbc00de38ba1541ea93`
(tree `4b03a07ccb8b821199dc2d5cd57f6b82d218a67d`), on
`work/issue-55-chromium-spike`. Later results/evidence documentation does not
change the APK sources. Exact final Git and evidence bindings are supplied in
the retained candidate manifest to the Manager.

| Input/artifact | SHA256 |
| --- | --- |
| External Cromite153.0.8010.37 complete APK | `dcb233a86d58ca922f8aa08dd4216c9158450866286d727a1358e502e935baa3` |
| Actual ARM64 `libwebviewchromium.so` | `79fc039e9c48b1ff0f9693050e47e66252c4ad8437d69a0feb9f2cbd402aabe1` |
| Experimental APP, 35,649,121 bytes | `2f4c53a8d3b0da2645c71c61aff20aabcef4e2e7c4cfc6be44be159466446688` |
| Final TEST, 2,744,572 bytes | `c7f1777b6f434bf9f60c67960c63dcd674d077c2ff69b4c06925412e322e7ebe` |

[inputs.json](inputs.json) records original upstream provenance, version, SDK,
ABI, signer and licenses. WebViewUpgrade source is pinned to
`5a5a7d11b5e67bba37787a697f45c9f5d45e6dda`; all62 source/manifest inputs are
checked before compiling. No published0.1.0 binary equivalence is assumed.
The engine is an external input and is absent from the repository and APP.
Cromite153 is distinct from the reported Google156 staged release.

Both experimental APKs verify with the expected debug certificate SHA256
`e372f80243affb0fd2462d4c90f8aede64a9ffb683cebad6c517d73eacc6a963`.
The installed package hashes matched their retained host artifacts before
invocation and at return. Package/UIDs are:

| Package | Actual UID |
| --- | --- |
| Original `com.code2hack.eyebrowse.rg` | 10073 |
| Experimental `com.code2hack.eyebrowse.rg.chromium` | 10074 |
| Experimental TEST `com.code2hack.eyebrowse.rg.chromium.test` | 10075 |

## Observations and boundaries

The reserved physical RG was serial1906092617103125, RG-glasses, Android12/API32,
ARM64, user0. A fresh experimental process loaded153.0.8010.37 with executable
maps of the pinned extracted library, not only a changed provider/UA string.
Actual renderer JavaScript passed `toReversed`, `AbortSignal.throwIfAborted`,
CSS `:has()`, container queries and `structuredClone`, without polyfills.

Main and renderer mappings were independently read under the experimental UID.
Both processes actually had UID10074; the Mihomo child also had that UID. The
upstream's five ordinary child-process stubs **do not preserve Android
isolated-UID renderer sandboxing**. Other possible native sandbox mechanisms
were not qualified by this observation.

One corrected cold-process initialization took6,094ms, including1,511ms input
verification, with main PSS77,153KiB. Filesystem cache was not cold-boot controlled.
A later real regression-workload snapshot recorded main/renderer/core PSS
134,470/102,467/73,677KiB respectively. These are bounded samples, not CPU
percentages, endurance, startup guarantees or production memory qualification.

| Executed check | Actual outcome |
| --- | --- |
| Ordinary debug and experimental host JVM suites | Each94 tests, zero failures/skips, including the hash-qualified genuine Linux Mihomo test |
| APP/TEST compilation and applicable lint | Completed; existing Kotlin2.2/analyzer2.0 metadata diagnostics remain, so full semantic lint coverage is not claimed |
| Native engine/API probe | `OK (1 test)` in3.495s; repeated successfully in the later native suite |
| Initial full two-method routing class | FAILED2/2 in30.797s; original stream preserved |
| Full class after preference lookup correction | FAILED1/2 in30.666s; preservation case passed, cross-origin case still failed |
| Full class on later bound TEST/source | FAILED1/2 in31.025s; same split, assertions/waits unchanged |
| Initial native/control suite | Six passes/six failures out of12; four failures were missing invocation/source arguments before their bodies, two were actual browser failures |
| Four keyboard cases with correct run/source arguments | Three passes/one failure in41.624s; original setup failures retained separately |
| Independent first-origin HTTPS/WSS case | FAILED1/1 in25.935s; native HTTPS/DIRECT controls completed, browser document/WSS did not |
| Anonymous Paseo root through native address/Open | FAILED1/1 in47.326s;87 observations stayed LOADING/hidden; empty body at the HTTPS origin |
| Anonymous ChatGPT root through native address/Open | FAILED1/1 in47.418s;87 observations stayed LOADING/hidden; document remained blank/origin null |

The passing keyboard cases exercised real Unicode, selection, caret movement,
Backspace, case/symbols, Enter and Done, plus address entry and field reveal.
The measured native key-dispatch maxima were3–5ms in those cases; this is not
an end-to-end typing latency or physical ergonomics claim. Bookmarks, native
D-pad admission, explicit HTTP-error controls and controlled Activity recreation
also passed in the native suite.

The persistent failures include the four-live-tabs case, normal link/history
navigation inside the geometry case, and a replacement-page JavaScript callback
after HTTP404 in the hidden-page recovery case. Earlier progress shorthand
called these “renderer recovery”; the source-bound failures do **not** establish
an actual renderer crash/termination test. No assertion was relaxed to pass them.

The routing case successfully loaded the initial loopback HTTP page and its
WebSocket, then stalled on navigation to `route.test`, retaining the earlier
document while the production state stayed LOADING. The genuine core was
Running and native/WebView configured ports matched. The independent HTTPS case
observed a real native HTTPS connection through the mixed listener, matched
`IPCIDR 127.0.0.0/8` with DIRECT and a destination receipt; its WebView remained
`about:` and the fixture received no browser page request. Later routing/pool,
HTTPS/WSS and recovery sections aborted behind these failures and are not passed.
Linux core evidence does not waive those Android browser gaps.

Retained loader logs show distinct Chromium instance names0/1 directed to the
same stub0. The pinned `ActivityManagerHook` derives its stub index from the
service class and drops the instance name when adapting to ordinary
`bindService`. This is a source-supported allocation hypothesis. The experiment
does not prove it is the sole cause of the observed navigation failures. There
was no loader patch, isolation-disabling flag or system/provider workaround.

## Failure history and evidence reuse

The missing-engine negative cold start failed closed and left no main process.
The first real-engine launch failed the initial native-map guard because its
literal `/data/user/0` path differed from the mapped `/data/data` alias. That
failure remains retained; canonical-path comparison then verified the executable
mapping. An original-APK copy over ADB timed out90s; the partial copy was retained
and device-side hashes plus the matching retained original APK supplied the
binding. These are separate observations, not concealed product passes.

The initial routing preservation failure was a test filename assumption:
`Activity.getPreferences()` follows `getLocalClassName()`, which becomes a full
class name when applicationId and namespace differ. Instrumentation now follows
that existing rule for both packages. No production persistence or migration
changed, and the original restoration assertions passed after correction.

The corrected native keyboard run used source
`960e216438ddf868694b12d04b5d6c50a77ed68d` and TEST
`32e7d9a55b3f2cfbec66510ba74b8acb376efd706e6a19cd6890ec120835e4dd`.
The TLS/final full-routing checks used
`eb8c0241c5865f5cb2afd67ae605cd2b99bd917a` and TEST
`3be25d4a5dd7b0718eb90a5c5743867f2c1fa6ea039bd482bcc4fd79955fdcbd`.
The anonymous native cases used6a7c725 and the final TEST shown above. The APP
hash stayed unchanged across these runs. Later commits added only isolated test
cases/documentation; unaffected earlier cases and unchanged JVM sources are
explicitly reused, not described as newly executed on every later Git head.

Two earlier debugging-transport attempts timed out at `Page.enable` **before**
public-site navigation. They are transport setup failures, not site results.
Both owned forwards were removed. The subsequent native address/Open checks
provide the actual site observations above; retained480×640 frames show the
real HUD and Loading state. No anonymous welcome, pairing, personal conversation
or authenticated session success was demonstrated or claimed.

## Preservation and return

Original installed APP SHA256 remained
`d6a9145be20a2e671381d06b60ba8318db6bf1ae6089fab76c7c29a27a6a9952`, UID10073,
version0.0.3/code3. System WebView remained95.0.4638.74 with APK SHA256
`54401e4da5ae93fe152b48e38771c734558e6dd10addb927e0d03e8e4586994e`.
No original private cookie/token/profile/tab values were inspected, copied,
cleared or restored. Live Owner authentication was not tested.

The owned host39030 fixture was stopped and returned separately at08:03:57UTC;
after Manager reconciliation it was not queried or restarted. RG was returned
at08:15:19UTC: experimental UID processes/instrumentation and observed owned
TCP/TCP6/Unix listeners absent, actual Awake/displayON, original/system hashes
unchanged. Only the experimental package was force-stopped. Rokid's power dump
required a separate display-controller read; its initial partial verification
record was preserved along with the final successful return record.

The Manager's no-real-account boundary remains in force: no Owner sign-in,
Paseo pairing/offer import, credentials or personal conversation operations.
Account/session checks are NOT_RUN pending a later explicit disposition. The
known forced-black Paseo menu-bar styling defect remains distinct and unfixed;
ChatGPT's prior stylesheet cause was not isolated. Optical, Phone and historical
data-loss dispositions were not changed.

Raw terminals, source/APK/input manifests, signer checks, scoped native maps,
process/PSS snapshots and anonymous frames are retained in the assigned private
run evidence outside the removable worktree. Scratch, device staging files,
experimental app/data and workspace cleanup are deferred until the Manager's
experiment-end boundary; source and required evidence remain preserved.
