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
    @Test fun phoneHostsAcrossBackgroundScreenOffLiveStopAndRestart() {
        val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext
        val args=InstrumentationRegistry.getArguments();val mission=UUID.fromString(args.getString("missionId")).toString()
        val phone=File(app.cacheDir,"i12-screen-$mission.phone");val rg=File(app.cacheDir,"i12-screen-$mission.rg");val screen=File(app.cacheDir,"i12-screen-$mission.screen")
        val browser=PhoneBrowserSession.get(app);val host=HostingController.get(app);val link=PhoneLinkServer.obtain(app)
        val scenario=ActivityScenario.launch(MainActivity::class.java);lateinit var activity:MainActivity
        val helper=BrowserControlJourneyTest();val url=checkNotNull(args.getString("fixtureBaseUrl"))+"/control.html?case=$mission"
        val trust=File(app.filesDir,"pairing/peer_trust.json").readBytes()
        fun await(label:String,bound:Long=10_000,check:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+bound
            while(SystemClock.elapsedRealtime()<end) {
                kotlin.check(!screen.exists() || screen.readText().trim()!="abort") { "screen runner aborted" }
                if(fw4RunOnMainChecked(check))return
                SystemClock.sleep(20)
            }
            fail(label)
        }
        fun waitRg(value:String)=await("RG $value",15_000) { rg.exists() && rg.readText().trim()==value }
        fun publish(value:String) { phone.writeText(value);Log.i("EyeBrowseA06","PHONE_PHASE mission=$mission phase=$value elapsedMs=${SystemClock.elapsedRealtime()}") }
        fun js(value:String)=helper.js(browser,value)
        var failure:Throwable?=null
        try {
            scenario.onActivity { activity=it;host.stop();link.stop();browser.openAddress(url) }
            await("fixture") { browser.lastCommittedUrl()==url && !browser.isLoading() && browser.pageTitle()?.startsWith("T03 A")==true }
            val original=fw4RunOnMainChecked { checkNotNull(browser.view()) }
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("native hosting",5_000) { host.status().state==HostingController.State.HOSTING }
            publish("ready");waitRg("owned")
            scenario.onActivity { assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner);it.moveTaskToBack(true) }
            await("Phone background",3_000) { !activity.hasWindowFocus() };publish("background")
            waitRg("background-done")
            await("returned A document") { browser.lastCommittedUrl()==url && !browser.isLoading() }
            fw4RunOnMainChecked { assertSame(original,browser.view()) }
            js("document.getElementById('state').value='A06-preserved';window.__i12Tick=0;window.__i12Timer=setInterval(()=>{document.body.style.backgroundColor='rgb('+(100+(++window.__i12Tick%100))+',200,180)'},100);true")
            val document=fw4RunOnMainChecked { browser.documentIdentity() }
            val history=fw4RunOnMainChecked { original.copyBackForwardList().size }
            publish("sleep")
            await("runner confirms physical OFF",10_000) { screen.exists() && screen.readText().trim()=="off" }
            assertFalse(app.getSystemService(android.os.PowerManager::class.java).isInteractive)
            waitRg("off-done")
            fw4RunOnMainChecked {
                assertFalse(app.getSystemService(android.os.PowerManager::class.java).isInteractive)
                assertSame(original,browser.view());assertEquals(document,browser.documentIdentity());assertEquals(160,original.scrollY)
                assertTrue(link.isLinkUp());assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
            }
            val effects=js("sessionStorage.getItem(window.fixtureKey)")
            assertEquals(1,Regex("click:A:1").findAll(effects).count());assertFalse(effects.contains("click:A:2"))
            assertEquals("\"A06-preserved\"",js("document.getElementById('state').value"))
            Log.i("EyeBrowseA06","OFF_EFFECTS mission=$mission click1=true nativeY=160 sameView=true sameDocument=true interactive=false diagnostics=${fw4RunOnMainChecked { host.captureDiagnostics() }}")
            publish("wake")
            await("runner confirms physical ON",10_000) { screen.readText().trim()=="on" }
            await("Phone native foreground",3_000) { activity.hasWindowFocus() }
            fw4RunOnMainChecked { assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner) }
            publish("stop");waitRg("stop-ready")
            val stopAt=SystemClock.elapsedRealtime()
            scenario.onActivity { assertTrue(link.isLinkUp());it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("live Stop retires link/resources",5_000) { !link.isLinkUp() && StopRecoveryAssertions.resourcesGone(host) }
            assertTrue(SystemClock.elapsedRealtime()-stopAt<=5_000)
            fw4RunOnMainChecked {
                assertNull(PhoneLinkServer::class.java.getDeclaredField("engine").apply { isAccessible=true }.get(link))
                assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                assertSame(original,browser.view());assertEquals(document,browser.documentIdentity());assertEquals(history,original.copyBackForwardList().size)
                assertTrue(activity.findViewById<Button>(R.id.button_open).isEnabled)
            }
            Log.i("EyeBrowseA06","LIVE_STOP mission=$mission stopMs=${SystemClock.elapsedRealtime()-stopAt} listenerRetired=true resourcesGone=true")
            publish("stopped");waitRg("retry-refused")
            fw4RunOnMainChecked { assertFalse(link.isLinkUp());assertTrue(StopRecoveryAssertions.resourcesGone(host)) }
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("explicit restart",5_000) { host.status().state==HostingController.State.HOSTING }
            publish("restarted");waitRg("restart-effect")
            fw4RunOnMainChecked { assertSame(original,browser.view());assertEquals(document,browser.documentIdentity());assertEquals(history,original.copyBackForwardList().size);assertEquals(0,original.scrollY) }
            assertEquals("\"A06-preserved\"",js("document.getElementById('state').value"))
            assertEquals(1,Regex("click:A:2").findAll(js("sessionStorage.getItem(window.fixtureKey)")).count())
            assertArrayEquals(trust,File(app.filesDir,"pairing/peer_trust.json").readBytes())
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("final Stop",5_000) { !link.isLinkUp() && StopRecoveryAssertions.resourcesGone(host) }
            publish("complete");waitRg("done")
        } catch(t:Throwable) { failure=t;throw t }
        finally {
            val errors=listOf<()->Unit>(
                { fw4RunOnMainChecked { host.stop();link.stop() } },
                { if(browser.isLive() && browser.lastCommittedUrl()==url) js("clearInterval(window.__i12Timer);delete window.__i12Timer;document.body.style.backgroundColor='';sessionStorage.removeItem('t03-$mission');true") },
                { scenario.close() },
                { listOf(phone,rg,screen).forEach { it.delete();assertFalse(it.exists()) } },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(failure!=null)errors.forEach { failure.addSuppressed(it) } else org.junit.runners.model.MultipleFailureException.assertEmpty(errors)
        }
    }

    @Test fun neverLeasedHostingReleasesCaptureWithinOriginalIdleDeadline() {
        val browser=PhoneBrowserSession.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val host=HostingController.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val link=PhoneLinkServer.obtain(InstrumentationRegistry.getInstrumentation().targetContext)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var activity:MainActivity
        scenario.onActivity { activity=it }
        var failure:Throwable?=null
        try {
            scenario.onActivity { host.stop();link.stop() }
            val loaded=StopRecoveryAssertions.openFixture(scenario,browser)
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick();it.moveTaskToBack(true) }
            StopRecoveryAssertions.await("private attachment and idle anchor",5_000) { host.status().attachment==HostingController.Attachment.PRIVATE_DISPLAY && host.lastDemandAnchorElapsedMs()>0 }
            val anchor=fw4RunOnMainChecked { host.lastDemandAnchorElapsedMs() };val deadline=anchor+30_000
            StopRecoveryAssertions.await("never-leased production release",35_000) { host.lastIdleReleaseCompletedElapsedMs()>=anchor }
            val completed=fw4RunOnMainChecked { host.lastIdleReleaseCompletedElapsedMs() }
            assertTrue("original idle deadline",completed<=deadline)
            fw4RunOnMainChecked { assertFalse(host.captureResourcesPresent());assertEquals(HostingController.State.HOSTING,host.status().state);assertSame(loaded.view,browser.view());assertEquals(loaded.documentId,browser.documentIdentity()) }
            Log.i("EyeBrowseA06","IDLE_RELEASE anchor=$anchor completed=$completed deadline=$deadline clock=elapsedRealtime")
            scenario.onActivity { it.startActivity(android.content.Intent(it,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            StopRecoveryAssertions.await("foreground before Stop",3_000) { activity.hasWindowFocus() }
            val stopAt=SystemClock.elapsedRealtime()
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            StopRecoveryAssertions.await("idle row Stop",5_000) { !link.isLinkUp() && StopRecoveryAssertions.resourcesGone(host) }
            assertTrue(SystemClock.elapsedRealtime()-stopAt<=5_000)
        } catch(t:Throwable) { failure=t;throw t }
        finally {
            val errors=listOf<()->Unit>({ fw4RunOnMainChecked { host.stop();link.stop() } },{ scenario.close() }).mapNotNull { runCatching(it).exceptionOrNull() }
            if(failure!=null)errors.forEach { failure.addSuppressed(it) } else org.junit.runners.model.MultipleFailureException.assertEmpty(errors)
        }
    }

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
