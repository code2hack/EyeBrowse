package com.code2hack.eyebrowse.rg

import android.graphics.Matrix
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import org.json.JSONArray

/** Direct View/WebView input. Android owns hit testing, selection and current editor focus. */
internal class NativeRgInputTarget(
    private val root: ViewGroup,
    private val pointer: PointerOverlay,
    private val page: () -> WebView?,
    private val dismissKeyboard: () -> Unit,
) : RgInputTarget {
    val keys = RgKeyboardKeys()
    private var selectingScroll: WebView? = null

    override fun activate(point: InputPoint): Boolean {
        if (!root.isShown || !point.x.isFinite() || !point.y.isFinite()) return false
        val pointerToGlobal = Matrix().apply { pointer.transformMatrixToGlobal(this) }
        val rootToGlobal = Matrix().apply { root.transformMatrixToGlobal(this) }
        val globalToRoot = Matrix()
        if (!rootToGlobal.invert(globalToRoot)) return false
        val position = floatArrayOf(point.x, point.y)
        pointerToGlobal.mapPoints(position); globalToRoot.mapPoints(position)
        if (!PointerBounds(0f, 0f, root.width.toFloat(), root.height.toFloat())
                .contains(InputPoint(position[0], position[1]))) return false
        val now = SystemClock.uptimeMillis()
        fun event(action: Int): Boolean {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, position[0], position[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            return try { root.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        val down = event(MotionEvent.ACTION_DOWN)
        val up = event(MotionEvent.ACTION_UP)
        return down && up
    }

    override fun scroll(delta: Int): Boolean = scroll(delta) { true }

    /** One geometry request at a time; dropped distance is never queued for later input. */
    fun scroll(delta: Int, stillApplicable: () -> Boolean): Boolean {
        val visible = page()?.takeIf { it.isShown } ?: return false
        if (delta == 0 || visible.width <= 0 || visible.height <= 0 || selectingScroll === visible) return false
        val pointerToGlobal = Matrix().apply { pointer.transformMatrixToGlobal(this) }
        val pageToGlobal = Matrix().apply { visible.transformMatrixToGlobal(this) }
        val globalToPage = Matrix()
        if (!pageToGlobal.invert(globalToPage)) return false
        val point = floatArrayOf(pointer.position.x, pointer.position.y)
        pointerToGlobal.mapPoints(point); globalToPage.mapPoints(point)
        if (point.any { !it.isFinite() }) return false
        val started = SystemClock.uptimeMillis()
        selectingScroll = visible
        visible.evaluateJavascript("$BROWSER_SCROLL_TARGET(${point[0]/visible.width},${point[1]/visible.height},$delta,${visible.width})") { value ->
            if (selectingScroll === visible) selectingScroll = null
            if (SystemClock.uptimeMillis()-started > 50 || page() !== visible || !visible.isShown ||
                !root.isShown || !visible.hasWindowFocus() || !stillApplicable()) return@evaluateJavascript
            val target = try { JSONArray(value) } catch (_: org.json.JSONException) { return@evaluateJavascript }
            if (target.length() != 3) return@evaluateJavascript
            val x = target.optDouble(0); val y = target.optDouble(1); val pixels = target.optDouble(2)
            if (!x.isFinite() || !y.isFinite() || x !in 0.0..<1.0 || y !in 0.0..<1.0 ||
                !pixels.isFinite() || pixels*delta <= 0 || kotlin.math.abs(pixels) > kotlin.math.abs(delta.toDouble())) return@evaluateJavascript
            val factor = ViewConfiguration.get(visible.context).scaledVerticalScrollFactor
            if (!factor.isFinite() || factor <= 0) return@evaluateJavascript
            val coordinates = MotionEvent.PointerCoords().apply {
                this.x = (x*visible.width).toFloat(); this.y = (y*visible.height).toFloat()
                setAxisValue(MotionEvent.AXIS_VSCROLL, (-pixels/factor).toFloat())
            }
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties),
                arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            try { visible.dispatchGenericMotionEvent(event) } finally { event.recycle() }
        }
        return true
    }

    override fun key(key: RgKeyboardKeys.Key): Boolean {
        if (keys.changeLayer(key)) return true
        if (key == RgKeyboardKeys.Key.Command.DONE) { dismissKeyboard(); return true }
        val focused = root.findFocus()?.takeIf { it.isShown } ?: return false
        val connection = focused.onCreateInputConnection(EditorInfo()) ?: return false
        return when (key) {
            is RgKeyboardKeys.Key.Character -> connection.commitText(key.text, 1)
            RgKeyboardKeys.Key.Command.SPACE -> connection.commitText(" ", 1)
            else -> {
                val code = when (key) {
                    RgKeyboardKeys.Key.Command.BACKSPACE -> KeyEvent.KEYCODE_DEL
                    RgKeyboardKeys.Key.Command.ENTER -> KeyEvent.KEYCODE_ENTER
                    RgKeyboardKeys.Key.Command.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    RgKeyboardKeys.Key.Command.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                    else -> return false
                }
                val down = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                val up = connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                down && up
            }
        }
    }
}
