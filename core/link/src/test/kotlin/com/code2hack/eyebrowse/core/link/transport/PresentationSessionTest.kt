package com.code2hack.eyebrowse.core.link.transport

import com.code2hack.eyebrowse.core.link.*
import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.framing.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PresentationSessionTest {
    private val hello = HelloMessage(1,1,LinkProtocol.ALL_CAPABILITIES)
    private val timings = Harness.FAST_TIMINGS.copy(livenessTimeoutMs=5_000)

    @Test fun rememberedLegacyTrustReconnectsWithPresentationCapabilitiesWithoutNewInvitation() {
        val server = Harness.ServerHarness(timings=timings,advertisedHello=hello)
        val port=server.start()
        val old=Harness.ClientHarness(timings=timings,advertisedHello=HelloMessage(1,0,LinkProtocol.REQUIRED_CAPABILITIES))
        old.startEngine()
        try {
            old.engine.connect(old.attempt(server.identity.spkiSha256Hex(),port,server.generateInvitation()))
            assertTrue(Harness.await(old.connected))
            val remembered=checkNotNull(server.storedPeerSpki.get()).copyOf()
            old.engine.disconnect(); assertTrue(Harness.await(server.linkDown))
            val current=Harness.ClientHarness(keyPair=old.keyPair,timings=timings,advertisedHello=hello)
            current.startEngine()
            try {
                current.engine.connect(current.attempt(server.identity.spkiSha256Hex(),port))
                assertTrue(Harness.await(current.connected))
                assertEquals(hello,current.sessionHello.poll(2,TimeUnit.SECONDS))
                assertArrayEquals(remembered,server.storedPeerSpki.get())
                // Only initial pairing commits the server trust; reconnect negotiates fresh metadata.
                assertEquals(0,checkNotNull(server.committedClientHello.get()).pmm)
            } finally { current.engine.disconnect() }
        } finally { old.engine.disconnect();server.stop() }
    }

    @Test fun negotiatedTlsCarriesControlAndOwnerGatedPresentationOnSameSession() {
        val server=Harness.ServerHarness(timings=timings,advertisedHello=hello)
        val port=server.start()
        val client=Harness.ClientHarness(timings=timings,advertisedHello=hello)
        val arbiter=BrowserControlCoordinator("doc")
        val profile=PresentationProfile(480,640,160)
        arbiter.setHostingGeneration(1,true);arbiter.setAuthenticated(true,true)
        server.controlHandler={ session,message ->
            if(message is HandoffRequestMessage) {
                val result=arbiter.requestHandoff(HandoffRequest(HandoffTarget.RG,message.observedControlEpoch,message.profile))
                val s=arbiter.snapshot()
                session.sendControl(HandoffResultMessage(result is HandoffDecision.Accepted,s.owner,s.context,s.profile))
                if(result is HandoffDecision.Accepted) session.setPresentation(s.context,s.profile)
            }
        }
        client.startEngine()
        try {
            client.engine.connect(client.attempt(server.identity.spkiSha256Hex(),port,server.generateInvitation()))
            assertTrue(Harness.await(client.connected))
            val session=server.sessions.poll(2,TimeUnit.SECONDS)!!
            fun frame(ctx:ControlContext,seq:Long)=PresentationFrame(PresentationFrameHeader(ctx,seq,100,480,640),ByteArray(64*1024){5})
            assertFalse(session.sendPresentation(frame(arbiter.snapshot().context,1)))
            assertTrue(client.engine.sendControl(HandoffRequestMessage(HandoffTargetWire.RG,0,profile)))
            val response=client.controls.poll(2,TimeUnit.SECONDS) as HandoffResultMessage
            assertTrue(response.accepted)
            assertTrue(session.sendPresentation(frame(response.context,1)))
            val received=client.frames.poll(2,TimeUnit.SECONDS)!!
            assertEquals(response.context,received.header.context)
            assertEquals(65536,received.pixels().size)
            assertFalse(session.sendPresentation(frame(response.context,1)))
            session.setPresentation(null,null)
            assertFalse(session.sendPresentation(frame(response.context,2)))
        } finally { client.engine.disconnect();server.stop() }
    }

    @Test fun authenticatedLegacyPeerGetsExplicitPresentationUpdateRequired() {
        val server=Harness.ServerHarness(timings=timings)
        val port=server.start();val client=Harness.ClientHarness(timings=timings,advertisedHello=hello)
        client.startEngine()
        try {
            client.engine.connect(client.attempt(server.identity.spkiSha256Hex(),port,server.generateInvitation()))
            assertTrue(Harness.await(client.connected))
            val peer=client.sessionHello.poll(2,TimeUnit.SECONDS)!!
            assertEquals(CapabilityNegotiation.UpdateRequired,CapabilityNegotiator.negotiate(peer,true))
            assertFalse(client.engine.sendControl(HandoffRequestMessage(HandoffTargetWire.RG,0,PresentationProfile(480,640,160))))
            assertNotNull(server.storedPeerSpki.get())
        } finally { client.engine.disconnect();server.stop() }
    }

    @Test fun authenticatedRgCannotInjectPresentationIntoPhone() {
        val server=Harness.ServerHarness(timings=timings,advertisedHello=hello)
        val port=server.start()
        try {
            val raw=Harness.RawClient(port,server.identity.spkiSha256Hex())
            try {
                val invitation=server.generateInvitation()
                raw.sendHello(caps=LinkProtocol.ALL_CAPABILITIES);raw.expectChallenge()
                raw.sendPairAuth(invitation.first,invitation.second,helloOverride=hello)
                assertTrue(raw.readMessage() is AuthOkMessage)
                assertTrue(raw.readNegotiatedRecord() is LinkRecord.Control)
                val context=ControlContext("lifetime",1,"doc",1,1)
                raw.sendRawBytes(PresentationRecordCodec.encodePresentation(
                    PresentationFrame(PresentationFrameHeader(context,1,1,480,640),byteArrayOf(1))))
                assertTrue(Harness.await(server.linkDown))
                assertFalse(server.engine.isLinkUp())
                assertNotNull(server.storedPeerSpki.get())
            } finally { raw.close() }
        } finally { server.stop() }
    }

    @Test fun presentationRecordCannotAuthenticateAPeer() {
        val server=Harness.ServerHarness(timings=timings,advertisedHello=hello)
        val port=server.start()
        try {
            val raw=Harness.RawClient(port,server.identity.spkiSha256Hex())
            try {
                raw.sendRawBytes(byteArrayOf(2,0,0,0,100))
                assertNull(raw.readRawFrame())
                assertNull(server.storedPeerSpki.get())
                assertFalse(server.engine.isLinkUp())
            } finally {raw.close()}
        } finally {server.stop()}
    }
}
