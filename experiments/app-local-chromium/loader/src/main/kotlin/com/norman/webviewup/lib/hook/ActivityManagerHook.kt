/*
 * Kotlin replacement of WebViewUpgrade ActivityManagerHook at
 * 5a5a7d11b5e67bba37787a697f45c9f5d45e6dda (JonaNorman, Apache-2.0).
 * Original source, copyright and LICENSE remain pinned and retained; see replacement-map.md.
 */
package com.norman.webviewup.lib.hook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import android.os.ResultReceiver
import android.util.Log
import com.norman.webviewup.lib.WebViewReplace
import com.norman.webviewup.lib.reflect.RuntimeAccess
import com.norman.webviewup.lib.sandbox.SandboxedProcessServiceDelegate as Delegate
import com.norman.webviewup.lib.service.binder.BinderHook
import com.norman.webviewup.lib.service.binder.ProxyBinder
import com.norman.webviewup.lib.service.interfaces.IActivityManager
import com.norman.webviewup.lib.service.interfaces.ISingleton
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** API32-qualified instance-aware redirection; unrelated calls go to the original AM unchanged. */
class ActivityManagerHook(private val context: Context, private val target: ReplaceTarget) : BinderHook() {
    private val instances = RendererInstances<IBinder, IBinder>(
        ActivityManagerHookPolicy.resolveMaxStubServices(context))
    private val lock = Any()
    private lateinit var singleton: ISingleton
    private lateinit var original: Any

    override fun onTargetBinderObtain(): IBinder? = null
    override fun onProxyBinderCreate(ignored: IBinder?): ProxyBinder? {
        val holder = RuntimeAccess.staticAccess(IActivityManager::class.java).iActivityManagerSingleton
        singleton = RuntimeAccess.objectAccess(ISingleton::class.java, holder)
        original = checkNotNull(singleton.instance)
        val contract = Class.forName("android.app.IActivityManager")
        val proxy = Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, source ->
            val args = source?.map { it }.orEmpty().toTypedArray()
            when (method.name) {
                "unbindService" -> synchronized(lock) {
                    val result = call(method, args)
                    if (result == true) instances.unbound((args.single() as IInterface).asBinder())
                    result
                }
                "bindIsolatedService", "bindServiceInstance", "bindService" -> bind(method, args)
                else -> call(method, args)
            }
        }
        singleton.instance = proxy
        return null
    }

    private fun bind(method: Method, args: Array<Any?>): Any? {
        val intent = args.firstOrNull { it is Intent } as? Intent ?: return call(method, args)
        val component = intent.component ?: return call(method, args)
        val custom = ActivityManagerHookPolicy.getSandboxServiceClassPattern()
        if (!isRendererService(target.webViewPackageName, component.packageName, component.className, custom)) {
            return call(method, args)
        }
        return synchronized(lock) { redirect(method, args, intent, component) }
    }

    private fun redirect(method: Method, args: Array<Any?>, intent: Intent, component: ComponentName): Any? {
        // Unknown platform signatures fail visibly; never guess which string carries instance identity.
        val named = method.name != "bindService"
        check(args.size == (if (named) 9 else 8) && args[2] === intent && args[5] is Int && args.last() is Int) {
            "Unqualified renderer binding signature: ${method.name}/${args.size}"
        }
        val connection = (args[4] as IInterface).asBinder()
        val key = RendererKey(component.flattenToString(), if (named) args[6] as String? else null)
        val realBind = Class.forName("android.app.IActivityManager").methods.single {
            it.name == "bindService" && it.parameterCount == 8 && it.parameterTypes[5] == Int::class.javaPrimitiveType
        }
        val binding = instances.acquire(key, connection) ?: run {
            Log.e(TAG, "EXHAUSTED capacity=${ActivityManagerHookPolicy.resolveMaxStubServices(context)}; no live slot reused")
            return 0
        }
        val lease = binding.lease
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                val process = data?.getBinder(CHILD_TOKEN) ?: return
                val pid = data.getInt("pid")
                if (data.getInt("uid") != Process.myUid() || pid <= 0 || pid == Process.myPid()) return
                if (!instances.started(lease, process)) return
                Log.i(TAG, "CHILD serial=${lease.serial} slot=${lease.slot} pid=$pid uid=${data.getInt("uid")} " +
                    "privateProbeReadable=${data.getBoolean("privateProbeReadable")} selinux=${data.getString("selinux")}")
                try {
                    process.linkToDeath({
                        instances.died(lease, process)
                        Log.i(TAG, "DEAD serial=${lease.serial} slot=${lease.slot} pid=$pid")
                    }, 0)
                } catch (_: android.os.RemoteException) { instances.died(lease, process) }
            }
        }
        val redirected = Intent(intent).apply {
            this.component = ComponentName(context.packageName, "$STUB_PREFIX${lease.slot}")
            putExtra(Delegate.EXTRA_WEBVIEW_APK_PATH, target.apkPath)
            putExtra(Delegate.EXTRA_WEBVIEW_LIBS_PATH, target.libsPath)
            putExtra(Delegate.EXTRA_WEBVIEW_PACKAGE_NAME, target.webViewPackageName)
            putExtra(Delegate.EXTRA_ORIGINAL_SERVICE_CLASS, component.className)
            putExtra(Delegate.EXTRA_SHARED_LIBS, WebViewReplace.REPLACE_WEB_VIEW_PACKAGE_INFO?.applicationInfo?.sharedLibraryFiles)
            putExtra(CHILD_RECEIVER, receiver)
        }
        val adapted = args.filterIndexed { index, _ -> !named || index != 6 }.toTypedArray()
        adapted[2] = redirected
        adapted[5] = (args[5] as Int) and Context.BIND_EXTERNAL_SERVICE.inv()
        adapted[6] = context.packageName
        Log.i(TAG, "BIND serial=${lease.serial} component=${component.className} instance=${key.instance} slot=${lease.slot} connection=${connection.hashCode()}")
        return try {
            val result = call(realBind, adapted)
            if ((result as Int) > 0) instances.accepted(binding) else instances.rejected(binding)
            result
        } catch (failure: Throwable) {
            // A lost reply can follow a successful bind. Reserve rather than risk live process reuse.
            instances.accepted(binding)
            throw failure
        }
    }

    private fun call(method: Method, args: Array<Any?>): Any? = try {
        method.invoke(original, *args)
    } catch (failure: InvocationTargetException) { throw checkNotNull(failure.cause) }

    override fun onTargetBinderRestore(binder: IBinder?) { singleton.instance = original }
    override fun onProxyBinderReplace(binder: ProxyBinder?) { Log.i(TAG, "Kotlin instance/lifetime hook active") }

    companion object {
        const val TAG = "ChromiumInstances"
        const val CHILD_RECEIVER = "eyebrowse.chromium.child.receiver"
        const val CHILD_TOKEN = "eyebrowse.chromium.child.token"
        const val STUB_PREFIX = "com.norman.webviewup.lib.sandbox.stub.StubSandboxedProcessService"
    }
}
