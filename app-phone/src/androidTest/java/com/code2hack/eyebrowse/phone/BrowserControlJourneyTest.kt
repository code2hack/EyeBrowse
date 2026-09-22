package com.code2hack.eyebrowse.phone

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserControlJourneyTest {
    @Test fun phonePreservesPageAcrossHandoffActionsAndDisconnectedTakeover() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val scenario=ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java))
        val browser=PhoneBrowserSession.get(app);val host=HostingController.get(app);val link=PhoneLinkServer.obtain(app)
        val nonce=java.util.UUID.randomUUID().toString()
        val url=InstrumentationRegistry.getArguments().getString("fixtureBaseUrl","http://127.0.0.1:26341")+"/control.html?case="+nonce
        var baseline:android.webkit.WebView?=null
        var primaryFailure: Throwable?=null
        try {
            lateinit var barrier:FixtureNavigationBarrier
            scenario.onActivity { barrier=FixtureNavigationBarrier(browser.documentIdentity(),url,"T03 A");browser.openAddress(url);link.start() }
            await("fresh control fixture",10_000) { barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(),browser.displayUrl(),browser.lastCommittedUrl(),browser.pageTitle(),browser.isLoading(),browser.isLive(),browser.errorMessage())) }
            val originalDoc=browser.documentIdentity()
            js(browser,"document.getElementById('state').value='T03-preserved';true")
            val marker=js(browser,"window.fixtureMarker")
            scenario.onActivity { baseline=browser.view();assertNotNull(baseline);it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting",5_000) { host.status().state==HostingController.State.HOSTING }
            Log.i("EyeBrowseT03","PHONE_T03_READY")
            await("RG first takeover",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG && host.isRgPresentationOwned() }
            scenario.onActivity {
                assertFalse(it.findViewById<Button>(R.id.button_open).isEnabled)
                assertNotSame(it.findViewById<ViewGroup>(R.id.web_container),browser.view()!!.parent)
            }
            await("first return to Phone",15_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.PHONE }
            scenario.onActivity {
                assertSame(baseline,browser.view());assertEquals(originalDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),browser.view()!!.parent)
                assertTrue(it.findViewById<Button>(R.id.button_open).isEnabled)
                assertFalse(host.isRgPresentationOwned())
            }
            assertEquals(marker,js(browser,"window.fixtureMarker"))
            assertEquals("\"T03-preserved\"",js(browser,"document.getElementById('state').value"))
            Log.i("EyeBrowseT03","HANDOFF_ROUNDTRIP sameView=true sameDocument=true sameMarker=true sameField=true")
            js(browser,"document.title='Phone verified';true")
            await("RG second takeover",15_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG }
            await("all RG actions complete",40_000) { browser.pageTitle()=="T03 done" && !browser.isLoading() }
            assertSame(baseline,browser.view())
            val events=JSONArray(JSONArray("["+js(browser,"sessionStorage.getItem(window.fixtureKey)")+"]").getString(0))
            val values=(0 until events.length()).map { events.getString(it) }
            assertEquals(1,values.count { it=="click:A:1" })
            assertFalse(values.contains("click:A:2"))
            assertTrue(values.any { it.startsWith("scroll:A:") && it.substringAfterLast(':').toInt()>0 })
            assertTrue(values.contains("scroll:A:0"))
            assertTrue("Back revisits A",values.count { it=="show:A" }>=2)
            assertTrue("Forward and Reload revisit B",values.count { it=="show:B" }>=3)
            assertTrue(values.contains("click:B"))
            Log.i("EyeBrowseT03","PAGE_EFFECTS ${values.joinToString(",")}")
            val finalDoc=browser.documentIdentity()
            await("RG disconnected",10_000) { !link.isLinkUp() }
            scenario.onActivity {
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                it.findViewById<Button>(R.id.button_use_phone).performClick()
                assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                assertSame(baseline,browser.view());assertEquals(finalDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),browser.view()!!.parent)
                assertTrue(it.findViewById<Button>(R.id.button_open).isEnabled)
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("Stop cleanup",5_000) { !host.hasDisplayResources() && !host.captureResourcesPresent() && !host.isWakeLockHeld() }
            js(browser,"sessionStorage.removeItem(window.fixtureKey);true")
            Log.i("EyeBrowseT03","PHONE_T03_PASS disconnectedTakeover=true stopClean=true")
        } catch (failure:Throwable) {
            primaryFailure=failure
            Log.e("EyeBrowseT03","PRIMARY_FAILURE",failure)
            throw failure
        } finally {
            val cleanup=listOf<()->Unit>(
                { InstrumentationRegistry.getInstrumentation().runOnMainSync { host.stop() } },
                { InstrumentationRegistry.getInstrumentation().runOnMainSync { link.stop() } },
                { scenario.close() },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(primaryFailure!=null) cleanup.forEach { primaryFailure.addSuppressed(it) }
            else if(cleanup.isNotEmpty()) throw cleanup.first()
        }
    }
    private fun await(label:String,bound:Long,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) {
            var ok=false;InstrumentationRegistry.getInstrumentation().runOnMainSync { ok=condition() }
            if(ok)return;SystemClock.sleep(25)
        };fail(label)
    }
    private fun js(browser:PhoneBrowserSession,script:String):String {
        val done=CountDownLatch(1);var value=""
        InstrumentationRegistry.getInstrumentation().runOnMainSync { browser.view()!!.evaluateJavascript(script) { value=it;done.countDown() } }
        assertTrue(done.await(3,TimeUnit.SECONDS));return value
    }
}
