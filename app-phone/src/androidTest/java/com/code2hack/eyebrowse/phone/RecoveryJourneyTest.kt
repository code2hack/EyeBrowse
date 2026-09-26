package com.code2hack.eyebrowse.phone

import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.control.PresentationStatus
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual-app companion; the host forwards only phase receipts through mission-owned ack files. */
@RunWith(AndroidJUnit4::class)
class RecoveryJourneyTest {
    @Test fun livePageSurvivesLinkLossRecreationAndTakeoverUntilExplicitStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val mission = UUID.fromString(checkNotNull(args.getString("missionId"))).toString()
        val url = checkNotNull(args.getString("fixtureBaseUrl")).trimEnd('/') + "/control.html?case=$mission"
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val link = PhoneLinkServer.obtain(app)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val ack = File(app.cacheDir, "i11-$mission.ack").apply { writeText("") }
        val helper = BrowserControlJourneyTest()
        fun js(script: String) = helper.js(browser, script)
        fun await(label: String, bound: Long = 10_000, condition: () -> Boolean) = StopRecoveryAssertions.await(label, bound, condition)
        fun title(phase: String) { js("window.__t03Title(${JSONObject.quote("I11-$mission-$phase")});true") }
        fun ready() = link.controlCoordinator.authority.snapshot().let { it.linkAuthenticated && it.owner == ControlOwner.RG && it.presentationStatus == PresentationStatus.READY }
        var failure: Throwable? = null
        try {
            scenario.onActivity { host.stop(); link.stop(); browser.openAddress(url) }
            await("fresh recovery fixture") { browser.isLive() && !browser.isLoading() && browser.lastCommittedUrl() == url }
            val document = browser.documentIdentity()
            var view: android.webkit.WebView? = null
            var history = 0
            scenario.onActivity { view = browser.view(); history = view!!.copyBackForwardList().size }
            val marker = js("window.fixtureMarker")
            js("document.getElementById('state').value='I11-unsaved';true")
            fun continuity(phase: String) {
                scenario.onActivity {
                    assertSame(view, browser.view()); assertEquals(document, browser.documentIdentity())
                    assertEquals(history, browser.view()!!.copyBackForwardList().size)
                }
                assertEquals(marker, js("window.fixtureMarker"))
                assertEquals("\"I11-unsaved\"", js("document.getElementById('state').value"))
                Log.i("EyeBrowseRecovery", "CONTINUITY mission=$mission phase=$phase sameView=true sameDocument=true sameHistory=true sameDraft=true")
            }
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting ready", 5_000) { host.status().state == HostingController.State.HOSTING }
            title("ready")
            scenario.onActivity { link.start(); link.publishPhoneViewport() }
            Log.i("EyeBrowseRecovery", "PHONE_READY mission=$mission")
            await("first RG ownership", 20_000) { ready() }
            var context = link.controlCoordinator.authority.snapshot().context
            title("drop")
            for (phase in listOf("reconnect", "recreate")) {
                await("$phase disconnected", 15_000) { !link.isLinkUp() }
                await("$phase capture demand released", 5_000) { !host.status().captureActive }
                continuity("$phase-disconnected")
                assertEquals(ControlOwner.RG, link.controlCoordinator.authority.snapshot().owner)
                Log.i("EyeBrowseRecovery", "PHONE_RELEASE mission=$mission phase=$phase")
                await("$phase live reconciliation") { ready() }
                val recovered = link.controlCoordinator.authority.snapshot().context
                assertEquals(context.lifetimeId, recovered.lifetimeId)
                assertEquals(context.controlEpoch, recovered.controlEpoch)
                assertTrue(recovered.viewportEpoch > context.viewportEpoch)
                context = recovered
                continuity(phase)
                if (phase == "reconnect") {
                    scenario.recreate()
                    continuity("phone-recreated")
                    assertEquals(context, link.controlCoordinator.authority.snapshot().context)
                    title("recreate-rg")
                }
            }
            title("takeover")
            await("disconnected takeover", 15_000) { !link.isLinkUp() }
            scenario.onActivity { it.findViewById<Button>(R.id.button_use_phone).performClick() }
            await("local authority returned", 5_000) { link.controlCoordinator.authority.snapshot().owner == ControlOwner.PHONE }
            continuity("phone-takeover")
            val phoneEpoch = link.controlCoordinator.authority.snapshot().controlEpoch
            Log.i("EyeBrowseRecovery", "PHONE_RELEASE mission=$mission phase=takeover")
            await("explicit fresh RG consent") { ready() && link.controlCoordinator.authority.snapshot().controlEpoch > phoneEpoch }
            title("stop")
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (ack.readText().trim() != "stop" && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
            assertEquals("RG observed fresh ownership before Stop", "stop", ack.readText().trim())
            val stopAt = SystemClock.elapsedRealtime()
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("Stop resources and link retired", 5_000) { !link.isLinkUp() && StopRecoveryAssertions.resourcesGone(host) }
            continuity("stop")
            assertEquals(ControlOwner.PHONE, link.controlCoordinator.authority.snapshot().owner)
            Log.i("EyeBrowseRecovery", "PHONE_PASS mission=$mission stopMs=${SystemClock.elapsedRealtime() - stopAt}")
        } catch (t: Throwable) { failure = t; throw t }
        finally {
            val cleanup = listOf<() -> Unit>(
                { instrumentation.runOnMainSync { host.stop(); link.stop() } },
                { if (browser.isLive() && browser.lastCommittedUrl() == url) js("sessionStorage.removeItem('t03-$mission');true") },
                { scenario.close() },
                { ack.delete(); assertFalse(ack.exists()) },
                { await("final resource cleanup", 5_000) { StopRecoveryAssertions.resourcesGone(host) } },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if (failure != null) cleanup.forEach { failure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) { cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }; throw cleanup.first() }
        }
    }
}
