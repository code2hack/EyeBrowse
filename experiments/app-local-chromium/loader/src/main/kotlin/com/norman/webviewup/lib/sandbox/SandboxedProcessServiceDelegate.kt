/* Narrow Kotlin replacement of WebViewUpgrade's Apache-2.0 SandboxedProcessServiceDelegate
 * at 5a5a7d11. Original source/license stays pinned; see replacement-map.md. */
package com.norman.webviewup.lib.sandbox

import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.IBinder
import android.os.ParcelFileDescriptor
import dalvik.system.PathClassLoader
import java.lang.ref.WeakReference
import java.io.File

/** API32 isolated renderer: code arrives as read-only capabilities, never ambient private paths. */
class SandboxedProcessServiceDelegate {
    private lateinit var host: Service
    private var real: Service? = null
    private var files = emptyMap<String, ParcelFileDescriptor>()
    private var provider: ResourcesProvider? = null
    private var resources: Resources? = null
    private var resourceLoader: ResourcesLoader? = null

    fun onCreate(service: Service, application: Context) { host = service }

    fun onBind(intent: Intent): IBinder? {
        if (real == null) initialize(intent)
        return checkNotNull(real).onBind(intent)
    }

    fun onRebind(intent: Intent) { real?.onRebind(intent) }

    fun onDestroy() {
        try { real?.onDestroy() } finally {
            resourceLoader?.let { resources?.removeLoaders(it) }
            provider?.close()
            files.values.forEach { it.close() }
        }
    }

    private fun initialize(intent: Intent) {
        check(android.os.Process.myUid() % 100000 in 90000..99999) { "Renderer must have an isolated UID" }
        files = RendererEngineFiles.receive(checkNotNull(intent.extras?.getBinder(RendererEngineFiles.EXTRA)))
        fun path(name: String) = "/proc/self/fd/${checkNotNull(files[name]).fd}"
        val archive = path("engine.apk")
        val libraries = files.filterKeys { it.endsWith(".so") }.mapKeys {
            it.key.removePrefix("lib").removeSuffix(".so")
        }.mapValues { "/proc/self/fd/${it.value.fd}" }
        val app = host.applicationContext
        val installedHostApk = app.applicationInfo.sourceDir
        val engineLoader = object : PathClassLoader("$archive${File.pathSeparator}$installedHostApk",
            "/proc/self/fd", host.classLoader.parent) {
            override fun findLibrary(name: String): String? = libraries[name] ?: super.findLibrary(name)
        }
        // Default Binder parcel decoding and the engine must resolve one FileDescriptorInfo class.
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getMethod("currentActivityThread").invoke(null)
        val packages = threadClass.getDeclaredField("mPackages").apply { isAccessible = true }.get(thread) as Map<*, *>
        val loadedApk = (packages[host.packageName] as WeakReference<*>).get()!!
        Class.forName("android.app.LoadedApk").getDeclaredField("mClassLoader").apply { isAccessible = true }
            .set(loadedApk, engineLoader)
        // Public native load, under the same defining loader used by Chromium's native declarations.
        engineLoader.loadClass(IsolatedNativeLoader::class.java.name).getMethod("load", String::class.java)
            .invoke(null, checkNotNull(libraries["webviewchromium"]))

        val apkProvider = ResourcesProvider.loadFromApk(checkNotNull(files["engine.apk"]))
        provider = apkProvider
        val loader = ResourcesLoader().apply { addProvider(apkProvider) }.also { resourceLoader = it }
        @Suppress("DEPRECATION")
        val engineResources = Resources(app.assets, app.resources.displayMetrics, app.resources.configuration)
            .apply { addLoaders(loader) }.also { resources = it }
        val info = ApplicationInfo(app.applicationInfo).apply {
            sourceDir = archive; publicSourceDir = archive; nativeLibraryDir = "/proc/self/fd"
        }
        val engineContext = object : ContextWrapper(app) {
            override fun getClassLoader(): ClassLoader = engineLoader
            override fun getResources(): Resources = engineResources
            override fun getAssets() = engineResources.assets
            override fun getApplicationInfo(): ApplicationInfo = info
            override fun getPackageCodePath(): String = archive
            override fun getPackageResourcePath(): String = archive
            override fun getApplicationContext(): Context = this
        }
        val serviceClass = engineLoader.loadClass(checkNotNull(intent.getStringExtra(EXTRA_ORIGINAL_SERVICE_CLASS)))
        val service = serviceClass.getDeclaredConstructor().newInstance() as Service
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply { isAccessible = true }
            .invoke(service, engineContext)
        real = service
        service.onCreate()
    }

    companion object {
        const val EXTRA_WEBVIEW_APK_PATH = "EXTRA_WEBVIEW_APK_PATH"
        const val EXTRA_WEBVIEW_LIBS_PATH = "EXTRA_WEBVIEW_LIBS_PATH"
        const val EXTRA_WEBVIEW_PACKAGE_NAME = "EXTRA_WEBVIEW_PACKAGE_NAME"
        const val EXTRA_ORIGINAL_SERVICE_CLASS = "EXTRA_ORIGINAL_SERVICE_CLASS"
        const val EXTRA_SHARED_LIBS = "EXTRA_WEBVIEW_SHARED_LIBS"
    }
}
