package com.code2hack.eyebrowse.phone

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Independent Phone oracle. The private signal file coordinates test-only interruptions, not product input. */
@RunWith(AndroidJUnit4::class)
class PointerBrowserJourneyTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val helpers=BrowserControlJourneyTest()
    @Test fun phoneVerifiesPointerEffectsAndEveryInterruptedIntent() {
        val app=instrumentation.targetContext
        val mission=UUID.fromString(InstrumentationRegistry.getArguments().getString("missionId")).toString()
        val signal=File(app.cacheDir,"i8-$mission.control")
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val browser=PhoneBrowserSession.get(app);val host=HostingController.get(app);val link=PhoneLinkServer.obtain(app)
        val url=InstrumentationRegistry.getArguments().getString("fixtureBaseUrl","http://127.0.0.1:26341")+"/control.html?case="+mission
        var primary:Throwable?=null
        signal.writeText("")
        fun js(script:String)=helpers.js(browser,script)
        fun title(value:String) { js("window.__t03Title(${org.json.JSONObject.quote(value)});true") }
        fun events():List<String> { val a=JSONArray(js("JSON.parse(sessionStorage.getItem(window.fixtureKey)||'[]')"));return (0 until a.length()).map(a::getString) }
        fun waitSignal(name:String,bound:Long=40_000) {
            val end=SystemClock.elapsedRealtime()+bound
            while(SystemClock.elapsedRealtime()<end) {
                if(signal.exists() && signal.readText().trim()=="abort") fail("paired driver aborted; run fixture cleanup")
                if(signal.exists() && signal.readText().trim()==name) { Log.i(TAG,"PHONE_SIGNAL $name at=${SystemClock.elapsedRealtime()}");return }
                SystemClock.sleep(10)
            }
            fail("Phone signal $name")
        }
        try {
            lateinit var barrier:FixtureNavigationBarrier
            scenario.onActivity { barrier=FixtureNavigationBarrier(browser.documentIdentity(),url,"T03 A");browser.openAddress(url);link.start() }
            await("fresh fixture",10_000) { barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(),browser.displayUrl(),browser.lastCommittedUrl(),browser.pageTitle()?.substringBefore("|G="),browser.isLoading(),browser.isLive(),browser.errorMessage())) }
            val originalDoc=browser.documentIdentity()
            js("document.getElementById('state').value='I8-preserved';window.i8Counter=17;true")
            val marker=js("window.fixtureMarker")
            lateinit var original:android.webkit.WebView
            var historySize=0;var historyIndex=0
            scenario.onActivity {
                original=checkNotNull(browser.view());val h=original.copyBackForwardList();historySize=h.size;historyIndex=h.currentIndex
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("hosting",5_000) { host.status().state==HostingController.State.HOSTING }
            val oldPhone=link.controlCoordinator.authority.snapshot().context
            fun assertBaseline(owner:ControlOwner,name:String) {
                scenario.onActivity {
                    assertSame(original,browser.view());assertEquals(originalDoc,browser.documentIdentity())
                    assertEquals(owner,link.controlCoordinator.authority.snapshot().owner)
                    assertEquals(0,original.scrollY)
                }
                assertEquals(listOf("load:A","show:A"),events())
                assertEquals(marker,js("window.fixtureMarker"));assertEquals("17",js("window.i8Counter"))
                assertEquals("\"I8-preserved\"",js("document.getElementById('state').value"))
                Log.i(TAG,"ZERO_EFFECT name=$name document=$originalDoc loads=1 clicks=0 scroll=0")
            }
            Log.i(TAG,"PHONE_I8_READY mission=$mission")
            waitSignal("phone_owned");assertBaseline(ControlOwner.PHONE,"phone_owned");title("I8 ACK phone_owned")
            await("real RG takeover",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG && host.isRgPresentationOwned() }
            val firstEpoch=link.controlCoordinator.authority.snapshot().controlEpoch
            scenario.onActivity {
                val stale=BrowserActionRequest(BrowserCommandId.create(oldPhone,1),oldPhone,BrowserAction.Reload,1)
                assertEquals(ActionRejection.STALE_CONTEXT,(link.controlCoordinator.authority.admitAction(ControlOwner.PHONE,stale) as ActionDecision.Rejected).reason)
                it.findViewById<android.widget.ImageButton>(R.id.button_reload).performClick()
                assertEquals(originalDoc,browser.documentIdentity())
            }
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                assertEquals(firstEpoch,link.controlCoordinator.authority.snapshot().controlEpoch)
                assertSame(original,browser.view());assertEquals(originalDoc,browser.documentIdentity())
            }
            title("I8 RG VERIFIED")
            await("first pointer return",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.PHONE }
            scenario.onActivity {
                assertSame(original,browser.view());assertEquals(originalDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),original.parent)
                assertTrue(it.findViewById<Button>(R.id.button_open).isEnabled)
                assertEquals(historySize,original.copyBackForwardList().size);assertEquals(historyIndex,original.copyBackForwardList().currentIndex)
            }
            assertBaseline(ControlOwner.PHONE,"roundtrip")
            Log.i(TAG,"HANDOFF_ROUNDTRIP sameView=true sameDocument=true sameHistory=true sameField=true sameCounter=true")
            title("I8 ROUNDTRIP VERIFIED")
            await("second RG takeover",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG }
            for(name in listOf("disabled_double","sensor","pause","viewport","modal")) {
                waitSignal(name);assertBaseline(ControlOwner.RG,name);title("I8 ACK $name")
            }

            waitSignal("busy")
            val entered=CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                entered.countDown();SystemClock.sleep(900) // Bounded test-only action/paint delay; no production hook.
                Log.i(TAG,"BUSY_MAIN_RELEASED at=${SystemClock.elapsedRealtime()}")
            }
            assertTrue(entered.await(2,TimeUnit.SECONDS))
            Log.i(TAG,"I8_HOST_ACK busy mission=$mission")
            waitSignal("busy_verified")
            assertEquals(1,events().count { it=="click:A:1" });assertFalse(events().contains("click:A:2"))
            assertEquals("\"Activate A 1\"",js("document.getElementById('action').textContent"))
            assertFalse(events().any { it.startsWith("scroll:") })
            scenario.onActivity { assertSame(original,browser.view());assertEquals(originalDoc,browser.documentIdentity());assertEquals(0,original.scrollY) }
            Log.i(TAG,"BUSY_ZERO_EXTRA clicks=1 scroll=0")
            title("I8 ACK busy_verified")

            waitSignal("scroll_start")
            assertEquals(listOf("load:A","show:A","click:A:1"),events())
            Log.i(TAG,"ZERO_EFFECT name=protocol_negatives clicks=1 loads=1")
            val before=checkNotNull(helpers.geometry(browser,"A-before",160,"action","next"))
            assertEquals(0,before.getInt("nativeScrollY"));title("I8 ACK scroll_start")
            waitSignal("positive_scroll")
            val positive=checkNotNull(helpers.geometry(browser,"I8-positive",160,"action","next"))
            assertEquals(originalDoc,positive.getString("document"));assertEquals(160,positive.getInt("nativeScrollY"))
            val cssDelta=positive.getJSONObject("css").getDouble("scrollY")-before.getJSONObject("css").getDouble("scrollY")
            assertEquals(160.0/before.getDouble("webViewScale"),cssDelta,1.0)
            val positiveEvent="scroll:A:"+kotlin.math.round(positive.getJSONObject("css").getDouble("scrollY")).toInt()
            title("I8 ACK positive_scroll")
            waitSignal("negative_scroll")
            val negative=checkNotNull(helpers.geometry(browser,"I8-negative",-160,"action","next"))
            assertEquals(originalDoc,negative.getString("document"));assertEquals(0,negative.getInt("nativeScrollY"))
            title("I8 ACK negative_scroll")

            waitSignal("navigation",50_000)
            val oldDoc=browser.documentIdentity();assertEquals(0,events().count { it=="click:B" })
            scenario.onActivity { assertSame(original,browser.view());browser.reload() }
            await("Phone-injected navigation completes",8_000) { browser.documentIdentity()!=oldDoc && !browser.isLoading() && browser.pageTitle()?.substringBefore("|G=")=="T03 B" }
            val bDoc=browser.documentIdentity();assertEquals(0,events().count { it=="click:B" })
            js("window.i8Heap='B-live';true")
            Log.i(TAG,"NAVIGATION_FENCE before=$oldDoc after=$bDoc clickB=0")
            title("I8 ACK navigation")
            waitSignal("takeover")
            scenario.onActivity {
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                val h=original.copyBackForwardList();historySize=h.size;historyIndex=h.currentIndex
                it.findViewById<Button>(R.id.button_use_phone).performClick()
                assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                assertSame(original,browser.view());assertEquals(bDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),original.parent)
            }
            assertEquals(0,events().count { it=="click:B" });assertEquals("\"B-live\"",js("window.i8Heap"))
            Log.i(TAG,"PHONE_TAKEOVER_FENCE document=$bDoc sameView=true clickB=0")
            title("I8 ACK takeover")
            waitSignal("phone_owned_again")
            scenario.onActivity { assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner);assertEquals(bDoc,browser.documentIdentity()) }
            assertEquals(0,events().count { it=="click:B" });title("I8 ACK phone_owned_again")
            await("third RG takeover",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG }
            waitSignal("actions_done")
            val finalEvents=events()
            val expected=listOf("load:A","show:A","click:A:1",positiveEvent,"scroll:A:0","load:B","show:B",
                "load:A","show:A","load:B","show:B","load:B","show:B","load:B","show:B","click:B")
            assertEquals("exact independent page-effect ledger",expected,finalEvents)
            assertEquals("\"Finished\"",js("document.getElementById('done').textContent"))
            assertEquals(bDoc,browser.documentIdentity());assertSame(original,browser.view())
            Log.i(TAG,"PAGE_EFFECTS ${finalEvents.joinToString(",")}")
            title("I8 EFFECTS VERIFIED")
            await("final pointer return",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.PHONE }
            scenario.onActivity {
                assertSame(original,browser.view());assertEquals(bDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),original.parent)
                assertEquals(historySize,original.copyBackForwardList().size);assertEquals(historyIndex,original.copyBackForwardList().currentIndex)
                assertTrue(it.findViewById<Button>(R.id.button_open).isEnabled)
            }
            assertEquals("\"B-live\"",js("window.i8Heap"));title("I8 FINAL RETURN VERIFIED")
            await("final RG ownership",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG }
            waitSignal("final_disconnected")
            await("actual link closed",3_000) { !link.isLinkUp() }
            assertEquals(finalEvents,events())
            scenario.onActivity {
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                it.findViewById<Button>(R.id.button_use_phone).performClick()
                assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                assertSame(original,browser.view());assertEquals(bDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),original.parent)
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("Stop resources",5_000) { !host.hasDisplayResources() && !host.captureResourcesPresent() && !host.isWakeLockHeld() }
            Log.i(TAG,"PHONE_I8_PASS sameView=true sameDocument=true disconnectedTakeover=true stopClean=true")
            Log.i(TAG,"I8_HOST_ACK done mission=$mission")
        } catch(failure:Throwable) { primary=failure;Log.e(TAG,"PRIMARY_FAILURE",failure);throw failure }
        finally {
            val cleanup=listOf<()->Unit>(
                { if(browser.isLive() && browser.displayUrl()?.contains("case=$mission")==true) {
                    js("sessionStorage.removeItem('t03-$mission');true")
                    assertEquals("null",js("sessionStorage.getItem('t03-$mission')"))
                    Log.i(TAG,"FIXTURE_STORAGE_CLEANUP key=t03-$mission absent=true")
                } },
                { instrumentation.runOnMainSync { host.stop();link.stop() } },
                { scenario.close() },{ signal.delete();assertFalse(signal.exists()) },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(primary!=null) cleanup.forEach { primary!!.addSuppressed(it) } else if(cleanup.isNotEmpty()) throw cleanup.first()
        }
    }
    private fun await(label:String,bound:Long,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) {
            var ok=false;instrumentation.runOnMainSync { ok=condition() };if(ok)return;SystemClock.sleep(15)
        };fail(label)
    }
    companion object { private const val TAG="EyeBrowseI8" }
}
