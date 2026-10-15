/* Kotlin replacements of WebViewUpgrade's five Apache-2.0 stub services at 5a5a7d11.
 * The maintained Kotlin delegate uses read-only engine FDs. See replacement-map.md. */
package com.norman.webviewup.lib.sandbox.stub

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.ResultReceiver
import android.util.Log
import com.norman.webviewup.lib.hook.ActivityManagerHook
import com.norman.webviewup.lib.sandbox.SandboxedProcessServiceDelegate
import java.io.File

abstract class RendererStubService : Service() {
    private val delegate = SandboxedProcessServiceDelegate()
    override fun onCreate() { super.onCreate(); delegate.onCreate(this, applicationContext) }
    @Suppress("DEPRECATION")
    override fun onBind(intent: Intent): IBinder? {
        val receiver = intent.getParcelableExtra<ResultReceiver>(ActivityManagerHook.CHILD_RECEIVER)
        val identity = Bundle().apply {
            putBinder(ActivityManagerHook.CHILD_TOKEN, ProcessLifetime.token)
            putInt("pid", Process.myPid()); putInt("uid", Process.myUid())
            putBoolean("privateProbeReadable", runCatching {
                File(filesDir, "chromium-boundary-probe.txt").readText() == "i57-owned-dummy"
            }.getOrDefault(false))
            putString("selinux", runCatching {
                File("/proc/self/attr/current").readText().substringBefore('\u0000').trim()
            }.getOrNull())
        }
        receiver?.send(0, identity)
        Log.i(ActivityManagerHook.TAG, "SERVICE component=${javaClass.name} pid=${Process.myPid()} uid=${Process.myUid()}")
        return try {
            delegate.onBind(intent).also {
                receiver?.send(1, Bundle(identity).apply {
                    putString("nativeMappings", File("/proc/self/maps").readLines()
                        .filter { line -> line.contains("libwebviewchromium.so") }.take(5).joinToString("\n"))
                })
            }
        } catch (failure: Throwable) {
            receiver?.send(2, Bundle(identity).apply { putString("failure", failure.stackTraceToString().take(3000)) })
            Process.killProcess(Process.myPid()) // Failed bootstrap owns no usable renderer; preserve the failure.
            throw failure
        }
    }
    override fun onRebind(intent: Intent) { super.onRebind(intent); delegate.onRebind(intent) }
    override fun onDestroy() {
        // Chromium owns termination. The allocator waits for Binder death, never this callback alone.
        try { delegate.onDestroy() } finally { super.onDestroy() }
    }
}

private object ProcessLifetime { val token = Binder() }
class StubSandboxedProcessService0 : RendererStubService()
class StubSandboxedProcessService1 : RendererStubService()
class StubSandboxedProcessService2 : RendererStubService()
class StubSandboxedProcessService3 : RendererStubService()
class StubSandboxedProcessService4 : RendererStubService()
