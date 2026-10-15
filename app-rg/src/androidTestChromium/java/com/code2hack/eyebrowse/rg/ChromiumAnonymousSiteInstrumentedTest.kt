package com.code2hack.eyebrowse.rg

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** One public root per fresh invocation. No sign-in, pairing, account commands or content capture. */
@RunWith(AndroidJUnit4::class)
class ChromiumAnonymousSiteInstrumentedTest {
    @Test fun observePublicRootThroughNativeAddressAndOpen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val site = checkNotNull(args.getString("anonymousSite"))
        val url = when (site) {
            "paseo" -> "https://app.paseo.sh/"
            "chatgpt" -> "https://chatgpt.com/"
            else -> error("Only the two authorized anonymous public roots are allowed")
        }
        val head = checkNotNull(args.getString("candidateHead"))
        require(head.matches(Regex("[0-9a-f]{40}")))
        val report = JSONObject().put("state", "INCOMPLETE").put("site", site).put("publicRoot", url)
            .put("candidateHead", head).put("scope", "ANONYMOUS_ONLY_NOT_ACCOUNT_SESSION_SUCCESS")
        val observations = JSONArray()
        var unexpectedPersonalState = false
        try {
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    // The actual native address control and its actual built-in Open control.
                    activity.address.requestFocus()
                    activity.address.setText(url)
                    assertTrue(activity.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER).performClick())
                }
                val end = SystemClock.elapsedRealtime() + 45_000
                var presented = false
                while (SystemClock.elapsedRealtime() < end) {
                    val observation = JSONObject().put("observedAtMs", SystemClock.elapsedRealtime())
                    scenario.onActivity { activity ->
                        val session = activity.tabs.current.session
                        val runtime = RgProxyRuntime.get(activity)
                        presented = session.state.phase == LocalBrowserSession.Phase.READY
                        observation.put("phase", session.state.phase.name).put("pageShown", session.page?.isShown == true)
                            .put("error", session.state.error).put("nativeProxyPort", runtime.proxyPort)
                            .put("webViewProxyPort", runtime.webViewPort)
                    }
                    val done = CountDownLatch(1)
                    val document = AtomicReference<JSONObject>()
                    scenario.onActivity { activity ->
                        activity.tabs.current.session.page!!.evaluateJavascript("""(()=>({origin:location.origin,
                            ready:document.readyState,bodyTextLength:document.body?.innerText.length??0,
                            buttonCount:document.querySelectorAll('button').length,
                            anonymousSignInText:/sign in|log in|sign up|connect|pair/i.test(document.body?.innerText??''),
                            challengeOrVerificationText:/verify you are human|checking your browser|just a moment/i.test(document.body?.innerText??''),
                            possiblePersonalLinks:document.querySelectorAll('a[href^="/c/"],a[href*="/agents/"]').length,
                            productionStylingApplied:!!document.getElementById('eyebrowse-local-colors')}))()""") { value ->
                            runCatching { document.set(JSONObject(value)) }
                            done.countDown()
                        }
                    }
                    observation.put("rendererCallback", done.await(2, TimeUnit.SECONDS))
                    document.get()?.let { observation.put("document", it) }
                    observations.put(observation)
                    unexpectedPersonalState = (document.get()?.optInt("possiblePersonalLinks", 0) ?: 0) != 0
                    if (unexpectedPersonalState || presented) break
                    SystemClock.sleep(500)
                }
                if (unexpectedPersonalState) {
                    report.put("state", "STOP_POSSIBLE_PERSONAL_STATE_NO_FURTHER_INSPECTION")
                    fail("Unexpected possible personal state; preserve profile and route to Manager")
                }
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() }
                if (focused) instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try {
                        File(context.filesDir, "chromium-anonymous-$site.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { bitmap.recycle() }
                }
                report.put("state", if (presented) "ANONYMOUS_PAGE_PRESENTED_ONLY" else "NOT_PASSED_PAGE_NOT_PRESENTED")
                assertTrue("actual public page presented; anonymous page is not account/session success", presented)
            }
        } finally {
            report.put("observations", observations)
            File(context.filesDir, "chromium-anonymous-$site.json").writeText(report.toString(2))
        }
    }
}
