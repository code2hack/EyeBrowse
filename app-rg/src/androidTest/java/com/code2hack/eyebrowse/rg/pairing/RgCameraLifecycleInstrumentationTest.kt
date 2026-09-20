package com.code2hack.eyebrowse.rg.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.code2hack.eyebrowse.rg.pairing.RgPairingActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Separate REAL camera evidence (plan §9-D): actual CameraX open/frame/deny/cancel/release on the
 * RG camera. Image injection is never claimed as camera evidence — that is
 * [RgQrImagePairingInstrumentationTest]'s job. Physical optical QR alignment remains
 * NOT EXERCISED (unattended profile).
 */
@RunWith(AndroidJUnit4::class)
class RgCameraLifecycleInstrumentationTest {

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shell(cmd: String) {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        try {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).readBytes() // drain
        } finally {
            pfd.close()
        }
    }

    private fun launchPairingActivity() {
        context.startActivity(
            android.content.Intent(context, RgPairingActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        device.waitForIdle(5_000)
    }

    private fun awaitText(fragment: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (device.findObjects(By.textContains(fragment)).isNotEmpty()) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun tapText(fragment: String): Boolean {
        val target = device.findObjects(By.textContains(fragment)).firstOrNull() ?: return false
        target.click()
        return true
    }

    @Test
    fun camera_permissionDenied_showsRecoveryUi_withoutCrash() {
        shell("pm revoke ${context.packageName} android.permission.CAMERA")
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            ContextCompat_checkPermission(),
        )
        launchPairingActivity()
        assertTrue(tapText("Scan QR"))
        // The runtime request shows the system dialog; deny it through the UI.
        awaitText("allow", 5_000) // dialog present (locale-tolerant wait on 'allow'/'Allow')
        tapDeny()
        assertTrue(
            "denial must surface the recovery note",
            awaitText("Camera permission denied", 5_000),
        )
        // Recovery: grant through the authorized device operation, rescan works.
        shell("pm grant ${context.packageName} android.permission.CAMERA")
        assertTrue(tapText("Scan QR"))
        assertTrue(awaitText("Camera active", 5_000))
    }

    @Test
    fun camera_granted_deliversRealAnalyzerFrame_within5s() {
        shell("pm grant ${context.packageName} android.permission.CAMERA")
        launchPairingActivity()
        val started = System.currentTimeMillis()
        assertTrue(tapText("Scan QR"))
        assertTrue(
            "real CameraX analyzer frame within 5 s",
            awaitText("Camera delivering frames", 5_000),
        )
        val elapsed = System.currentTimeMillis() - started
        assertTrue("first frame took ${elapsed}ms", elapsed <= 5_000)
    }

    @Test
    fun camera_cancel_unbindsWithin2s_andReacquiresOnRestart() {
        shell("pm grant ${context.packageName} android.permission.CAMERA")
        launchPairingActivity()
        assertTrue(tapText("Scan QR"))
        assertTrue(awaitText("Camera delivering frames", 5_000))

        val cancelStarted = System.currentTimeMillis()
        assertTrue(tapText("Cancel"))
        assertTrue(
            "cancel confirmation within 2 s (unbind is synchronous on the main thread)",
            awaitText("Cancelled.", 2_000),
        )
        val cancelElapsed = System.currentTimeMillis() - cancelStarted
        assertTrue("cancel took ${cancelElapsed}ms", cancelElapsed <= 2_000)

        // Reacquisition: a restart of the scanner re-binds the camera and delivers frames again.
        assertTrue(tapText("Scan QR"))
        assertTrue(awaitText("Camera delivering frames", 5_000))
        assertTrue(tapText("Cancel"))
        assertTrue(awaitText("Cancelled.", 2_000))
    }

    private fun tapDeny(): Boolean =
        tapText("Don't allow") || tapText("Deny") || tapText("NOT NOW") || tapText("No thanks")

    private fun ContextCompat_checkPermission(): Int =
        context.checkSelfPermission(Manifest.permission.CAMERA)
}
