package com.code2hack.eyebrowse.rg

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class PointerInputInstrumentedTest {
    private class ReplaySource(private val present: Boolean = true) : HeadPoseSource {
        override val description="test raw rotation vectors"
        override var registered=false
            private set
        val callbacks=mutableListOf<(RotationSample)->Unit>()
        override fun start(consumer: (RotationSample)->Unit): Boolean {
            registered=present;callbacks.add(consumer);return present
        }
        override fun stop() { registered=false }
        fun emit(yDegrees: Double=0.0, callback: Int=callbacks.lastIndex) {
            val half=Math.toRadians(yDegrees)/2
            callbacks[callback](RotationSample(SystemClock.elapsedRealtimeNanos(),0f,sin(half).toFloat(),0f,cos(half).toFloat()))
        }
    }
    private fun await(label: String, boundMs: Long=3_000, condition: ()->Boolean) {
        val end=SystemClock.elapsedRealtime()+boundMs
        while(SystemClock.elapsedRealtime()<end) { if(condition()) return;SystemClock.sleep(10) }
        fail(label)
    }
    private fun replay(body: (ActivityScenario<MainActivity>,PointerOverlay,ReplaySource)->Unit) {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var overlay: PointerOverlay
        val source=ReplaySource()
        try {
            scenario.onActivity {
                overlay=it.findViewById(R.id.rg_pointer);overlay.stop()
                overlay.replaceSourceForTest(source);overlay.start();source.emit()
            }
            await("neutral drawn") { var ready=false;scenario.onActivity { ready=overlay.position.available && overlay.lastDrawSampleReceiptNs>0 };ready }
            body(scenario,overlay,source)
        } finally { scenario.close() }
        assertFalse("source released on exit",source.registered)
    }

    @Test fun realSourcePauseResumeAndRecreationReleaseThePredecessor() {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var old: PointerOverlay
        try {
            scenario.onActivity { old=it.findViewById(R.id.rg_pointer) }
            await("production real source") { var ready=false;scenario.onActivity { ready=old.acceptedSamples>=5 && old.position.available };ready }
            scenario.onActivity { assertTrue(old.sourceRegistered);Log.i("EyeBrowsePointerTest","PRODUCTION_SOURCE ${old.sourceDescription} samples=${old.acceptedSamples}") }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(old.sourceRegistered)
            val stopped=old.acceptedSamples;SystemClock.sleep(300);assertEquals(stopped,old.acceptedSamples)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await("fresh registration after resume") { var ready=false;scenario.onActivity { ready=old.acceptedSamples>stopped && old.position.available };ready }
            scenario.recreate()
            assertFalse("retired Activity listener",old.sourceRegistered)
            val retired=old.acceptedSamples
            await("new Activity source") { var ready=false;scenario.onActivity { ready=it.findViewById<PointerOverlay>(R.id.rg_pointer).position.available };ready }
            assertEquals("old Activity has no updates",retired,old.acceptedSamples)
            Log.i("EyeBrowsePointerTest","LIFECYCLE_RELEASE pause=true resume=true recreate=true")
        } finally { scenario.close() }
    }

    @Test fun rawReplayDrawsLocallyWithinBoundAndRecenterDoesNotSendActions() = replay { scenario,overlay,source ->
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("browser-command-sequence",Context.MODE_PRIVATE)
        val before=prefs.all.toMap()
        var center=0f;var receipt=0L
        scenario.onActivity { center=overlay.position.x;source.emit(-2.0);receipt=overlay.lastSampleReceiptNs }
        await("small sample reaches draw",100) {
            var drawn=false
            scenario.onActivity { drawn=overlay.lastDrawSampleReceiptNs==receipt && overlay.position.x>center && overlay.lastDrawElapsedNs>=receipt }
            drawn
        }
        var latency=0.0
        scenario.onActivity { latency=(overlay.lastDrawElapsedNs-receipt)/1e6;assertTrue("local draw <=100ms",latency<=100) }
        val settleStart=SystemClock.elapsedRealtime()
        repeat(35) { scenario.onActivity { source.emit(-10.0) };SystemClock.sleep(20) }
        var held=0f
        scenario.onActivity { held=overlay.position.x }
        repeat(10) { scenario.onActivity { source.emit(-10.0) };SystemClock.sleep(20) }
        scenario.onActivity { activity ->
            assertEquals("held pose settles",held,overlay.position.x,.2f)
            activity.findViewById<Button>(R.id.rg_recenter).performClick()
            assertEquals("recenter",center,overlay.position.x,.1f)
            assertNull(activity.presentation.lastActionResult);assertNull(activity.presentation.lastHandoffResult)
            assertNull(activity.presentation.browserState());assertEquals(0L,activity.presentation.displayedFrames)
            Log.i("EyeBrowsePointerTest","LOCAL_DRAW_MS=$latency SETTLE_OBSERVATION_MS=${SystemClock.elapsedRealtime()-settleStart} framesWithheld=true")
            Log.i("EyeBrowsePointerTest","OVERLAY_CAPTURE_READY x=${overlay.position.x} y=${overlay.position.y} profile=${activity.presentation.profile()}")
        }
        assertEquals("pose and recenter reserve no command",before,prefs.all)
        // Give the non-UiAutomation host screencap collector a bounded observation window.
        repeat(25) { scenario.onActivity { source.emit(-10.0) };SystemClock.sleep(20) }
    }

    @Test fun sensorSilenceFreezesUnavailableAimAndRecoveryRebases() = replay { scenario,overlay,source ->
        scenario.onActivity { source.emit(-10.0) }
        await("silence disables aim",700) { var stale=false;scenario.onActivity { stale=!overlay.position.available };stale }
        var frozen: PointerPosition?=null
        scenario.onActivity {
            frozen=overlay.position
            assertEquals(it.getString(R.string.pointer_unavailable),it.findViewById<TextView>(R.id.rg_pointer_status).text.toString())
        }
        SystemClock.sleep(100)
        scenario.onActivity {
            assertEquals(frozen,overlay.position);source.emit(-70.0)
            val root=it.findViewById<android.view.View>(R.id.rg_root)
            assertTrue(overlay.position.available)
            assertEquals((root.paddingLeft+root.width-root.paddingRight)/2f,overlay.position.x,.1f)
            assertNull(it.presentation.lastActionResult)
        }
        Log.i("EyeBrowsePointerTest","SILENCE_RECOVERY frozen=true unavailable=true freshNeutral=true")
    }

    @Test fun queuedOldRegistrationCannotMoveRestartedOrRecreatedOverlay() = replay { scenario,overlay,source ->
        scenario.onActivity {
            val before=overlay.acceptedSamples
            overlay.stop();overlay.start()
            source.emit(-40.0,0)
            assertEquals(before,overlay.acceptedSamples);assertFalse(overlay.position.available)
            source.emit(-40.0)
            assertEquals(before+1,overlay.acceptedSamples);assertTrue(overlay.position.available)
        }
        scenario.recreate()
        scenario.onActivity {
            val current=it.findViewById<PointerOverlay>(R.id.rg_pointer)
            current.stop();val replacement=ReplaySource();current.replaceSourceForTest(replacement);current.start();replacement.emit()
            val before=current.position;val oldCount=overlay.acceptedSamples
            source.emit(60.0,0);source.emit(60.0)
            assertEquals(before,current.position);assertEquals(oldCount,overlay.acceptedSamples)
            assertFalse(source.registered)
        }
        Log.i("EyeBrowsePointerTest","REGISTRATION_FENCE restart=true recreation=true")
    }

    @Test fun missingSourceAndInvalidRawSamplesLeaveRecoverableLocalControls() {
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity {
                val overlay=it.findViewById<PointerOverlay>(R.id.rg_pointer)
                overlay.stop();overlay.replaceSourceForTest(ReplaySource(false));overlay.start()
                assertFalse(overlay.position.available);assertFalse(overlay.sourceRegistered)
                assertTrue(it.findViewById<Button>(R.id.rg_recenter).isEnabled)
                assertTrue(it.findViewById<Button>(R.id.button_scan_pair).isEnabled)
                val source=ReplaySource();overlay.stop();overlay.replaceSourceForTest(source);overlay.start()
                val before=overlay.acceptedSamples;val now=SystemClock.elapsedRealtimeNanos()
                source.callbacks.last()(RotationSample(now,Float.NaN,0f,0f,1f))
                source.callbacks.last()(RotationSample(now-500_000_000,0f,0f,0f,1f))
                source.callbacks.last()(RotationSample(now+1_000_000_000,0f,0f,0f,1f))
                assertEquals(before,overlay.acceptedSamples);assertFalse(overlay.position.available)
                source.emit();assertTrue(overlay.position.available)
            }
            Log.i("EyeBrowsePointerTest","SOURCE_NEGATIVES absent=true invalid=true stale=true future=true")
        } finally { scenario.close() }
    }

    @Test fun overlayIsTouchTransparentAndPoseDoesNotResizeTheContentProfile() = replay { scenario,overlay,source ->
        var initial: com.code2hack.eyebrowse.core.link.presentation.PresentationProfile?=null
        scenario.onActivity {
            initial=it.presentation.profile();assertNotNull(initial)
            val event=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),MotionEvent.ACTION_DOWN,20f,20f,0)
            try { assertFalse("no competing click route",overlay.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        repeat(10) { n -> scenario.onActivity { source.emit(-n.toDouble()) };SystemClock.sleep(20) }
        scenario.onActivity { assertEquals(initial,it.presentation.profile());assertFalse(overlay.isClickable);assertFalse(overlay.isFocusable) }
        Log.i("EyeBrowsePointerTest","GEOMETRY_STABLE profile=$initial touchTransparent=true")
    }

    @Test fun platformInventoryAcquiresFiniteRotationSamplesAndReleasesListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = instrumentation.targetContext.getSystemService(SensorManager::class.java)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val samples = CountDownLatch(5)
        val received = AtomicInteger()
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        assertNotNull("ordinary-app rotation source", sensor)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values.copyOf()
                if (values.size >= 3 && values.all(Float::isFinite)) {
                    received.incrementAndGet()
                    if (samples.count > 0) Log.i("EyeBrowsePointerTest", "RAW_SAMPLE " + JSONObject()
                        .put("sensorTimestampNs", event.timestamp)
                        .put("receiptElapsedNs", SystemClock.elapsedRealtimeNanos())
                        .put("values", JSONArray(values.toList())))
                    samples.countDown()
                }
            }
        }
        try {
            Log.i("EyeBrowsePointerTest", "PLATFORM_INVENTORY " + JSONObject()
                .put("sensors", JSONArray(manager.getSensorList(Sensor.TYPE_ALL).map {
                    JSONObject().put("name", it.name).put("vendor", it.vendor).put("type", it.type)
                        .put("version", it.version).put("minDelayUs", it.minDelay)
                        .put("maxDelayUs", it.maxDelay).put("wakeUp", it.isWakeUpSensor)
                }))
                .put("inputs", JSONArray(InputDevice.getDeviceIds().toList().mapNotNull { id ->
                    InputDevice.getDevice(id)?.let { device ->
                        val keys = (0..KeyEvent.getMaxKeyCode()).filter { device.hasKeys(it)[0] }
                        JSONObject().put("id", id).put("name", device.name).put("sources", device.sources)
                            .put("keys", JSONArray(keys.map { JSONObject().put("code", it)
                                .put("name", KeyEvent.keyCodeToString(it)) }))
                            .put("ranges", JSONArray(device.motionRanges.map { range ->
                                JSONObject().put("axis", range.axis).put("min", range.min).put("max", range.max)
                            }))
                    }
                })))
            assertTrue(manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME,
                Handler(Looper.getMainLooper())))
            assertTrue("five finite real samples", samples.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { manager.unregisterListener(listener) }
            val afterRelease = received.get()
            SystemClock.sleep(150)
            assertEquals("listener released", afterRelease, received.get())
            Log.i("EyeBrowsePointerTest", "SOURCE_RELEASED sensor=${sensor!!.name} count=$afterRelease")
        } finally {
            manager.unregisterListener(listener)
            scenario.close()
        }
    }
}
