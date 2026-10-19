package com.code2hack.eyebrowse.projector.directhost

import com.code2hack.eyebrowse.projector.protocol.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ScrcpyControlTest {
    @Test fun panelPowerCannotSplitAnInputMessageAndClosedControlRejectsFurtherWrites() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val buffer = ByteArrayOutputStream()
        val output = object : OutputStream() {
            override fun write(value: Int) { buffer.write(value) }
            override fun write(data: ByteArray) {
                if (data.size > 2) {
                    buffer.write(data[0].toInt()); entered.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                    buffer.write(data, 1, data.size - 1)
                } else buffer.write(data)
            }
        }
        val control = ScrcpyControl(output)
        val tap = Scrcpy.tap(Tap(10, 20, Geometry(480, 640)))
        val input = thread { control.write(tap) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val panel = thread { control.write(Scrcpy.displayPower(false)) }
        release.countDown()
        input.join(2000); panel.join(2000)
        assertFalse(input.isAlive); assertFalse(panel.isAlive)
        assertArrayEquals(tap + byteArrayOf(10, 0), buffer.toByteArray())
        control.write(Scrcpy.displayPower(true))
        control.close()
        assertThrows(IllegalStateException::class.java) { control.write(Scrcpy.displayPower(false)) }
        assertArrayEquals(tap + byteArrayOf(10, 0, 10, 1), buffer.toByteArray())
    }
}
