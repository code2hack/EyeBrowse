package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.HostStatusValue
import com.code2hack.eyebrowse.core.link.LinkError
import com.code2hack.eyebrowse.core.link.PairingState
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
import com.code2hack.eyebrowse.core.link.session.PeerTrustRecord
import com.code2hack.eyebrowse.core.link.session.PeerTrustStore
import com.code2hack.eyebrowse.core.link.testfix.TestCrypto
import com.code2hack.eyebrowse.rg.link.RgLinkClient
import com.code2hack.eyebrowse.rg.link.RgPairingStore
import org.junit.Assert.*
import org.junit.Test

/** Actual controller -> link client -> trust-read boundary, without Android startup or dialing. */
class NativeRetryDispatchTest {
    private class Harness {
        val monitor=Any()
        // Constructor-free only to omit Android Handler/bitmap infrastructure from this JVM check.
        private val allocator=Class.forName("sun.misc.Unsafe")
        val controller=allocator.getMethod("allocateInstance",Class::class.java).invoke(
            allocator.getDeclaredField("theUnsafe").apply { isAccessible=true }.get(null),
            RgPresentationController::class.java) as RgPresentationController
        var reads=0
        var readHeldMonitor=false
        val failures=mutableListOf<LinkError>()
        init {
            val commands=CommandSequence({null},{true},{},{0})
            set("lock",monitor);set("commands",commands);set("reservationOwner",commands.claim {})
            val observer=object : PeerTrustStore {
                override fun read(): PeerTrustRead {
                    reads++;readHeldMonitor=Thread.holdsLock(monitor)
                    return PeerTrustRead.Absent // Stop before network and never access real pairing data.
                }
                override fun save(record: PeerTrustRecord) { error("Unexpected trust write") }
                override fun clear() { error("Unexpected trust clear") }
            }
            val store=RgPairingStore::class.java.getDeclaredConstructor(PeerTrustStore::class.java)
                .apply { isAccessible=true }.newInstance(observer)
            val listener=object : RgLinkClient.Listener {
                override fun onStateChange(state: PairingState) { error("Unexpected connection") }
                override fun onStatus(status: HostStatusValue) {}
                override fun onLinkLost() {}
                override fun onConnectFailed(error: LinkError) { failures.add(error) }
            }
            set("client",RgLinkClient(TestCrypto.softwareSigningIdentity(TestCrypto.ecKeyPair()),store,listener))
        }
        fun set(name: String,value: Any) {
            RgPresentationController::class.java.getDeclaredField(name).apply { isAccessible=true }.set(controller,value)
        }
    }

    @Test fun nativeRetryValidatesOriginalContextThenReadsTrustWithoutControllerMonitor() {
        val h=Harness();val controller=h.controller
        val accepted=controller.dispatchIfCurrent(controller.inputSnapshot(),LocalInputAction.RETRY) {
            controller.reconnect();true // Same native Retry listener as MainActivity.
        }
        println("NATIVE_RETRY_BOUNDARY dispatchAccepted=$accepted trustReadReached=${h.reads==1}"+
            " trustReadHoldsControllerMonitor=${h.readHeldMonitor}")
        assertTrue(accepted)
        assertEquals(1,h.reads)
        assertEquals(listOf(LinkError.InvitationInvalid),h.failures)
        assertFalse("Native Retry trust read must not inherit the validated-dispatch monitor",h.readHeldMonitor)

        val current=controller.inputSnapshot()
        assertFalse(controller.dispatchIfCurrent(current.copy(revision=current.revision+1),LocalInputAction.RETRY) {
            controller.reconnect();true
        })
        h.set("closed",true)
        assertFalse(controller.dispatchIfCurrent(current,LocalInputAction.RETRY) { controller.reconnect();true })
        assertEquals("Stale or closed input never invokes Retry or reads trust",1,h.reads)
    }

    @Test fun pageAndOtherNativeCallbacksRetainAtomicValidatedDispatch() {
        val h=Harness();var calls=0
        for(action in listOf<LocalInputAction?>(null)+LocalInputAction.entries.filter { it!=LocalInputAction.RETRY }) {
            val expected=h.controller.inputSnapshot()
            assertTrue(h.controller.dispatchIfCurrent(expected,action) {
                assertTrue("Page/handoff and other callbacks retain their atomic boundary",Thread.holdsLock(h.monitor))
                calls++;true
            })
            assertFalse(h.controller.dispatchIfCurrent(expected.copy(reservationRevision=expected.reservationRevision+1),action) {
                error("Old readiness cannot dispatch")
            })
        }
        assertEquals(LocalInputAction.entries.size,calls)
        assertEquals(0,h.reads)
    }
}
