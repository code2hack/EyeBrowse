package com.code2hack.eyebrowse.phone

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.control.PresentationStatus
import com.code2hack.eyebrowse.phone.link.PhoneLinkIdentity
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import com.code2hack.eyebrowse.phone.pairing.PairingActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Fixed mission phases only; no product receiver or trust-restoration shortcut. */
@RunWith(AndroidJUnit4::class)
class RgRecoveryCompanionTest {
    @Test fun liveHostSurvivesRgProcessRestart() = companion("restart")
    @Test fun authenticPeerPrecedesWrongPeerAtTestAlias() = companion("wrong-peer")
    @Test fun nativeRgForgetAndAuthenticatedRepairPreservePhone() = companion("forget")

    private fun companion(mode: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        require(args.getString("restartOwner", "rg") in listOf("rg", "phone"))
        require(args.getString("forgetMode", "online") in listOf("online", "offline"))
        val mission = UUID.fromString(checkNotNull(args.getString("missionId"))).toString()
        val takeover = args.getString("restartOwner", "rg") == "phone"
        val online = args.getString("forgetMode", "online") == "online"
        val phase = File(app.cacheDir, "i11-rg-$mission.phase").apply { writeText("starting") }
        val qr = File(app.cacheDir, "i11-rg-$mission.png")
        val trust = File(app.filesDir, "pairing/peer_trust.json")
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val server = PhoneLinkServer.obtain(app)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var pairing: ActivityScenario<PairingActivity>? = null
        val url = checkNotNull(args.getString("fixtureBaseUrl")).trimEnd('/') + "/control.html?case=$mission"
        val helper = BrowserControlJourneyTest()
        val deadline = SystemClock.elapsedRealtime() + 55_000
        fun await(label: String, bound: Long = 10_000, predicate: () -> Boolean) =
            StopRecoveryAssertions.await(label, minOf(bound, (deadline-SystemClock.elapsedRealtime()).coerceAtLeast(1)), predicate)
        fun publish(value: String) { phase.writeText(value); Log.i("EyeBrowseRgRecovery", "PHONE_PHASE mission=$mission mode=$mode phase=$value") }
        var failure: Throwable? = null
        try {
            assertTrue("remembered RG required", server.isPaired())
            val trustBefore = trust.readBytes()
            val ownIdentity = PhoneLinkIdentity().spkiSha256Hex()
            val navigation = FixtureNavigationBarrier(browser.documentIdentity(), url, "T03 A")
            scenario.onActivity { host.stop(); server.stop(); browser.openAddress(url) }
            await("fresh fixture") { navigation.isReady(FixtureNavigationBarrier.Observation(
                browser.documentIdentity(),browser.displayUrl(),browser.lastCommittedUrl(),
                browser.pageTitle()?.substringBefore("|G="),browser.isLoading(),browser.isLive(),browser.errorMessage())) }
            val document = browser.documentIdentity()
            var view: android.webkit.WebView? = null
            var history = 0
            scenario.onActivity { view=browser.view();history=view!!.copyBackForwardList().size }
            val marker = helper.js(browser,"window.fixtureMarker")
            helper.js(browser,"document.getElementById('state').value='I11-RG-live';true")
            fun continuity(label: String) {
                scenario.onActivity { assertSame(view,browser.view());assertEquals(document,browser.documentIdentity());assertEquals(history,browser.view()!!.copyBackForwardList().size) }
                assertEquals(marker,helper.js(browser,"window.fixtureMarker"))
                assertEquals("\"I11-RG-live\"",helper.js(browser,"document.getElementById('state').value"))
                Log.i("EyeBrowseRgRecovery","PHONE_CONTINUITY mission=$mission stage=$label sameView=true sameDocument=true sameHistory=true sameDraft=true")
            }
            when (mode) {
                "restart" -> {
                    scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick();server.start() }
                    await("hosting",5_000) { host.status().state==HostingController.State.HOSTING }
                    publish("ready")
                    await("RG owns current presentation",20_000) { server.controlCoordinator.authority.snapshot().let { it.owner==ControlOwner.RG && it.presentationStatus==PresentationStatus.READY } }
                    val before=server.controlCoordinator.authority.snapshot().context
                    publish("prepared")
                    await("RG process link gone",15_000) { !server.isLinkUp() }
                    await("capture demand stopped",6_000) { !host.status().captureActive }
                    continuity("rg-absent")
                    if(takeover) scenario.onActivity { it.findViewById<Button>(R.id.button_use_phone).performClick() }
                    val expected=if(takeover) ControlOwner.PHONE else ControlOwner.RG
                    await("expected owner",5_000) { server.controlCoordinator.authority.snapshot().owner==expected }
                    publish("resume")
                    await("restart reconciled") { server.isLinkUp() && server.controlCoordinator.authority.snapshot().let { it.owner==expected && (takeover || it.presentationStatus==PresentationStatus.READY) } }
                    val after=server.controlCoordinator.authority.snapshot().context
                    assertEquals(before.lifetimeId,after.lifetimeId);assertEquals(before.documentId,after.documentId)
                    if(takeover) assertTrue(after.controlEpoch>before.controlEpoch) else assertEquals(before.controlEpoch,after.controlEpoch)
                    assertTrue(after.viewportEpoch>before.viewportEpoch)
                    publish("reconciled")
                    await("RG terminal verification") { phase.readText().trim()=="verified" }
                    continuity("rg-restarted")
                    assertArrayEquals(trustBefore,trust.readBytes())
                }
                "wrong-peer" -> {
                    scenario.onActivity { server.start() };publish("ready")
                    await("positive authenticated RG",15_000) { server.isLinkUp() };publish("authenticated")
                    await("positive session closed") { !server.isLinkUp() };publish("swap-ready")
                    await("wrong peer row complete",20_000) { phase.readText().trim()=="verified" }
                    assertArrayEquals(trustBefore,trust.readBytes());continuity("wrong-peer")
                }
                "forget" -> {
                    scenario.onActivity { if(online)server.start() else server.stop() };publish("ready")
                    await("native RG Forget complete",20_000) { phase.readText().trim()=="forgotten" }
                    await("RG no longer connected",5_000) { !server.isLinkUp() }
                    assertArrayEquals("RG Forget cannot erase Phone trust",trustBefore,trust.readBytes())
                    continuity("rg-forgotten")
                    pairing=ActivityScenario.launch(PairingActivity::class.java)
                    pairing!!.onActivity {
                        it.findViewById<Button>(R.id.button_generate_qr).performClick()
                        val bitmap=(it.findViewById<ImageView>(R.id.pairing_qr).drawable as BitmapDrawable).bitmap
                        qr.outputStream().use { stream -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) }
                    }
                    assertNotNull(server.activeInvitation());publish("qr-ready")
                    await("authenticated repair complete",20_000) { phase.readText().trim()=="repaired" }
                    assertNull("fresh invitation consumed",server.activeInvitation())
                    assertTrue(server.isPaired())
                    assertEquals(ownIdentity,PhoneLinkIdentity().spkiSha256Hex())
                    assertEquals(ControlOwner.PHONE,server.controlCoordinator.authority.snapshot().owner)
                    pairing!!.close();pairing=null
                    continuity("rg-repaired")
                }
            }
            Log.i("EyeBrowseRgRecovery","PHONE_PASS mission=$mission mode=$mode")
        } catch(t: Throwable) { failure=t;throw t }
        finally {
            val cleanup=listOf<()->Unit>(
                { pairing?.close() },
                { instrumentation.runOnMainSync { server.cancelInvitation();host.stop();server.stop() } },
                { if(browser.isLive() && browser.lastCommittedUrl()==url) helper.js(browser,"sessionStorage.removeItem('t03-$mission');true") },
                { scenario.close() },
                { qr.delete();phase.delete();assertFalse(qr.exists());assertFalse(phase.exists()) },
                { StopRecoveryAssertions.await("resource cleanup",5_000) { StopRecoveryAssertions.resourcesGone(host) } },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(failure!=null) cleanup.forEach { failure.addSuppressed(it) }
            else if(cleanup.isNotEmpty()) { cleanup.drop(1).forEach { cleanup.first().addSuppressed(it) };throw cleanup.first() }
        }
    }
}
