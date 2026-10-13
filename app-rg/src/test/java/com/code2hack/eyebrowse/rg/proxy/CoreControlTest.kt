package com.code2hack.eyebrowse.rg.proxy

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class CoreControlTest {
    @Test fun parsesOrdinaryJsonAndNoContent() {
        assertEquals("v1.19.32", CoreControl.decode("HTTP/1.1 200 OK\r\nContent-Length: 22\r\n\r\n{\"version\":\"v1.19.32\"}".toByteArray()).getString("version"))
        assertEquals(0, CoreControl.decode("HTTP/1.1 204 No Content\r\n\r\n".toByteArray()).length())
    }
    @Test fun chunkLengthsAreBytesAndErrorsDoNotExposeCoreMessages() {
        val body = "{\"name\":\"节点\"}".toByteArray()
        val wire = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n${body.size.toString(16)}\r\n".toByteArray() + body + "\r\n0\r\n\r\n".toByteArray()
        assertEquals("节点", CoreControl.decode(wire).getString("name"))
        val error = assertThrows(IOException::class.java) {
            CoreControl.decode("HTTP/1.1 400 Bad Request\r\n\r\ncredential-bearing failure".toByteArray())
        }
        assertFalse(error.message!!.contains("credential"))
        assertThrows(IOException::class.java) { CoreControl.decode(wire.copyOf(wire.size - 8)) }
    }
}
