package com.code2hack.eyebrowse.rg

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Owned fixed-root page and the production SensorHeadPoseSource; never installs a replay source. */
@RunWith(AndroidJUnit4::class)
class SensorNestedScrollInstrumentedTest {
    @get:Rule val testName=TestName()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val arguments=InstrumentationRegistry.getArguments()
    private val observations=JSONArray()
    private val cleanup=JSONObject()
    private val invocation=arguments.getString("evidenceRunId")
    private val candidate=arguments.getString("candidateHead")
    private val url=checkNotNull(arguments.getString("fixtureBaseUrl")).trimEnd('/')+"/keyboard.html"
    private var sensorVerified=false
    private var headMovement=false
    private var ownerAction: String?=null

    private fun await(label: String, milliseconds: Long=10_000, condition: () -> Boolean) {
        val end=SystemClock.elapsedRealtime()+milliseconds
        do { if(condition()) return;SystemClock.sleep(20) } while(SystemClock.elapsedRealtime()<end)
        fail(label)
    }
    private fun write(suffix: String, report: JSONObject) {
        context.openFileOutput("sensor-nested-${testName.methodName}-$suffix.json",0).use {
            it.write(report.toString().toByteArray());it.fd.sync()
        }
    }
    private fun fixture(body: (ActivityScenario<LocalBrowserActivity>) -> Unit) {
        assertTrue("unique invocation binding",invocation?.matches(Regex("[a-zA-Z0-9-]{1,160}"))==true)
        assertTrue("exact source binding",candidate?.matches(Regex("[0-9a-f]{40}"))==true)
        val preferences=localBrowserTestPreferences(context)
        val previous=preferences.getString("local.tabs",null)
        var restoreAllowed=true
        var opened=false
        var pointer: PointerOverlay?=null
        var complete=false
        var failure: Throwable?=null
        val started=SystemClock.elapsedRealtimeNanos()
        try {
            Closeable {
                check(restoreAllowed) { "Unexpected current recovery state kept; captured starting state remains private" }
                assertTrue("restore captured recovery only",preferences.edit().apply {
                    if(previous==null) remove("local.tabs") else putString("local.tabs",previous)
                }.commit())
                assertTrue("captured recovery restored",preferences.getString("local.tabs",null)==previous)
                cleanup.put("recoveryRestored",true)
            }.use {
                val scene=ActivityScenario.launch(LocalBrowserActivity::class.java)
                Closeable {
                    scene.close();assertFalse("production sensor released",pointer?.sourceRegistered ?: false)
                    cleanup.put("activityAndSensorReleased",true)
                }.use {
                    Closeable {
                        restoreAllowed=false
                        scene.onActivity { restoreAllowed= !opened ||
                            (it.tabs.count==1 && it.tabs.current.session.state.url==url && it.tabs.current.draft==null) }
                        cleanup.put("currentStateStillOwned",restoreAllowed)
                    }.use {
                        scene.onActivity {
                            pointer=it.pointer
                            while(it.tabs.count>1) it.closeTab();it.closeTab()
                            assertTrue("owned navigation admitted",it.tabs.current.session.open(url).accepted())
                            opened=true
                        }
                        await("actual owned page and production sensor") {var ready=false;scene.onActivity {
                            ready=it.hasWindowFocus() && it.tabs.current.session.state.phase==LocalBrowserSession.Phase.READY &&
                                it.tabs.current.session.page?.isShown==true && it.pointer.position.available && it.pointer.sourceRegistered
                        };ready}
                        scene.onActivity {
                            assertEquals("default hardware acquisition",SensorHeadPoseSource(context).description,it.pointer.sourceDescription)
                            sensorVerified=true
                            observations.put(JSONObject().put("kind","producer").put("description",it.pointer.sourceDescription)
                                .put("registered",it.pointer.sourceRegistered).put("acceptedSamples",it.pointer.acceptedSamples)
                                .put("chromeVersion",it.tabs.current.session.page?.settings?.userAgentString?.let { ua ->
                                    Regex("Chrome/([0-9.]+)").find(ua)?.groupValues?.get(1)
                                } ?: JSONObject.NULL))
                        }
                        write("ready",JSONObject().put("invocationId",invocation).put("candidateHead",candidate)
                            .put("productionSensorVerified",sensorVerified).put("ownerActionReference",ownerAction ?: JSONObject.NULL)
                            .put("physicalMovementNotYetProved",true))
                        body(scene);complete=true
                    }
                }
            }
        } catch(t: Throwable) { failure=t }
        finally {
            val report=JSONObject().put("invocationId",invocation).put("candidateHead",candidate)
                .put("method",testName.methodName).put("startedNs",started).put("finishedNs",SystemClock.elapsedRealtimeNanos())
                .put("productionSensorVerified",sensorVerified).put("rawPoseReplay",false)
                .put("ownerActionReference",ownerAction ?: JSONObject.NULL).put("headMovementObserved",headMovement)
                .put("bodyCompleted",complete).put("cleanup",cleanup).put("observations",observations)
                .put("failureClass",failure?.javaClass?.name ?: JSONObject.NULL)
                .put("suppressedFailureClasses",JSONArray(failure?.suppressed?.map { it.javaClass.name } ?: emptyList<String>()))
            try { write("observations",report) } catch(t: Throwable) { if(failure==null) failure=t else failure.addSuppressed(t) }
        }
        failure?.let { throw it }
    }
    private fun sample(scene: ActivityScenario<LocalBrowserActivity>): JSONObject {
        val row=JSONObject();val done=CountDownLatch(1);var dom=""
        scene.onActivity {
            assertTrue("only owned document observed",it.tabs.current.session.state.url==url && it.tabs.current.session.page?.url==url)
            val pointer=it.pointer
            row.put("sampleNs",SystemClock.elapsedRealtimeNanos()).put("x",pointer.position.x).put("y",pointer.position.y)
                .put("available",pointer.position.available).put("visible",pointer.cursorVisible).put("running",it.edgeScrollRunning)
                .put("registered",pointer.sourceRegistered).put("receiptNs",pointer.lastSampleReceiptNs).put("expiresNs",pointer.expiresAtNs)
                .put("drawY",pointer.lastDrawPosition.y).put("drawNs",pointer.lastDrawElapsedNs)
                .put("drawReceiptNs",pointer.lastDrawSampleReceiptNs).put("acceptedSamples",pointer.acceptedSamples)
                .put("width",checkNotNull(it.tabs.current.session.page).width)
            it.tabs.current.session.page?.evaluateJavascript(
                "[document.getElementById('right').scrollTop,document.getElementById('left').scrollTop,scrollY,innerWidth]"
            ) { dom=it;done.countDown() }
        }
        assertTrue("owned geometry observation",done.await(3,TimeUnit.SECONDS))
        val values=JSONArray(dom);val scale=row.getInt("width")/values.getDouble(3)
        row.put("right",values.getDouble(0)*scale).put("left",values.getDouble(1)*scale).put("root",values.getDouble(2)*scale)
        assertEquals("fixed root stays fixed",0.0,row.getDouble("root"),0.0)
        assertEquals("other panel stays fixed",0.0,row.getDouble("left"),0.0)
        observations.put(row);return row
    }

    @Test fun ownedFixtureReceivesProductionSensorSamplesAndRestoresRecovery()=fixture { scene ->
        val before=sample(scene).getLong("acceptedSamples")
        await("fresh hardware samples keep arriving",3_000) { val row=sample(scene)
            row.getLong("acceptedSamples")>=before+5 && row.getBoolean("available") && row.getBoolean("registered") }
        // A working real sensor at rest is a qualification, not a head-movement demonstration.
    }

    @Test fun ownerHeadMovesAndReversesTheFixedRootNestedPanel() {
        ownerAction=arguments.getString("ownerActionReference")
        assertTrue("authenticated wearer action reference required before launch",
            ownerAction?.matches(Regex("[a-zA-Z0-9-]{1,160}"))==true)
        fixture { scene ->
            val end=SystemClock.elapsedRealtime()+180_000
            fun observe(label: String, condition: (JSONObject) -> Boolean): JSONObject {
                var row=JSONObject()
                await(label,(end-SystemClock.elapsedRealtime()).coerceAtLeast(0)) { row=sample(scene);condition(row) }
                return row
            }
            fun returned(label: String): JSONObject {
                val row=observe(label) { it.getBoolean("available") && it.getBoolean("visible") && !it.getBoolean("running") &&
                    it.getDouble("drawY")>=0 && it.getDouble("drawY")<640 && it.getLong("drawNs")>0 && it.getLong("drawReceiptNs")>0 }
                assertTrue("actual sensor receipt to visible draw within100ms",
                    row.getLong("drawNs")-row.getLong("drawReceiptNs") in 0..100_000_000)
                val right=row.getDouble("right");val until=SystemClock.elapsedRealtime()+200
                do { assertTrue("returned head leaves the panel stationary",abs(sample(scene).getDouble("right")-right)<=2) }
                while(SystemClock.elapsedRealtime()<until)
                return row
            }
            observe("Owner head drives the right panel downward") { it.getBoolean("available") && it.getDouble("x")>280 &&
                it.getDouble("y")>=640 && it.getDouble("right")>=60 }
            val down=returned("Owner returns head into the visible screen")
            observe("Owner head reverses the same panel upward") { it.getBoolean("available") && it.getDouble("x")>280 &&
                it.getDouble("y")<0 && it.getDouble("right")<=down.getDouble("right")-30 }
            returned("Owner returns head after upward scrolling")
            headMovement=true
        }
    }
}
