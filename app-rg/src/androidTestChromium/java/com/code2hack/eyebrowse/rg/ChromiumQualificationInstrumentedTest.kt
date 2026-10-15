package com.code2hack.eyebrowse.rg

import android.os.Debug
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import com.code2hack.eyebrowse.rg.chromium.ChromiumApplication
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real production page/native engine observations; no polyfills or UA substitutions. */
@RunWith(AndroidJUnit4::class)
class ChromiumQualificationInstrumentedTest {
    /** Diagnostic only: preserve the original routing assertion while observing the owned JSON DOM. */
    @Test fun observeOwnedJsonReceiptConsumerBoundary() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val base = checkNotNull(args.getString("fixtureBaseUrl")).trimEnd('/')
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val nonce = java.util.UUID.randomUUID().toString()
        val report = JSONObject().put("state", "INCOMPLETE").put("candidateHead", args.getString("candidateHead"))
            .put("scope", "OWNED_DUMMY_RECEIPT_DIAGNOSTIC_NOT_FULL_ROUTING_ACCEPTANCE").put("nonce", nonce)
        try {
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(it.tabs.current.session.open("$base/route-probe/$nonce").accepted()) }
                val end = SystemClock.elapsedRealtime() + 15_000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < end) {
                    scenario.onActivity { ready = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY }
                    if (!ready) SystemClock.sleep(30)
                }
                assertTrue("owned receipt document presented", ready)
                val done = CountDownLatch(1)
                var document: JSONObject? = null
                scenario.onActivity { activity ->
                    activity.tabs.current.session.page!!.evaluateJavascript("""(()=>{
                        const body=document.body.innerText, pre=document.querySelector('pre')?.innerText??'';
                        function parse(text){try{return JSON.parse(text)}catch(e){return null}}
                        return {origin:location.origin,contentType:document.contentType,bodyText:body,preText:pre,
                            bodyReceipt:parse(body),preReceipt:parse(pre),bodyChildren:[...document.body.children].map(e=>e.tagName)};
                    })()""") { value -> document = JSONObject(value); done.countDown() }
                }
                assertTrue("owned receipt DOM callback", done.await(5, TimeUnit.SECONDS))
                report.put("document", document).put("state", "DIAGNOSTIC_OBSERVED")
                // Only the controlled dummy receipt is captured; no public-site content or credentials.
                assertTrue("actual owned nonce present", checkNotNull(document).getString("bodyText").contains(nonce))
            }
        } finally { File(context.filesDir, "chromium-receipt-diagnostic.json").writeText(report.toString(2)) }
    }

    @Test fun pinnedNativeEngineRunsModernWebApisInTheCurrentBrowser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val base = checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl")).trimEnd('/')
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val report = JSONObject().put("state", "INCOMPLETE")
        try {
            val startup = JSONObject(File(context.filesDir, "chromium-startup.json").readText())
            report.put("startup", startup)
            assertEquals("NATIVE_ENGINE_LOADED_API_CHECKS_PENDING", startup.getString("state"))
            assertEquals(ChromiumApplication.ENGINE_VERSION, WebView.getCurrentWebViewPackage()?.versionName)
            assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
            report.put("appApkSha256", ChromiumApplication.sha256(File(context.applicationInfo.sourceDir)))
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(it.tabs.current.session.open("$base/keyboard.html").accepted()) }
                val end = SystemClock.elapsedRealtime() + 20_000
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < end) {
                    scenario.onActivity { ready = it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY }
                    if (!ready) SystemClock.sleep(30)
                }
                assertTrue("real fixture page presented", ready)
                val done = CountDownLatch(1)
                var capabilities: JSONObject? = null
                scenario.onActivity {
                    checkNotNull(it.tabs.current.session.page).evaluateJavascript("""(()=>({
                        toReversed:typeof Array.prototype.toReversed==='function'&&[1,2].toReversed().join(',')==='2,1',
                        throwIfAborted:typeof AbortSignal.prototype.throwIfAborted==='function'&&(()=>{
                            const c=new AbortController();c.abort();try{c.signal.throwIfAborted();return false}catch(e){return e.name==='AbortError'}
                        })(),
                        cssHas:CSS.supports('selector(:has(*))'),
                        containerQueries:CSS.supports('container-type','inline-size'),
                        structuredClone:typeof structuredClone==='function'&&structuredClone({n:2}).n===2
                    }))()""".trimIndent()) { value ->
                        capabilities = JSONObject(value); done.countDown()
                    }
                }
                assertTrue("native renderer capability result", done.await(5, TimeUnit.SECONDS))
                report.put("capabilities", capabilities)
                for (name in listOf("toReversed", "throwIfAborted", "cssHas", "containerQueries", "structuredClone")) {
                    assertTrue(name, checkNotNull(capabilities).getBoolean(name))
                }
                report.put("workloadPssKiB", Debug.getPss()).put("proxyOverrideSupported", true)
            }
            report.put("state", "PASS_EXERCISED_ENGINE_CAPABILITIES")
        } finally {
            File(context.filesDir, "chromium-qualification.json").writeText(report.toString(2))
        }
    }
}
