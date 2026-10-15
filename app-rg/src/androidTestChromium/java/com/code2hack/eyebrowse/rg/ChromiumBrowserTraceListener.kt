package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.net.http.SslError
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.Description
import org.junit.runner.notification.RunListener
import java.io.File
import java.util.IdentityHashMap

/** Optional diagnostic listener; the original test bodies/assertions/waits remain unchanged. */
class ChromiumBrowserTraceListener : RunListener() {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val originals = IdentityHashMap<LocalBrowserSession, (LocalBrowserSession.State) -> Unit>()
    private val events = JSONArray()
    private var dropped = 0
    private var application: Application? = null
    private var current: LocalBrowserActivity? = null
    private val main = Handler(Looper.getMainLooper())
    private val metrics = JSONArray()
    private val sample = object : Runnable {
        override fun run() {
            current?.let { attach(it) } // The original tests replace the initial empty tab.
            current?.tabs?.current?.session?.page?.let { page ->
                if (page.url?.let { it.startsWith("https://app.paseo.sh/") || it.startsWith("https://chatgpt.com/") } == true) {
                    page.evaluateJavascript("""(()=>({origin:location.origin,visibility:document.visibilityState,
                        ready:document.readyState,bodyChildren:document.body?.children.length,scripts:document.scripts.length,
                        scriptKinds:Array.from(document.scripts,e=>({type:e.type,async:e.async,defer:e.defer,
                        external:!!e.src,origin:e.src?new URL(e.src,location.href).origin:null})),
                        navigation:performance.getEntriesByType('navigation').map(e=>({responseStart:e.responseStart,
                        responseEnd:e.responseEnd,domInteractive:e.domInteractive,domContentLoaded:e.domContentLoadedEventEnd,
                        load:e.loadEventEnd,bytes:e.decodedBodySize})),resources:performance.getEntriesByType('resource').slice(-20)
                        .map(e=>({origin:new URL(e.name,location.href).origin,type:e.initiatorType,status:e.responseStatus,
                        duration:e.duration,bytes:e.decodedBodySize}))}))()""") { value ->
                        if (metrics.length() < 60) metrics.put(runCatching { JSONObject(value) }.getOrDefault(JSONObject()))
                    }
                }
            }
            main.postDelayed(this, 1000)
        }
    }
    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {}
        override fun onActivityStarted(activity: Activity) { attach(activity) }
        override fun onActivityResumed(activity: Activity) { attach(activity); if (activity is LocalBrowserActivity) current = activity }
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) { if (current === activity) current = null }
    }

    private fun location(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        val uri = android.net.Uri.parse(value)
        return if (uri.host in setOf("100.92.81.33", "127.0.0.1", "route.test"))
            "${uri.scheme}://${uri.authority}${uri.path}" else "${uri.scheme}://${uri.host.orEmpty()}"
    }

    private fun attach(activity: Activity) {
        if (activity !is LocalBrowserActivity) return
        activity.tabs.items.forEach { tab ->
            val session = tab.session
            if (originals.containsKey(session)) return@forEach
            val original = session.onStateChanged
            originals[session] = original
            session.page?.let { page ->
                val client = checkNotNull(page.webViewClient)
                fun network(kind: String, url: String?, mainFrame: Boolean?, code: Int? = null, detail: String? = null) {
                    val event = JSONObject().put("atNs", android.os.SystemClock.elapsedRealtimeNanos())
                        .put("networkCallback", kind).put("origin", location(url))
                        .put("mainFrame", mainFrame).put("code", code).put("errorKind", detail)
                    main.post { if (events.length() < 512) events.put(event) else dropped++ }
                }
                page.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, icon: android.graphics.Bitmap?) = client.onPageStarted(view, url, icon)
                    override fun onPageFinished(view: WebView, url: String?) = client.onPageFinished(view, url)
                    override fun onPageCommitVisible(view: WebView, url: String?) = client.onPageCommitVisible(view, url)
                    override fun doUpdateVisitedHistory(view: WebView, url: String?, reload: Boolean) = client.doUpdateVisitedHistory(view, url, reload)
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = client.shouldOverrideUrlLoading(view, request)
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        network("REQUEST", request.url.toString(), request.isForMainFrame)
                        return client.shouldInterceptRequest(view, request)
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        val kind = listOf("ERR_NAME_NOT_RESOLVED", "ERR_CONNECTION_TIMED_OUT", "ERR_CONNECTION_REFUSED",
                            "ERR_TUNNEL_CONNECTION_FAILED", "ERR_CONNECTION_CLOSED", "ERR_CONNECTION_RESET", "ERR_INTERNET_DISCONNECTED")
                            .firstOrNull { error.description.contains(it) } ?: "OTHER"
                        network("ERROR", request.url.toString(), request.isForMainFrame, error.errorCode, kind)
                        client.onReceivedError(view, request, error)
                    }
                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        network("HTTP", request.url.toString(), request.isForMainFrame, response.statusCode)
                        client.onReceivedHttpError(view, request, response)
                    }
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        network("SSL", error.url, null, error.primaryError)
                        client.onReceivedSslError(view, handler, error)
                    }
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = client.onRenderProcessGone(view, detail)
                }
                val chrome = page.webChromeClient
                page.webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String?) { chrome?.onReceivedTitle(view, title) }
                    override fun onProgressChanged(view: WebView, progress: Int) { chrome?.onProgressChanged(view, progress) }
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        val text = message.message()
                        val kind = listOf("TypeError", "ReferenceError", "SyntaxError", "Content Security Policy", "Failed to load", "CORS")
                            .firstOrNull { text.contains(it, true) } ?: "OTHER"
                        if (events.length() < 512) events.put(JSONObject().put("consoleKind", kind)
                            .put("level", message.messageLevel().name).put("origin", location(message.sourceId())))
                        return chrome?.onConsoleMessage(message) ?: false
                    }
                }
            }
            val record: (LocalBrowserSession.State) -> Unit = { state ->
                if (events.length() >= 512) dropped++ else {
                    val page = session.page
                    val history = page?.copyBackForwardList()
                    val runtime = com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime.get(activity)
                    events.put(JSONObject().put("atNs", android.os.SystemClock.elapsedRealtimeNanos())
                        .put("phase", state.phase.name).put("error", state.error)
                        .put("stateLocation", location(state.url)).put("viewLocation", location(page?.url))
                        .put("shown", page?.isShown).put("viewVisibility", page?.visibility)
                        .put("back", state.canGoBack).put("forward", state.canGoForward)
                        .put("historyIndex", history?.currentIndex).put("historySize", history?.size)
                        .put("historyLocation", location(history?.currentItem?.url))
                        .put("coreStatus", runtime.status).put("corePort", runtime.proxyPort)
                        .put("appliedPort", runtime.webViewPort)
                        .put("callback", Throwable().stackTrace.map { it.methodName }.take(9).joinToString("/")))
                }
            }
            session.onStateChanged = { state -> original(state); record(state) }
            record(session.state)
        }
    }

    override fun testStarted(description: Description) {
        instrumentation.runOnMainSync {
            application = instrumentation.targetContext.applicationContext as Application
            application!!.registerActivityLifecycleCallbacks(lifecycle)
            main.post(sample)
        }
    }

    override fun testFinished(description: Description) {
        instrumentation.runOnMainSync {
            application?.unregisterActivityLifecycleCallbacks(lifecycle)
            main.removeCallbacks(sample); current = null
            originals.forEach { (session, callback) -> if (session.page != null) session.onStateChanged = callback }
            originals.clear()
            val args = InstrumentationRegistry.getArguments()
            val report = JSONObject().put("scope", "PASSIVE_CONTROLLED_CALLBACK_HISTORY_DIAGNOSTIC")
                .put("method", description.methodName).put("candidateHead", args.getString("candidateHead"))
                .put("evidenceRunId", args.getString("evidenceRunId")).put("events", events)
                .put("droppedEvents", dropped).put("publicResourceMetrics", metrics)
            File(instrumentation.targetContext.filesDir, "chromium-browser-trace-${description.methodName}.json")
                .writeText(report.toString(2))
            while (events.length() > 0) events.remove(events.length() - 1)
            while (metrics.length() > 0) metrics.remove(metrics.length() - 1)
            dropped = 0
        }
    }
}
