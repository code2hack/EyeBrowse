package com.code2hack.eyebrowse.rg

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.rg.qr.CameraQrScanner
import com.code2hack.eyebrowse.rg.qr.QrFixtures
import com.code2hack.eyebrowse.core.browser.AddressPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Real acquisition diagnostics are separate from controlled result-handler checks. Metadata by default; private captures require explicit run authorization. */
@RunWith(AndroidJUnit4::class)
class QrReliabilityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun retainPrivateLuma(image: ImageProxy, name: String) {
        val plane=image.planes[0]; val buffer=plane.buffer.duplicate(); val origin=buffer.position()
        val pixels=IntArray(image.width*image.height) { index ->
            val y=buffer.get(origin+(index/image.width)*plane.rowStride+(index%image.width)*plane.pixelStride).toInt() and 255
            android.graphics.Color.rgb(y,y,y)
        }
        val bitmap=android.graphics.Bitmap.createBitmap(pixels,image.width,image.height,android.graphics.Bitmap.Config.ARGB_8888)
        context.openFileOutput(name,0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    private fun await(label: String, check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return
            SystemClock.sleep(20)
        }
        fail(label)
    }

    private fun scene(body: (ActivityScenario<LocalBrowserActivity>) -> Unit) {
        val preferences = localBrowserTestPreferences(context)
        val previous = preferences.getString("local.tabs", null)
        val scenario = ActivityScenario.launch(LocalBrowserActivity::class.java)
        try {
            await("focused production window") { var ready = false; scenario.onActivity { ready = it.hasWindowFocus() }; ready }
            body(scenario)
        } finally {
            scenario.close()
            assertTrue(preferences.edit().apply {
                if (previous == null) remove("local.tabs") else putString("local.tabs", previous)
            }.commit())
        }
    }

    @Test fun profilePurposeReturnsRawPayloadOnceWithoutOpeningThePage() = scene { s ->
        // Import-format/address policy belongs to #44. No URL-based guessing in the shared scanner.
        for (payload in listOf("https://subscription.example.invalid/subscribe?token=owned-dummy",
            "clash://install-config?url=https%3A%2F%2Fsubscription.example.invalid%2Fowned-dummy",
            "unsupported owned dummy data")) {
            val received = mutableListOf<String?>()
            s.onActivity { activity ->
                val tab = activity.tabs.current
                val state = tab.session.state
                activity.scanProfileQr { received.add(it) }
                assertEquals("Add a new profile", activity.root.findViewWithTag<TextView>("qr.title").text)
                activity.previewQr(payload)
                activity.previewQr("late owned dummy")
                assertEquals(listOf(payload), received)
                assertSame(tab, activity.tabs.current)
                assertTrue("profile result leaves page state unchanged", state == tab.session.state)
                assertFalse(activity.cameraActive)
                assertEquals(LocalBrowserActivity.Utility.BROWSING, activity.utility)
                assertNull(activity.root.findViewWithTag<View>("qr.open"))
            }
        }
    }

    @Test fun profileCancelAndWebPurposeKeepTheirSeparateResultBehavior() = scene { s ->
        s.onActivity { activity ->
            val results = mutableListOf<String?>()
            activity.scanProfileQr { results.add(it) }
            activity.root.findViewWithTag<View>("qr.cancel").performClick()
            activity.dismissUtility()
            assertEquals(listOf<String?>(null), results)
            activity.showUtility(LocalBrowserActivity.Utility.QR)
            val before = activity.tabs.current.session.state
            activity.previewQr("https://example.com/")
            assertTrue("web preview leaves page state unchanged", before == activity.tabs.current.session.state)
            assertEquals("https://example.com/", activity.root.findViewWithTag<TextView>("qr.url").text)
            assertNotNull(activity.root.findViewWithTag<View>("qr.open"))
            activity.dismissUtility()
            activity.showUtility(LocalBrowserActivity.Utility.QR)
            activity.previewQr("clash://install-config?url=owned-dummy")
            assertNull(activity.root.findViewWithTag<View>("qr.open"))
            assertTrue(activity.root.findViewWithTag<TextView>("qr.message").text.contains("Unsupported"))
            assertEquals(listOf<String?>(null), results)
        }
    }

    @Test fun webPreviewRequiresDeliberateOpenInCurrentTab() = scene { s ->
        val base = checkNotNull(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl"))
        val target = "${base.trimEnd('/')}/index.html"
        s.onActivity { activity ->
            val tab = activity.tabs.current
            val state = tab.session.state
            activity.showUtility(LocalBrowserActivity.Utility.QR)
            activity.previewQr(target)
            assertSame(tab, activity.tabs.current)
            assertTrue("preview does not navigate", state == tab.session.state)
            assertFalse(activity.cameraActive)
            assertEquals(target, activity.root.findViewWithTag<TextView>("qr.url").text)
            assertTrue(activity.root.findViewWithTag<View>("qr.open").performClick())
            assertSame(tab, activity.tabs.current)
            assertEquals(LocalBrowserActivity.Utility.BROWSING, activity.utility)
        }
        await("deliberately opened owned fixture ready") {
            var ready=false
            s.onActivity { ready=it.tabs.current.session.state.phase == LocalBrowserSession.Phase.READY }
            ready
        }
        s.onActivity { assertEquals(target, it.tabs.current.session.state.url) }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    @Test fun actualFrameLayoutArrivalAndExecutorRelease() = scene { s ->
        assertEquals("existing app CAMERA permission required", PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.CAMERA))
        val manager = context.getSystemService(CameraManager::class.java)
        val available = ConcurrentHashMap<String, Boolean>()
        val availability = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) { available[cameraId] = true }
            override fun onCameraUnavailable(cameraId: String) { available[cameraId] = false }
        }
        manager.registerAvailabilityCallback(availability, Handler(Looper.getMainLooper()))
        val cycles = JSONArray()
        val privateCapture=InstrumentationRegistry.getArguments().getString("capturePrivateFrames")=="owner-authorized"
        try {
            repeat(if(privateCapture) 1 else 3) { cycle ->
                val frames = CopyOnWriteArrayList<JSONObject>()
                val enough = CountDownLatch(1)
                val result = CountDownLatch(1)
                var scanner: CameraQrScanner? = null
                var observedCameraInfo: CameraInfo? = null
                var preview: PreviewView? = null
                var error = false
                var decoded = false
                var frameCount = 0
                var latestFrame: JSONObject? = null
                val startedNs = SystemClock.elapsedRealtimeNanos()
                val record = JSONObject().put("cycle", cycle).put("startedNs", startedNs)
                cycles.put(record)
                try {
                    s.onActivity { activity ->
                        activity.dismissUtility()
                        preview = PreviewView(activity).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                        }.also { activity.root.addView(it, android.widget.FrameLayout.LayoutParams(448, 272).apply { leftMargin=16; topMargin=124 }) }
                        scanner = CameraQrScanner(activity, activity, { payload ->
                            decoded=true
                            record.put("decodedLength", payload.length)
                                .put("decodedScheme", android.net.Uri.parse(payload).scheme ?: JSONObject.NULL)
                                .put("addressAccepted", AddressPolicy.resolve(payload).accepted())
                            enough.countDown(); result.countDown()
                        }, { observedCameraInfo=scanner?.cameraInfo }, { error = true; enough.countDown(); result.countDown() }, frameObserver = { image ->
                            frameCount++
                            if(privateCapture && (frameCount==1 || frameCount%10==0))
                                retainPrivateLuma(image,"issue42-private-$cycle-${if(frameCount==1) "first" else "last"}.png")
                            if (frames.size < 12 || frameCount % 10 == 0) {
                                val plane = image.planes[0]
                                val luma = plane.buffer.duplicate()
                                val origin = luma.position()
                                var minimum = 255; var maximum = 0; var sum = 0L; var samples = 0
                                for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) {
                                    val value = luma.get(origin + y * plane.rowStride + x * plane.pixelStride).toInt() and 255
                                    minimum = minOf(minimum, value); maximum = maxOf(maximum, value); sum += value; samples++
                                }
                                val observation = JSONObject().put("observedNs", SystemClock.elapsedRealtimeNanos())
                                    .put("imageTimestampNs", image.imageInfo.timestamp).put("width", image.width).put("height", image.height)
                                    .put("rotationDegrees", image.imageInfo.rotationDegrees).put("crop", image.cropRect.flattenToString())
                                    .put("format", image.format).put("rowStride", plane.rowStride).put("pixelStride", plane.pixelStride)
                                    .put("bufferPosition", origin).put("bufferRemaining", luma.remaining())
                                    .put("lumaMin", minimum).put("lumaMax", maximum).put("sampleMean", sum.toDouble()/samples)
                                latestFrame = observation
                                if (frames.size < 12) frames.add(observation)
                                if (frames.size == 12) enough.countDown()
                            }
                        }).also { it.start(checkNotNull(preview)) }
                    }
                    assertTrue("12 actual analyzer frames or successful early decode", enough.await(10, TimeUnit.SECONDS))
                    result.await(5, TimeUnit.SECONDS)
                    if (!decoded) await("preview streaming") { var streaming=false; s.onActivity { streaming=preview?.previewStreamState?.value==PreviewView.StreamState.STREAMING }; streaming }
                    s.onActivity {
                        assertFalse("camera error", error)
                        val info = checkNotNull(observedCameraInfo ?: scanner?.cameraInfo)
                        val camera = Camera2CameraInfo.from(info)
                        record.put("cameraId", camera.cameraId)
                            .put("sensorOrientation", camera.getCameraCharacteristic(CameraCharacteristics.SENSOR_ORIENTATION))
                            .put("lensFacing", camera.getCameraCharacteristic(CameraCharacteristics.LENS_FACING))
                            .put("afModes", camera.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList())
                            .put("minimumFocusDistance", camera.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE))
                            .put("exposureRange", camera.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.toString())
                            .put("exposureCompensationIndex", info.exposureState.exposureCompensationIndex)
                            .put("yuvSizes", camera.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                                ?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)?.map { it.toString() })
                            .put("decodedEarly", decoded).put("previewStreamState", preview?.previewStreamState?.value.toString())
                            .put("previewScaleType", preview?.scaleType.toString())
                    }
                    if(privateCapture) {
                        val screen=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                        val crop=android.graphics.Bitmap.createBitmap(screen,16,124,448,272)
                        context.openFileOutput("issue42-private-$cycle-preview.png",0).use { crop.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                        crop.recycle();screen.recycle()
                        record.put("privatePreviewCapturedNs",SystemClock.elapsedRealtimeNanos())
                    }
                } finally {
                    s.onActivity { scanner?.cancel(); it.root.removeView(preview) }
                    record.put("frames", JSONArray(frames)).put("lastSampledFrame", latestFrame)
                        .put("frameCount", frameCount).put("cameraError", error).put("rawDecoded", decoded)
                    await("analyzer executor terminated") { scanner?.executorTerminated == true }
                    await("owned camera available after unbind") { available.isNotEmpty() && available.values.all { it } }
                }
                cycles.getJSONObject(cycle).put("cameraReleased", true).put("executorTerminated", true)
            }
        } finally {
            manager.unregisterAvailabilityCallback(availability)
            context.openFileOutput("issue42-frame-observations.json", 0).use { it.write(cycles.toString(2).toByteArray()) }
        }
    }

    @Test fun repeatedProfileScanCancelAndPauseResumeReleaseTheCamera() = scene { s ->
        repeat(3) { cycle ->
            s.onActivity { it.scanProfileQr { } }
            await("profile purpose actual analyzer") { var ready=false; s.onActivity { ready=it.cameraHasFrame }; ready }
            if (cycle==1) {
                s.moveToState(Lifecycle.State.CREATED)
                s.onActivity { assertFalse(it.cameraActive) }
                s.moveToState(Lifecycle.State.RESUMED)
                s.onActivity { it.root.findViewWithTag<View>("qr.retry").performClick() }
                await("resumed profile camera frame") { var ready=false; s.onActivity { ready=it.cameraHasFrame }; ready }
            }
            s.onActivity { it.root.findViewWithTag<View>("qr.cancel").performClick(); assertFalse(it.cameraActive) }
        }
    }

    /** Invoke only after the rig shows a complete owned target; inspect retained first frames. */
    @Test fun opticalTenCyclesOfDeclaredOwnedTarget() = scene { s ->
        val fixture = InstrumentationRegistry.getArguments().getString("qrFixture")
        val expected = when (fixture) {
            "short" -> QrFixtures.SHORT_URL
            "long" -> QrFixtures.LONG_URL
            else -> throw IllegalArgumentException("Optical run needs an aligned display and qrFixture=short or long")
        }
        val observations = JSONArray()
        try {
            repeat(10) { cycle ->
                val frameReady = CountDownLatch(1)
                val decoded = CountDownLatch(1)
                var firstFrameNs = 0L
                var payload: String? = null
                var decodedNs = 0L
                var cameraError = false
                var scanner: CameraQrScanner? = null
                var preview: PreviewView? = null
                val record = JSONObject().put("fixture", fixture).put("cycle", cycle)
                observations.put(record)
                try {
                    s.onActivity { activity ->
                        preview = PreviewView(activity).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                        }.also { activity.root.addView(it, android.widget.FrameLayout.LayoutParams(448,272)) }
                        scanner = CameraQrScanner(activity, activity, {
                            payload=it; decodedNs=SystemClock.elapsedRealtimeNanos(); decoded.countDown()
                        }, { }, { cameraError=true; decoded.countDown() }, frameObserver = { image ->
                            if (firstFrameNs == 0L) {
                                firstFrameNs=SystemClock.elapsedRealtimeNanos()
                                val plane=image.planes[0]
                                retainPrivateLuma(image,"issue42-optical-$fixture-$cycle-first.png")
                                record.put("width",image.width).put("height",image.height).put("crop",image.cropRect.flattenToString())
                                    .put("rotationDegrees",image.imageInfo.rotationDegrees).put("rowStride",plane.rowStride).put("pixelStride",plane.pixelStride)
                                frameReady.countDown()
                            }
                        }).also { it.start(checkNotNull(preview)) }
                    }
                    assertTrue("actual first frame",frameReady.await(10,TimeUnit.SECONDS))
                    assertTrue("actual optical decode within five seconds of first frame",decoded.await(5,TimeUnit.SECONDS))
                    assertFalse("camera acquisition error", cameraError)
                    record.put("payloadMatched",payload==expected).put("decodedNs",decodedNs)
                    assertTrue("actual optical payload matches declared owned target",payload==expected)
                    assertTrue("five-second target",(decodedNs-firstFrameNs)/1_000_000.0<=5_000)
                } finally {
                    s.onActivity { scanner?.cancel(); it.root.removeView(preview) }
                    record.put("firstFrameNs",firstFrameNs).put("decodedNs",decodedNs)
                        .put("payloadMatched",payload==expected)
                        .put("cameraError",cameraError)
                        .put("firstFrameToDecodeMs",if(decodedNs>0) (decodedNs-firstFrameNs)/1_000_000.0 else JSONObject.NULL)
                    await("optical analyzer executor terminated") { scanner?.executorTerminated==true }
                }
            }
        } finally {
            context.openFileOutput("issue42-optical-$fixture-results.json",0).use { it.write(observations.toString(2).toByteArray()) }
        }
    }
}
