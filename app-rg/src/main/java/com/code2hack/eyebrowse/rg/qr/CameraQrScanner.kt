package com.code2hack.eyebrowse.rg.qr

import android.content.Context
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.util.concurrent.Executors

/** One camera acquisition. All UI callbacks and teardown run on the main thread. */
internal class CameraQrScanner(
    context: Context,
    private val owner: LifecycleOwner,
    private val onPayload: (String) -> Unit,
    private val onFirstFrame: () -> Unit,
    private val onError: () -> Unit,
    private val selector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
) {
    private val providerFuture = ProcessCameraProvider.getInstance(context)
    private val main = ContextCompat.getMainExecutor(context)
    private val worker = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var info: CameraInfo? = null
    private val preview = Preview.Builder().build()
    private val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    @Volatile private var cancelled = false
    private var firstFrame = true
    private val cameraState = Observer<CameraState> { if (it.error != null) fail() }

    fun start(view: PreviewView) {
        providerFuture.addListener({
            if (cancelled) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                preview.setSurfaceProvider(view.surfaceProvider)
                analysis.setAnalyzer(worker, ::analyze)
                info = cameraProvider.bindToLifecycle(owner, selector, preview, analysis).cameraInfo
                info?.cameraState?.observe(owner, cameraState)
            } catch (_: Exception) { fail() }
        }, main)
    }

    private fun analyze(image: ImageProxy) {
        var payload: String? = null
        var failed = false
        try {
            if (cancelled) return
            if (firstFrame) {
                firstFrame = false
                main.execute { if (!cancelled) onFirstFrame() }
            }
            image.planes.firstOrNull()?.let { plane ->
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                payload = QrDecoder.decodeYPlane(bytes, plane.rowStride, image.width, image.height)
            }
        } catch (_: Exception) { failed = true }
        finally { image.close() }
        if (failed) main.execute { fail() }
        payload?.let { value -> main.execute {
            if (!cancelled) { cancel(); onPayload(value) }
        } }
    }

    private fun fail() {
        if (cancelled) return
        cancel(); onError()
    }

    fun cancel() {
        cancelled = true
        info?.cameraState?.removeObserver(cameraState); info = null
        analysis.clearAnalyzer()
        provider?.unbind(preview, analysis); provider = null
        worker.shutdown()
    }
}
