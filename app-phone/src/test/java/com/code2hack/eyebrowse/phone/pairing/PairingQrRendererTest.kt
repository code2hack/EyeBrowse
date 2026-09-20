package com.code2hack.eyebrowse.phone.pairing

import com.code2hack.eyebrowse.core.link.invitation.InvitationCodec
import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T02 predeclared: PairingQrRendererTest. The pure matrix core is verified JVM-side (an
 * independent ZXing reader decodes the rendered matrix); the bitmap wrapper is exercised on
 * device through the rendered QR screenshot path.
 */
class PairingQrRendererTest {

    private fun payload(sample: Int): String = InvitationCodec.encode(
        invitationId = "i${sample.toString().padStart(8, '0')}",
        invitationSecret = ByteArray(32) { (it + sample).toByte() },
        phoneSpkiSha256Hex = sample.toString(16).padStart(64, '0'),
        locators = listOf(
            com.code2hack.eyebrowse.core.link.transport.Locator(
                java.net.InetAddress.getByName("192.168.0.75"),
                39818,
            ),
        ),
    )

    private fun decodeMatrix(matrix: com.google.zxing.common.BitMatrix): String {
        val width = matrix.width
        val pixels = IntArray(width * width)
        for (y in 0 until width) for (x in 0 until width) pixels[y * width + x] = if (matrix.get(x, y)) 0 else -1
        val source = com.google.zxing.RGBLuminanceSource(width, width, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun `rendered matrix decodes back to the exact invitation payload`() {
        val p = payload(1)
        assertEquals(p, decodeMatrix(PairingQrRenderer.renderMatrix(p, 480)))
    }

    @Test
    fun `rendering is deterministic for the same payload and size`() {
        val p = payload(2)
        val a = PairingQrRenderer.renderMatrix(p, 480)
        val b = PairingQrRenderer.renderMatrix(p, 480)
        for (y in 0 until a.width) for (x in 0 until a.width) {
            assertEquals(a.get(x, y), b.get(x, y))
        }
    }

    @Test
    fun `different payloads render different matrices`() {
        assertNotEquals(
            decodeMatrix(PairingQrRenderer.renderMatrix(payload(3), 480)),
            decodeMatrix(PairingQrRenderer.renderMatrix(payload(4), 480)),
        )
    }

    @Test
    fun `bounds are enforced before any rendering`() {
        assertThrows(IllegalArgumentException::class.java) { PairingQrRenderer.renderMatrix("", 480) }
        assertThrows(IllegalArgumentException::class.java) { PairingQrRenderer.renderMatrix(payload(5), 399) }
        assertTrue(PairingQrRenderer.renderMatrix(payload(5), PairingQrRenderer.MIN_SIZE_PX).width >= PairingQrRenderer.MIN_SIZE_PX)
    }
}
