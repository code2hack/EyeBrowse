package com.code2hack.eyebrowse.rg.chromium

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.ResultReceiver
import dalvik.system.PathClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bounded feasibility probe, separate from the renderer allocator and its five live slots. */
class IsolatedEngineProbeService : Service() {
    private val descriptors = mutableListOf<ParcelFileDescriptor>()

    @Suppress("DEPRECATION")
    override fun onBind(intent: Intent): IBinder {
        val receiver = checkNotNull(intent.getParcelableExtra<ResultReceiver>("result"))
        val report = JSONObject().put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("privateDummyReadable", runCatching {
                File(filesDir, "chromium-boundary-probe.txt").readText() == "i57-owned-dummy"
            }.getOrDefault(false))
            .put("selinux", File("/proc/self/attr/current").readText().substringBefore('\u0000').trim())
        try {
            val archive = checkNotNull(intent.getParcelableExtra<ParcelFileDescriptor>("archive"))
                .also { descriptors += it }
            val library = checkNotNull(intent.getParcelableExtra<ParcelFileDescriptor>("library"))
                .also { descriptors += it }
            val archivePath = "/proc/self/fd/${archive.fd}"
            val libraryPath = "/proc/self/fd/${library.fd}"
            report.put("archiveFdReadable", File(archivePath).inputStream().use { it.read() >= 0 })
            val loader = PathClassLoader(archivePath, "/proc/self/fd", classLoader.parent)
            val engineClass = loader.loadClass("org.chromium.content.app.SandboxedProcessService0")
            report.put("engineClass", engineClass.name)
            // Keep the engine's own defining loader for JNI registration and native namespace.
            val load = Runtime::class.java.getDeclaredMethod("load0", Class::class.java, String::class.java)
            load.isAccessible = true
            load.invoke(Runtime.getRuntime(), engineClass, libraryPath)
            report.put("nativeLoadReturned", true)
        } catch (failure: Throwable) {
            report.put("failure", (failure.cause ?: failure).toString())
        }
        report.put("engineMappings", JSONArray(File("/proc/self/maps").readLines().filter {
            it.contains("libwebviewchromium.so") || it.contains("chromium-engine/engine.apk")
        }))
        receiver.send(0, Bundle().apply { putString("report", report.toString()) })
        return Binder()
    }

    override fun onDestroy() {
        descriptors.forEach { it.close() }
        super.onDestroy()
        Process.killProcess(Process.myPid()) // This probe owns only this isolated process.
    }
}
