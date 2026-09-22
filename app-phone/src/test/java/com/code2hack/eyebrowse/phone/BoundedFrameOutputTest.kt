package com.code2hack.eyebrowse.phone

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BoundedFrameOutputTest {
    @Test fun compressionCannotAllocatePastWireBudget() {
        val output = BoundedFrameOutput(4)
        output.write(byteArrayOf(1,2,3))
        assertThrows(IOException::class.java) { output.write(ByteArray(2)) }
        output.write(4)
        assertThrows(IOException::class.java) { output.write(5) }
        assertArrayEquals(byteArrayOf(1,2,3,4), output.toByteArray())
    }
    @Test fun oversizedSingleWriteIsRejectedWithoutRetainingPixels() {
        val output = BoundedFrameOutput(4)
        assertThrows(IOException::class.java) { output.write(ByteArray(100)) }
        assertEquals(0, output.toByteArray().size)
    }
}
