package com.code2hack.eyebrowse.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptureDrainSequenceTest {

    @Test
    fun `fresh render request follows stale buffer drain`() {
        val events = mutableListOf<String>()
        CaptureDrainSequence.run(
            drain = { events += "drain" },
            requestFresh = { events += "fresh" },
        )
        assertEquals(listOf("drain", "fresh"), events)
    }

    @Test
    fun `fresh render request still occurs when drain fails`() {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            CaptureDrainSequence.run(
                drain = {
                    events += "drain"
                    throw IllegalStateException("synthetic drain failure")
                },
                requestFresh = { events += "fresh" },
            )
        }
        assertEquals(listOf("drain", "fresh"), events)
    }
}
