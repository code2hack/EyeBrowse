package com.code2hack.eyebrowse.phone

import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.lifecycle.Lifecycle
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

/** Read-only page assertions for actual RG keyboard effects; no positive DOM-value assignments. */
@RunWith(AndroidJUnit4::class)
class KeyboardJourneyTest {
    @Test fun phoneObservesActualKeyboardEffectsAndPageContinuity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val mission = UUID.fromString(InstrumentationRegistry.getArguments().getString("missionId")).toString()
        val phase = File(app.cacheDir,"kbd-$mission.phase")
        val ack = File(app.cacheDir,"kbd-$mission.ack")
        val browser = PhoneBrowserSession.get(app)
        val host = HostingController.get(app)
        val link = PhoneLinkServer.obtain(app)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val url = InstrumentationRegistry.getArguments().getString("fixtureBaseUrl","http://127.0.0.1:26341") + "/keyboard.html?case=" + mission
        fun js(script: String) = BrowserControlJourneyTest().js(browser,script)
        fun await(label: String, ms: Long = 10_000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime()+ms
            while(SystemClock.elapsedRealtime()<end) { if(condition())return;SystemClock.sleep(20) }
            fail(label)
        }
        fun phase(name: String, assertion: () -> Unit) {
            await("RG phase $name",180_000) {
                val observed = if (phase.exists()) phase.readText().trim() else ""
                check(observed != "abort") { "RG keyboard journey aborted" }
                observed == name
            }
            assertion();ack.writeText(name)
            Log.i("EyeBrowseKeyboardTest","PHONE_VERIFIED phase=$name mission=$mission")
        }
        fun equalsValue(id: String, value: String) {
            assertEquals("true",js("(()=>{const e=document.getElementById('$id');return (e.isContentEditable?e.textContent:e.value)===${org.json.JSONObject.quote(value)}})()"))
        }
        var primary: Throwable? = null
        try {
            phase.writeText("");ack.writeText("")
            scenario.onActivity { browser.openAddress(url);link.start() }
            await("fixture loaded") { browser.pageTitle()?.startsWith("KBD|")==true && !browser.isLoading() }
            val originalView = browser.view()
            val originalIdentity = js("fixtureIdentity")
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting") { host.status().state==HostingController.State.HOSTING }
            Log.i("EyeBrowseKeyboardTest","PHONE_KBD_READY mission=$mission")
            phase("invalid_address") { assertEquals(originalIdentity,js("fixtureIdentity"));equalsValue("text","") }
            phase("coalesced_layout") { assertEquals(originalIdentity,js("fixtureIdentity"));equalsValue("text","") }
            var identity = ""
            phase("address_opened") { identity=js("fixtureIdentity");assertNotEquals(originalIdentity,identity);assertSame(originalView,browser.view()) }
            phase("double_tap") { equalsValue("text","");assertEquals("0",js("fixtureInputs"));assertEquals("0",js("fixtureSubmits")) }
            phase("stale_case") { equalsValue("text","");assertEquals("0",js("fixtureInputs")) }
            phase("text_submit") { equalsValue("text","aB1");assertEquals("1",js("fixtureSubmits")) }
            phase("dismiss_pending") {
                equalsValue("text","aB1");assertEquals("1",js("fixtureSubmits"))
                assertTrue(link.editorController!!.isQuiescent())
            }
            phase("password") {
                equalsValue("password","p7");assertEquals("true",js("document.getElementById('password').type==='password'"))
                assertEquals(identity,js("fixtureIdentity"));assertTrue(link.editorController!!.isQuiescent())
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
            }
            phase("multiline") {
                equalsValue("multiline","m\nn");assertEquals("1",js("fixtureSubmits"))
                assertTrue(link.editorController!!.isQuiescent())
            }
            phase("plain") {
                equalsValue("plain","e");assertEquals(identity,js("fixtureIdentity"));assertSame(originalView,browser.view())
                assertTrue(link.editorController!!.isQuiescent())
                scenario.moveToState(Lifecycle.State.RESUMED)
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
            }
            phase("invalidate") { assertEquals("true",js("document.getElementById('text').readOnly=true;true")) }
            phase("invalidated") { equalsValue("text","aB1");assertEquals("1",js("fixtureSubmits")) }
            phase("return") {
                assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                equalsValue("text","aB1");equalsValue("password","p7");equalsValue("multiline","m\nn");equalsValue("plain","e")
                assertEquals(identity,js("fixtureIdentity"));assertSame(originalView,browser.view())
            }
            await("RG received final assertion",15_000) { phase.exists() && phase.readText().trim()=="complete" }
            Log.i("EyeBrowseKeyboardTest","PHONE_KBD_PASS mission=$mission samePage=true allFieldTypes=true")
        } catch (failure: Throwable) {
            primary=failure
            Log.e("EyeBrowseReadingTest","JOURNEY_PRIMARY_FAILURE",failure)
            throw failure
        } finally {
            val failures = listOf<Pair<String,() -> Unit>>(
                "stop" to { fw4RunOnMainChecked { host.stop();link.stop() } },
                "resources" to { await("teardown resources retired",5_000) {
                    fw4RunOnMainChecked { StopRecoveryAssertions.resourcesGone(host) && !link.isLinkUp() }
                } },
                "scenario" to { scenario.close() },
                "signals" to { phase.delete();ack.delete();assertFalse(phase.exists());assertFalse(ack.exists()) },
            ).mapNotNull { (name, action) ->
                Log.i("EyeBrowseReadingTest","TEARDOWN_BEGIN step=$name at=${SystemClock.elapsedRealtime()}")
                runCatching(action).exceptionOrNull().also { failure ->
                    Log.i("EyeBrowseReadingTest","TEARDOWN_END step=$name at=${SystemClock.elapsedRealtime()} failure=${failure?.javaClass?.name}")
                    if(failure!=null) Log.e("EyeBrowseReadingTest","TEARDOWN_FAILURE step=$name",failure)
                }
            }
            if(primary!=null) failures.forEach { primary.addSuppressed(it) }
            else if(failures.isNotEmpty()) {
                failures.drop(1).forEach { failures.first().addSuppressed(it) };throw failures.first()
            }
        }
    }
}
