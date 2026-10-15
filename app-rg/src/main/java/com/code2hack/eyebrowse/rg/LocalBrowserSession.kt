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
    private val runtime = com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime.get(context)
    private var pendingAddress: String? = null
    private var pendingHttpError: Pair<String, String>? = null
    private val startPendingAddress = Runnable {
        val address = pendingAddress
        pendingAddress = null
        if (address != null) page?.loadUrl(address)
    }
    private var resumed = false
    private val resumeConfiguredPage = Runnable { if (resumed) page?.onResume() }

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
            override fun onPageCommitVisible(view: WebView, url: String?) { presentCommittedPage(view, url, false) }
            override fun onPageFinished(view: WebView, url: String?) { presentCommittedPage(view, url, true) }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val allowed = AddressPolicy.resolve(request.url.toString()).accepted()
                if (!allowed && request.isForMainFrame) publish(state.phase, "Blocked unsupported address")
                return !allowed
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                com.code2hack.eyebrowse.rg.proxy.RgProxyRuntime.get(view.context).onNetworkError()
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
                    // The response can precede the history commit. Do not advertise a settled
                    // error document while Refresh would still reload the previous entry.
                    pendingHttpError = request.url.toString() to "HTTP error ${response.statusCode}"
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
            pendingHttpError = null
            val view = page ?: createPage() // Deliberate recovery only; never replay a failed form.
            hidePage(view)
            publish(Phase.LOADING)
            pendingAddress = checkNotNull(result.url())
            runtime.afterBrowserConfigured(startPendingAddress)
        }
        return result
    }

    fun back() { page?.takeIf { it.canGoBack() }?.let {
        pendingHttpError = null
        cancelPendingAddress()
        hidePage(it); publish(Phase.LOADING); it.goBack()
    } }
    fun forward() { page?.takeIf { it.canGoForward() }?.let {
        pendingHttpError = null
        cancelPendingAddress()
        hidePage(it); publish(Phase.LOADING); it.goForward()
    } }
    fun refresh() {
        page?.takeIf { state.phase != Phase.EMPTY && state.phase != Phase.INTERRUPTED }?.let {
            pendingHttpError = null
            cancelPendingAddress()
            hidePage(it)
            publish(Phase.LOADING)
            it.reload()
        }
    }
    fun resume() { resumed = true; runtime.afterBrowserConfigured(resumeConfiguredPage) }
    fun pause() { resumed = false; runtime.cancelBrowserAction(resumeConfiguredPage); page?.onPause() }
    fun destroy() {
        cancelPendingAddress()
        runtime.cancelBrowserAction(resumeConfiguredPage)
        onStateChanged = {}
        page?.let { surface.removeView(it); it.stopLoading(); it.destroy() }
        page = null
    }

    private fun cancelPendingAddress() {
        pendingAddress = null
        runtime.cancelBrowserAction(startPendingAddress)
    }

    private fun hidePage(view: WebView) {
        // Transparency alone still admits native touch/focus. Keep layout for the visual callback.
        view.clearFocus()
        view.visibility = View.INVISIBLE
    }

    private fun presentCommittedPage(view: WebView, url: String?, finished: Boolean) {
        if (state.phase != Phase.LOADING || url != view.url) return
        pendingHttpError?.takeIf { it.first == url }?.let { failure ->
            hidePage(view)
            if (finished) publish(Phase.ERROR, failure.second)
            return
        }
        // A committed frame can be usable while a site's remaining resources are still loading.
        // Keep the same live styling and visual-state barrier before admitting page input.
        view.evaluateJavascript(PRESENTATION_SCRIPT) {
            view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (state.phase == Phase.LOADING && url == view.url) {
                        view.visibility = View.VISIBLE
                        if (finished) publish(Phase.READY)
                    }
                }
            })
        }
    }

    private fun publish(phase: Phase, error: String? = null) {
        state = State(phase, page?.url.orEmpty(), page?.title.orEmpty(),
            page?.canGoBack() == true, page?.canGoForward() == true, error)
        onStateChanged(state)
    }

    companion object {
        // Presentation only. No field values, DOM replacement, media inversion or native bridge.
        private const val COLOR_TARGETS = "html,body,p,div,span,section,article,header,footer,main,nav,form,label," +
            "input,textarea,select,button,table,td,th,ul,ol,li,a,[contenteditable]"
        private const val CSS = COLOR_TARGETS +
            "{background-color:var(--eyebrowse-local-background,transparent)!important;color:#fff!important}" +
            "html,body{background-color:#000!important}"
        private val PRESENTATION_SCRIPT = """(()=>{
            if(document.getElementById('eyebrowse-local-colors'))return;
            const targets=${JSONObject.quote(COLOR_TARGETS)};
            const style=document.createElement('style');style.id='eyebrowse-local-colors';
            style.textContent=${JSONObject.quote(CSS)};
            (document.head||document.documentElement).appendChild(style);
            const options={subtree:true,childList:true,attributes:true,attributeFilter:['class','style'],characterData:true};
            const collect=(root,set)=>{
                if(root.nodeType!==1)return;
                if(root.matches(targets))set.add(root);
                root.querySelectorAll(targets).forEach(e=>set.add(e));
            };
            const paint=elements=>{
                // Read authored alpha without our override. Transparent overlay hosts must stay transparent.
                style.sheet.disabled=true;
                const backgrounds=Array.from(elements,e=>{
                    const color=getComputedStyle(e).backgroundColor;
                    const alpha=color==='transparent'?'0':color.startsWith('rgba(')?color.slice(color.lastIndexOf(',')+1,-1).trim():'1';
                    return [e,'rgba(0,0,0,'+alpha+')'];
                });
                style.sheet.disabled=false;
                backgrounds.forEach(([e,color])=>{
                    if(e.style.getPropertyValue('--eyebrowse-local-background')!==color)
                        e.style.setProperty('--eyebrowse-local-background',color);
                });
            };
            const observer=new MutationObserver(records=>{
                observer.disconnect();
                const changed=new Set();
                records.forEach(record=>{
                    if(record.target===style)return;
                    if(record.type==='attributes')collect(record.target,changed);
                    if(record.type==='childList')record.addedNodes.forEach(node=>{
                        collect(node,changed);
                        if(node.nodeName==='STYLE'||node.nodeName==='LINK')collect(document.documentElement,changed);
                    });
                    if(record.target.nodeName==='STYLE'||record.target.parentElement?.nodeName==='STYLE')
                        collect(document.documentElement,changed);
                });
                paint(changed);observer.observe(document.documentElement,options);
            });
            paint(document.querySelectorAll(targets));observer.observe(document.documentElement,options);
        })()""".trimIndent()
    }
}
