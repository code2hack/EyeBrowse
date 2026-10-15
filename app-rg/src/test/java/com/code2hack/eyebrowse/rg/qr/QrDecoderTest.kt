package com.code2hack.eyebrowse.rg.qr

import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Synthetic rasters verify the decoder; actual camera acquisition is a separate device gate. */
class QrDecoderTest {
    private fun payload(sample: Int) = "https://fixture.example/qr/$sample?from=camera#preview"

    @Test fun `global threshold recovers a small low contrast code in a dark frame`() {
        // Owned replacement for the private optical frame that HybridBinarizer missed.
        val value = "https://subscription.example.invalid/subscribe?token=owned-dummy-0123456789abcdef&format=clash&source=rg-threshold-regression"
        val qr = com.google.zxing.qrcode.QRCodeWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 104, 104)
        val width = 640; val height = 480
        val frame = ByteArray(width * height) { 10 }
        for (y in 0 until qr.height) for (x in 0 until qr.width) {
            frame[(y + 32) * width + x + 32] = if (qr[x, y]) 60 else 100
        }
        assertEquals(value, QrDecoder.decodeYPlane(frame, width, width, height))
    }

    @Test fun `light modules on a dark background decode without changing the payload`() {
        for (value in listOf(QrFixtures.SHORT_URL, QrFixtures.LONG_URL)) {
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 384, 384)
            val inverted = ByteArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) 220.toByte() else 35.toByte()
            }
            assertEquals(value, QrDecoder.decodeYPlane(inverted, matrix.width, matrix.width, matrix.height))
        }
    }

    @Test fun `representative optical targets decode in rotated padded rectangular frames`() {
        for ((name, value) in listOf("short" to QrFixtures.SHORT_URL, "long" to QrFixtures.LONG_URL)) {
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 320, 320)
            for (rotation in listOf(0, 90, 180, 270)) {
                val stride = 672; val width = 640; val height = 480
                val luma = ByteArray((height-1)*stride+width) { 230.toByte() }
                for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                    val black = when (rotation) {
                        90 -> matrix[y, matrix.width-1-x]
                        180 -> matrix[matrix.width-1-x, matrix.height-1-y]
                        270 -> matrix[matrix.height-1-y, x]
                        else -> matrix[x,y]
                    }
                    if (black) luma[(y+64)*stride+x+128] = 20
                }
                assertEquals("$name / $rotation", value, QrDecoder.decodeYPlane(luma, stride, width, height))
            }
            // Regenerable vector targets for a real display. Rendering a target is not a camera scan.
            val natural = com.google.zxing.qrcode.QRCodeWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0)
            val path = buildString {
                for (y in 0 until natural.height) for (x in 0 until natural.width) {
                    if (natural[x,y]) append("M$x $y h1v1h-1z ")
                }
            }
            java.io.File("build/qr-optical-fixture").apply { mkdirs() }.resolve("$name.svg").writeText(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"720\" height=\"720\" viewBox=\"0 0 ${natural.width} ${natural.height}\" shape-rendering=\"crispEdges\"><path fill=\"white\" d=\"M0 0h${natural.width}v${natural.height}H0z\"/><path fill=\"black\" d=\"$path\"/></svg>\n",
            )
        }
    }

    @Test fun `last row need not include stride padding for a longer subscription`() {
        val value = "https://subscription.example.invalid/subscribe?token=owned-dummy-0123456789abcdef&format=clash"
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 320, 320)
        val stride = matrix.width + 32
        val luma = ByteArray((matrix.height - 1) * stride + matrix.width) { 220.toByte() }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) luma[y * stride + x] = 35
        }
        assertEquals(value, QrDecoder.decodeYPlane(luma, stride, matrix.width, matrix.height))
    }

    @Test fun `valid codes decode even when their values must later be rejected`() {
        for (value in listOf("javascript:alert(1)", "intent://example", "file:///sdcard/private", "not a web address")) {
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(value, com.google.zxing.BarcodeFormat.QR_CODE, 256, 256)
            val bytes = ByteArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) 0 else 0xff.toByte()
            }
            assertEquals(value, QrDecoder.decodeYPlane(bytes, matrix.width, matrix.width, matrix.height))
        }
    }

    /** Render with the independent writer, then rasterize to a Y plane the scanner would see. */
    private fun matrixToYPlane(matrix: BitMatrix, quietModules: Int = 2): Pair<ByteArray, Pair<Int, Int>> {
        val qr = matrix.width
        val size = qr + 2 * quietModules
        val luma = ByteArray(size * size) { 0xFF.toByte() } // white background
        for (y in 0 until qr) {
            for (x in 0 until qr) {
                if (matrix.get(x, y)) {
                    luma[(y + quietModules) * size + (x + quietModules)] = 0x00
                }
            }
        }
        return luma to (size to size)
    }

    @Test
    fun `decodes a URL QR from the camera luminance path`() {
        val p = payload(1)
        val (luma, dims) = matrixToYPlane(
            com.google.zxing.qrcode.QRCodeWriter()
                .encode(p, com.google.zxing.BarcodeFormat.QR_CODE, 33, 33),
        )
        val (width, height) = dims
        assertEquals(p, QrDecoder.decodeYPlane(luma, width, width, height))
    }

    @Test
    fun `decodes with row stride padding like CameraX Y planes`() {
        val p = payload(2)
        val quiet = 2
        val matrix = com.google.zxing.qrcode.QRCodeWriter()
            .encode(p, com.google.zxing.BarcodeFormat.QR_CODE, 33, 33)
        val qr = matrix.width // writer auto-sizes the version; use the actual matrix edge
        val size = qr + 2 * quiet
        val rowStride = size + 8 // padded rows, as CameraX often delivers
        val luma = ByteArray(rowStride * size) { 0xFF.toByte() }
        for (y in 0 until qr) for (x in 0 until qr) {
            if (matrix.get(x, y)) luma[(y + quiet) * rowStride + (x + quiet)] = 0x00
        }
        assertEquals(p, QrDecoder.decodeYPlane(luma, rowStride, size, size))
    }

    @Test
    fun `noise and blank rasters decode to null without throwing`() {
        assertNull(QrDecoder.decodeYPlane(ByteArray(64 * 64) { 0x7F }, 64, 64, 64))
        val garbage = ByteArray(64 * 64) { if (it % 3 == 0) 0x00 else 0xFF.toByte() }
        assertNull(QrDecoder.decodeYPlane(garbage, 64, 64, 64))
        assertNull(QrDecoder.decodeYPlane(ByteArray(0), 0, 0, 0))
        assertNull(QrDecoder.decodeYPlane(ByteArray(15), 4, 4, 4))
        assertNull(QrDecoder.decodeYPlane(ByteArray(16), 3, 4, 4))
        assertNull(QrDecoder.decodeYPlane(ByteArray(1), Int.MAX_VALUE, 1, Int.MAX_VALUE))
    }

    @Test
    fun `the decoder is reusable across frames`() {
        val first = payload(3)
        val second = payload(4)
        fun planeFor(p: String): Pair<ByteArray, Int> {
            val (luma, dims) = matrixToYPlane(
                com.google.zxing.qrcode.QRCodeWriter()
                    .encode(p, com.google.zxing.BarcodeFormat.QR_CODE, 33, 33),
            )
            return luma to dims.first
        }
        val (l1, w1) = planeFor(first)
        val (l2, w2) = planeFor(second)
        assertEquals(first, QrDecoder.decodeYPlane(l1, w1, w1, w1))
        assertEquals(second, QrDecoder.decodeYPlane(l2, w2, w2, w2))
        assertEquals(first, QrDecoder.decodeYPlane(l1, w1, w1, w1))
    }
}
