package com.code2hack.eyebrowse.rg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The same public API32 request tests both the original APK (red) and repaired APK (green). */
@RunWith(AndroidJUnit4::class)
class ChromiumInstanceInstrumentedTest {
    private class Connection : ServiceConnection {
        val ready = CountDownLatch(1)
        var binder: IBinder? = null
        var component: ComponentName? = null
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            component = name; binder = service; ready.countDown()
        }
        override fun onNullBinding(name: ComponentName) { component = name; ready.countDown() }
        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    @Test fun distinctNamedInstancesAndRepeatedBindingUseTheCorrectChild() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val args = InstrumentationRegistry.getArguments()
        val head = checkNotNull(args.getString("candidateHead"))
        require(head.matches(Regex("[0-9a-f]{40}")))
        val engine = ComponentName("com.android.webview", "org.chromium.content.app.SandboxedProcessService0")
        val intent = Intent().setComponent(engine).putExtra(
            "org.chromium.base.process_launcher.extra.browser_package_name", context.packageName)
        val connections = mutableListOf<Connection>()
        val observations = JSONArray()
        val report = JSONObject().put("state", "INCOMPLETE").put("candidateHead", head)
            .put("control", args.getString("loaderControl", "repair")).put("requests", observations)
        fun bind(name: String): Connection {
            val connection = Connection()
            var bound = false
            instrumentation.runOnMainSync {
                bound = context.bindIsolatedService(intent, Context.BIND_AUTO_CREATE or Context.BIND_EXTERNAL_SERVICE,
                    name, context.mainExecutor, connection)
            }
            if (bound) connections += connection
            assertTrue("named request accepted", bound)
            assertTrue("named request connected", connection.ready.await(15, TimeUnit.SECONDS))
            observations.put(JSONObject().put("instance", name).put("component", connection.component?.flattenToString())
                .put("binderIdentity", connection.binder?.hashCode()).put("requestComponent", intent.component?.flattenToString()))
            assertNotNull("real child binder", connection.binder)
            return connection
        }
        Closeable { File(context.filesDir, "chromium-instance-control.json").writeText(report.toString(2)) }.use {
          Closeable {
            var failure: Throwable? = null
            connections.forEach { connection ->
                try { instrumentation.runOnMainSync { context.unbindService(connection) } }
                catch (next: Throwable) { if (failure == null) failure = next else failure!!.addSuppressed(next) }
            }
            failure?.let { throw it }
          }.use {
            val first = bind("i57-first")
            val second = bind("i57-second")
            assertNotEquals("different instance names must connect to different live children", first.binder, second.binder)
            assertEquals("repeat binding must retain the first live child", first.binder, bind("i57-first").binder)
            assertEquals("the caller's Intent component must remain unchanged", engine, intent.component)
            report.put("state", "PASS_DISTINCT_AND_REPEATED_API32_BINDINGS")
          }
        }
    }

    @Test fun ordinaryOwnedJobServiceBindingPreservesItsComponent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val component = ComponentName(context.packageName, "com.code2hack.eyebrowse.rg.proxy.RgRefreshJob")
        val intent = Intent().setComponent(component)
        val connection = Connection()
        var bound = false
        try {
            instrumentation.runOnMainSync { bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            assertTrue("ordinary app-owned service bound", bound)
            assertTrue(connection.ready.await(10, TimeUnit.SECONDS))
            assertNotNull(connection.binder)
            assertEquals(component, connection.component)
            assertEquals(component, intent.component)
            // No JobService commands are sent; binding does not run a refresh job.
        } finally {
            if (bound) instrumentation.runOnMainSync { context.unbindService(connection) }
        }
    }
}
