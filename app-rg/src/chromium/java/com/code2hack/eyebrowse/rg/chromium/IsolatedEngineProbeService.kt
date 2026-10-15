package com.code2hack.eyebrowse.rg.chromium

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import dalvik.system.PathClassLoader
import dalvik.system.BaseDexClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Bounded feasibility probe, separate from the renderer allocator and its five live slots. */
class IsolatedEngineProbeService : Service() {
    private val descriptors = mutableListOf<ParcelFileDescriptor>()

    override fun onBind(intent: Intent): IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(IsolatedEngineProbeService::class.java.name)
            check(getCallingUid() == applicationInfo.uid) { "Only the owning app may supply engine descriptors" }
            val archive = checkNotNull(data.readFileDescriptor()).also { descriptors += it }
            val library = checkNotNull(data.readFileDescriptor()).also { descriptors += it }
            reply!!.writeNoException()
            reply.writeString(probe(archive, library).toString())
            return true
        }
    }

    private fun probe(archive: ParcelFileDescriptor, library: ParcelFileDescriptor): JSONObject {
        val report = JSONObject().put("pid", Process.myPid()).put("uid", Process.myUid())
            .put("privateDummyReadable", runCatching {
                File(filesDir, "chromium-boundary-probe.txt").readText() == "i57-owned-dummy"
            }.getOrDefault(false))
            .put("selinux", File("/proc/self/attr/current").readText().substringBefore('\u0000').trim())
        try {
            val archivePath = "/proc/self/fd/${archive.fd}"
            val libraryPath = "/proc/self/fd/${library.fd}"
            report.put("archiveFdReadable", File(archivePath).inputStream().use { it.read() >= 0 })
            val loader = PathClassLoader(archivePath, "/proc/self/fd", classLoader.parent)
            // Use the same defining loader for the public System.load caller and engine JNI.
            // These are the loader's existing dex-element fields, not a hidden native-load API.
            val pathList = BaseDexClassLoader::class.java.getDeclaredField("pathList").apply { isAccessible = true }
            val from = pathList.get(loader)
            val to = pathList.get(classLoader)
            val elements = from.javaClass.getDeclaredField("dexElements").apply { isAccessible = true }
            val engineElements = elements.get(from) as Array<*>
            val appElements = elements.get(to) as Array<*>
            val combined = java.lang.reflect.Array.newInstance(engineElements.javaClass.componentType!!,
                engineElements.size + appElements.size)
            System.arraycopy(engineElements, 0, combined, 0, engineElements.size)
            System.arraycopy(appElements, 0, combined, engineElements.size, appElements.size)
            elements.set(to, combined)
            val engineClass = classLoader.loadClass("org.chromium.content.app.SandboxedProcessService0")
            report.put("engineClass", engineClass.name)
            System.load(libraryPath)
            report.put("nativeLoadReturned", true)
        } catch (failure: Throwable) {
            report.put("failure", (failure.cause ?: failure).toString())
        }
        report.put("engineMappings", JSONArray(File("/proc/self/maps").readLines().filter {
            it.contains("libwebviewchromium.so") || it.contains("chromium-engine/engine.apk")
        }))
        return report
    }

    override fun onDestroy() {
        descriptors.forEach { it.close() }
        super.onDestroy()
        Process.killProcess(Process.myPid()) // This probe owns only this isolated process.
    }
}
