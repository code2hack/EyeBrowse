package com.code2hack.eyebrowse.phone

import android.content.Intent
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
        var historySize=0
        var historyIndex=0
        var primaryFailure: Throwable?=null
        try {
            lateinit var barrier:FixtureNavigationBarrier
            scenario.onActivity { barrier=FixtureNavigationBarrier(browser.documentIdentity(),url,"T03 A");browser.openAddress(url);link.start() }
            await("fresh control fixture",10_000) { barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(),browser.displayUrl(),browser.lastCommittedUrl(),browser.pageTitle()?.substringBefore("|G="),browser.isLoading(),browser.isLive(),browser.errorMessage())) }
            val originalDoc=browser.documentIdentity()
            js(browser,"document.getElementById('state').value='T03-preserved';true")
            val marker=js(browser,"window.fixtureMarker")
            scenario.onActivity {
                baseline=browser.view();assertNotNull(baseline)
                val history=baseline!!.copyBackForwardList();historySize=history.size;historyIndex=history.currentIndex
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("hosting",5_000) { host.status().state==HostingController.State.HOSTING }
            val oldPhoneContext=link.controlCoordinator.authority.snapshot().context
            Log.i("EyeBrowseT03","PHONE_T03_READY")
            await("RG first takeover",20_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG && host.isRgPresentationOwned() }
            scenario.onActivity {
                assertFalse(it.findViewById<Button>(R.id.button_open).isEnabled)
                assertNotSame(it.findViewById<ViewGroup>(R.id.web_container),browser.view()!!.parent)
                val stale=BrowserActionRequest(BrowserCommandId.create(oldPhoneContext,1),oldPhoneContext,BrowserAction.Reload,1)
                assertEquals(ActionRejection.STALE_CONTEXT,(link.controlCoordinator.authority.admitAction(ControlOwner.PHONE,stale) as ActionDecision.Rejected).reason)
                it.findViewById<android.widget.ImageButton>(R.id.button_reload).performClick()
                assertEquals(originalDoc,browser.documentIdentity())
            }
            val rgEpoch=link.controlCoordinator.authority.snapshot().controlEpoch
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                assertEquals(rgEpoch,link.controlCoordinator.authority.snapshot().controlEpoch)
                assertEquals(originalDoc,browser.documentIdentity())
                assertTrue(host.isRgPresentationOwned())
            }
            Log.i("EyeBrowseT03","OLD_PHONE_REJECTED foregroundDoesNotSteal=true")
            js(browser,"window.__t03Title('RG ownership verified');true")
            await("first return to Phone",15_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.PHONE }
            scenario.onActivity {
                assertSame(baseline,browser.view());assertEquals(originalDoc,browser.documentIdentity())
                assertSame(it.findViewById<ViewGroup>(R.id.web_container),browser.view()!!.parent)
                assertTrue(it.findViewById<Button>(R.id.button_open).isEnabled)
                assertEquals(historySize,baseline!!.copyBackForwardList().size)
                assertEquals(historyIndex,baseline!!.copyBackForwardList().currentIndex)
                assertFalse(host.isRgPresentationOwned())
            }
            assertEquals(marker,js(browser,"window.fixtureMarker"))
            assertEquals("\"T03-preserved\"",js(browser,"document.getElementById('state').value"))
            Log.i("EyeBrowseT03","HANDOFF_ROUNDTRIP sameView=true sameDocument=true sameMarker=true sameField=true")
            js(browser,"window.__t03Title('Phone verified');true")
            await("RG second takeover",15_000) { link.controlCoordinator.authority.snapshot().owner==ControlOwner.RG }
            await("second presentation ready",2_000) { link.controlCoordinator.authority.snapshot().presentationStatus==PresentationStatus.READY }
            val beforeScroll=geometry(browser,"A-before",160,"action","next")!!
            assertEquals("fixture starts at native top",0,beforeScroll.getInt("nativeScrollY"))
            js(browser,"window.__t03Title('T03 A');true")
            var positiveRecorded=false
            var negativeRecorded=false
            val actionDeadline=SystemClock.elapsedRealtime()+40_000
            while(SystemClock.elapsedRealtime()<actionDeadline) {
                var title="";var loading=true
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    title=browser.pageTitle()?.substringBefore("|G=") ?: "";loading=browser.isLoading()
                }
                if(title=="T03 done" && !loading) break
                if(title.startsWith("T03 A scroll ") && !loading) {
                    val css=title.substringBefore('|').substringAfterLast(' ').toInt()
                    if(css>0 && !positiveRecorded) {
                        val after=geometry(browser,"positive-after",160,"action","next")!!
                        assertEquals("commanded +160 viewport px",beforeScroll.getInt("nativeScrollY")+160,after.getInt("nativeScrollY"))
                        positiveRecorded=true
                        js(browser,"window.__t03Title('T03 A positive verified');true")
                    } else if(css==0 && positiveRecorded && !negativeRecorded) {
                        val after=geometry(browser,"negative-after",-160,"action","next")!!
                        assertEquals("commanded -160 viewport px",beforeScroll.getInt("nativeScrollY"),after.getInt("nativeScrollY"))
                        negativeRecorded=true
                        js(browser,"window.__t03Title('T03 A negative verified');true")
                    }
                }
                SystemClock.sleep(20)
            }
            await("all RG actions complete",1_000) { browser.pageTitle()?.substringBefore("|G=")=="T03 done" && !browser.isLoading() }
            assertTrue("positive native delta observed",positiveRecorded)
            assertTrue("negative native delta observed",negativeRecorded)
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
            Log.i("EyeBrowseT03","PHONE_T03_PASS disconnectedTakeover=true stopClean=true")
        } catch (failure:Throwable) {
            primaryFailure=failure
            Log.e("EyeBrowseT03","PRIMARY_FAILURE",failure)
            throw failure
        } finally {
            val cleanup=listOf<()->Unit>(
                { if(browser.isLive() && browser.displayUrl()?.startsWith(url.substringBefore("/control.html")+"/control")==true) clearFixtureStorage(browser) },
                { InstrumentationRegistry.getInstrumentation().runOnMainSync { host.stop() } },
                { InstrumentationRegistry.getInstrumentation().runOnMainSync { link.stop() } },
                { scenario.close() },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(primaryFailure!=null) cleanup.forEach { primaryFailure.addSuppressed(it) }
            else if(cleanup.isNotEmpty()) throw cleanup.first()
        }
    }
    private fun geometry(browser:PhoneBrowserSession,stage:String,command:Int,target:String,next:String?):JSONObject? {
        val visual=CountDownLatch(1)
        var document=""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            document=browser.documentIdentity()
            browser.view()!!.postVisualStateCallback(0,object:android.webkit.WebView.VisualStateCallback() {
                override fun onComplete(requestId:Long) { visual.countDown() }
            })
        }
        assertTrue("fixture visual state",visual.await(3,TimeUnit.SECONDS))
        val native=JSONObject()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val v=browser.view()!!
            native.put("stage",stage).put("commandViewportPx",command).put("document",document)
                .put("nativeScrollY",v.scrollY).put("webViewScale",v.scale.toDouble())
                .put("width",v.width).put("height",v.height).put("densityDpi",v.resources.displayMetrics.densityDpi)
                .put("nativeObservedMs",SystemClock.elapsedRealtime())
        }
        val script="""(function(){function rect(id){const e=document.getElementById(id);if(!e)return null;const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};}return {scrollY:scrollY,dpr:devicePixelRatio,innerWidth:innerWidth,visualScale:visualViewport.scale,metadata:window.__t03Geometry(),action:rect(${JSONObject.quote(target)}),next:rect(${JSONObject.quote(next ?: "")})};})()"""
        val css=JSONObject(js(browser,script))
        var stillCurrent=false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { stillCurrent=browser.documentIdentity()==document && !browser.isLoading() }
        if(!stillCurrent || css.isNull("action")) return null // An intended navigation retired this observation.
        native.put("css",css)
        val measured=FixtureViewportGeometry(native.getDouble("webViewScale"),native.getInt("width"),native.getInt("height"))
        native.put("expectedCssDelta",measured.cssDelta(command))
        assertEquals("native and visual-viewport scale agree",measured.scale,css.getDouble("dpr")*css.getDouble("visualScale"),0.01)
        if(stage=="A-before") {
            val rect=css.getJSONObject("action")
            val center=measured.center(rect.getDouble("x"),rect.getDouble("y"),rect.getDouble("w"),rect.getDouble("h"))
            assertEquals("measured action x",center.first,css.getJSONObject("metadata").getDouble("x"),1.0)
            assertEquals("measured action y",center.second,css.getJSONObject("metadata").getDouble("y"),1.0)
        }
        Log.i("EyeBrowseT03","SCROLL_GEOMETRY $native")
        return native
    }
    private fun clearFixtureStorage(browser:PhoneBrowserSession) {
        val removed=js(browser,"(function(){const keys=Object.keys(sessionStorage).filter(k=>/^t03-[0-9a-f-]{36}$/.test(k));keys.forEach(k=>sessionStorage.removeItem(k));return keys;})()")
        assertEquals("[]",js(browser,"Object.keys(sessionStorage).filter(k=>/^t03-[0-9a-f-]{36}$/.test(k))"))
        Log.i("EyeBrowseT03","FIXTURE_STORAGE_CLEANUP removed=$removed remaining=0")
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
