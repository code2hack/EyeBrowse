package com.code2hack.eyebrowse.phone.pairing

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.code2hack.eyebrowse.phone.R
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer

/**
 * The "Pair RG" utility surface (plan Phase C). Hosts the process-scoped [PhoneLinkServer] via the
 * UI, renders the one active invitation as a QR bitmap, and offers Cancel/Forget. Hosting state is
 * observed read-only through the server's status provider — this surface never touches
 * HostingController control APIs (ticket proof: pairing never starts hosting or grants control).
 */
class PairingActivity : ComponentActivity() {

    private lateinit var server: PhoneLinkServer
    private lateinit var controller: PhonePairingUiController
    private lateinit var qrImage: ImageView
    private lateinit var noteText: TextView
    private lateinit var linkText: TextView
    private lateinit var generateButton: Button
    private lateinit var cancelButton: Button
    private lateinit var forgetButton: Button

    private var lastRenderedPayload: String? = null

    private val linkObserver = {
        runOnUiThread { render(controller.onRefresh()) }
        Unit
    }

    private val surface = object : PhonePairingUiController.PairingSurface {
        override fun generateInvitation(): PhonePairingUiController.PairingSurface.GeneratedInvitation? {
            // Server lifecycle starts with the surface; identity/Keystore init happens inside.
            return try {
                server.start()
                val invitation = server.generateInvitation()
                PhonePairingUiController.PairingSurface.GeneratedInvitation(
                    id = invitation.id,
                    payload = invitation.payload,
                    expiresAtMs = invitation.expiresAtElapsedMillis + System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime(),
                )
            } catch (e: IllegalStateException) {
                null
            }
        }

        override fun cancelInvitation(): Boolean = server.cancelInvitation()

        override fun forget() = server.forget()

        override fun activeInvitationId(): String? = server.activeInvitation()?.id

        override fun isLinkUp(): Boolean = server.isLinkUp()

        override fun isPaired(): Boolean = server.isPaired()

        override fun isCorrupt(): Boolean = server.trustIsCorrupt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)
        server = PhoneLinkServer.obtain(this)
        controller = PhonePairingUiController(surface) { System.currentTimeMillis() }
        qrImage = findViewById(R.id.pairing_qr)
        noteText = findViewById(R.id.pairing_note)
        linkText = findViewById(R.id.pairing_link_state)
        generateButton = findViewById(R.id.button_generate_qr)
        cancelButton = findViewById(R.id.button_cancel_invitation)
        forgetButton = findViewById(R.id.button_forget_pairing)
        generateButton.setOnClickListener { render(controller.onGenerateClicked()) }
        cancelButton.setOnClickListener { render(controller.onCancelClicked()) }
        forgetButton.setOnClickListener { render(controller.onForgetClicked()) }
        render(controller.onScreenShown())
    }

    override fun onResume() {
        super.onResume()
        // Listener recovery (plan Phase E step 5): the surface-scoped listener returns with the
        // surface after an app restart — it must not require a fresh Generate tap. Idempotent;
        // an unusable identity (inadequate alias under VALID/CORRUPT trust) keeps failing closed
        // at the generate attempt's existing catch, with the corrupt-Forget recovery reachable.
        runCatching { server.start() }
        server.addLinkObserver(linkObserver)
        render(controller.onRefresh())
    }

    override fun onPause() {
        super.onPause()
        server.removeLinkObserver(linkObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the listener while this surface is gone; pairing state and trust persist.
        if (isFinishing) server.stop()
    }

    private fun render(state: PhonePairingUiController.UiState) {
        noteText.text = state.note
        linkText.text =
            if (state.linkUp) PhonePairingUiController.LINKED_NOTE
            else getString(R.string.pairing_link_down)
        val payload = state.invitationPayload
        if (payload == null) {
            qrImage.setImageDrawable(null)
            qrImage.contentDescription = null
            lastRenderedPayload = null
        } else if (payload != lastRenderedPayload) {
            val metrics = resources.displayMetrics
            val sizePx = minOf(metrics.widthPixels, metrics.heightPixels)
                .coerceAtLeast(PairingQrRenderer.MIN_SIZE_PX)
            val bitmap: Bitmap = PairingQrRenderer.render(payload, sizePx)
            qrImage.setImageBitmap(bitmap)
            qrImage.contentDescription = getString(R.string.pairing_qr_description)
            lastRenderedPayload = payload
        }
        generateButton.isEnabled = true
        cancelButton.isEnabled = state.invitationPayload != null
        // Review B2: the explicit reset stays reachable under CORRUPT trust (it is distinct from
        // a clean "not paired" and must not strand the user without recovery).
        forgetButton.isEnabled = state.forgetEnabled
    }
}
