package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Calls the original failing body verbatim, with a separate controlled-document observer. */
@RunWith(AndroidJUnit4::class)
class ChromiumRoutingObservationInstrumentedTest {
    @Test(timeout = 150000) fun observeOriginalRoutingBody() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val application = context.applicationContext as Application
        val main = Handler(Looper.getMainLooper())
        val samples = CopyOnWriteArrayList<JSONObject>()
        var current: LocalBrowserActivity? = null
        val tick = object : Runnable {
            override fun run() {
                current?.let { activity ->
                    val session = activity.tabs.current.session
                    val sample = JSONObject().put("phase", session.state.phase.name)
                        .put("error", session.state.error).put("stateUrl", session.state.url)
                        .put("viewUrl", session.page?.url).put("atNs", System.nanoTime())
                    val url = session.page?.url.orEmpty()
                    // The original body uses only these controlled browser origins.
                    if (listOf("http://127.0.0.1:", "http://route.test:", "https://route.test:",
                            "http://100.92.81.33:39030/").any { url.startsWith(it) }) {
                        session.page?.evaluateJavascript("({url:location.href,ready:document.readyState,type:document.contentType,body:document.body?.innerText.slice(0,700),html:document.documentElement?.outerHTML.slice(0,1100)})") { value ->
                            samples += sample.put("document", runCatching { JSONObject(value) }.getOrDefault(JSONObject()))
                        }
                    } else samples += sample.put("document", "NO_CONTROLLED_DOCUMENT")
                }
                main.postDelayed(this, 500)
            }
        }
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) { if (activity is LocalBrowserActivity) current = activity }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) { if (current === activity) current = null }
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        main.post(tick)
        try {
            MihomoRoutingInstrumentedTest().actualProviderCoreAndNativeRoutesFallbackAndRecreation()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { main.removeCallbacks(tick); current = null }
            application.unregisterActivityLifecycleCallbacks(callbacks)
            File(context.filesDir, "chromium-routing-diagnostic.json").writeText(JSONObject()
                .put("scope", "DIAGNOSTIC_WRAPPER_ORIGINAL_BODY_ASSERTIONS_WAITS_PROTOCOL_UNCHANGED")
                .put("samples", JSONArray(samples)).toString(2))
        }
    }
}
