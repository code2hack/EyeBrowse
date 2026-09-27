package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.HostStatusValue
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real TLS peers; only server scheduling after the AuthOk flush is controlled. */
class AuthPublicationRaceTest {
    private fun atAuthOk(check: (Harness.ServerHarness, Harness.ClientHarness, CountDownLatch) -> Unit) {
        val server = Harness.ServerHarness()
        val client = Harness.ClientHarness()
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val port = server.start()
        server.engine.afterAuthOkForTest = {
            reached.countDown()
            check(release.await(3, TimeUnit.SECONDS)) { "AuthOk scheduling hold expired" }
        }
        client.startEngine()
        try {
            client.engine.connect(client.attempt(server.identity.spkiSha256Hex(), port, server.generateInvitation()))
            assertTrue("server flushed AuthOk", reached.await(2, TimeUnit.SECONDS))
            assertTrue("client observed CONNECTED", client.connected.await(2, TimeUnit.SECONDS))
            println("I11_AUTH_PUBLICATION clientConnected=true serverPhase=${server.engine.currentPhase}")
            check(server, client, release)
        } finally {
            release.countDown()
            server.engine.afterAuthOkForTest = null
            client.engine.disconnect()
            server.stop()
        }
    }

    @Test fun `status push after client connected survives server publication delay`() = atAuthOk { server, client, release ->
        server.engine.pushStatus(HostStatusValue.HOST_STARTING)
        server.engine.pushStatus(HostStatusValue.HOSTING)
        release.countDown()
        assertTrue("server completed publication", server.linkUp.await(2, TimeUnit.SECONDS))
        assertEquals(HostStatusValue.HOST_INACTIVE, client.statuses.pollFirst(1, TimeUnit.SECONDS))
        val firstStarting = client.statuses.pollFirst(200, TimeUnit.MILLISECONDS)
        val firstHosting = client.statuses.pollFirst(200, TimeUnit.MILLISECONDS)

        // Same API, payloads and live connection; change only server publication completion.
        server.engine.pushStatus(HostStatusValue.HOST_STARTING)
        server.engine.pushStatus(HostStatusValue.HOSTING)
        val laterStarting = client.statuses.pollFirst(1, TimeUnit.SECONDS)
        val laterHosting = client.statuses.pollFirst(1, TimeUnit.SECONDS)
        println("I11_AUTH_PUBLICATION earlyStatus=$firstStarting,$firstHosting laterStatus=$laterStarting,$laterHosting")
        assertEquals("positive control starting", HostStatusValue.HOST_STARTING, laterStarting)
        assertEquals("positive control hosting", HostStatusValue.HOSTING, laterHosting)
        assertEquals("connected peer must receive starting status", HostStatusValue.HOST_STARTING, firstStarting)
        assertEquals("connected peer must receive latest status", HostStatusValue.HOSTING, firstHosting)
    }

    @Test fun `forget after client connected survives server publication delay`() = atAuthOk { server, client, release ->
        server.engine.sendForgetNotice()
        release.countDown()
        assertTrue("server completed publication", server.linkUp.await(2, TimeUnit.SECONDS))
        assertEquals(HostStatusValue.HOST_INACTIVE, client.statuses.pollFirst(1, TimeUnit.SECONDS))
        val firstDisconnected = client.disconnected.await(200, TimeUnit.MILLISECONDS)
        server.engine.sendForgetNotice()
        val laterDisconnected = client.disconnected.await(1, TimeUnit.SECONDS)
        println("I11_AUTH_PUBLICATION earlyForget=$firstDisconnected laterForget=$laterDisconnected")
        assertTrue("positive control disconnect", laterDisconnected)
        assertTrue("connected peer must receive the first Forget", firstDisconnected)
    }
}
