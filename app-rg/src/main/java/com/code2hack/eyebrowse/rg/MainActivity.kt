package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.*
import com.code2hack.eyebrowse.rg.pairing.RgPairingActivity
import com.code2hack.eyebrowse.core.link.messages.BrowserStateMessage

/** Local browser chrome and a measured image surface; never an RG WebView. */
class MainActivity : Activity() {
    lateinit var presentation: RgPresentationController
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.rg_root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        val image = findViewById<ImageView>(R.id.rg_page)
        val status = findViewById<TextView>(R.id.rg_status)
        val location = findViewById<TextView>(R.id.rg_detail)
        presentation = RgPresentationController(this, object : RgPresentationController.Surface {
            override fun status(text: String) { status.text = text }
            override fun browserState(state: BrowserStateMessage) {
                location.text = state.title ?: state.url ?: getString(R.string.rg_location_empty)
            }
            override fun frame(bitmap: android.graphics.Bitmap) { image.setImageBitmap(bitmap) }
        })
        image.addOnLayoutChangeListener { _,l,t,r,b,_,_,_,_ ->
            presentation.measure(r-l,b-t,resources.displayMetrics.densityDpi)
        }
        findViewById<Button>(R.id.rg_retry).setOnClickListener { presentation.reconnect() }
        findViewById<Button>(R.id.button_scan_pair).setOnClickListener {
            presentation.close()
            startActivity(Intent(this,RgPairingActivity::class.java))
            finish()
        }
    }
    override fun onStop() { presentation.pause(); super.onStop() }
    override fun onDestroy() { presentation.close(); super.onDestroy() }
}
