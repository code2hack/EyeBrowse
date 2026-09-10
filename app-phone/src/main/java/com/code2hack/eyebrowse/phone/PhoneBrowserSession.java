package com.code2hack.eyebrowse.phone;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.code2hack.eyebrowse.core.browser.AddressPolicy;
import com.code2hack.eyebrowse.core.browser.NavigationPolicy;
import com.code2hack.eyebrowse.core.browser.StartupPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * The one application-owned Phone browser session: a single WebView, its callbacks and the
 * authoritative navigation state the UI observes.
 *
 * <p>All methods run on the UI thread. The session outlives a single Activity: it detaches from a
 * destroyed Activity (reverting to the application context) and reattaches the same live WebView to
 * a new one, so configuration changes do not reload the document, create a second page or leak the
 * dead Activity. After the renderer dies the dead WebView is disposed and the session is marked
 * not live; recovery is explicit (no automatic replay).
 */
final class PhoneBrowserSession {

    interface Listener {
        void onSessionChanged(PhoneBrowserSession session);
    }

    private static final String PREFS_NAME = "phone_browser";
    private static final String KEY_LAST_COMMITTED_URL = "last_committed_url";

    /**
     * Application-scoped single session. The context wrapper holds an Activity only while attached
     * and is reverted to the application context in {@link #detach()}.
     */
    @SuppressLint("StaticFieldLeak")
    private static PhoneBrowserSession instance;

    static synchronized PhoneBrowserSession get(Context context) {
        if (instance == null) {
            instance = new PhoneBrowserSession(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final SharedPreferences preferences;
    private final MutableContextWrapper contextWrapper;
    private final List<Listener> listeners = new ArrayList<>();

    private WebView webView;
    private ViewGroup attachedContainer;
    private boolean rendererGone;

    private String displayUrl;
    private String lastCommittedUrl;
    private String pageTitle;
    private boolean loading;
    private int progress;
    private String errorMessage;
    private String noticeMessage;
    private String failedNavigationUrl;

    private PhoneBrowserSession(Context appContext) {
        this.appContext = appContext;
        this.preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.contextWrapper = new MutableContextWrapper(appContext);
        String persisted = preferences.getString(KEY_LAST_COMMITTED_URL, null);
        this.lastCommittedUrl = NavigationPolicy.isAllowed(persisted) ? persisted : null;
    }

    // ---------------------------------------------------------------- state

    boolean isLive() {
        return webView != null && !rendererGone;
    }

    String displayUrl() {
        return displayUrl != null ? displayUrl : lastCommittedUrl;
    }

    String lastCommittedUrl() {
        return lastCommittedUrl;
    }

    String pageTitle() {
        return pageTitle;
    }

    boolean isLoading() {
        return loading;
    }

    int progress() {
        return progress;
    }

    String errorMessage() {
        return errorMessage;
    }

    String noticeMessage() {
        return noticeMessage;
    }

    boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    boolean canGoForward() {
        return webView != null && webView.canGoForward();
    }

    StartupPolicy.Decision startupDecision() {
        return StartupPolicy.decide(isLive(), lastCommittedUrl);
    }

    // ------------------------------------------------------------- commands

    /** Submits typed address-bar input; rejects without navigating and without touching the page. */
    AddressPolicy.Result openAddress(String input) {
        AddressPolicy.Result result = AddressPolicy.resolve(input);
        if (!result.accepted()) {
            noticeMessage = result.message();
            notifyListeners();
            return result;
        }
        noticeMessage = null;
        errorMessage = null;
        failedNavigationUrl = null;
        load(result.url());
        return result;
    }

    void goBack() {
        if (canGoBack()) {
            webView.goBack();
        }
    }

    void goForward() {
        if (canGoForward()) {
            webView.goForward();
        }
    }

    void reload() {
        if (webView != null) {
            webView.reload();
        }
    }

    /** Flushes persistent cookies; called at the Activity stop boundary. */
    void flushCookies() {
        CookieManager.getInstance().flush();
    }

    // ------------------------------------------------------- attach/detach

    void attach(Activity activity, ViewGroup container) {
        contextWrapper.setBaseContext(activity);
        attachedContainer = container;
        container.removeAllViews();
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null && parent != container) {
                parent.removeView(webView);
            }
            container.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            webView.requestLayout();
            webView.invalidate();
        }
        notifyListeners();
    }

    void detach() {
        attachedContainer = null;
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            // Size the released view from the application context, not the destroyed Activity.
            contextWrapper.setBaseContext(appContext);
        }
    }

    // ------------------------------------------------------- test-only seams

    /**
     * Test-only: return the session to a clean, never-loaded state so instrumentation cases are
     * independent. UI thread only; not reachable from the product UI.
     */
    void resetForTest() {
        disposeWebView();
        rendererGone = false;
        displayUrl = null;
        lastCommittedUrl = null;
        pageTitle = null;
        loading = false;
        progress = 0;
        errorMessage = null;
        noticeMessage = null;
        failedNavigationUrl = null;
        preferences.edit().clear().apply();
        notifyListeners();
    }

    /**
     * Test-only: emulate a fresh browser-process lifetime that still has the persisted last URL,
     * so the explicit recovery path can be exercised without restarting the test process.
     * UI thread only; not reachable from the product UI.
     */
    void simulateProcessRestartForTest() {
        disposeWebView();
        rendererGone = false;
        displayUrl = null;
        pageTitle = null;
        loading = false;
        progress = 0;
        errorMessage = null;
        noticeMessage = null;
        failedNavigationUrl = null;
        notifyListeners();
    }

    // ----------------------------------------------------------- listeners

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onSessionChanged(this);
        }
    }

    // --------------------------------------------------------- web plumbing

    private void load(String url) {
        ensureWebView().loadUrl(url);
    }

    private WebView ensureWebView() {
        if (webView != null) {
            return webView;
        }
        WebView view = new WebView(contextWrapper);
        view.setId(R.id.browser_web_view);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        view.setBackgroundColor(Color.WHITE);
        view.setWebViewClient(new Client());
        view.setWebChromeClient(new Chrome());
        webView = view;
        rendererGone = false;
        if (attachedContainer != null) {
            // The first load may arrive after the Activity attached; attach the new WebView here so
            // the loaded document is actually displayed instead of living in a detached view.
            attachedContainer.removeAllViews();
            attachedContainer.addView(view, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return view;
    }

    private void persistLastCommitted(String url) {
        lastCommittedUrl = url;
        preferences.edit().putString(KEY_LAST_COMMITTED_URL, url).apply();
    }

    private void disposeWebView() {
        if (webView == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) {
            parent.removeView(webView);
        }
        webView.destroy();
        webView = null;
    }

    private final class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return false;
            }
            String url = request.getUrl() == null ? null : request.getUrl().toString();
            if (NavigationPolicy.isAllowed(url)) {
                return false;
            }
            noticeMessage = appContext.getString(R.string.status_blocked);
            notifyListeners();
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (NavigationPolicy.isAllowed(url)) {
                displayUrl = url;
            }
            loading = true;
            progress = 0;
            notifyListeners();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            loading = false;
            if (NavigationPolicy.isAllowed(url)) {
                displayUrl = url;
                if (!url.equals(failedNavigationUrl)) {
                    persistLastCommitted(url);
                }
            }
            notifyListeners();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) {
                // Subresource failures never replace the whole navigation result.
                return;
            }
            String url = request.getUrl() == null ? null : request.getUrl().toString();
            failedNavigationUrl = url;
            loading = false;
            progress = 0;
            errorMessage = appContext.getString(R.string.status_load_failed, url);
            notifyListeners();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            handler.cancel();
            loading = false;
            progress = 0;
            errorMessage = appContext.getString(R.string.status_load_failed, error.getUrl());
            notifyListeners();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            disposeWebView();
            rendererGone = true;
            loading = false;
            progress = 0;
            errorMessage = appContext.getString(R.string.status_renderer_gone);
            notifyListeners();
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress = newProgress;
            notifyListeners();
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            pageTitle = title;
            notifyListeners();
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
            noticeMessage = appContext.getString(R.string.status_permission_denied);
            notifyListeners();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();
            noticeMessage = appContext.getString(R.string.status_permission_denied);
            notifyListeners();
        }

        @Override
        public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback,
                FileChooserParams params) {
            noticeMessage = appContext.getString(R.string.status_permission_denied);
            notifyListeners();
            return false;
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                Message resultMsg) {
            // Unreachable with multiple windows disabled; refusing keeps any future policy change
            // from silently creating a second browser context.
            return false;
        }
    }
}
