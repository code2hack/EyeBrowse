package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.LinkProtocol
import com.code2hack.eyebrowse.core.link.messages.HelloMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class ServerStopTest {
    @Test fun stopClosesTlsOffCallerAndImmediatelyRetiresState() {
        val server = Harness.ServerHarness(advertisedHello=HelloMessage(1,1,LinkProtocol.ALL_CAPABILITIES))
        val client = Harness.ClientHarness(advertisedHello=HelloMessage(1,1,LinkProtocol.ALL_CAPABILITIES))
        val port = server.start(); client.startEngine()
        val seen = CountDownLatch(1)
        val closeThread = AtomicReference<Thread>()
        try {
            client.engine.connect(client.attempt(server.identity.spkiSha256Hex(),port,server.generateInvitation()))
            assertTrue(Harness.await(client.connected))
            assertTrue(Harness.await(server.linkUp))
            val caller = Thread.currentThread()
            server.engine.beforeStopTlsCloseForTest = { closeThread.set(Thread.currentThread()); seen.countDown() }
            server.stop()
            assertEquals(LinkServerEngine.Phase.IDLE,server.engine.currentPhase)
            assertEquals(-1,server.engine.boundPort())
            assertTrue(seen.await(2,TimeUnit.SECONDS))
            assertNotSame("TLS close_notify must never run on stop's caller",caller,closeThread.get())
        } finally { server.engine.beforeStopTlsCloseForTest=null;client.engine.disconnect();server.stop() }
    }

    @Test fun delayedStopCloseCannotPoisonRestartedSession() {
        val timings=Harness.FAST_TIMINGS.copy(livenessTimeoutMs=5_000)
        val server=Harness.ServerHarness(timings=timings)
        val first=Harness.ClientHarness(timings=timings);first.startEngine()
        val port=server.start()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val closed=CountDownLatch(1)
        var second: Harness.ClientHarness?=null
        try {
            first.engine.connect(first.attempt(server.identity.spkiSha256Hex(),port,server.generateInvitation()))
            assertTrue(Harness.await(first.connected));assertTrue(Harness.await(server.linkUp))
            server.engine.beforeStopTlsCloseForTest={ entered.countDown();release.await(5,TimeUnit.SECONDS);closed.countDown() }
            server.stop();assertTrue(Harness.await(entered))
            assertFalse(server.engine.isLinkUp())
            server.engine.start(port) // same engine AND port, even while prior TLS cleanup is held
            val next=Harness.ClientHarness(keyPair=first.keyPair,timings=timings);second=next;next.startEngine()
            next.engine.connect(next.attempt(server.identity.spkiSha256Hex(),port))
            assertTrue(Harness.await(next.connected))
            release.countDown();assertTrue(Harness.await(closed))
            assertTrue(Harness.await(first.disconnected))
            assertTrue(server.engine.isLinkUp())
            assertEquals(0,server.listenerLinkDownNotifications.get())
        } finally { release.countDown();server.engine.beforeStopTlsCloseForTest=null;first.engine.disconnect();second?.engine?.disconnect();server.stop() }
    }

    @Test fun stoppedAuthenticatorCannotCommitTrustAfterForgetBoundary() {
        val server=Harness.ServerHarness();val port=server.start()
        val client=Harness.ClientHarness();client.startEngine()
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val returned=CountDownLatch(1)
        try {
            server.engine.beforePairingCommitForTest={ entered.countDown();release.await(5,TimeUnit.SECONDS);returned.countDown() }
            client.engine.connect(client.attempt(server.identity.spkiSha256Hex(),port,server.generateInvitation()))
            assertTrue(Harness.await(entered))
            server.stop()
            server.storedPeerSpki.set(null) // Phone Forget clears store AFTER stop returns.
            release.countDown();assertTrue(Harness.await(returned))
            assertFalse(server.committed.await(300,TimeUnit.MILLISECONDS))
            assertNull(server.storedPeerSpki.get())
        } finally { release.countDown();client.engine.disconnect();server.stop() }
    }
}
