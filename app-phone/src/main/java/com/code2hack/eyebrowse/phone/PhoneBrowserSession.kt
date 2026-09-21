package com.code2hack.eyebrowse.phone

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import com.code2hack.eyebrowse.core.browser.AddressPolicy
import com.code2hack.eyebrowse.core.browser.NavigationPolicy
import com.code2hack.eyebrowse.core.browser.StartupPolicy

/**
 * The one application-owned Phone browser session: a single WebView, its callbacks and the
 * authoritative navigation state the UI observes.
 *
 * <p>All methods run on the UI thread. The session outlives a single Activity: it detaches from a
 * destroyed Activity (reverting to the application context) and reattaches the same live WebView to
 * a new one, so configuration changes do not reload the document, create a second page or leak the
 * dead Activity. After the renderer dies the dead WebView is disposed and the session is marked
 * not live; recovery is explicit (no automatic replay).
 *
 * <p>Migration note: the Java original was `static synchronized get(...)`; the explicit
 * `synchronized(PhoneBrowserSession::class.java)` block preserves the class monitor. `Client` and
 * `Chrome` were non-static inner classes and stay `inner`. `Attachment` keeps its
 * package-private-equivalent constructor (`internal`); identity comparisons use `===` where the
 * Java original relied on reference equality. Visibility widened package-private -> public for the
 * separately compiled androidTest consumer (disclosed in the migration ledger).
 */
class PhoneBrowserSession private constructor(private val appContext: Context) {

    fun interface Listener {
        fun onSessionChanged(session: PhoneBrowserSession)
    }

    /**
     * Opaque attachment ownership token. Only the owner of the current token may detach the
     * WebView, so an old Activity's {@code onDestroy()} can never detach a newly attached Activity
     * or the offscreen hosting presentation.
     */
    class Attachment internal constructor()

    private val preferences: android.content.SharedPreferences =
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val contextWrapper = MutableContextWrapper(appContext)
    private val listeners = ArrayList<Listener>()

    private var webView: WebView? = null
    private var attachedContainer: ViewGroup? = null
    private var currentAttachment: Attachment? = null
    private var rendererGone = false
    @Volatile private var documentId = java.util.UUID.randomUUID().toString()
    fun documentIdentity(): String = documentId

    private var displayUrl: String? = null
    private var lastCommittedUrl: String? =
            preferences.getString(KEY_LAST_COMMITTED_URL, null)?.takeIf { NavigationPolicy.isAllowed(it) }
    private var pageTitle: String? = null
    private var loading = false
    private var progress = 0
    private var errorMessage: String? = null
    private var noticeMessage: String? = null
    private var failedNavigationUrl: String? = null

    // ---------------------------------------------------------------- state

    fun isLive(): Boolean {
        return webView != null && !rendererGone
    }

    fun displayUrl(): String? {
        return displayUrl ?: lastCommittedUrl
    }

    fun lastCommittedUrl(): String? {
        return lastCommittedUrl
    }

    fun pageTitle(): String? {
        return pageTitle
    }

    fun isLoading(): Boolean {
        return loading
    }

    fun progress(): Int {
        return progress
    }

    fun errorMessage(): String? {
        return errorMessage
    }

    fun noticeMessage(): String? {
        return noticeMessage
    }

    fun canGoBack(): Boolean {
        val view = webView
        return view != null && view.canGoBack()
    }

    fun canGoForward(): Boolean {
        val view = webView
        return view != null && view.canGoForward()
    }

    fun startupDecision(): StartupPolicy.Decision {
        return StartupPolicy.decide(isLive(), lastCommittedUrl)
    }

    // ------------------------------------------------------------- commands

    /** Submits typed address-bar input; rejects without navigating and without touching the page. */
    fun openAddress(input: String?): AddressPolicy.Result {
        val result = AddressPolicy.resolve(input)
        if (!result.accepted()) {
            noticeMessage = result.message()
            notifyListeners()
            return result
        }
        noticeMessage = null
        errorMessage = null
        failedNavigationUrl = null
        load(result.url())
        return result
    }

    fun goBack() {
        val view = webView
        if (canGoBack() && view != null) {
            view.goBack()
        }
    }

    fun goForward() {
        val view = webView
        if (canGoForward() && view != null) {
            view.goForward()
        }
    }

    fun reload() {
        webView?.reload()
    }

    /** Flushes persistent cookies; called at the Activity stop boundary. */
    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    // ------------------------------------------------------- attach/detach

    /** Takes attachment ownership for {@code activity} and returns its token. */
    fun attach(activity: Activity?, container: ViewGroup?): Attachment {
        val attachment = Attachment()
        currentAttachment = attachment
        attachToContainer(activity, container)
        return attachment
    }

    /**
     * Detaches only when {@code attachment} is still the current owner; a stale token (a destroyed
     * Activity whose view was since reattached elsewhere) is a no-op that must not steal the
     * WebView from the current owner.
     */
    fun detach(attachment: Attachment?) {
        if (attachment == null || attachment !== currentAttachment) {
            return
        }
        currentAttachment = null
        attachedContainer = null
        val view = webView
        if (view != null) {
            val parent = view.parent as? ViewGroup
            if (parent != null) {
                parent.removeView(view)
            }
            // Size the released view from the application context, not the destroyed Activity.
            contextWrapper.setBaseContext(appContext)
        }
        notifyListeners()
    }

    /**
     * Moves the live WebView into the hosting presentation's container. The base context becomes
     * {@code baseContext} (the presentation display/window context), releasing any Activity reference so a
     * backgrounded or destroyed Activity cannot leak through the wrapper.
     */
    fun attachExternal(container: ViewGroup?, baseContext: Context?) {
        currentAttachment = null
        attachToContainer(baseContext, container)
    }

    /**
     * Releases the attachment the hosting presentation owns: acts only when the WebView is
     * actually hosted in {@code container}, releasing that container reference and the external
     * base context. A view living in a Phone Activity attachment (successor or current) is left
     * untouched, preserving its container and live Activity context (F5).
     */
    fun detachExternal(container: ViewGroup?) {
        val view = webView
        if (view == null) {
            // The view is already gone (e.g. renderer loss disposed it): still release the
            // external context ownership so the hosting service context does not stay rooted
            // through the wrapper when cleanup relies on this path (R7).
            if (contextWrapper.baseContext !== appContext) {
                contextWrapper.setBaseContext(appContext)
            }
            if (attachedContainer === container) {
                attachedContainer = null
            }
            return
        }
        if (view.parent !== container) {
            return // Not hosted here: a Phone attachment's container/context stays untouched.
        }
        container!!.removeView(view)
        if (contextWrapper.baseContext !== appContext) {
            contextWrapper.setBaseContext(appContext)
        }
        if (attachedContainer === container) {
            attachedContainer = null
        }
        notifyListeners()
    }

    /** The one live WebView, or {@code null}; same-package access for the hosting seam. */
    fun view(): WebView? {
        return webView
    }

    /**
     * Requests a fresh draw after the capture path armed a new/recreated reader and drained
     * pre-arm buffers. May be invoked from the capture thread; View.post performs the actual
     * invalidation on the WebView/UI thread and fences renderer replacement.
     */
    fun requestFreshCaptureFrame(isCurrentOwner: () -> Boolean = { true }) {
        val target = webView ?: return
        target.post {
            if (webView !== target || rendererGone || !isCurrentOwner()) {
                return@post
            }
            target.requestLayout()
            target.invalidate()
            target.postInvalidateOnAnimation()
        }
    }

    /** True when {@code attachment} is still the current owner of the WebView. */
    fun isCurrentAttachment(attachment: Attachment?): Boolean {
        return attachment != null && attachment === currentAttachment
    }

    private fun attachToContainer(baseContext: Context?, container: ViewGroup?) {
        val target = container!!
        val view = webView
        val oldParent = view?.parent as? ViewGroup
        val alreadyInTarget = view != null && oldParent === target

        for (step in BrowserAttachmentTransfer.plan(view != null, alreadyInTarget)) {
            when (step) {
                BrowserAttachmentTransfer.Step.DETACH_OLD_PARENT -> {
                    // WebView.onDetachedFromWindow must still observe the OLD owner/display
                    // context. Switching the MutableContextWrapper first can break Chromium's
                    // detach path while crossing Activity/private-display ownership.
                    oldParent?.removeView(view)
                }
                BrowserAttachmentTransfer.Step.UPDATE_CONTEXT -> {
                    if (baseContext != null) {
                        contextWrapper.setBaseContext(baseContext)
                    }
                    attachedContainer = target
                }
                BrowserAttachmentTransfer.Step.CLEAR_TARGET -> target.removeAllViews()
                BrowserAttachmentTransfer.Step.ATTACH_TARGET -> {
                    target.addView(
                        view!!,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            }
        }

        if (view != null) {
            // A same-parent claim must reconcile layout without a gratuitous detach/re-add cycle.
            view.requestLayout()
            view.invalidate()
        }
        notifyListeners()
    }

    // ------------------------------------------------------- test-only seams

    /**
     * Test-only: return the session to a clean, never-loaded state so instrumentation cases are
     * independent. UI thread only; not reachable from the product UI.
     */
    fun resetForTest() {
        disposeWebView()
        rendererGone = false
        displayUrl = null
        lastCommittedUrl = null
        pageTitle = null
        loading = false
        progress = 0
        errorMessage = null
        noticeMessage = null
        failedNavigationUrl = null
        preferences.edit().clear().apply()
        notifyListeners()
    }

    /**
     * Test-only: emulate a fresh browser-process lifetime that still has the persisted last URL,
     * so the explicit recovery path can be exercised without restarting the test process.
     * UI thread only; not reachable from the product UI.
     */
    fun simulateProcessRestartForTest() {
        disposeWebView()
        rendererGone = false
        displayUrl = null
        pageTitle = null
        loading = false
        progress = 0
        errorMessage = null
        noticeMessage = null
        failedNavigationUrl = null
        notifyListeners()
    }

    // ----------------------------------------------------------- listeners

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in ArrayList(listeners)) {
            listener.onSessionChanged(this)
        }
    }

    // --------------------------------------------------------- web plumbing

    private fun load(url: String) {
        ensureWebView().loadUrl(url)
    }

    private fun ensureWebView(): WebView {
        val existing = webView
        if (existing != null) {
            return existing
        }
        val view = WebView(contextWrapper)
        view.id = R.id.browser_web_view
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Hosting moves this single authoritative WebView onto an app-owned private Presentation.
        // Keep Chromium raster tiles live while the physical Phone Activity is offscreen; this is
        // the same platform setting validated by experiments/locked-webview-spike.
        settings.offscreenPreRaster = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setGeolocationEnabled(false)
        settings.saveFormData = false
        view.setBackgroundColor(Color.WHITE)
        view.webViewClient = Client()
        view.webChromeClient = Chrome()
        webView = view
        rendererGone = false
        val container = attachedContainer
        if (container != null) {
            // The first load may arrive after the Activity attached; attach the new WebView here so
            // the loaded document is actually displayed instead of living in a detached view.
            container.removeAllViews()
            container.addView(view, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
        }
        return view
    }

    private fun persistLastCommitted(url: String?) {
        lastCommittedUrl = url
        preferences.edit().putString(KEY_LAST_COMMITTED_URL, url).apply()
    }

    private fun disposeWebView() {
        val view = webView ?: return
        val parent = view.parent as? ViewGroup
        if (parent != null) {
            parent.removeView(view)
        }
        view.destroy()
        webView = null
    }

    inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) {
                return false
            }
            val url = request.url?.toString()
            if (NavigationPolicy.isAllowed(url)) {
                return false
            }
            noticeMessage = appContext.getString(R.string.status_blocked)
            notifyListeners()
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            documentId = java.util.UUID.randomUUID().toString()
            if (NavigationPolicy.isAllowed(url)) {
                displayUrl = url
                // A new main-frame navigation supersedes earlier refusal/error feedback, so a stale
                // notice can never be mistaken for the result of the current action.
                noticeMessage = null
                errorMessage = null
            }
            loading = true
            progress = 0
            notifyListeners()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            loading = false
            if (NavigationPolicy.isAllowed(url) && url != null) {
                displayUrl = url
                if (url != failedNavigationUrl) {
                    persistLastCommitted(url)
                }
            }
            notifyListeners()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest,
                error: WebResourceError) {
            if (!request.isForMainFrame) {
                // Subresource failures never replace the whole navigation result.
                return
            }
            val url = request.url?.toString()
            failedNavigationUrl = url
            loading = false
            progress = 0
            errorMessage = appContext.getString(R.string.status_load_failed, url)
            notifyListeners()
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler,
                error: android.net.http.SslError) {
            handler.cancel()
            loading = false
            progress = 0
            errorMessage = appContext.getString(R.string.status_load_failed, error.url)
            notifyListeners()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            disposeWebView()
            rendererGone = true
            documentId = java.util.UUID.randomUUID().toString()
            currentAttachment = null
            attachedContainer = null
            loading = false
            progress = 0
            errorMessage = appContext.getString(R.string.status_renderer_gone)
            notifyListeners()
            return true
        }
    }

    inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress
            notifyListeners()
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            pageTitle = title
            notifyListeners()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String,
                callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
            noticeMessage = appContext.getString(R.string.status_permission_denied)
            notifyListeners()
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
            noticeMessage = appContext.getString(R.string.status_permission_denied)
            notifyListeners()
        }

        override fun onShowFileChooser(view: WebView,
                callback: android.webkit.ValueCallback<Array<android.net.Uri>>,
                params: FileChooserParams): Boolean {
            noticeMessage = appContext.getString(R.string.status_permission_denied)
            notifyListeners()
            return false
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean,
                resultMsg: Message): Boolean {
            // Unreachable with multiple windows disabled; refusing keeps any future policy change
            // from silently creating a second browser context.
            return false
        }
    }

    companion object {
        private const val PREFS_NAME = "phone_browser"
        private const val KEY_LAST_COMMITTED_URL = "last_committed_url"

        /**
         * Application-scoped single session. The context wrapper holds an Activity only while
         * attached and is reverted to the application context in {@link #detach}.
         */
        @SuppressLint("StaticFieldLeak")
        private var instance: PhoneBrowserSession? = null

        @JvmStatic
        fun get(context: Context?): PhoneBrowserSession {
            // Java original: `static synchronized get(...)` — monitor is PhoneBrowserSession.class.
            synchronized(PhoneBrowserSession::class.java) {
                if (instance == null) {
                    instance = PhoneBrowserSession(context!!.applicationContext)
                }
                return instance!!
            }
        }
    }
}
