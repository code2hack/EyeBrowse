package com.code2hack.eyebrowse.projector.directrg

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.code2hack.eyebrowse.projector.protocol.Fit
import com.code2hack.eyebrowse.projector.protocol.Geometry
import java.io.File
import org.json.JSONObject

class ReceiverActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var root: FrameLayout
    private lateinit var screen: SurfaceView
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private var session: ProjectionSession? = null
    private var currentGeometry: Geometry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = SurfaceView(this).apply {
            contentDescription = "Projected application; tap a visible target"
            holder.addCallback(this@ReceiverActivity)
            setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val geometry = currentGeometry
                    if (geometry != null) Fit.inside(view.width, view.height, geometry).touch(event.x, event.y, geometry)?.let { session?.tap(it) }
                    view.performClick()
                }
                true
            }
        }
        root.addView(screen, FrameLayout.LayoutParams(480, 640, Gravity.CENTER))
        status = TextView(this).apply { text = getString(R.string.disconnected); setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK)
            addView(status)
            addView(Button(this@ReceiverActivity).apply { text = getString(R.string.connect); setOnClickListener { connect() } })
        }
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        root.addView(Button(this).apply {
            text = getString(R.string.stop); contentDescription = "Stop projection and disconnect"
            setOnClickListener { stop(); finish() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resize() }
        setContentView(root)
        root.post {
            window.insetsController?.apply { hide(WindowInsets.Type.systemBars()); systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
        }
    }

    private fun resize() {
        val geometry = currentGeometry ?: return
        if (root.width <= 0 || root.height <= 0) return
        val fit = Fit.inside(root.width, root.height, geometry)
        File(filesDir, "display.json").writeText(JSONObject().put("drawableWidth", root.width).put("drawableHeight", root.height)
            .put("rotation", display?.rotation).put("videoWidth", geometry.width).put("videoHeight", geometry.height)
            .put("fitLeft", fit.left).put("fitTop", fit.top).put("fitWidth", fit.width).put("fitHeight", fit.height).toString())
        val old = screen.layoutParams
        if (old.width != fit.width || old.height != fit.height) {
            screen.layoutParams = FrameLayout.LayoutParams(fit.width, fit.height, Gravity.CENTER)
        }
    }

    private fun connect() {
        if (!screen.holder.surface.isValid) { status.text = getString(R.string.display_unavailable); return }
        session?.close()
        currentGeometry = null; panel.visibility = View.VISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val next = ProjectionSession(File(filesDir, "session"), screen.holder.surface,
            { source, state -> runOnUiThread {
                if (session === source) {
                    panel.visibility = if (state == "STREAMING") View.GONE else View.VISIBLE
                    status.text = when (state) {
                        "STREAMING" -> getString(R.string.connected)
                        "CONNECTING", "WAITING_FOR_VIDEO", "WAITING_FOR_AUDIO", "WAITING_FOR_AUDIO_CLOCK" -> getString(R.string.connecting)
                        else -> "Projection stopped: $state"
                    }
                    if (state !in listOf("STREAMING", "CONNECTING", "WAITING_FOR_VIDEO", "WAITING_FOR_AUDIO", "WAITING_FOR_AUDIO_CLOCK")) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } },
            { source, geometry -> runOnUiThread { if (session === source) { currentGeometry = geometry; resize() } } }, applicationContext)
        session = next; next.start()
    }

    private fun stop() {
        val old = session; session = null; old?.close()
        currentGeometry = null; panel.visibility = View.VISIBLE; status.text = getString(R.string.disconnected)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onPause() { stop(); super.onPause() }
    override fun onDestroy() { stop(); super.onDestroy() }
    override fun surfaceCreated(holder: SurfaceHolder) { /* Connection is an explicit local action. */ }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }
}
