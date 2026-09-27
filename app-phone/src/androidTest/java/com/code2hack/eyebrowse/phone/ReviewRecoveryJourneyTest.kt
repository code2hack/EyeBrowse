package com.code2hack.eyebrowse.phone

import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.transport.LinkServerEngine
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Independent field/navigation/submit effects; coordination never supplies a result oracle. */
@RunWith(AndroidJUnit4::class)
class ReviewRecoveryJourneyTest {
    @Test fun observesEditorAndNavigationEffectsAcrossResultFaultsAndRecovery() = run(false)
    @Test fun nativeStopWhileRememberedReconnectIsInFlight() = run(true)
    private fun run(stopReconnect:Boolean) {
        val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext
        val args=InstrumentationRegistry.getArguments();val mission=UUID.fromString(checkNotNull(args.getString("missionId"))).toString()
        val url=checkNotNull(args.getString("fixtureBaseUrl")).trimEnd('/')+
            if(stopReconnect) "/control.html?case=$mission" else "/keyboard.html?case=$mission&recoveryLedger=1"
        val browser=PhoneBrowserSession.get(app);val host=HostingController.get(app);val link=PhoneLinkServer.obtain(app)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val phase=File(app.cacheDir,"i11-review-$mission.phase");val ack=File(app.cacheDir,"i11-review-$mission.ack")
        val helper=BrowserControlJourneyTest()
        fun js(script:String)=helper.js(browser,script)
        fun await(label:String,bound:Long=5_000,condition:()->Boolean)=StopRecoveryAssertions.await(label,bound,condition)
        fun step(name:String,verify:()->Unit) {
            await("RG phase $name",20_000) { val value=phase.takeIf { it.exists() }?.readText()?.trim();check(value!="abort");value==name }
            verify();ack.writeText(name)
            Log.i("EyeBrowseReviewRecovery","PHONE_VERIFIED mission=$mission phase=$name at=${SystemClock.elapsedRealtime()}")
        }
        fun editor(value:String,inputs:Int,submits:Int) {
            assertEquals("true",js("document.getElementById('text').value===${org.json.JSONObject.quote(value)}"))
            assertEquals(inputs.toString(),js("fixtureInputs"));assertEquals(submits.toString(),js("fixtureSubmits"))
            Log.i("EyeBrowseReviewRecovery","EFFECTS mission=$mission valueMatches=true inputs=$inputs submits=$submits")
        }
        fun clickCount()=assertEquals("1",js("JSON.parse(sessionStorage.getItem(window.fixtureKey)||'[]').filter(e=>e.startsWith('click:')).length"))
        var primary:Throwable?=null
        try {
            phase.writeText("");ack.writeText("")
            lateinit var barrier:FixtureNavigationBarrier
            scenario.onActivity {
                host.stop();link.stop()
                barrier=FixtureNavigationBarrier(browser.documentIdentity(),url,if(stopReconnect) "T03 A" else "KBD")
                browser.openAddress(url)
            }
            await("fresh mission document",10_000) {
                barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(),browser.displayUrl(),browser.lastCommittedUrl(),browser.pageTitle()?.substringBefore('|'),browser.isLoading(),browser.isLive(),browser.errorMessage()))
            }
            assertEquals("true",js("document.readyState==='complete'"))
            val view=browser.view();val document=browser.documentIdentity()
            val identity=js(if(stopReconnect) "fixtureMarker" else "fixtureIdentity")
            val history=fw4RunOnMainChecked { checkNotNull(view).copyBackForwardList().size }
            fun continuity() {
                fw4RunOnMainChecked { assertSame(view,browser.view());assertEquals(document,browser.documentIdentity());assertEquals(history,checkNotNull(view).copyBackForwardList().size) }
                assertEquals(identity,js(if(stopReconnect) "fixtureMarker" else "fixtureIdentity"))
            }
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting") { host.status().state==HostingController.State.HOSTING }
            scenario.onActivity { link.start();link.publishPhoneViewport() }
            Log.i("EyeBrowseReviewRecovery","PHONE_READY mission=$mission stopReconnect=$stopReconnect")
            if(stopReconnect) {
                val engine=PhoneLinkServer::class.java.getDeclaredField("engine").let { it.isAccessible=true;it.get(link) as LinkServerEngine }
                step("effect_before_reconnect") { clickCount();continuity();assertTrue(link.isLinkUp()) }
                step("link_down") { await("old app link closed") { !link.isLinkUp() };clickCount();continuity() }
                step("reconnect_inflight") {
                    assertFalse(link.isLinkUp());assertEquals(HostingController.State.HOSTING,host.status().state)
                    Log.i("EyeBrowseReviewRecovery","STOP_BEFORE_RELEASE mission=$mission at=${SystemClock.elapsedRealtime()} rememberedReconnectBarrier=true")
                    scenario.onActivity { assertTrue(it.findViewById<Button>(R.id.button_hosting_toggle).performClick()) }
                    await("Stop listener and hosting retired") { !link.isLinkUp() && engine.boundPort()==-1 && StopRecoveryAssertions.resourcesGone(host) }
                    assertTrue(link.isPaired());continuity();clickCount()
                }
                step("settled_after_stop") {
                    assertFalse(link.isLinkUp());assertEquals(-1,engine.boundPort());assertTrue(StopRecoveryAssertions.resourcesGone(host))
                    assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                    scenario.onActivity {
                        assertTrue(it.findViewById<Button>(R.id.button_open).isEnabled)
                        assertSame(it.findViewById<android.view.ViewGroup>(R.id.web_container),browser.view()!!.parent)
                    }
                    continuity();clickCount();assertTrue(link.isPaired())
                }
            } else {
                step("lost_edit") { editor("a",1,0);continuity() }
                step("duplicate_edit") { editor("ab",2,0);continuity() }
                step("lost_enter") { editor("ab",2,1);continuity() }
                step("pending_edit") { editor("abc",3,1);continuity();assertTrue(link.isLinkUp()) }
                step("editor_link_down") {
                    await("authenticated link and editor retired") { !link.isLinkUp() && link.editorController!!.isQuiescent() }
                    editor("abc",3,1);continuity()
                }
                step("editor_recovered") {
                    assertTrue(link.isLinkUp());assertTrue(link.editorController!!.isQuiescent())
                    editor("abc",3,1);continuity()
                }
                step("explicit_fresh_edit") { editor("abcd",4,1);continuity() }
                step("navigation_effect") {
                    assertNotEquals(document,browser.documentIdentity());assertEquals(url,browser.lastCommittedUrl())
                    assertEquals("true",js("sessionStorage.getItem(fixtureRecoveryKey)==='2'"))
                    editor("",0,0)
                }
                step("navigation_duplicate") {
                    assertEquals("true",js("sessionStorage.getItem(fixtureRecoveryKey)==='2'"));editor("",0,0)
                    assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                }
            }
            await("RG complete") { phase.exists() && phase.readText().trim()=="complete" }
            Log.i("EyeBrowseReviewRecovery","PHONE_PASS mission=$mission stopReconnect=$stopReconnect")
        } catch(t:Throwable) { primary=t;throw t }
        finally {
            val errors=listOf<()->Unit>(
                { fw4RunOnMainChecked { host.stop();link.stop() } },
                { await("resources retired") { StopRecoveryAssertions.resourcesGone(host) && !link.isLinkUp() } },
                { if(browser.isLive() && browser.lastCommittedUrl()==url) {
                    js("sessionStorage.removeItem('${if(stopReconnect) "t03-" else "i11-loads-"}$mission');true")
                    assertEquals("null",js("sessionStorage.getItem('${if(stopReconnect) "t03-" else "i11-loads-"}$mission')"))
                } },
                { scenario.close() },{ phase.delete();ack.delete();assertFalse(phase.exists());assertFalse(ack.exists()) },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(primary!=null)errors.forEach { primary.addSuppressed(it) }
            else if(errors.isNotEmpty()) { errors.drop(1).forEach { errors.first().addSuppressed(it) };throw errors.first() }
        }
    }
}
