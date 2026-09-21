package com.code2hack.eyebrowse.rg.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.rg.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Separate REAL camera evidence (plan §9-D as revised by the review adjudication 2026-09-20,
 * option c narrow revision): actual CameraX open/frame/cancel/release on the RG camera.
 * Binding contract under test:
 *  - COLD first analyzer frame <= 10 s (cold = first scanner start requiring a fresh CameraX
 *    provider/bind after app/process startup; runs FIRST via NAME_ASCENDING after a host-side
 *    force-stop, so it really is the process's first scanner start),
 *  - warm reacquisition <= 5 s,
 *  - cancel/unbind/release <= 2 s (unchanged),
 *  - provider/bind failures surface CameraUnavailable (review B3) and release partial resources.
 * Permission denial/recovery lives in [RgCameraPermissionInstrumentationTest] (a `pm revoke`
 * while the instrumented process is alive kills that process on Android 12 — the revoke is
 * therefore performed from the host shell before that separate invocation).
 *
 * Image injection is never claimed as camera evidence — that is
 * [RgQrImagePairingInstrumentationTest]'s job. Physical optical QR alignment remains
 * NOT EXERCISED (unattended profile). The historical physical-RG cold-open measurement of
 * 7911 ms is preserved as evidence (PASS under the revised <= 10 s contract; not re-measured).
 *
 * Navigation uses the real user path (launcher MainActivity -> PAIR WITH PHONE -> scanner) and
 * matches text case-insensitively (the theme renders buttons uppercase).
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
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

    /**
     * COLD first analyzer frame <= 10 s (adjudicated contract, option c narrow revision). Runs
     * FIRST (NAME_ASCENDING; host force-stops the app before this invocation) so this really is
     * the first scanner start of a freshly started app process: fresh CameraX provider init,
     * fresh bind, cold camera HAL open.
     */
    @Test
    fun camera_a_coldStart_firstAnalyzerFrame_within10s() {
        grant()
        openScannerSurface()
        val started = System.currentTimeMillis()
        assertTrue("scan tapped", tapText("Scan QR"))
        val frameSeen = awaitText("Camera delivering frames", 15_000)
        val elapsed = System.currentTimeMillis() - started
        assertTrue(
            "cold first analyzer frame within 10 s (frameSeen=$frameSeen actual=${elapsed}ms)",
            frameSeen && elapsed <= 10_000,
        )
    }

    /** Warm reacquisition <= 5 s + cancel/unbind <= 2 s (unchanged bound). */
    @Test
    fun camera_b_warmCycle_cancelUnbind_within2s_reacquire_within5s() {
        grant()
        openScannerSurface()
        assertTrue("scan tapped", tapText("Scan QR"))
        assertTrue("warm scanner running (first bind done by the cold test)", awaitText("Camera delivering frames", 15_000))

        val cancelStarted = System.currentTimeMillis()
        assertTrue("cancel tapped", tapText("Cancel"))
        assertTrue(
            "cancel confirmation within 2 s (unbind is synchronous on the main thread)",
            awaitText("Cancelled.", 2_000),
        )
        val cancelElapsed = System.currentTimeMillis() - cancelStarted
        assertTrue("cancel took ${cancelElapsed}ms", cancelElapsed <= 2_000)

        // Warm reacquisition: re-bind without a fresh provider/camera open must be <= 5 s.
        assertTrue("rescan tapped", tapText("Scan QR"))
        val reacquireStarted = System.currentTimeMillis()
        val reacquired = awaitText("Camera delivering frames", 15_000)
        val reacquireElapsed = System.currentTimeMillis() - reacquireStarted
        assertTrue(
            "warm reacquisition within 5 s (reacquired=$reacquired actual=${reacquireElapsed}ms)",
            reacquired && reacquireElapsed <= 5_000,
        )
        assertTrue("second cancel tapped", tapText("Cancel"))
        assertTrue(awaitText("Cancelled.", 2_000))
    }

    /**
     * Review B3 deterministic failure-path coverage: a bind that cannot succeed (a camera
     * selector no camera satisfies) MUST surface [LinkError.CameraUnavailable] through the
     * production failure callback and must release partial resources — evidenced by the
     * camera being immediately re-acquirable afterwards. Observation is poll-based (no fixed
     * early cancels: cold CameraX provider resolution can take seconds on this device).
     */
    @Test
    fun camera_c_bindFailure_reportsCameraUnavailable_andReleases() {
        grant()
        ActivityScenario.launch(RgPairingActivity::class.java).use { scenario ->
            val errors = LinkedBlockingQueue<LinkError>()
            val recoveredFrames = LinkedBlockingQueue<Boolean>()
            lateinit var failing: CameraQrScanner
            lateinit var recovery: CameraQrScanner
            scenario.onActivity { activity ->
                val noMatchSelector: CameraSelector =
                    CameraSelector.Builder().addCameraFilter { emptyList() }.build()
                failing = CameraQrScanner(
                    context = activity,
                    lifecycleOwner = activity,
                    mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(activity),
                    onPayload = {},
                    onFirstFrame = {},
                    onCameraError = { errors.add(it) },
                    cameraSelector = noMatchSelector,
                )
                failing.start(attachPreview(activity))
            }
            assertEquals(
                "bind failure must surface CameraUnavailable (review B3)",
                LinkError.CameraUnavailable,
                errors.poll(25, TimeUnit.SECONDS),
            )
            // Post-failure cleanup path must be safe (provider already released by the scanner).
            scenario.onActivity { failing.cancel() }
            // Partial-resource release evidence: a production scanner re-acquires right after.
            scenario.onActivity { activity ->
                recovery = CameraQrScanner(
                    context = activity,
                    lifecycleOwner = activity,
                    mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(activity),
                    onPayload = {},
                    onFirstFrame = { recoveredFrames.add(true) },
                )
                recovery.start(attachPreview(activity))
            }
            assertTrue(
                "camera re-acquired after failure-path release",
                recoveredFrames.poll(25, TimeUnit.SECONDS) == true,
            )
            scenario.onActivity { recovery.cancel() }
        }
    }

    /** A PreviewView attached to the activity window (CameraX defers open without a surface). */
    private fun attachPreview(activity: RgPairingActivity): PreviewView {
        val view = PreviewView(activity)
        activity.addContentView(
            view,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return view
    }

    private fun resId(id: String) = "com.code2hack.eyebrowse.rg:id/$id"

    @Suppress("unused")
    private val layoutSmoke = R.string.rg_action_scan // keep R linkage explicit
}
