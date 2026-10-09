package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration

/** One pad consumer; a confirmed tap acts on the current pointer and current native View tree. */
internal class RgInputRouter(
    private val activity: Activity,
    private val pointer: PointerOverlay,
    private val surface: View,
    private val target: RgInputTarget,
    private val feedback: (Boolean) -> Unit,
) {
    /** The local Activity supplies its tab gesture binding; no legacy mode/scroll binding is inherited. */
    var onSwipe: (Int) -> Unit = {}
    internal var lastDispatchMs: Long? = null
        private set
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var focused = false
    private val gestures = PadGestureRecognizer<Unit>(
        ViewConfiguration.getDoubleTapTimeout().toLong().coerceIn(100, 600),
        ViewConfiguration.getLongPressTimeout().toLong().coerceIn(200, 2_000),
        { Unit }, { if (usable()) {
            val point = pointer.inputPosition()
            val started = SystemClock.uptimeMillis()
            val accepted = target.activate(InputPoint(point.x, point.y))
            lastDispatchMs = SystemClock.uptimeMillis() - started
            feedback(accepted)
        } }, {}, { if (usable()) onSwipe(it) },
    )
    private val confirmation = Runnable {
        if (usable()) gestures.confirm(SystemClock.uptimeMillis()) else cancel()
        schedule()
    }

    fun resume() { active = true; focused = activity.hasWindowFocus() }
    fun pause() { active = false; cancel() }
    fun focus(value: Boolean) { focused = value; if (!value) cancel() }
    fun cancel() { gestures.cancel(); main.removeCallbacks(confirmation) }
    private fun usable() = active && focused && activity.hasWindowFocus() && surface.isShown &&
        pointer.inputPosition().available

    fun key(event: KeyEvent): Boolean {
        val key = nativePadKey(event.device?.name, event.source, event.keyCode) ?: return false
        if (!usable() || event.metaState != 0) { cancel(); feedback(false); return true }
        val phase = when {
            event.isCanceled || event.isLongPress -> PadGestureRecognizer.Phase.CANCEL
            event.action == KeyEvent.ACTION_DOWN -> PadGestureRecognizer.Phase.DOWN
            event.action == KeyEvent.ACTION_UP -> PadGestureRecognizer.Phase.UP
            else -> PadGestureRecognizer.Phase.CANCEL
        }
        gestures.accept(PadGestureRecognizer.Event(key, phase, event.eventTime, event.downTime, event.repeatCount),
            SystemClock.uptimeMillis())
        schedule()
        return true // The focused native View must not also consume this pad sequence.
    }

    private fun schedule() {
        main.removeCallbacks(confirmation)
        if (active) gestures.deadline?.let { main.postAtTime(confirmation, it) }
    }
}

/** Native admission shared by the router and its JVM regression; all other keyboards pass through. */
internal fun nativePadKey(deviceName: String?, source: Int, keyCode: Int): PadGestureRecognizer.Key? {
    if (deviceName != "ROKID,PSOC-TP-R" || (source and InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_ENTER -> PadGestureRecognizer.Key.TAP
        291 -> PadGestureRecognizer.Key.DOUBLE
        KeyEvent.KEYCODE_DPAD_RIGHT, 292 -> PadGestureRecognizer.Key.FORWARD
        KeyEvent.KEYCODE_DPAD_LEFT, 293 -> PadGestureRecognizer.Key.BACKWARD
        else -> null
    }
}
