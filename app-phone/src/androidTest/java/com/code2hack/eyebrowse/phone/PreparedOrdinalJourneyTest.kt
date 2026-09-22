package com.code2hack.eyebrowse.phone

import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.phone.link.PhoneLinkServer
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Independent real-page oracle for the predeclared prepared-ordinal matrix. */
@RunWith(AndroidJUnit4::class)
class PreparedOrdinalJourneyTest {
    @Test fun phoneVerifiesEveryPreparedOrdinalMatrixEffect() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val args=InstrumentationRegistry.getArguments()
        val mode=args.getString("ordinalCase","cold")
        require(mode in listOf("cold","rapid","refill","recreate","stall","uncertain"))
        val count=when(mode) { "rapid" -> 5;"refill","recreate" -> 2;else -> 1 }
        val mission=UUID.fromString(args.getString("missionId")).toString()
        val signal=File(instrumentation.targetContext.cacheDir,"i8-"+mission+".control")
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val browser=PhoneBrowserSession.get(instrumentation.targetContext)
        val host=HostingController.get(instrumentation.targetContext)
        val link=PhoneLinkServer.obtain(instrumentation.targetContext)
        val helper=BrowserControlJourneyTest()
        val url=args.getString("fixtureBaseUrl","http://127.0.0.1:26341")+"/control.html?case="+mission
        var failure:Throwable?=null
        fun js(script:String)=helper.js(browser,script)
        fun title(value:String) { js("window.__t03Title("+org.json.JSONObject.quote(value)+");true") }
        fun await(label:String,bound:Long=10_000,condition:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+bound
            while(SystemClock.elapsedRealtime()<end) {
                var ok=false;instrumentation.runOnMainSync { ok=condition() }
                if(ok)return;SystemClock.sleep(10)
            };fail(label)
        }
        fun waitSignal(name:String) {
            val end=SystemClock.elapsedRealtime()+45_000
            while(SystemClock.elapsedRealtime()<end) {
                val value=signal.takeIf { it.exists() }?.readText()?.trim()
                if(value=="abort") fail("matrix peer aborted")
                if(value==name) return
                SystemClock.sleep(10)
            };fail("matrix signal "+name)
        }
        signal.writeText("")
        try {
            lateinit var barrier:FixtureNavigationBarrier
            scenario.onActivity { barrier=FixtureNavigationBarrier(browser.documentIdentity(),url,"T03 A");browser.openAddress(url);link.start() }
            await("fresh matrix fixture") { barrier.isReady(FixtureNavigationBarrier.Observation(browser.documentIdentity(),
                browser.displayUrl(),browser.lastCommittedUrl(),browser.pageTitle()?.substringBefore("|G="),
                browser.isLoading(),browser.isLive(),browser.errorMessage())) }
            val document=browser.documentIdentity()
            lateinit var original:android.webkit.WebView
            scenario.onActivity { original=checkNotNull(browser.view());it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting") { host.status().state==HostingController.State.HOSTING }
            fun effects(expected:Int) {
                scenario.onActivity {
                    assertSame(original,browser.view());assertEquals(document,browser.documentIdentity());assertEquals(0,original.scrollY)
                    assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                }
                val a=JSONArray(js("JSON.parse(sessionStorage.getItem(window.fixtureKey)||'[]')"))
                val actual=(0 until a.length()).map(a::getString)
                assertEquals(listOf("load:A","show:A")+(1..expected).map { "click:A:"+it },actual)
                if(expected>0) assertEquals("\"Activate A "+expected+"\"",js("document.getElementById('action').textContent"))
                Log.i(TAG,"MATRIX_EFFECT mode="+mode+" count="+expected+" document="+document+" events="+actual)
            }
            Log.i(TAG,"PHONE_I8_READY mission="+mission)
            if(mode=="stall" || mode=="uncertain") {
                waitSignal("unavailable_verified");effects(0);title("I8 ACK unavailable_verified")
            }
            for(i in if(mode=="rapid") listOf(count) else (1..count).toList()) {
                waitSignal("click_"+i);effects(i);title("I8 ACK click_"+i)
                if(mode=="refill" && i==1) {
                    waitSignal("unavailable_verified");effects(1);title("I8 ACK unavailable_verified")
                }
            }
            waitSignal("matrix_done");effects(count)
            scenario.onActivity {
                it.findViewById<Button>(R.id.button_use_phone).performClick()
                assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                assertSame(original,browser.view());assertEquals(document,browser.documentIdentity())
                it.findViewById<Button>(R.id.button_hosting_toggle).performClick()
            }
            await("matrix Stop resources",5_000) { !host.hasDisplayResources() && !host.captureResourcesPresent() && !host.isWakeLockHeld() }
            Log.i(TAG,"PREPARED_PHONE_PASS mode="+mode+" intendedEffects="+count)
            Log.i(TAG,"I8_HOST_ACK done mission="+mission)
        } catch(t:Throwable) { failure=t;Log.e(TAG,"PRIMARY_FAILURE",t);throw t }
        finally {
            val errors=listOf<()->Unit>(
                { if(browser.isLive() && browser.displayUrl()?.contains("case="+mission)==true) {
                    js("sessionStorage.removeItem('t03-"+mission+"');true")
                    assertEquals("null",js("sessionStorage.getItem('t03-"+mission+"')"))
                    Log.i(TAG,"FIXTURE_STORAGE_CLEANUP mission="+mission+" absent=true")
                } },
                { instrumentation.runOnMainSync { host.stop();link.stop() } },
                { scenario.close() },{ signal.delete();assertFalse(signal.exists()) },
            ).mapNotNull { runCatching(it).exceptionOrNull() }
            if(failure!=null) errors.forEach { failure!!.addSuppressed(it) } else if(errors.isNotEmpty()) throw errors.first()
        }
    }
    companion object { private const val TAG="EyeBrowseI8" }
}
