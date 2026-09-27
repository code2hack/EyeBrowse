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

/** Observe native page effects and the real Phone lease, independently of RG's controller. */
@RunWith(AndroidJUnit4::class)
class ReadingJourneyTest {
    @Test fun phoneExpiresReadingWithoutFinalStop() {
        val inst=InstrumentationRegistry.getInstrumentation();val app=inst.targetContext
        val mission=UUID.fromString(InstrumentationRegistry.getArguments().getString("missionId")).toString()
        val phase=File(app.cacheDir,"kbd-$mission.phase");val ack=File(app.cacheDir,"kbd-$mission.ack")
        val browser=PhoneBrowserSession.get(app);val host=HostingController.get(app);val link=PhoneLinkServer.obtain(app)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val url=InstrumentationRegistry.getArguments().getString("fixtureBaseUrl","http://127.0.0.1:26341")+"/keyboard.html?case="+mission
        fun await(label:String,bound:Long=3_000,test:()->Boolean) {
            val end=SystemClock.elapsedRealtime()+bound
            while(SystemClock.elapsedRealtime()<end) { if(test())return;SystemClock.sleep(10) };fail(label)
        }
        fun y():Int { var value=0;inst.runOnMainSync { value=checkNotNull(browser.view()).scrollY };return value }
        fun phase(name:String,verify:()->Unit) {
            await("RG phase $name",60_000) { val observed=phase.takeIf { it.exists() }?.readText()?.trim();check(observed!="abort");observed==name }
            verify();ack.writeText(name);Log.i("EyeBrowseReadingTest","PHONE_VERIFIED phase=$name at=${SystemClock.elapsedRealtime()} y=${y()}")
        }
        fun stopped() {
            SystemClock.sleep(600) // Declared bound from loss with at most one already-valid in-flight update.
            val position=y();val steps=link.continuousScrollSteps
            SystemClock.sleep(200);assertEquals(position,y());assertEquals(steps,link.continuousScrollSteps)
        }
        var position=0
        var primary: Throwable? = null
        try {
            phase.writeText("");ack.writeText("")
            val injected = IllegalStateException("controlled test-cleanup exception")
            val delivered = runCatching { fw4RunOnMainChecked { throw injected } }.exceptionOrNull()
            assertSame("cleanup exception returns to the JUnit thread",injected,delivered)
            assertNotEquals(android.os.Looper.getMainLooper().thread,Thread.currentThread())
            Log.i("EyeBrowseReadingTest","TEARDOWN_DISPATCH_QUALIFIED sameThrowable=true testThread=true")
            scenario.onActivity { browser.openAddress(url);link.start() }
            await("fixture loaded",10_000) { browser.pageTitle()?.startsWith("KBD|")==true && !browser.isLoading() }
            val originalView=browser.view()
            val identity=BrowserControlJourneyTest().js(browser,"fixtureIdentity")
            scenario.onActivity { it.findViewById<Button>(R.id.button_hosting_toggle).performClick() }
            await("hosting") { host.status().state==HostingController.State.HOSTING }
            Log.i("EyeBrowseKeyboardTest","PHONE_KBD_READY mission=$mission")
            phase("reading_ready") { position=y();assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner) }
            phase("reading_down") {
                await("positive actual native scroll") { y()>position+30 };position=y()
                scenario.moveToState(Lifecycle.State.CREATED) // Supported Phone background hosting.
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
            }
            phase("reading_neutral") { stopped();position=y() }
            phase("reading_up") { await("negative actual native scroll") { y()<position-20 } }
            phase("reading_swipe") { stopped();position=y() }
            phase("reading_held") { assertEquals(position,y()) }
            phase("reading_renewed") { await("neutral renews") { y()>position+30 } }
            phase("reading_sensor") { stopped();position=y() }
            phase("reading_sensor_held") { assertEquals(position,y()) }
            phase("reading_before_modal") { await("fresh neutral after sensor") { y()>position+30 } }
            phase("reading_inactive") { stopped();position=y() }
            phase("reading_inactive_held") { assertEquals(position,y()) }
            phase("reading_before_loss") { await("fresh neutral renews after sensor loss") { y()>position+30 } }
            phase("reading_lost_stop") {
                var deadline:Long?=null
                inst.runOnMainSync { deadline=link.continuousScroll.deadlineMs }
                assertNotNull("lease exists when updates disappear",deadline)
                await("Phone monotonic expiry",1_000) {
                    var active=true;inst.runOnMainSync {
                        active=link.continuousScroll.active
                        link.continuousScroll.deadlineMs?.let { deadline=maxOf(checkNotNull(deadline),it) }
                    };!active
                }
                stopped()
                // An already-issued credit can be accepted once. It cannot extend effects by >300ms again.
                assertTrue("no effect at/after the last admitted deadline",link.lastContinuousScrollAt < checkNotNull(deadline))
                assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner)
                assertEquals(HostingController.State.HOSTING,host.status().state)
                Log.i("EyeBrowseReadingTest","LOST_STOP deadlineObserved=$deadline lastEffect=${link.lastContinuousScrollAt} observed=${SystemClock.elapsedRealtime()} steps=${link.continuousScrollSteps} captureLeaseNotExpired=true")
            }
            phase("reading_exit") {
                assertSame(originalView,browser.view());assertEquals(identity,BrowserControlJourneyTest().js(browser,"fixtureIdentity"))
                assertEquals("0",BrowserControlJourneyTest().js(browser,"fixtureSubmits"))
            }
            position=y()
            phase("reading_before_disconnect") { await("second Reading scroll") { y()>position+30 } }
            phase("reading_disconnected") { stopped();position=y() }
            phase("reading_reconnected_held") { assertEquals(position,y());assertEquals(ControlOwner.RG,link.controlCoordinator.authority.snapshot().owner) }
            phase("reading_reconnected_neutral") { await("fresh neutral after reconnect") { y()>position+30 } }
            phase("reading_phone_owner") {
                stopped();assertEquals(ControlOwner.PHONE,link.controlCoordinator.authority.snapshot().owner)
                scenario.moveToState(Lifecycle.State.RESUMED)
                assertSame(originalView,browser.view());assertEquals(identity,BrowserControlJourneyTest().js(browser,"fixtureIdentity"))
            }
            await("RG complete",5_000) { phase.readText().trim()=="complete" }
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
