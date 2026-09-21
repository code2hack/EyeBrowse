package com.code2hack.eyebrowse.core.link

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.framing.*
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test

class T01FoundationTest {
    private val profile = PresentationProfile(800, 600, 240)
    private fun ready(): BrowserControlCoordinator = BrowserControlCoordinator("doc-1").apply {
        setAuthenticated(true, true)
        setHostingGeneration(7, true)
        assertTrue(requestHandoff(HandoffRequest(HandoffTarget.RG, 0, profile)) is HandoffDecision.Accepted)
        assertTrue(markPresentationReady(snapshot().context))
    }
    private fun frame(context: ControlContext, seq: Long = 1, size: Int = 40_000) =
        PresentationFrame(PresentationFrameHeader(context, seq, 100, profile.width, profile.height), ByteArray(size) { 42 })

    @Test fun capabilityNegotiationPreservesLegacyBaseLink() {
        val old = HelloMessage(1, 0, LinkProtocol.REQUIRED_CAPABILITIES)
        assertEquals(CapabilityNegotiation.Accepted, CapabilityNegotiator.negotiate(old, false))
        assertEquals(CapabilityNegotiation.UpdateRequired, CapabilityNegotiator.negotiate(old, true))
        assertEquals(CapabilityNegotiation.Accepted, CapabilityNegotiator.negotiate(HelloMessage(1, 1, LinkProtocol.ALL_CAPABILITIES), true))
    }

    @Test fun invalidMeasuredProfilesAreRejected() {
        for ((w,h,d) in listOf(Triple(0,640,160), Triple(-1,640,160), Triple(4096,4096,160), Triple(5000,640,160), Triple(480,640,0))) {
            assertNull(PresentationProfile.fromMeasured(w,h,d))
        }
        assertNotNull(PresentationProfile.fromMeasured(2048,2048,160))
    }

    @Test fun inactiveHostingNeverGrantsHandoff() {
        val c = BrowserControlCoordinator("doc")
        c.setAuthenticated(true,true)
        c.setHostingGeneration(9,false)
        assertEquals(HandoffDecision.Rejected(HandoffRejection.HOSTING_INACTIVE), c.requestHandoff(HandoffRequest(HandoffTarget.RG,0,profile)))
    }

    @Test fun unauthenticatedAndIncompatibleHandoffsFail() {
        val c = BrowserControlCoordinator("doc")
        c.setHostingGeneration(1,true)
        assertEquals(HandoffDecision.Rejected(HandoffRejection.LINK_UNAVAILABLE),c.requestHandoff(HandoffRequest(HandoffTarget.RG,0,profile)))
        c.setAuthenticated(true,false)
        assertEquals(HandoffDecision.Rejected(HandoffRejection.INCOMPATIBLE_SESSION),c.requestHandoff(HandoffRequest(HandoffTarget.RG,0,profile)))
    }

    @Test fun concurrentHandoffsAdvanceEpochExactlyOnce() {
        val c = BrowserControlCoordinator("doc")
        c.setHostingGeneration(1,true); c.setAuthenticated(true,true)
        val start = CountDownLatch(1)
        val results = java.util.concurrent.ConcurrentLinkedQueue<HandoffDecision>()
        val threads = (1..8).map { Thread { start.await(); results.add(c.requestHandoff(HandoffRequest(HandoffTarget.RG,0,profile))) }.apply { start() } }
        start.countDown(); threads.forEach { it.join(2000); assertFalse(it.isAlive) }
        assertEquals(1,results.count { it is HandoffDecision.Accepted })
        assertEquals(1L,c.snapshot().controlEpoch)
    }

    @Test fun takeoverUsesFreshPhoneMeasurementAndRejectsOldCallbacks() {
        val c = ready(); val old = c.snapshot()
        val local = PresentationProfile(1080,1800,450)
        assertTrue(c.requestHandoff(HandoffRequest(HandoffTarget.PHONE,old.controlEpoch,profile)) is HandoffDecision.Rejected)
        assertTrue(c.requestHandoff(HandoffRequest(HandoffTarget.PHONE,old.controlEpoch)) { local } is HandoffDecision.Accepted)
        assertEquals(local,c.snapshot().profile)
        assertFalse(c.markPresentationReady(old.context))
        assertTrue(c.requestHandoff(HandoffRequest(HandoffTarget.RG,c.snapshot().controlEpoch,profile)) is HandoffDecision.Accepted)
        assertFalse(c.markPresentationReady(old.context))
        assertFalse(c.markPresentationStale(old.context))
    }

    @Test fun reconnectDoesNotTransferOwnerOrRevivePresentation() {
        val c=ready(); val old=c.snapshot()
        c.setAuthenticated(false); c.setAuthenticated(true,true)
        assertEquals(ControlOwner.RG,c.snapshot().owner)
        assertEquals(old.controlEpoch,c.snapshot().controlEpoch)
        assertEquals(PresentationStatus.STALE,c.snapshot().presentationStatus)
        assertFalse(c.markPresentationReady(old.context))
    }

    @Test fun wrongOwnerAndEachStaleContextAreRejected() {
        val c=ready();val ctx=c.snapshot().context
        fun request(x: ControlContext)=BrowserActionRequest("c",x,BrowserAction.Back)
        assertEquals(ActionDecision.Rejected(ActionRejection.WRONG_OWNER),c.admitAction(ControlOwner.PHONE,request(ctx)))
        for (x in listOf(ctx.copy(controlEpoch=0),ctx.copy(documentId="old"),ctx.copy(viewportEpoch=0),ctx.copy(lifetimeId="old"),ctx.copy(hostingGeneration=2))) {
            assertEquals(ActionDecision.Rejected(ActionRejection.STALE_CONTEXT),c.admitAction(ControlOwner.RG,request(x)))
        }
    }

    @Test fun commandPressureCannotEvictIdsAndPermitReplay() {
        val c=BrowserControlCoordinator("doc",2)
        fun req(id:String)=BrowserActionRequest(id,c.snapshot().context,BrowserAction.Back)
        assertTrue(c.admitAction(ControlOwner.PHONE,req("1")) is ActionDecision.Accepted)
        assertTrue(c.admitAction(ControlOwner.PHONE,req("2")) is ActionDecision.Accepted)
        assertEquals(ActionDecision.Rejected(ActionRejection.COMMAND_LIMIT_REACHED),c.admitAction(ControlOwner.PHONE,req("3")))
        assertEquals(ActionDecision.Rejected(ActionRejection.DUPLICATE_COMMAND),c.admitAction(ControlOwner.PHONE,req("1")))
    }

    @Test fun recordsLargerThan32KiBRoundTrip() {
        val f=frame(ready().snapshot().context)
        val bytes=PresentationRecordCodec.encodePresentation(f)
        val decoded=PresentationRecordCodec.read(ByteArrayInputStream(bytes),true,true) { it.context==f.header.context } as LinkRecord.Presentation
        assertArrayEquals(f.pixels(),decoded.frame.pixels())
        assertEquals(f.header,decoded.frame.header)
    }

    @Test fun preauthWrongOwnerAndInvalidKindRejectBeforeBodyRead() {
        fun wire(kind:Int,size:Int)=ByteArrayInputStream(ByteBuffer.allocate(5).put(kind.toByte()).putInt(size).array())
        val full=PresentationRecordCodec.encodePresentation(frame(ready().snapshot().context))
        val preauth=ByteArrayInputStream(full)
        assertEquals("record before authentication",assertThrows(IOException::class.java) {
            PresentationRecordCodec.read(preauth,false,true) { true }
        }.message)
        assertEquals(full.size-5,preauth.available())
        val wrongOwner=ByteArrayInputStream(full)
        assertEquals("presentation without RG ownership",assertThrows(IOException::class.java) {
            PresentationRecordCodec.read(wrongOwner,true,false) { true }
        }.message)
        assertEquals(full.size-5,wrongOwner.available())
        assertThrows(IOException::class.java) { PresentationRecordCodec.read(wire(99,100),true,true) }
        for(size in listOf(0,-1,Int.MAX_VALUE,LinkProtocol.PRESENTATION_RECORD_MAX_BYTES+1)) {
            assertThrows(IOException::class.java) { PresentationRecordCodec.read(wire(2,size),true,true) }
        }
    }

    @Test fun staleFrameContextAndMidFrameDisconnectFailClosed() {
        val f=frame(ready().snapshot().context)
        val bytes=PresentationRecordCodec.encodePresentation(f)
        assertThrows(IOException::class.java) { PresentationRecordCodec.read(ByteArrayInputStream(bytes),true,true) { false } }
        assertThrows(IOException::class.java) { PresentationRecordCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size-1)),true,true) { true } }
    }

    @Test fun unsignedLegacyLengthCannotAllocateNegativeArray() {
        assertThrows(LinkFrameCodec.FrameTooLargeException::class.java) { LinkFrameCodec.parseHeader(byteArrayOf(-1,-1,-1,-1)) }
    }

    @Test fun queueIsBoundedPrioritizedLatestOnlyAndDoesNotReplay() {
        val q=MultiplexedRecordQueue(2); val ctx=ready().snapshot().context
        q.setPresentation(ctx,profile)
        assertTrue(q.offerFrame(frame(ctx,1))); assertTrue(q.offerFrame(frame(ctx,2)))
        assertFalse(q.offerFrame(frame(ctx,1)))
        assertTrue(q.offerControl(PingMessage));assertTrue(q.offerControl(PongMessage));assertFalse(q.offerControl(PingMessage))
        repeat(2){assertTrue(PresentationRecordCodec.read(ByteArrayInputStream(q.poll()),true,false) is LinkRecord.Control)}
        val f=PresentationRecordCodec.read(ByteArrayInputStream(q.poll()),true,true){true} as LinkRecord.Presentation
        assertEquals(2L,f.frame.header.frameSeq)
        assertFalse(q.offerFrame(frame(ctx,1)))
        q.setPresentation(null,null);assertFalse(q.offerFrame(frame(ctx,3)));assertNull(q.poll())
        q.setPresentation(ctx,profile);assertFalse(q.offerFrame(frame(ctx,2)))
        assertTrue(q.offerFrame(frame(ctx,3)))
    }


    @Test fun malformedMetadataAndOversizedControlAreRejected() {
        val invalid=ByteBuffer.allocate(9).put(2).putInt(100).putInt(Int.MAX_VALUE).array()
        assertEquals("invalid metadata length",assertThrows(IOException::class.java) {
            PresentationRecordCodec.read(ByteArrayInputStream(invalid),true,true) { true }
        }.message)
        val control=ByteBuffer.allocate(5).put(1).putInt(LinkProtocol.FRAME_MAX_BYTES+1).array()
        assertEquals("invalid record length",assertThrows(IOException::class.java) {
            PresentationRecordCodec.read(ByteArrayInputStream(control),true,false)
        }.message)
    }

    @Test fun maximumRecordBoundAndImmutableQueuedPixels() {
        val context=ready().snapshot().context
        val bytes=ByteArray(40_000){7}
        val f=PresentationFrame(PresentationFrameHeader(context,1,1,800,600),bytes)
        bytes.fill(9)
        assertEquals(7,f.pixels()[0].toInt())
        assertThrows(IllegalArgumentException::class.java) {
            PresentationRecordCodec.encodePresentation(frame(context,size=LinkProtocol.PRESENTATION_RECORD_MAX_BYTES))
        }
    }

    @Test fun typedActionMessagesRoundTrip() {
        val ctx=ready().snapshot().context
        for(action in listOf(BrowserAction.Back,BrowserAction.Forward,BrowserAction.Reload,BrowserAction.ActivateAt(1f,2f),BrowserAction.ScrollBy(0f,4f))) {
            val message=BrowserActionMessage("c",ctx,action)
            assertEquals(message,(LinkMessageCodec.decode(LinkMessageCodec.encode(message)).getOrThrow() as LinkMessageCodec.Incoming.Known).message)
        }
    }
}
