package com.code2hack.eyebrowse.rg.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REAL camera permission denial + recovery (plan §9-D; review round 1 keeps this evidence in a
 * dedicated invocation). A `pm revoke` while the instrumented process is alive KILLS that
 * process on Android 12 ("permissions revoked" kill — observed in this round's device runs), so
 * the revoke is performed from the HOST shell BEFORE this instrumentation starts; this class
 * assumes the revoked pre-state and never revokes in-process.
 *
 * The device's permission dialog is zh-CN on this unit; deny buttons are matched
 * locale-tolerantly. Navigation uses the real user path (theme renders buttons uppercase).
 */
@RunWith(AndroidJUnit4::class)
class RgCameraPermissionInstrumentationTest {

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

    @Test
    fun cameraPermission_denied_showsRecoveryUi_andRecoversOnGrant() {
        // Pre-state (host-executed revoke): permission must NOT be granted here.
        assertTrue(
            "pre-state: CAMERA must be revoked by the host before this invocation",
            !hasCameraPermission(),
        )

        device.pressHome()
        Thread.sleep(600)
        shell("am start -n com.code2hack.eyebrowse.rg/.MainActivity")
        assertTrue("MainActivity entry visible", awaitText("Pair with Phone", 8_000))
        assertTrue("main entry tapped", tapText("Pair with Phone"))
        device.waitForIdle(3_000)
        assertTrue("scanner surface visible (SCAN button)", awaitText("Scan QR", 6_000))

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
        // (The post-force-stop camera open on this RG measures ~8 s; this is an existence check —
        // the <=10 s cold bound is asserted in RgCameraLifecycleInstrumentationTest.)
        grant()
        assertTrue("scan tapped after grant", tapText("Scan QR"))
        assertTrue(
            "scanner starts after recovery grant",
            awaitText("Camera active", 15_000) || awaitText("Camera delivering frames", 15_000),
        )
    }
}
