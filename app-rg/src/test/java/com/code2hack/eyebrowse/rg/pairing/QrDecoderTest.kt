package com.code2hack.eyebrowse.rg.pairing

import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.code2hack.eyebrowse.core.link.transport.Locator
import com.google.zxing.common.BitMatrix
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T02 predeclared: QrDecoderTest. The Y-plane entry point (camera path) is verified on the JVM
 * against synthetic luminance rasters; the bitmap entry point (instrumentation image-input seam)
 * is exercised on device through the seam instrumentation. Both share the one production decoder.
 */
class QrDecoderTest {

    private fun payload(sample: Int): String = InvitationCodec.encode(
        invitationId = "q${sample.toString().padStart(8, '0')}",
        invitationSecret = ByteArray(32) { (it * 3 + sample).toByte() },
        phoneSpkiSha256Hex = sample.toString(16).padStart(64, '0'),
        locators = listOf(Locator(InetAddress.getByName("192.168.0.75"), 39818)),
    )

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
    fun `decodes a real invitation QR from the camera luminance path`() {
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
