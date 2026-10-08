package com.code2hack.eyebrowse.rg

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Historical remote keyboard controls; not the direct local View/WebView keyboard contract. */
internal class RgKeyboardView(context: Context) : LinearLayout(context) {
    private var rendered: List<Any?>? = null
    init { orientation = VERTICAL; visibility = GONE; setBackgroundColor(android.graphics.Color.BLACK) }

    fun render(controller: RgPresentationController) {
        val keyboard = controller.keyboard
        val rows = keyboard.rows() + listOf(listOf(RgKeyboardKeys.Key.Command.SHIFT, RgKeyboardKeys.Key.Command.SYMBOLS,
            RgKeyboardKeys.Key.Command.SPACE, RgKeyboardKeys.Key.Command.BACKSPACE, RgKeyboardKeys.Key.Command.ENTER,
            RgKeyboardKeys.Key.Command.DONE))
        val signature = listOf(keyboard.generation, keyboard.visible, rows.flatten().map(controller::canKey))
        if (signature == rendered) return
        rendered = signature
        removeAllViews(); visibility = if (keyboard.visible) VISIBLE else GONE
        if (!keyboard.visible) return
        if (keyboard.destination == RgKeyboard.Destination.ADDRESS) {
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                addView(button(controller, RgKeyboardKeys.Key.Command.LEFT), LayoutParams(dp(42), dp(32)))
                addView(TextView(context).apply {
                    text = keyboard.draft.substring(0, keyboard.caret) + "│" + keyboard.draft.substring(keyboard.caret)
                    setTextColor(android.graphics.Color.WHITE); textSize = 13f
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    contentDescription = context.getString(R.string.keyboard_address)
                    isSaveEnabled = false
                }, LayoutParams(0, dp(32), 1f))
                addView(button(controller, RgKeyboardKeys.Key.Command.RIGHT), LayoutParams(dp(42), dp(32)))
            })
        }
        rows.forEach { keys ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                keys.forEach { key -> addView(button(controller, key), LayoutParams(0, dp(36), 1f)) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)))
        }
    }
    private fun button(controller: RgPresentationController, key: RgKeyboardKeys.Key) = Button(context).apply {
        val keyboard = controller.keyboard
        text = when (key) {
            is RgKeyboardKeys.Key.Character -> key.text
            RgKeyboardKeys.Key.Command.SHIFT -> if (keyboard.uppercase) "ABC" else "abc"
            RgKeyboardKeys.Key.Command.SYMBOLS -> if (keyboard.symbols) "abc" else "123"
            RgKeyboardKeys.Key.Command.SPACE -> context.getString(R.string.keyboard_space)
            RgKeyboardKeys.Key.Command.BACKSPACE -> "⌫"
            RgKeyboardKeys.Key.Command.ENTER -> context.getString(if (keyboard.destination == RgKeyboard.Destination.ADDRESS) R.string.keyboard_go else R.string.keyboard_enter)
            RgKeyboardKeys.Key.Command.DONE -> context.getString(R.string.keyboard_done)
            RgKeyboardKeys.Key.Command.LEFT -> "←"
            RgKeyboardKeys.Key.Command.RIGHT -> "→"
        }
        contentDescription = when (key) {
            RgKeyboardKeys.Key.Command.SHIFT -> context.getString(R.string.keyboard_shift)
            RgKeyboardKeys.Key.Command.SYMBOLS -> context.getString(R.string.keyboard_symbols)
            RgKeyboardKeys.Key.Command.BACKSPACE -> context.getString(R.string.keyboard_backspace)
            RgKeyboardKeys.Key.Command.LEFT -> context.getString(R.string.keyboard_left)
            RgKeyboardKeys.Key.Command.RIGHT -> context.getString(R.string.keyboard_right)
            else -> text
        }
        isAllCaps = false; textSize = 12f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(0,0,0,0); isSaveEnabled = false
        tag = keyboard.capture(key)
        isEnabled = controller.canKey(key)
        setOnClickListener { controller.key(tag as RgKeyboard.Intent) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
