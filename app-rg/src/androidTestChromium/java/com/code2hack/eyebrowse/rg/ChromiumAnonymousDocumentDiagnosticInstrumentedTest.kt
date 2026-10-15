package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Separate diagnostic: page completion can precede a public application's rendered interface. */
@RunWith(AndroidJUnit4::class)
class ChromiumAnonymousDocumentDiagnosticInstrumentedTest {
    @Test fun observePublicDocumentForOneBoundedWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        require(context.packageName == "com.code2hack.eyebrowse.rg.chromium")
        val site = checkNotNull(args.getString("anonymousSite"))
        val url = when (site) {
            "paseo" -> "https://app.paseo.sh/"
            "chatgpt" -> "https://chatgpt.com/"
            else -> error("Only the two authorized anonymous public roots")
        }
        val report = JSONObject().put("scope", "READ_ONLY_DOCUMENT_DIAGNOSTIC_NOT_BROWSER_ACCEPTANCE")
            .put("candidateHead", args.getString("candidateHead"))
            .put("evidenceRunId", args.getString("evidenceRunId")).put("site", site)
        val observations = JSONArray()
        try {
            ActivityScenario.launch(LocalBrowserActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.address.requestFocus()
                    activity.address.setText(url)
                    check(activity.keyButtons.getValue(RgKeyboardKeys.Key.Command.ENTER).performClick())
                }
                val started = SystemClock.elapsedRealtime()
                val end = started + 45_000
                while (SystemClock.elapsedRealtime() < end) {
                    val row = JSONObject().put("elapsedMs", SystemClock.elapsedRealtime() - started)
                    val done = CountDownLatch(1)
                    val document = AtomicReference<JSONObject>()
                    scenario.onActivity { activity ->
                        val session = activity.tabs.current.session
                        val runtime = RgProxyRuntime.get(activity)
                        row.put("phase", session.state.phase.name).put("pageShown", session.page?.isShown == true)
                            .put("error", session.state.error).put("nativeProxyPort", runtime.proxyPort)
                            .put("webViewProxyPort", runtime.webViewPort)
                        session.page?.evaluateJavascript("""(()=>({origin:location.origin,ready:document.readyState,
                            bodyTextLength:document.body?.innerText.length??0,
                            buttonCount:document.querySelectorAll('button').length,
                            anonymousSignInText:/sign in|log in|sign up|connect|pair/i.test(document.body?.innerText??''),
                            challengeOrVerificationText:/verify you are human|checking your browser|just a moment/i.test(document.body?.innerText??''),
                            possiblePersonalLinks:document.querySelectorAll('a[href^="/c/"],a[href*="/agents/"]').length,
                            productionStylingApplied:!!document.getElementById('eyebrowse-local-colors')}))()""") { value ->
                            runCatching { document.set(JSONObject(value)) }
                            done.countDown()
                        }
                    }
                    val remaining = (end - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                    row.put("rendererCallback", done.await(minOf(2_000, remaining), TimeUnit.MILLISECONDS))
                    document.get()?.let { row.put("document", it) }
                    observations.put(row)
                    if ((document.get()?.optInt("possiblePersonalLinks", 0) ?: 0) != 0) {
                        report.put("state", "STOP_POSSIBLE_PERSONAL_STATE_NO_FURTHER_INSPECTION")
                        return
                    }
                    SystemClock.sleep(minOf(500, (end - SystemClock.elapsedRealtime()).coerceAtLeast(0)))
                }
                report.put("elapsedMs", SystemClock.elapsedRealtime() - started)
                    .put("state", "DIAGNOSTIC_WINDOW_COMPLETE_NOT_BROWSER_ACCEPTANCE")
            }
        } finally {
            report.put("observations", observations)
            File(context.filesDir, "chromium-anonymous-document-$site.json").writeText(report.toString(2))
        }
    }
}
