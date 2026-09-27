package com.code2hack.eyebrowse.phone

import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Fixture observations are independent of the RG result-delivery fault wrapper. */
@RunWith(AndroidJUnit4::class)
class AckLossJourneyTest {
    @Test fun observesExactlyFourEffectsDespiteLostDelayedAndDuplicateResults() = journey(false)
    @Test fun observesContinuityAndTwoEffectsAcrossUncertainReconnectAndStop() = journey(true)

    private fun journey(recovery: Boolean) {
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
        val phase = File(app.cacheDir, "i11-$mission.phase").apply { writeText("") }
        val helper = BrowserControlJourneyTest()
        fun js(script: String) = helper.js(browser, script)
        fun await(label: String, bound: Long = 10_000, condition: () -> Boolean) = StopRecoveryAssertions.await(label, bound, condition)
        fun effects(expected: Int) {
            assertEquals((1..expected).joinToString(",", "[", "]") { "\"click:A:$it\"" },
                js("JSON.parse(sessionStorage.getItem(window.fixtureKey)||'[]').filter(e=>e.startsWith('click:'))"))
            Log.i("EyeBrowseAckLoss", "EFFECTS mission=$mission clicks=$expected source=Phone-WebView-sessionStorage")
        }
        var failure: Throwable? = null
        try {
            lateinit var barrier: FixtureNavigationBarrier
            scenario.onActivity {
                host.stop(); link.stop()
                barrier = FixtureNavigationBarrier(browser.documentIdentity(), url, "T03 A")
                browser.openAddress(url)
            }
            await("fresh ACK fixture") {
                barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(), browser.displayUrl(), browser.lastCommittedUrl(), browser.pageTitle()?.substringBefore("|G="), browser.isLoading(), browser.isLive(), browser.errorMessage()))
            }
            assertEquals("true", js("document.readyState==='complete' && typeof window.__t03Geometry==='function'"))
            val document = browser.documentIdentity()
            val marker = js("window.fixtureMarker")
            js("document.getElementById('state').value='I11-ack-unsaved';true")
            var view: android.webkit.WebView? = null
            var history = 0
            var historyIndex = 0
            scenario.onActivity {
                view = browser.view(); history = view!!.copyBackForwardList().size; historyIndex = view!!.copyBackForwardList().currentIndex
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("hosting", 5_000) { host.status().state == HostingController.State.HOSTING }
            scenario.onActivity { link.start(); link.publishPhoneViewport() }
            phase.writeText("ready")
            Log.i("EyeBrowseAckLoss", "PHONE_READY mission=$mission recovery=$recovery")
            fun continuity() {
                scenario.onActivity {
                    assertSame(view, browser.view()); assertEquals(document, browser.documentIdentity())
                    assertEquals(history, view!!.copyBackForwardList().size)
                    assertEquals(historyIndex, view!!.copyBackForwardList().currentIndex)
                }
                assertEquals(marker, js("window.fixtureMarker"))
                assertEquals("\"I11-ack-unsaved\"", js("document.getElementById('state').value"))
            }
            await("RG ownership", 15_000) { link.controlCoordinator.authority.snapshot().owner == ControlOwner.RG }
            val context = link.controlCoordinator.authority.snapshot().context
            if (recovery) {
                await("RG uncertain-action disconnect", 15_000) { ack.readText().trim() == "disconnected" && !link.isLinkUp() }
                await("absent-client capture released", 5_000) { !host.status().captureActive }
                effects(1); continuity()
                assertEquals(ControlOwner.RG, link.controlCoordinator.authority.snapshot().owner)
                phase.writeText("reconnect")
                Log.i("EyeBrowseAckLoss", "PHONE_PHASE mission=$mission phase=reconnect")
                await("RG uncertain-action Stop barrier", 15_000) { ack.readText().trim() == "stop" }
                val recovered = link.controlCoordinator.authority.snapshot().context
                assertEquals(context.lifetimeId, recovered.lifetimeId)
                assertEquals(context.controlEpoch, recovered.controlEpoch)
                assertTrue(recovered.viewportEpoch > context.viewportEpoch)
                effects(2)
            } else {
                await("RG completed ACK matrix", 35_000) { ack.readText().trim() == "done" }
                effects(4)
                assertEquals(context.controlEpoch + 2, link.controlCoordinator.authority.snapshot().controlEpoch)
            }
            continuity()
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("Stop resources and authenticated link retired", 5_000) { !link.isLinkUp() && StopRecoveryAssertions.resourcesGone(host) }
            continuity(); effects(if (recovery) 2 else 4)
            assertEquals(ControlOwner.PHONE, link.controlCoordinator.authority.snapshot().owner)
            Log.i("EyeBrowseAckLoss", "PHONE_PASS mission=$mission recovery=$recovery sameView=true sameDocument=true sameHistory=true sameDraft=true stopClean=true")
        } catch (t: Throwable) { failure = t; throw t }
        finally {
            val cleanup = listOf<() -> Unit>(
                { instrumentation.runOnMainSync { host.stop(); link.stop() } },
                { if (browser.isLive() && browser.lastCommittedUrl() == url) { js("sessionStorage.removeItem('t03-$mission');true"); assertEquals("null", js("sessionStorage.getItem('t03-$mission')")) } },
                { scenario.close() }, { ack.delete(); assertFalse(ack.exists()); phase.delete(); assertFalse(phase.exists()) },
                { await("final resources released", 5_000) { StopRecoveryAssertions.resourcesGone(host) } },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if (failure != null) cleanup.forEach { failure.addSuppressed(it) }
            else if (cleanup.isNotEmpty()) { cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) }; throw cleanup.first() }
        }
    }
}
