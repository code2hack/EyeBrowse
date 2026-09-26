package com.code2hack.eyebrowse.phone

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Paired physical-device fixture companion. No trust/data mutation or production test receiver. */
@RunWith(AndroidJUnit4::class)
class LivePresentationInstrumentedTest {
    @Test fun measuredRgProfileSurvivesPhoneConfigurationAndStopsCleanly() = runCompanion(true)

    /** Narrow T2-R1 rerun: real connected RG Stop, without repeating configuration evidence. */
    @Test fun rgConnectedStopRestoresPhoneAttachment() = runCompanion(false)

    private fun runCompanion(verifyConfiguration: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val server = PhoneLinkServer.obtain(app)
        var originalView: android.webkit.WebView? = null
        var originalDocument: String? = null
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        var primaryFailure: Throwable? = null
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        try {
            scenario.onActivity {
                originalOrientation = it.requestedOrientation
                server.start()
            }
            val fixture = StopRecoveryAssertions.openFixture(scenario, browser)
            originalView = fixture.view
            originalDocument = fixture.documentId
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument("companion_before_start", browser, fixture.view, fixture.documentId)
                assertNotNull("fixture must have a live baseline WebView",originalView)
                Log.i("EyeBrowseT02","LIVE_WEBVIEW_BASELINE=${System.identityHashCode(originalView)}")
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("hosting active", 5_000) { host.status().state == HostingController.State.HOSTING }
            Log.i("EyeBrowseT02", "PHONE_READY")
            await("explicit RG request and capture", 30_000) { host.isRgPresentationOwned() && host.status().captureActive }
            val profile = host.presentationProfile()
            val context = server.controlCoordinator.authority.snapshot().context
            assertEquals(profile.width,server.controlCoordinator.authority.snapshot().profile!!.width)
            assertEquals(profile.height,server.controlCoordinator.authority.snapshot().profile!!.height)
            Log.i("EyeBrowseT02", "PHONE_PROFILE ${profile.width}x${profile.height}@${profile.densityDpi}")
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument("companion_rg_attached", browser, fixture.view, fixture.documentId)
                assertFalse("RG ownership excludes Phone input", it.findViewById<Button>(R.id.button_open).isEnabled)
                assertNotSame(it.findViewById<android.view.ViewGroup>(R.id.web_container), browser.view()!!.parent)
            }
            if (verifyConfiguration) {
                SystemClock.sleep(5_000)
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                SystemClock.sleep(2_000)
                scenario.onActivity {
                    StopRecoveryAssertions.sameDocument("companion_after_configuration", browser, fixture.view, fixture.documentId)
                    val snapshot = host.privateDisplaySnapshot()!!
                    Log.i("EyeBrowseT02", "CONFIGURATION_FACTS expected=$profile actual=${host.presentationProfile()} expectedContext=$context actualContext=${server.controlCoordinator.authority.snapshot().context} sameView=${originalView === browser.view()} display=$snapshot")
                    assertEquals("immutable RG profile",profile,host.presentationProfile())
                    assertEquals("control context",context,server.controlCoordinator.authority.snapshot().context)
                    assertSame("same live WebView",originalView,browser.view())
                    assertEquals("private display width",profile.width,snapshot.actualWidth)
                    assertEquals("private display height",profile.height,snapshot.actualHeight)
                    assertEquals("private display ON",android.view.Display.STATE_ON,snapshot.state)
                }
                Log.i("EyeBrowseT02", "CONFIGURATION_PRESERVED ${host.privateDisplaySnapshot()}")
            }
            // Stop while the RG companion is still connected (its observation window is 15 s).
            SystemClock.sleep(5_000)
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument("companion_before_stop", browser, fixture.view, fixture.documentId)
                assertTrue("Stop must exercise an authenticated RG peer", server.isLinkUp())
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
                // Synchronous Stop must clear exclusion before its final listeners reattach/render.
                StopRecoveryAssertions.afterStop(it, browser, host, checkNotNull(originalView), checkNotNull(originalDocument))
                assertTrue("TLS peer still present before explicit link Stop", server.isLinkUp())
                val before = SystemClock.elapsedRealtime()
                server.stop() // Actual Activity-lifecycle call shape, under normal StrictMode.
                assertFalse(server.isLinkUp())
                assertTrue("main-thread stop returns within bound",SystemClock.elapsedRealtime()-before < 1_000)
                Log.i("EyeBrowseT02", "LINK_STOP_MAIN_MS=${SystemClock.elapsedRealtime()-before}")
            }
            await("Stop cleanup", 5_000) {
                StopRecoveryAssertions.sameDocument("companion_stop_cleanup", browser, fixture.view, fixture.documentId, log = false)
                StopRecoveryAssertions.resourcesGone(host)
            }
            scenario.onActivity {
                StopRecoveryAssertions.afterStop(it, browser, host, checkNotNull(originalView), checkNotNull(originalDocument))
            }
            Log.i("EyeBrowseT02", "STOP_CLEAN")
            StopRecoveryAssertions.restartAndStop(scenario, browser, host, checkNotNull(originalView), checkNotNull(originalDocument))
        } catch (failure: Throwable) {
            primaryFailure = failure
            Log.e("EyeBrowseT02", "PRIMARY_FAILURE",failure)
            throw failure
        } finally {
            val cleanup = listOf<() -> Unit>(
                { scenario.onActivity { it.requestedOrientation = originalOrientation } },
                { instrumentation.runOnMainSync { host.stop() } },
                { instrumentation.runOnMainSync { server.stop() } },
                { scenario.close() },
                { StopRecoveryAssertions.await("final cleanup", 5_000) { StopRecoveryAssertions.resourcesGone(host) } },
            ).mapNotNull { step -> runCatching(step).exceptionOrNull() }
            cleanup.forEach { Log.e("EyeBrowseT02","CLEANUP_FAILURE",it) }
            if (primaryFailure != null) cleanup.forEach { primaryFailure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) {
                cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }
                throw cleanup.first()
            }
        }
    }
    /**
     * FW5 Phone companion. A production "encoded" receipt is emitted only after the qualified
     * HostingFrame was WebP85-compressed into BoundedFrameOutput and sendPresentation() accepted
     * it into the current authenticated session. The RG companion correlates the same
     * seq/capture/profile tuple after real direct-LAN delivery.
     */
    @Test
    fun fw5QualifiedWindowFramesEncodeIntoAuthenticatedRgSessionAcrossHandoff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val server = PhoneLinkServer.obtain(app)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
        val mission = java.util.UUID.fromString(
            checkNotNull(InstrumentationRegistry.getArguments().getString("missionId")) {
                "FW5 paired missionId is required"
            },
        ).toString()
        val readyTitle = "FW5 READY " + mission
        var primaryFailure: Throwable? = null
        try {
            // Pairing/trust is retained, but no prior authenticated session or Hosting generation
            // may satisfy this run. This is test-owned chronology cleanup, not a pairing reset.
            scenario.onActivity {
                server.stop()
                host.stop()
            }
            StopRecoveryAssertions.await("FW5 clean chronology start", 5_000) {
                !server.isLinkUp() && StopRecoveryAssertions.resourcesGone(host)
            }

            val fixture = StopRecoveryAssertions.openFixture(scenario, browser)
            val originalView = fixture.view
            val originalDocument = fixture.documentId

            // Make Hosting active BEFORE exposing the server so RG cannot race an inactive handoff.
            scenario.onActivity {
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("FW5 hosting active before link start", 5_000) {
                host.status().state == HostingController.State.HOSTING
            }

            // Cross-device run handshake. The title is fixture-only state in this same document.
            // publishPhoneViewport() uses the normal BrowserState publication path; no test wire
            // receiver or trust material is introduced.
            BrowserControlJourneyTest().js(
                browser,
                "document.title=" + JSONObject.quote(readyTitle) + ";document.title",
            )
            await("FW5 run-specific title committed", 2_000) {
                browser.pageTitle() == readyTitle
            }

            val firstCaptureFloor = SystemClock.elapsedRealtime()
            scenario.onActivity {
                server.start()
                server.publishPhoneViewport()
            }
            val readyState = server.controlCoordinator.authority.snapshot()
            assertEquals(
                "FW5 ready BrowserState binds the current Hosting generation",
                host.currentGeneration().toLong(),
                readyState.context.hostingGeneration,
            )
            Log.i(
                "EyeBrowseFW5",
                "PHONE_PHASE_READY mission=" + mission +
                    " hosting=true titleBound=true hostingGen=" +
                    readyState.context.hostingGeneration,
            )

            await("FW5 first RG owner with authenticated presentation", 15_000) {
                val state = server.controlCoordinator.authority.snapshot()
                server.isLinkUp() &&
                    state.owner == com.code2hack.eyebrowse.core.link.control.ControlOwner.RG &&
                    state.presentationStatus ==
                        com.code2hack.eyebrowse.core.link.control.PresentationStatus.READY &&
                    host.isRgPresentationOwned() && host.status().captureActive
            }
            val firstState = server.controlCoordinator.authority.snapshot()
            val firstProfile = checkNotNull(firstState.profile)
            val firstGeometry = checkNotNull(host.profileGeometry())
            assertTrue("FW5 first RG host has a valid private Window",
                firstGeometry.windowId != 0 && firstGeometry.presentationId != 0)
            assertTrue("FW5 Phone private local focus is continuously ready",
                host.localEditorFocusReady())
            assertEquals("FW5 Phone/WebView document unchanged for RG presentation",
                originalDocument, browser.documentIdentity())
            assertSame("FW5 same live WebView enters private presentation",
                originalView, browser.view())

            val firstReceipt = awaitEncodedReceipt(firstCaptureFloor, firstProfile.width, firstProfile.height)
            assertEncodedReceiptBound(firstReceipt)
            val firstDiagnostics = host.captureDiagnostics()
            assertTrue("FW5 encoder receipt is downstream of qualified Window SUCCESS: " +
                firstDiagnostics,
                firstDiagnostics.contains("copyResult=" + android.view.PixelCopy.SUCCESS))
            assertTrue("FW5 qualified capture delivered before encoding: " + firstDiagnostics,
                diagnosticCount(firstDiagnostics, "delivered") > 0)
            Log.i(
                "EyeBrowseFW5",
                "PHONE_ENCODED_FIRST mission=" + mission +
                    " seq=" + firstReceipt.sequence +
                    " capture=" + firstReceipt.captureElapsedMs +
                    " bytes=" + firstReceipt.bytes +
                    " profile=" + firstReceipt.width + "x" + firstReceipt.height +
                    " controlEpoch=" + firstState.context.controlEpoch +
                    " viewportEpoch=" + firstState.context.viewportEpoch +
                    " hostingGen=" + firstState.context.hostingGeneration,
            )

            // RG companion retires an actual pending old-context frame and hands control to Phone.
            await("FW5 RG stale-frame phase returns ownership to Phone", 8_000) {
                server.controlCoordinator.authority.snapshot().owner ==
                    com.code2hack.eyebrowse.core.link.control.ControlOwner.PHONE
            }
            val phoneState = server.controlCoordinator.authority.snapshot()
            Log.i("EyeBrowseFW5", "PHONE_PHASE_OLD_CONTEXT_RETIRED mission=" + mission)
            assertTrue("FW5 first handoff advances control epoch",
                phoneState.context.controlEpoch > firstState.context.controlEpoch)
            assertTrue("FW5 authenticated link remains up during Phone ownership",
                server.isLinkUp())
            scenario.onActivity {
                StopRecoveryAssertions.sameDocument(
                    "fw5_phone_handoff",
                    browser,
                    originalView,
                    originalDocument,
                )
            }

            val secondCaptureFloor = SystemClock.elapsedRealtime()
            await("FW5 RG reacquires current presentation", 8_000) {
                val state = server.controlCoordinator.authority.snapshot()
                state.owner == com.code2hack.eyebrowse.core.link.control.ControlOwner.RG &&
                    state.context.controlEpoch > firstState.context.controlEpoch &&
                    state.presentationStatus ==
                        com.code2hack.eyebrowse.core.link.control.PresentationStatus.READY &&
                    host.isRgPresentationOwned() && host.status().captureActive
            }
            val secondState = server.controlCoordinator.authority.snapshot()
            val secondProfile = checkNotNull(secondState.profile)
            val secondGeometry = checkNotNull(host.profileGeometry())
            // Owner handoff legitimately returns the WebView to Phone and may retire/recreate the
            // private host. FW5 same-window identity across keyboard profile resize is covered by
            // hostedRendererQualificationMatrix/FW3; this paired row asserts application identity
            // and a newly valid private host after reacquisition instead.
            assertSame("FW5 same live application WebView across paired owner roundtrip",
                originalView, browser.view())
            assertEquals("FW5 same document across paired owner roundtrip",
                originalDocument, browser.documentIdentity())
            assertEquals("FW5 reacquired geometry binds the same live WebView",
                System.identityHashCode(originalView), secondGeometry.viewId)
            assertTrue("FW5 reacquired private Window valid",
                secondGeometry.windowId != 0 && secondGeometry.presentationId != 0)
            assertTrue("FW5 local focus ready after RG reacquire",
                host.localEditorFocusReady())

            val secondReceipt = awaitEncodedReceipt(
                secondCaptureFloor,
                secondProfile.width,
                secondProfile.height,
            )
            assertEncodedReceiptBound(secondReceipt)
            assertTrue("FW5 fresh context produces a later capture receipt",
                secondReceipt.captureElapsedMs >= secondCaptureFloor)
            val secondDiagnostics = host.captureDiagnostics()
            assertTrue("FW5 reacquired encoder remains downstream of Window SUCCESS: " +
                secondDiagnostics,
                secondDiagnostics.contains("copyResult=" + android.view.PixelCopy.SUCCESS))
            Log.i(
                "EyeBrowseFW5",
                "PHONE_ENCODED_FRESH mission=" + mission +
                    " seq=" + secondReceipt.sequence +
                    " capture=" + secondReceipt.captureElapsedMs +
                    " bytes=" + secondReceipt.bytes +
                    " profile=" + secondReceipt.width + "x" + secondReceipt.height +
                    " controlEpoch=" + secondState.context.controlEpoch +
                    " viewportEpoch=" + secondState.context.viewportEpoch +
                    " hostingGen=" + secondState.context.hostingGeneration,
            )

            // RG's final handoff is the paired completion handshake.
            await("FW5 paired RG companion completes with Phone owner", 8_000) {
                server.controlCoordinator.authority.snapshot().owner ==
                    com.code2hack.eyebrowse.core.link.control.ControlOwner.PHONE
            }
            Log.i("EyeBrowseFW5", "PHONE_PHASE_COMPLETE mission=" + mission)
            assertTrue("FW5 link still authenticated before explicit cleanup", server.isLinkUp())
            assertSame(originalView, browser.view())
            assertEquals(originalDocument, browser.documentIdentity())
        } catch (failure: Throwable) {
            primaryFailure = failure
            Log.e("EyeBrowseFW5", "PHONE_PRIMARY_FAILURE", failure)
            throw failure
        } finally {
            val cleanup = listOf<() -> Unit>(
                { instrumentation.runOnMainSync { host.stop() } },
                { instrumentation.runOnMainSync { server.stop() } },
                { scenario.close() },
                {
                    StopRecoveryAssertions.await("FW5 phone cleanup", 5_000) {
                        StopRecoveryAssertions.resourcesGone(host)
                    }
                },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            cleanup.forEach { Log.e("EyeBrowseFW5", "PHONE_CLEANUP_FAILURE", it) }
            if (primaryFailure != null) cleanup.forEach { primaryFailure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) {
                cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }
                throw cleanup.first()
            }
        }
    }

    private data class EncodedReceipt(
        val sequence: Long,
        val captureElapsedMs: Long,
        val bytes: Int,
        val width: Int,
        val height: Int,
    )

    private fun awaitEncodedReceipt(
        minCaptureElapsedMs: Long,
        width: Int,
        height: Int,
    ): EncodedReceipt {
        var last: List<EncodedReceipt> = emptyList()
        val end = SystemClock.elapsedRealtime() + 3_000
        while (SystemClock.elapsedRealtime() < end) {
            last = encodedReceipts().filter {
                it.captureElapsedMs >= minCaptureElapsedMs &&
                    it.width == width && it.height == height
            }
            if (last.isNotEmpty()) return last.maxByOrNull { it.captureElapsedMs }!!
            SystemClock.sleep(50)
        }
        fail(
            "FW5 no production encoder receipt for capture>=" + minCaptureElapsedMs +
                " profile=" + width + "x" + height + " observed=" + last,
        )
        error("unreachable")
    }

    private fun encodedReceipts(): List<EncodedReceipt> {
        val text = shellOutput(
            "logcat -d -v brief -s EyeBrowsePresentation:I"
        )
        val regex = Regex(
            """encoded seq=(\d+) capture=(\d+) bytes=(\d+) profile=(\d+)x(\d+)"""
        )
        return regex.findAll(text).map { match ->
            EncodedReceipt(
                match.groupValues[1].toLong(),
                match.groupValues[2].toLong(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
                match.groupValues[5].toInt(),
            )
        }.toList()
    }

    private fun assertEncodedReceiptBound(receipt: EncodedReceipt) {
        val maxPixels =
            com.code2hack.eyebrowse.core.link.LinkProtocol.PRESENTATION_RECORD_MAX_BYTES -
                com.code2hack.eyebrowse.core.link.LinkProtocol.PRESENTATION_METADATA_MAX_BYTES - 4
        assertTrue("FW5 encoded payload must be non-empty", receipt.bytes > 0)
        assertTrue(
            "FW5 BoundedFrameOutput/wire payload must remain within bound: " + receipt,
            receipt.bytes <= maxPixels,
        )
        assertTrue("FW5 frame sequence positive", receipt.sequence > 0)
        assertTrue("FW5 capture timestamp positive", receipt.captureElapsedMs > 0)
    }

    private fun diagnosticCount(diagnostics: String, key: String): Long {
        val match = Regex("(?:^| )" + Regex.escape(key) + "=(\\d+)").find(diagnostics)
        return checkNotNull(match) { "missing " + key + " in {" + diagnostics + "}" }
            .groupValues[1].toLong()
    }

    private fun shellOutput(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        return try {
            java.io.FileInputStream(descriptor.fileDescriptor).use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toString("UTF-8")
            }
        } finally {
            descriptor.close()
        }
    }

    private fun await(label: String, bound: Long, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime()+bound
        while (SystemClock.elapsedRealtime() < end) {
            var pass = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { pass = predicate() }
            if (pass) return
            SystemClock.sleep(50)
        }
        fail(label)
    }
}
