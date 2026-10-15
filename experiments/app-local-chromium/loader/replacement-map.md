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
| `sandbox/SandboxedProcessServiceDelegate.java` | Excluded; replaced by the same-name Kotlin delegate using private Binder/read-only engine FDs and API30 resources |
| All other pinned sources, including `BindServiceArgsAdapter` | Unchanged vendor Java; the old argument adapter remains compiled but the Kotlin hook does not call it |
| Original manifest | Verified as input, replaced by the five non-exported isolated services in the maintained manifest |

`RendererInstances.kt` is new Kotlin lifetime bookkeeping, tested independently
of Android. The API32 hook preserves `(component, instanceName)`, copies the
caller's Intent, redirects only the selected engine's renderer services and
passes unrelated calls to the original AM. It removes `instanceName` only after
assigning a unique service slot. Its fixed pool rejects exhaustion
explicitly rather than aliasing a live renderer or lowering the browser tab limit.

A connection token is a set member, matching Android unbind semantics: repeating
the same binding does not require additional unbind calls. A child sends a
process-lifetime Binder through the private loader-added ResultReceiver. A slot
is reusable only after all tracked connections are unbound and that actual
process Binder dies. `onDestroy` alone does not release it. Unknown bind outcomes
or a child that never reports its token conservatively retain the slot. Chromium's
existing destroy/exit path is retained. No sandbox-disabling or single-process
flags are added.

The five stubs now declare Android isolated processes. A private Binder capability
returns only the pinned APK and two native libraries as read-only descriptors;
the child supplies no path. It accepts only a same-user isolated caller. The
engine and a tiny native-load bridge share one defining loader, which resolves
only the qualified library descriptors. Public resource-loader APIs provide the
engine assets without granting access to other app-private files.

The allocator still waits for the actual child process Binder to die, retains
connections across repeated binds, and fails closed on exhaustion. These source
declarations are not an observed renderer-isolation PASS. Required native runs
must independently bind UID, SELinux, dummy private-file denial, pinned ELF and
browser behavior. The preceding isolated-service FD probe is separate from real
renderer acceptance. Existing ordinary-UID and failed setup evidence remains
unchanged. No engine/native input is packaged or modified by this correction.
