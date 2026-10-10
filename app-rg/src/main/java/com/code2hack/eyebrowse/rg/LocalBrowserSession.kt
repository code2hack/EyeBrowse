package com.code2hack.eyebrowse.rg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.view.View
import android.view.MotionEvent
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.code2hack.eyebrowse.core.browser.AddressPolicy
import org.json.JSONObject

/** One RG-owned page. No Phone link, captured input target, tab policy or production HUD. */
internal class LocalBrowserSession(context: Context, private val onPageTouchUp: () -> Unit = {}) {
    enum class Phase { EMPTY, LOADING, READY, ERROR, INTERRUPTED }

    class State(
        val phase: Phase = Phase.EMPTY,
        val url: String = "",
        val title: String = "",
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val error: String? = null,
    ) {
        override fun toString() = "LocalBrowserState(phase=$phase, location/title redacted)"
    }

    val surface = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    var page: WebView? = null
        private set
    var state = State()
        private set
    var onStateChanged: (State) -> Unit = {}

    init { createPage() }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION")
    private fun createPage(): WebView = WebView(surface.context).also { view ->
        page = view
        view.setBackgroundColor(Color.BLACK)
        view.visibility = View.INVISIBLE
        view.defaultFocusHighlightEnabled = false
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) onPageTouchUp()
            false // Native WebView handles the touch, including after explicit renderer recovery.
        }
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setNeedInitialFocus(false) // A native page tap owns focus; do not first focus/scroll to the first DOM node.
            forceDark = WebSettings.FORCE_DARK_OFF
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                publish(state.phase, state.error)
            }
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                publish(state.phase, state.error)
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // WebView can report the failed document's start after its HTTP error.
                if (state.phase == Phase.ERROR) return
                hidePage(view)
                publish(Phase.LOADING)
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                publish(state.phase, state.error)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (state.phase != Phase.LOADING || url != view.url) return
                // The same fixed public styling route qualified in #28, on the live document.
                view.evaluateJavascript(PRESENTATION_SCRIPT) {
                    view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (state.phase == Phase.LOADING && url == view.url) {
                                view.visibility = View.VISIBLE
                                publish(Phase.READY)
                            }
                        }
                    })
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = AddressPolicy.resolve(request.url.toString()).accepted()
                if (!allowed && request.isForMainFrame) publish(state.phase, "Blocked unsupported address")
                return !allowed
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    hidePage(view)
                    publish(Phase.ERROR, "Page unavailable (${error.errorCode})")
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                hidePage(view)
                publish(Phase.ERROR, "Certificate error; connection refused")
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) {
                    hidePage(view)
                    publish(Phase.ERROR, "HTTP error ${response.statusCode}")
                }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                surface.removeView(view)
                page = null
                view.destroy()
                state = State(Phase.INTERRUPTED, state.url, state.title,
                    error = "Page renderer stopped. Open the address to recover; unsaved page state was lost.")
                onStateChanged(state)
                return true
            }
        }
        surface.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    /** Rejected drafts do not touch the existing page, focus, history or loading state. */
    fun open(draft: String): AddressPolicy.Result {
        val result = AddressPolicy.resolve(draft)
        if (result.accepted()) {
            val view = page ?: createPage() // Deliberate recovery only; never replay a failed form.
            hidePage(view)
            publish(Phase.LOADING)
            view.loadUrl(checkNotNull(result.url()))
        }
        return result
    }

    fun back() { page?.takeIf { it.canGoBack() }?.let {
        hidePage(it); publish(Phase.LOADING); it.goBack()
    } }
    fun forward() { page?.takeIf { it.canGoForward() }?.let {
        hidePage(it); publish(Phase.LOADING); it.goForward()
    } }
    fun refresh() {
        page?.takeIf { state.phase != Phase.EMPTY && state.phase != Phase.INTERRUPTED }?.let {
            hidePage(it)
            publish(Phase.LOADING)
            it.reload()
        }
    }
    fun resume() { page?.onResume() }
    fun pause() { page?.onPause() }
    fun destroy() {
        onStateChanged = {}
        page?.let { surface.removeView(it); it.stopLoading(); it.destroy() }
        page = null
    }

    private fun hidePage(view: WebView) {
        // Transparency alone still admits native touch/focus. Keep layout for the visual callback.
        view.clearFocus()
        view.visibility = View.INVISIBLE
    }

    private fun publish(phase: Phase, error: String? = null) {
        state = State(phase, page?.url.orEmpty(), page?.title.orEmpty(),
            page?.canGoBack() == true, page?.canGoForward() == true, error)
        onStateChanged(state)
    }

    companion object {
        // Presentation only. No field values, DOM replacement, media inversion or native bridge.
        private const val CSS = "html,body,p,div,span,section,article,header,footer,main,nav,form,label," +
            "input,textarea,select,button,table,td,th,ul,ol,li,a,[contenteditable]" +
            "{background-color:#000!important;color:#fff!important}"
        private val PRESENTATION_SCRIPT = """(()=>{
            let style=document.getElementById('eyebrowse-local-colors');
            if(!style){style=document.createElement('style');style.id='eyebrowse-local-colors';
                (document.head||document.documentElement).appendChild(style)}
            style.textContent=${JSONObject.quote(CSS)};
        })()""".trimIndent()
    }
}
