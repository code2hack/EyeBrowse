package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only navigation preparation. Its diagnostic layout is NOT the HUD-G1 reference. */
class LocalBrowserPreparationActivity : Activity() {
    internal lateinit var root: FrameLayout
    internal lateinit var session: LocalBrowserSession
    internal lateinit var pointer: PointerOverlay
    internal lateinit var router: RgInputRouter
    internal lateinit var input: NativeRgInputTarget
    internal lateinit var address: EditText
    internal lateinit var status: TextView
    internal lateinit var keyboard: LinearLayout
    internal val controls = linkedMapOf<String, Button>()
    internal val keyButtons = linkedMapOf<RgKeyboardKeys.Key, Button>()
    private lateinit var back: Button
    private lateinit var forward: Button
    private lateinit var refresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this).apply {
            id = R.id.rg_root
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
            // This focus holder must not paint Material's highlight over the entire black page.
            defaultFocusHighlightEnabled = false
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            setTextColor(Color.WHITE); setBackgroundColor(Color.BLACK); textSize = 12f
            text = "DEVELOPMENT PREPARATION · HUD-G1 OPEN"
        }
        column.addView(status, LinearLayout.LayoutParams(-1, -2))
        address = EditText(this).apply {
            hint = "Full HTTP(S) address"
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.BLACK); showSoftInputOnFocus = false
            textSize = 14f
            setOnFocusChangeListener { _, focused -> if (focused && ::keyboard.isInitialized) showKeyboard() }
            setOnKeyListener { _, code, event ->
                if (code != KeyEvent.KEYCODE_ENTER) false else {
                    if (event.action == KeyEvent.ACTION_UP) submitAddress()
                    true
                }
            }
        }
        column.addView(address, LinearLayout.LayoutParams(-1, -2))
        val navigation = LinearLayout(this)
        column.addView(navigation, LinearLayout.LayoutParams(-1, 40))
        control(navigation, "Open") { submitAddress() }
        back = control(navigation, "Back") { session.back() }
        forward = control(navigation, "Forward") { session.forward() }
        refresh = control(navigation, "Refresh") { session.refresh() }
        control(navigation, "Keys") { showKeyboard() }
        session = LocalBrowserSession(this)
        column.addView(session.surface, LinearLayout.LayoutParams(-1, 0, 1f))
        keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); visibility = View.GONE
        }
        column.addView(keyboard, LinearLayout.LayoutParams(-1, -2))
        pointer = PointerOverlay(this)
        root.addView(pointer, FrameLayout.LayoutParams(-1, -1))
        input = NativeRgInputTarget(root, pointer, { session.page }) { dismissKeyboard() }
        router = RgInputRouter(this, pointer, root, input) {}
        // Production tab selection and edge policy are held/later work, not diagnostic substitutes.
        pointer.onAvailabilityChanged = { if (!it) router.cancel() }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            pointer.bounds(0f, 0f, root.width.toFloat(), root.height.toFloat(), display?.rotation ?: 0)
        }
        session.onStateChanged = { updatePageState(it) }
        renderKeys()
        setContentView(root)
        root.requestFocus()
        updatePageState(session.state)
    }

    private fun control(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label; textSize = 11f; setTextColor(Color.WHITE)
            isFocusable = false; isFocusableInTouchMode = false
            minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = GradientDrawable().apply {
                setColor(Color.BLACK); setStroke(1, Color.GRAY)
            }
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(0, -1, 1f))
        controls[label] = button
        return button
    }

    internal fun submitAddress() {
        val result = session.open(address.text.toString())
        if (!result.accepted()) {
            status.text = result.message()
            return
        }
        dismissKeyboard()
        session.page?.requestFocus()
    }

    private fun updatePageState(state: LocalBrowserSession.State) {
        if (!address.hasFocus() && state.url.isNotEmpty()) address.setText(state.url)
        back.isEnabled = state.canGoBack
        forward.isEnabled = state.canGoForward
        refresh.isEnabled = state.phase != LocalBrowserSession.Phase.EMPTY && session.page != null
        status.text = state.error ?: when (state.phase) {
            LocalBrowserSession.Phase.EMPTY -> "DEVELOPMENT PREPARATION · Enter an address"
            LocalBrowserSession.Phase.LOADING -> "Loading local page"
            LocalBrowserSession.Phase.READY -> state.title.ifEmpty { "Local page ready" }
            else -> "Page unavailable; Open an address to recover"
        }
    }

    internal fun showKeyboard() { keyboard.visibility = View.VISIBLE }
    internal fun dismissKeyboard() {
        keyboard.visibility = View.GONE
        if (address.hasFocus()) { address.clearFocus(); root.requestFocus() }
    }

    private fun renderKeys() {
        keyboard.removeAllViews(); keyButtons.clear()
        val rows = input.keys.rows() + listOf(listOf(
            RgKeyboardKeys.Key.Command.SHIFT, RgKeyboardKeys.Key.Command.SYMBOLS,
            RgKeyboardKeys.Key.Command.SPACE, RgKeyboardKeys.Key.Command.BACKSPACE,
            RgKeyboardKeys.Key.Command.ENTER, RgKeyboardKeys.Key.Command.DONE,
        ))
        for (keys in rows) {
            val row = LinearLayout(this)
            keyboard.addView(row, LinearLayout.LayoutParams(-1, 36))
            for (key in keys) {
                val label = when (key) {
                    is RgKeyboardKeys.Key.Character -> key.text
                    RgKeyboardKeys.Key.Command.SYMBOLS -> if (input.keys.symbols) "ABC" else "123"
                    else -> (key as RgKeyboardKeys.Key.Command).name.lowercase().replaceFirstChar { it.uppercase() }
                }
                keyButtons[key] = control(row, label) {
                    input.key(key)
                    if (key == RgKeyboardKeys.Key.Command.SHIFT || key == RgKeyboardKeys.Key.Command.SYMBOLS) renderKeys()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::router.isInitialized && router.key(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK && keyboard.isShown) {
            if (event.action == KeyEvent.ACTION_UP) dismissKeyboard()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::router.isInitialized) router.focus(hasFocus)
    }
    override fun onResume() { super.onResume(); session.resume(); pointer.start(); router.resume() }
    override fun onPause() {
        router.pause(); pointer.stop(); dismissKeyboard(); session.pause(); super.onPause()
    }
    override fun onDestroy() {
        router.pause(); pointer.stop(); session.destroy(); super.onDestroy()
    }
}
