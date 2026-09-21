package com.code2hack.eyebrowse.rg

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import com.code2hack.eyebrowse.rg.pairing.RgPairingActivity

/**
 * The Reading Glasses launcher: an honest, legible "not connected" state plus the entry point into
 * the pairing/scanner surface. It holds no WebView, opens no network connection by itself and
 * claims no working Phone link; pairing happens only through [RgPairingActivity]'s production
 * scanner and the [com.code2hack.eyebrowse.rg.link.RgLinkClient] controller.
 *
 * Plan-mandated Kotlin conversion (ticket plan Phase C: no first-party Java remains).
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.rg_root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<Button>(R.id.button_scan_pair).setOnClickListener {
            startActivity(Intent(this, RgPairingActivity::class.java))
        }
    }
}
