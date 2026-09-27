package com.code2hack.eyebrowse.rg

import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView

/** Reads displayed text at pre-draw; never registers TextWatchers or changes text buffers. */
internal class PreDrawTextObserver(root: View, private val views: List<TextView>, private val changed: (Int) -> Unit) : AutoCloseable {
    private val tree = root.viewTreeObserver
    private val previous = views.map { it.text.toString() }.toMutableList()
    private var active = true
    private val listener = ViewTreeObserver.OnPreDrawListener {
        if (active) views.forEachIndexed { index, view ->
            val value = view.text.toString()
            if (value != previous[index]) { previous[index] = value; changed(index) }
        }
        true
    }
    init { tree.addOnPreDrawListener(listener) }
    override fun close() {
        active = false
        if (tree.isAlive) tree.removeOnPreDrawListener(listener)
        previous.clear()
    }
}
