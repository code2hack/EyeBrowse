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
        var cleanupRequested=false
        var nativeStopRequested=false
        var hostingReceipts=0
        var previousHosting:HostingController.State?=null
        var previousReason:String?=null
        var restorePublisher:(()->Unit)?=null
        fun reasonCode(reason:String?):String = when(reason) {
            null -> "none"
            "Profile resize did not preserve a settled focused window" -> "profile-focus-unsettled"
            "RG presentation allocation failed" -> "rg-allocation-failed"
            "Editor retirement unconfirmed" -> "editor-retirement-unconfirmed"
            "private presentation became unavailable; explicit restart required" -> "private-presentation-unavailable"
            "Profile frame deadline exceeded" -> "profile-frame-deadline"
            "Capture unavailable" -> "capture-unavailable"
            "In-place profile resize unavailable" -> "in-place-resize-unavailable"
            "Frame encoding failed or exceeded limit" -> "encoding-failed"
            app.getString(R.string.hosting_failure_browser_lost) -> "browser-lost"
            app.getString(R.string.hosting_failure_service_lost) -> "service-lost"
            else -> "other-present"
        }
        fun decisionStack():String=Thread.currentThread().stackTrace
            .filter { it.className.startsWith("com.code2hack.eyebrowse.") }
            .take(14).joinToString(";") { "${it.className}.${it.methodName}:${it.lineNumber}" }
        val hostingObserver=HostingController.Listener {
            val status=host.status();val reason=reasonCode(status.failureReason)
            if((previousHosting!=status.state || previousReason!=reason) && hostingReceipts++<48) {
                previousHosting=status.state;previousReason=reason
                Log.i("EyeBrowseReviewRecovery","HOST_DECISION mission=$mission elapsedMs=${SystemClock.elapsedRealtime()} state=${status.state} reason=$reason generation=${status.generation} attachment=${status.attachment} browserLive=${status.browserLive} capture=${status.captureActive} wake=${status.wakeLockHeld} testCleanup=$cleanupRequested testNativeStop=$nativeStopRequested stack=${decisionStack()}")
            }
        }
        val helper=BrowserControlJourneyTest()
        fun js(script:String)=helper.js(browser,script)
        fun await(label:String,bound:Long=5_000,condition:()->Boolean)=StopRecoveryAssertions.await(label,bound,condition)
        fun step(name:String,verify:()->Unit) {
            var lastReceipt=-1L;var receipts=0
            await("RG phase $name",20_000) {
                val now=SystemClock.elapsedRealtime()
                if(name=="editor_recovered" && now-lastReceipt>=250 && receipts++<80) {
                    lastReceipt=now
                    val state=link.controlCoordinator.authority.snapshot()
                    Log.i("EyeBrowseReviewRecovery","RECOVERY_PHONE mission=$mission elapsedMs=$now linkUp=${link.isLinkUp()} authenticated=${state.linkAuthenticated} compatible=${state.sessionCompatible} owner=${state.owner} context=${state.context} profile=${state.profile} presentation=${state.presentationStatus} host=${host.status().state} editorPhase=${link.editorController?.authority?.phase} editorTarget=${link.editorController?.authority?.grant?.target}")
                }
                val value=phase.takeIf { it.exists() }?.readText()?.trim();check(value!="abort");value==name
            }
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
            fw4RunOnMainChecked { host.addListener(hostingObserver) }
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
            fw4RunOnMainChecked {
                // Observe the existing publisher; do not replace its decision or forwarding path.
                val getter=PhoneLinkServer::class.java.getDeclaredMethod("getPublisher").apply { isAccessible=true }
                val publisher=checkNotNull(getter.invoke(link))
                val callback=PhonePresentationPublisher::class.java.getDeclaredField("degraded").apply { isAccessible=true }
                @Suppress("UNCHECKED_CAST")
                val original=callback.get(publisher) as (com.code2hack.eyebrowse.core.link.control.ControlContext,String)->Unit
                var count=0
                val observed:(com.code2hack.eyebrowse.core.link.control.ControlContext,String)->Unit={ context,reason ->
                    if(count++<24) Log.i("EyeBrowseReviewRecovery","PUBLISHER_DEGRADED mission=$mission elapsedMs=${SystemClock.elapsedRealtime()} reason=${reasonCode(reason)} context=$context testCleanup=$cleanupRequested testNativeStop=$nativeStopRequested stack=${decisionStack()}")
                    original(context,reason)
                }
                callback.set(publisher,observed)
                restorePublisher={ callback.set(publisher,original) }
            }
            Log.i("EyeBrowseReviewRecovery","PHONE_READY mission=$mission stopReconnect=$stopReconnect")
            if(stopReconnect) {
                val engine=PhoneLinkServer::class.java.getDeclaredField("engine").let { it.isAccessible=true;it.get(link) as LinkServerEngine }
                step("effect_before_reconnect") { clickCount();continuity();assertTrue(link.isLinkUp()) }
                step("link_down") { await("old app link closed") { !link.isLinkUp() };clickCount();continuity() }
                step("reconnect_inflight") {
                    assertFalse(link.isLinkUp());assertEquals(HostingController.State.HOSTING,host.status().state)
                    Log.i("EyeBrowseReviewRecovery","STOP_BEFORE_RELEASE mission=$mission at=${SystemClock.elapsedRealtime()} rememberedReconnectBarrier=true")
                    scenario.onActivity { nativeStopRequested=true;assertTrue(it.findViewById<Button>(R.id.button_hosting_toggle).performClick()) }
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
            cleanupRequested=true
            val errors=listOf<()->Unit>(
                { fw4RunOnMainChecked { host.stop();link.stop() } },
                { await("resources retired") { StopRecoveryAssertions.resourcesGone(host) && !link.isLinkUp() } },
                { if(browser.isLive() && browser.lastCommittedUrl()==url) {
                    js("sessionStorage.removeItem('${if(stopReconnect) "t03-" else "i11-loads-"}$mission');true")
                    assertEquals("null",js("sessionStorage.getItem('${if(stopReconnect) "t03-" else "i11-loads-"}$mission')"))
                } },
                { fw4RunOnMainChecked { restorePublisher?.invoke();host.removeListener(hostingObserver) } },
                { scenario.close() },{ phase.delete();ack.delete();assertFalse(phase.exists());assertFalse(ack.exists()) },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(primary!=null)errors.forEach { primary.addSuppressed(it) }
            else if(errors.isNotEmpty()) { errors.drop(1).forEach { errors.first().addSuppressed(it) };throw errors.first() }
        }
    }
}
