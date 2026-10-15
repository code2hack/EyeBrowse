# Maintained loader repair (#57)

Upstream: JonaNorman/WebViewUpgrade commit
`5a5a7d11b5e67bba37787a697f45c9f5d45e6dda`, Apache-2.0. All 62 original
source/manifest pins in `source.sha256` are still verified before compilation;
the external extraction is never edited. Its LICENSE and copyright notices
remain retained and packaged by the existing experimental application wiring.

| Original source under `core/src/main/java/com/norman/webviewup/lib/` | Compilation |
| --- | --- |
| `hook/ActivityManagerHook.java` | Excluded; replaced by `src/main/kotlin/com/norman/webviewup/lib/hook/ActivityManagerHook.kt` |
| `sandbox/stub/StubSandboxedProcessService0.java` through `StubSandboxedProcessService4.java` | Excluded; replaced by the five same-name classes in `src/main/kotlin/com/norman/webviewup/lib/sandbox/stub/StubSandboxedProcessServices.kt` |
| All other pinned sources, including `SandboxedProcessServiceDelegate` and `BindServiceArgsAdapter` | Unchanged vendor Java; the old argument adapter remains compiled but the Kotlin hook does not call it |
| Original manifest | Unchanged five ordinary child-process services; no isolated UID claim |

`RendererInstances.kt` is new Kotlin lifetime bookkeeping, tested independently
of Android. The API32 hook preserves `(component, instanceName)`, copies the
caller's Intent, redirects only the selected engine's renderer services and
passes unrelated calls to the original AM. It removes `instanceName` only after
assigning a unique ordinary service slot. Its fixed pool rejects exhaustion
explicitly rather than aliasing a live renderer or lowering the browser tab limit.

A connection token is a set member, matching Android unbind semantics: repeating
the same binding does not require additional unbind calls. A child sends a
process-lifetime Binder through the private loader-added ResultReceiver. A slot
is reusable only after all tracked connections are unbound and that actual
process Binder dies. `onDestroy` alone does not release it. Unknown bind outcomes
or a child that never reports its token conservatively retain the slot. Chromium's
existing destroy/exit path is retained. No sandbox-disabling or single-process
flags are added.

The five stubs still run with the experimental host UID. This repair does not
make the private external APK/native paths accessible to an Android isolated
UID; merely adding `isolatedProcess` would fail that boundary. Loading external
code through an isolated UID requires separately qualified code/native/asset
transport and startup support. Do not treat functional results as production
or account-use approval. Actual ELF/process/UID and classes.jar hashes belong
in the candidate evidence; a repaired runtime JAR is not the original JAR.
