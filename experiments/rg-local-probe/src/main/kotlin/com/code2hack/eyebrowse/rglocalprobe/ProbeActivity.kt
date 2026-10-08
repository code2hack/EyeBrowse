package com.code2hack.eyebrowse.rglocalprobe

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.code2hack.eyebrowse.core.browser.AddressPolicy
import org.json.JSONObject

/** Separate-package diagnostic window; direct public input, not the production HUD. */
class ProbeActivity : Activity() {
    lateinit var web: WebView
        private set
    lateinit var address: EditText
        private set
    lateinit var status: TextView
        private set
    val controls = linkedMapOf<String, Button>()
    private lateinit var keys: LinearLayout
    private var engineDarkeningOnly = false
    var pauseCount = 0L
        private set
    var networkError: Int? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineDarkeningOnly = intent.getBooleanExtra("engineDarkeningOnly", false)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        address = EditText(this).apply {
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 12f
            showSoftInputOnFocus = false
            setOnFocusChangeListener { _, focused -> if (focused && ::keys.isInitialized) showKeys(true) }
        }
        root.addView(address, LinearLayout.LayoutParams(-1, 42))
        val navigation = LinearLayout(this)
        root.addView(navigation, LinearLayout.LayoutParams(-1, 42))
        button(navigation, "Open") { open(address.text.toString()) }
        button(navigation, "Back") { if (web.canGoBack()) web.goBack() }
        button(navigation, "Forward") { if (web.canGoForward()) web.goForward() }
        button(navigation, "Reload") { web.reload() }
        // Do not request focus again: provider95 can select its first field on that request.
        button(navigation, "Field") { showKeys(true) }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 10f
            text = "Direct native input qualification"
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 24))
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            // Native keyboard-focus highlighting otherwise lightens the whole rendered page.
            defaultFocusHighlightEnabled = false
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            @Suppress("DEPRECATION")
            settings.forceDark = if (engineDarkeningOnly) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    networkError = null
                    // Keep ordinary native focus while black backing covers loading pixels.
                    view.alpha = 0f
                    status.text = "Loading"
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    if (networkError == null) {
                        if (!address.hasFocus()) address.setText(view.url.orEmpty())
                        if (!engineDarkeningOnly) applyProbeColors(view)
                        else view.alpha = 1f
                        status.text = "Local page ready"
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        networkError = error.errorCode
                        status.text = "Network error ${error.errorCode}"
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !AddressPolicy.resolve(request.url.toString()).accepted()
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        keys = LinearLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(keys, LinearLayout.LayoutParams(-1, 160))
        button(keys, "A") { insert("a") }
        button(keys, "B") { insert("b") }
        button(keys, "Space") { insert(" ") }
        button(keys, "Delete") {
            if (address.hasFocus()) {
                val start = address.selectionStart.coerceAtLeast(0)
                val end = address.selectionEnd.coerceAtLeast(start)
                if (start != end) address.text.delete(start, end)
                else if (start > 0) address.text.delete(Character.offsetByCodePoints(address.text, start, -1), start)
            } else nativeKey(KeyEvent.KEYCODE_DEL)
        }
        button(keys, "Enter") {
            if (address.hasFocus()) open(address.text.toString()) else nativeKey(KeyEvent.KEYCODE_ENTER)
        }
        button(keys, "Done") { showKeys(false) }
        setContentView(root)
        intent.getStringExtra("fixtureUrl")?.let { address.setText(it); open(it) }
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 9f
            isFocusable = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            setOnClickListener { action() }
        }
        controls[label] = button
        parent.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
    }

    fun open(draft: String) {
        val result = AddressPolicy.resolve(draft)
        if (!result.accepted()) { status.text = "Invalid address"; return }
        web.requestFocus()
        showKeys(false)
        // Cover the pending request immediately; page callbacks may wait for response data.
        web.alpha = 0f
        status.text = "Loading"
        web.loadUrl(result.url()!!)
    }

    private fun insert(text: String) {
        if (address.hasFocus()) {
            val start = address.selectionStart.coerceAtLeast(0)
            val end = address.selectionEnd.coerceAtLeast(start)
            address.text.replace(start, end, text)
        } else web.onCreateInputConnection(EditorInfo())?.commitText(text, 1)
    }

    private fun nativeKey(code: Int) {
        val connection = web.onCreateInputConnection(EditorInfo()) ?: return
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Fixed presentation-only experiment: no value edits, media filters or page replacement. */
    private fun applyProbeColors(view: WebView) {
        val css = "html,body,p,div,span,section,article,header,footer,main,nav,form,label," +
            "input,textarea,select,button,table,td,th,ul,ol,li,a,[contenteditable]" +
            "{background-color:#000!important;color:#fff!important}"
        view.evaluateJavascript("""(()=>{
            let style=document.getElementById('rg-probe-colors');
            if(!style){style=document.createElement('style');style.id='rg-probe-colors';document.head.appendChild(style)}
            style.textContent=${JSONObject.quote(css)};
        })()""") {
            view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { view.alpha = 1f }
            })
        }
    }

    fun showKeys(visible: Boolean) { keys.visibility = if (visible) View.VISIBLE else View.GONE }
    override fun onPause() { pauseCount++; showKeys(false); web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onDestroy() { web.stopLoading(); web.destroy(); super.onDestroy() }
}
