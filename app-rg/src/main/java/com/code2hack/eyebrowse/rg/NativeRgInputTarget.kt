package com.code2hack.eyebrowse.rg

import android.graphics.Matrix
import android.os.SystemClock
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo

/** Direct View/WebView input. Android owns hit testing, selection and current editor focus. */
internal class NativeRgInputTarget(
    private val root: ViewGroup,
    private val pointer: View,
    private val page: () -> View?,
    private val dismissKeyboard: () -> Unit,
) : RgInputTarget {
    val keys = RgKeyboardKeys()

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

    override fun scroll(delta: Int): Boolean {
        val visible = page()?.takeIf { it.isShown } ?: return false
        visible.scrollBy(0, delta)
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
