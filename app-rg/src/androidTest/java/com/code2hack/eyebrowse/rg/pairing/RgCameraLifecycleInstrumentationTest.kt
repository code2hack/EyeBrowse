package com.code2hack.eyebrowse.rg.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.code2hack.eyebrowse.rg.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Separate REAL camera evidence (plan §9-D): actual CameraX open/frame/deny/cancel/release on the
 * RG camera. Image injection is never claimed as camera evidence — that is
 * [RgQrImagePairingInstrumentationTest]'s job. Physical optical QR alignment remains
 * NOT EXERCISED (unattended profile).
 *
 * Navigation uses the real user path (launcher MainActivity -> PAIR WITH PHONE -> scanner) and
 * matches text case-insensitively (the theme renders buttons uppercase). The device's permission
 * dialog is zh-CN on this unit; deny buttons are matched locale-tolerantly.
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

    private fun grant() = shell("pm grant ${context.packageName} android.permission.CAMERA")
    private fun revoke() = shell("pm revoke ${context.packageName} android.permission.CAMERA")

    private fun hasCameraPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // Theme applies textAllCaps: match case-insensitively by probing common casings.
    private fun findByText(fragment: String) =
        device.findObjects(By.textContains(fragment)).ifEmpty {
            device.findObjects(By.textContains(fragment.uppercase()))
        }.ifEmpty {
            device.findObjects(By.textContains(fragment.lowercase()))
        }

    private fun awaitText(fragment: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (findByText(fragment).isNotEmpty()) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun tapText(fragment: String): Boolean {
        val target = findByText(fragment).firstOrNull() ?: return false
        target.click()
        return true
    }

    private fun awaitPermissionDialog(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val found = device.findObjects(By.pkg("com.google.android.permissioncontroller")) +
                device.findObjects(By.pkg("com.android.permissioncontroller")) +
                device.findObjects(By.pkg("com.android.packageinstaller"))
            if (found.isNotEmpty()) return true
            Thread.sleep(150)
        }
        return false
    }

    private fun tapDeny(): Boolean =
        // Locale-tolerant deny: en + the device's zh-CN dialog buttons.
        tapText("Don't allow") || tapText("Don’t allow") || tapText("Deny") ||
            tapText("DENY") || tapText("Not now") || tapText("拒绝")

    /** Real user path to the scanner surface. Returns when the SCAN button is visible. */
    private fun openScannerSurface() {
        device.pressHome()
        Thread.sleep(600)
        shell("am start -n com.code2hack.eyebrowse.rg/.MainActivity")
        assertTrue("MainActivity entry visible", awaitText("Pair with Phone", 8_000))
        assertTrue("main entry tapped", tapText("Pair with Phone"))
        device.waitForIdle(3_000)
        assertTrue("scanner surface visible (SCAN button)", awaitText("Scan QR", 6_000))
    }

    @Test
    fun camera_permissionDenied_showsRecoveryUi_withoutCrash() {
        revoke()
        openScannerSurface()
        assertTrue("scan tapped (runtime request)", tapText("Scan QR"))
        val dialogShown = awaitPermissionDialog(8_000)
        val denyClicked = if (dialogShown) tapDeny() else false
        assertTrue(
            "permission dialog must appear and be deniable (dialog=$dialogShown denyClicked=$denyClicked)",
            dialogShown && denyClicked,
        )
        assertTrue(
            "denial must surface the recovery note",
            awaitText("Camera permission denied", 5_000),
        )
        assertTrue("permission must now be denied", !hasCameraPermission())

        // Recovery: grant through the authorized device operation; the same Scan affordance works.
        grant()
        assertTrue("scan tapped after grant", tapText("Scan QR"))
        assertTrue(
            "scanner starts after recovery grant",
            awaitText("Camera active", 6_000) || awaitText("Camera delivering frames", 6_000),
        )
    }

    @Test
    fun camera_granted_deliversRealAnalyzerFrame_within5s() {
        grant()
        openScannerSurface()
        val started = System.currentTimeMillis()
        assertTrue("scan tapped", tapText("Scan QR"))
        val frameSeen = awaitText("Camera delivering frames", 15_000)
        val elapsed = System.currentTimeMillis() - started
        assertTrue("real CameraX analyzer frame within 5 s (frameSeen=$frameSeen actual=${elapsed}ms)", frameSeen && elapsed <= 5_000)
    }

    @Test
    fun camera_cancel_unbindsWithin2s_andReacquiresOnRestart() {
        grant()
        openScannerSurface()
        assertTrue("scan tapped", tapText("Scan QR"))
        // Cold camera open on this RG measures ~8 s (see frame-latency test evidence); the
        // 5 s plan target applies to the frame-delivery bound, measured separately. The BOUND
        // UNDER TEST here is the <=2 s cancellation, kept strict below.
        assertTrue(awaitText("Camera delivering frames", 15_000))

        val cancelStarted = System.currentTimeMillis()
        assertTrue("cancel tapped", tapText("Cancel"))
        assertTrue(
            "cancel confirmation within 2 s (unbind is synchronous on the main thread)",
            awaitText("Cancelled.", 2_000),
        )
        val cancelElapsed = System.currentTimeMillis() - cancelStarted
        assertTrue("cancel took ${cancelElapsed}ms", cancelElapsed <= 2_000)

        // Reacquisition: a restart of the scanner re-binds the camera and delivers frames again.
        assertTrue("rescan tapped", tapText("Scan QR"))
        assertTrue(awaitText("Camera delivering frames", 5_000))
        assertTrue("second cancel tapped", tapText("Cancel"))
        assertTrue(awaitText("Cancelled.", 2_000))
    }

    private fun resId(id: String) = "com.code2hack.eyebrowse.rg:id/$id"

    @Suppress("unused")
    private val layoutSmoke = R.string.rg_action_scan // keep R linkage explicit
}
