package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import com.code2hack.eyebrowse.rg.pairing.RgPairingActivity
import com.code2hack.eyebrowse.core.link.messages.BrowserStateMessage
import com.code2hack.eyebrowse.core.link.control.ControlOwner

/** Local browser chrome and a measured image surface; never an RG WebView. */
class MainActivity : Activity() {
    lateinit var presentation: RgPresentationController
        private set
    private lateinit var pointer: PointerOverlay
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.rg_root)
        pointer = findViewById(R.id.rg_pointer)
        fun pointerBounds() = pointer.bounds(root.paddingLeft.toFloat(),root.paddingTop.toFloat(),
            (root.width-root.paddingRight).coerceAtLeast(root.paddingLeft).toFloat(),
            (root.height-root.paddingBottom).coerceAtLeast(root.paddingTop).toFloat(),display?.rotation ?: 0)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            pointerBounds()
            insets
        }
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> pointerBounds() }
        pointer.onAvailabilityChanged = { available ->
            findViewById<TextView>(R.id.rg_pointer_status).setText(
                if(available) R.string.pointer_ready else R.string.pointer_unavailable)
        }
        findViewById<Button>(R.id.rg_recenter).setOnClickListener { pointer.recenter() }
        val image = findViewById<ImageView>(R.id.rg_page)
        val status = findViewById<TextView>(R.id.rg_status)
        val location = findViewById<TextView>(R.id.rg_detail)
        presentation = RgPresentationController(this, object : RgPresentationController.Surface {
            override fun status(text: String) { status.text = text }
            override fun browserState(state: BrowserStateMessage) {
                location.text = state.url ?: state.title ?: getString(R.string.rg_location_empty)
                val rg = state.owner == ControlOwner.RG
                findViewById<Button>(R.id.rg_handoff).apply {
                    setText(if (rg) R.string.use_on_phone else R.string.use_on_glasses)
                    isEnabled = presentation.canHandoff()
                }
                findViewById<View>(R.id.rg_navigation).visibility = if (rg) View.VISIBLE else View.INVISIBLE
                findViewById<Button>(R.id.rg_back).isEnabled = presentation.canAct() && state.canGoBack
                findViewById<Button>(R.id.rg_forward).isEnabled = presentation.canAct() && state.canGoForward
                findViewById<Button>(R.id.rg_reload).isEnabled = presentation.canAct()
            }
            override fun frame(bitmap: android.graphics.Bitmap) { image.setImageBitmap(bitmap) }
        })
        image.addOnLayoutChangeListener { _,l,t,r,b,_,_,_,_ ->
            presentation.measure(r-l,b-t,resources.displayMetrics.densityDpi)
        }
        findViewById<Button>(R.id.rg_handoff).setOnClickListener {
            if (presentation.browserState()?.owner == ControlOwner.RG) presentation.requestPhone()
            else presentation.requestPresentation()
        }
        findViewById<Button>(R.id.rg_back).setOnClickListener { presentation.back() }
        findViewById<Button>(R.id.rg_forward).setOnClickListener { presentation.forward() }
        findViewById<Button>(R.id.rg_reload).setOnClickListener { presentation.reload() }
        findViewById<Button>(R.id.rg_retry).setOnClickListener { presentation.reconnect() }
        findViewById<Button>(R.id.button_scan_pair).setOnClickListener {
            presentation.close()
            startActivity(Intent(this,RgPairingActivity::class.java))
            finish()
        }
    }
    override fun onResume() { super.onResume();pointer.start() }
    override fun onPause() { pointer.stop();super.onPause() }
    override fun onStop() { presentation.pause(); super.onStop() }
    override fun onDestroy() { pointer.stop();presentation.close(); super.onDestroy() }
}
