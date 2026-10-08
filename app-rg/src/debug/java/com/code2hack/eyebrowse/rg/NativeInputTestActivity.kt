package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Debug-only owned page/controls for #29 direct dispatch; no legacy controller, link or image. */
class NativeInputTestActivity : Activity() {
    internal lateinit var root: FrameLayout
    internal lateinit var pointer: PointerOverlay
    internal lateinit var router: RgInputRouter
    internal lateinit var input: NativeRgInputTarget
    internal lateinit var page: WebView
    internal lateinit var button: Button
    internal lateinit var keyboard: LinearLayout
    internal val buttons = mutableMapOf<RgKeyboardKeys.Key, Button>()
    internal var activations = 0
    internal var dismissals = 0
    internal var pageReady = false
    internal var keyDispatchMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { id = R.id.rg_root; setBackgroundColor(Color.BLACK) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        button = Button(this).apply {
            text = "Native activation fixture"
            setOnClickListener { activations++; text = "Activation count: $activations" }
        }
        column.addView(button, LinearLayout.LayoutParams(-1, 80))
        page = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { pageReady = true }
            }
        }
        column.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        pointer = PointerOverlay(this)
        root.addView(pointer, FrameLayout.LayoutParams(-1, -1))
        input = NativeRgInputTarget(root, pointer, { page }) { dismissals++; keyboard.visibility = View.GONE }
        keyboard = LinearLayout(this)
        for (key in listOf(RgKeyboardKeys.Key.Character("x"), RgKeyboardKeys.Key.Command.BACKSPACE,
                RgKeyboardKeys.Key.Command.ENTER, RgKeyboardKeys.Key.Command.DONE)) {
            val nativeKey = Button(this).apply {
                isFocusable = false; isFocusableInTouchMode = false
                text = when (key) {
                    is RgKeyboardKeys.Key.Character -> key.text
                    else -> (key as RgKeyboardKeys.Key.Command).name
                }
                setOnClickListener {
                    val started = SystemClock.uptimeMillis()
                    input.key(key)
                    keyDispatchMs = SystemClock.uptimeMillis() - started
                }
            }
            buttons[key] = nativeKey
            keyboard.addView(nativeKey, LinearLayout.LayoutParams(0, 60, 1f))
        }
        column.addView(keyboard, LinearLayout.LayoutParams(-1, 60))
        setContentView(root)
        router = RgInputRouter(this, pointer, root, input) {}
        pointer.onAvailabilityChanged = { if (!it) router.cancel() }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pointer.bounds(0f, 0f, root.width.toFloat(), root.height.toFloat(), display?.rotation ?: 0)
        }
        page.loadDataWithBaseURL("https://native-input.invalid/", """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{background:black;color:white;margin:8px}input,textarea{display:block;
            width:90%;height:44px;margin:8px 0;font:20px monospace}</style>
            <input id="text"><input id="password" type="password">
            <textarea id="multiline"></textarea><div style="height:1500px"></div>
        """.trimIndent(), "text/html", "UTF-8", null)
    }

    override fun dispatchKeyEvent(event: KeyEvent) =
        if (::router.isInitialized && router.key(event)) true else super.dispatchKeyEvent(event)
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::router.isInitialized) router.focus(hasFocus)
    }
    override fun onResume() { super.onResume(); pointer.start(); router.resume() }
    override fun onPause() { router.pause(); pointer.stop(); super.onPause() }
    override fun onDestroy() { router.pause(); pointer.stop(); page.destroy(); super.onDestroy() }
}
