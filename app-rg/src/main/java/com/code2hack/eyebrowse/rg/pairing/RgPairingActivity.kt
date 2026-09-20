package com.code2hack.eyebrowse.rg.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.PairingState
import com.code2hack.eyebrowse.rg.R
import com.code2hack.eyebrowse.rg.link.RgLinkClient
import com.code2hack.eyebrowse.rg.link.RgLinkIdentity
import com.code2hack.eyebrowse.rg.link.RgPairingStore

/**
 * The RG pairing/scanner surface (plan Phase C): real CameraX preview/analyzer, Scan/Cancel/
 * Retry/Forget, explicit errors. Decoded QR payloads go to the production [RgLinkClient]; the
 * camera path and the instrumentation image-input seam are two pixel sources into the same
 * decoder + controller. Error codes are surfaced distinctly — corrupt trust keeps its
 * replacement-required semantics (invariant: no error-code normalization for UI convenience).
 */
class RgPairingActivity : ComponentActivity() {

    private lateinit var client: RgLinkClient
    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var cancelButton: Button
    private lateinit var retryButton: Button
    private lateinit var forgetButton: Button
    private lateinit var previewView: PreviewView

    private var scanner: CameraQrScanner? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startScanning() else show(CAMERA_DENIED_NOTE)
        }

    private val clientListener = object : RgLinkClient.Listener {
        override fun onStateChange(state: PairingState) = show(describe(state))
        override fun onStatus(status: HostStatusValue) = show(describe(status))
        override fun onLinkLost() = show(LINK_LOST_NOTE)
        override fun onConnectFailed(error: LinkError) = show(describe(error))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)
        client = RgLinkClient(RgLinkIdentity(), RgPairingStore(this), clientListener)
        statusText = findViewById(R.id.rg_pairing_status)
        previewView = findViewById(R.id.rg_preview)
        scanButton = findViewById(R.id.rg_button_scan)
        cancelButton = findViewById(R.id.rg_button_cancel)
        retryButton = findViewById(R.id.rg_button_retry)
        forgetButton = findViewById(R.id.rg_button_forget)
        scanButton.setOnClickListener { ensureCameraAndScan() }
        cancelButton.setOnClickListener { cancelAll() }
        retryButton.setOnClickListener {
            if (client.isPaired()) client.reconnect() else ensureCameraAndScan()
        }
        forgetButton.setOnClickListener {
            cancelAll()
            client.forget()
            show(FORGOTTEN_NOTE)
        }
        show(if (client.isPaired()) PAIRED_NOTE else IDLE_NOTE)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanner?.cancel()
        client.disconnect()
    }

    private fun ensureCameraAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startScanning()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startScanning() {
        cancelScanner()
        val newScanner = CameraQrScanner(
            context = this,
            lifecycleOwner = this,
            mainExecutor = ContextCompat.getMainExecutor(this),
            onPayload = { payload -> runOnUiThread { onDecoded(payload) } },
            onFirstFrame = { runOnUiThread { show(CAMERA_FRAME_NOTE) } },
        )
        scanner = newScanner
        newScanner.start(previewView)
        show(SCANNING_NOTE)
    }

    private fun onDecoded(payload: String) {
        cancelScanner()
        // Production controller only: decode result has no shortcut into pairing state.
        client.pairFromQr(payload)
    }

    private fun cancelScanner() {
        scanner?.cancel()
        scanner = null
    }

    private fun cancelAll() {
        cancelScanner()
        client.disconnect()
        show(CANCELLED_NOTE)
    }

    private fun show(note: String) {
        statusText.text = note
    }

    private fun describe(state: PairingState): String = when (state) {
        PairingState.UNPAIRED -> IDLE_NOTE
        PairingState.INVITATION_READY -> SCANNING_NOTE
        PairingState.SCANNING -> SCANNING_NOTE
        PairingState.CONNECTING -> CONNECTING_NOTE
        PairingState.AUTHENTICATING -> AUTHENTICATING_NOTE
        PairingState.PAIRED_DISCONNECTED -> PAIRED_DISCONNECTED_NOTE
        PairingState.CONNECTED -> CONNECTED_NOTE
        PairingState.FORGETTING -> FORGETTING_NOTE
    }

    private fun describe(status: HostStatusValue): String = "Host status: $status"

    private fun describe(error: LinkError): String = when (error) {
        LinkError.NetworkUnreachable -> NETWORK_NOTE
        LinkError.PeerReplacementRequired -> PEER_REPLACEMENT_NOTE
        LinkError.WrongPhoneIdentity -> WRONG_IDENTITY_NOTE
        LinkError.WrongRgIdentity -> WRONG_IDENTITY_NOTE
        LinkError.InvitationInvalid -> INVITATION_INVALID_NOTE
        LinkError.InvitationExpired -> INVITATION_EXPIRED_NOTE
        LinkError.InvitationCancelled -> INVITATION_CANCELLED_NOTE
        LinkError.InvitationReused -> INVITATION_REUSED_NOTE
        LinkError.IncompatibleProtocol -> PROTOCOL_NOTE
        LinkError.AuthenticationFailed -> AUTH_FAILED_NOTE
        LinkError.CameraPermissionDenied -> CAMERA_DENIED_NOTE
        LinkError.CameraUnavailable -> CAMERA_UNAVAILABLE_NOTE
        LinkError.ScanCancelled -> CANCELLED_NOTE
        LinkError.MalformedQr -> MALFORMED_NOTE
    }

    companion object {
        const val IDLE_NOTE = "Scan the QR shown on the Phone to pair."
        const val PAIRED_NOTE = "Paired. Use Retry to reconnect; Forget to unpair."
        const val SCANNING_NOTE = "Camera active — point at the Phone QR."
        const val CAMERA_FRAME_NOTE = "Camera delivering frames."
        const val CAMERA_DENIED_NOTE = "Camera permission denied — allow it to scan."
        const val CANCELLED_NOTE = "Cancelled."
        const val FORGOTTEN_NOTE = "Pairing forgotten. Scan a fresh QR to pair again."
        const val LINK_LOST_NOTE = "Link lost. Use Retry."
        const val CONNECTING_NOTE = "Connecting to Phone…"
        const val AUTHENTICATING_NOTE = "Authenticating…"
        const val CONNECTED_NOTE = "Connected to Phone."
        const val PAIRED_DISCONNECTED_NOTE = "Paired; link down. Use Retry."
        const val FORGETTING_NOTE = "Forgetting pairing…"
        const val NETWORK_NOTE = "Phone not reachable. Check the network, then Retry."
        const val CAMERA_UNAVAILABLE_NOTE = "Camera unavailable — retry."
        const val PEER_REPLACEMENT_NOTE = "This RG is already paired to another Phone. Use Forget first."
        const val WRONG_IDENTITY_NOTE = "Phone identity mismatch."
        const val INVITATION_INVALID_NOTE = "Invalid invitation."
        const val INVITATION_EXPIRED_NOTE = "Invitation expired — generate a fresh QR."
        const val INVITATION_CANCELLED_NOTE = "Invitation was cancelled on the Phone."
        const val INVITATION_REUSED_NOTE = "Invitation already used — generate a fresh QR."
        const val PROTOCOL_NOTE = "Incompatible link protocol version."
        const val AUTH_FAILED_NOTE = "Authentication failed."
        const val MALFORMED_NOTE = "Unreadable QR — try again."
    }
}
