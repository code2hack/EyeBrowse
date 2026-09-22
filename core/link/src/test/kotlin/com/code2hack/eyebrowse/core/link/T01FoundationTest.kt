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
        fun request(x: ControlContext)=BrowserActionRequest(BrowserCommandId.create(x,1),x,BrowserAction.Back,1)
        assertEquals(ActionDecision.Rejected(ActionRejection.WRONG_OWNER),c.admitAction(ControlOwner.PHONE,request(ctx)))
        for (x in listOf(ctx.copy(controlEpoch=0),ctx.copy(documentId="old"),ctx.copy(viewportEpoch=0),ctx.copy(lifetimeId="old"),ctx.copy(hostingGeneration=2))) {
            assertEquals(ActionDecision.Rejected(ActionRejection.STALE_CONTEXT),c.admitAction(ControlOwner.RG,request(x)))
        }
    }

    @Test fun commandPressureCannotEvictIdsAndPermitReplay() {
        val c=BrowserControlCoordinator("doc")
        fun req(sequence:Long): BrowserActionRequest {
            val context=c.snapshot().context
            return BrowserActionRequest(BrowserCommandId.create(context,sequence),context,BrowserAction.Back,sequence)
        }
        assertTrue(c.admitAction(ControlOwner.PHONE,req(1)) is ActionDecision.Accepted)
        assertTrue(c.admitAction(ControlOwner.PHONE,req(2)) is ActionDecision.Accepted)
        for (sequence in 3L..512L) assertTrue(c.admitAction(ControlOwner.PHONE,req(sequence)) is ActionDecision.Accepted)
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),c.admitAction(ControlOwner.PHONE,req(1)))
    }

    @Test fun stableRgScrollingHasNoLedgerCeilingAndOldSequencesStayRejected() {
        val c=ready()
        val before=c.snapshot()
        fun request(sequence:Long,id:String=if(sequence>0) BrowserCommandId.create(before.context,sequence) else "invalid") =
            BrowserActionRequest(id,before.context,BrowserAction.ScrollBy(0f,1f),sequence)
        for (sequence in 1L..10_000L) {
            assertTrue("sequence $sequence",c.admitAction(ControlOwner.RG,request(sequence)) is ActionDecision.Accepted)
        }
        assertEquals(before,c.snapshot()) // No navigation, viewport change or artificial ownership churn.
        for(sequence in listOf(1L,256L,9_999L,10_000L)) {
            assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),
                c.admitAction(ControlOwner.RG,request(sequence)))
            assertEquals(ActionDecision.Rejected(ActionRejection.INVALID_COMMAND_ID),
                c.admitAction(ControlOwner.RG,request(sequence,"different-correlation-id")))
        }
        assertTrue(c.admitAction(ControlOwner.RG,request(10_001L)) is ActionDecision.Accepted)
        assertTrue(c.admitAction(ControlOwner.RG,request(Long.MAX_VALUE)) is ActionDecision.Accepted)
        assertEquals(ActionDecision.Rejected(ActionRejection.INVALID_COMMAND_SEQUENCE),c.admitAction(ControlOwner.RG,request(Long.MIN_VALUE)))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),c.admitAction(ControlOwner.RG,request(Long.MAX_VALUE)))
    }

    @Test fun duplicateCommandIdWithNewSequenceIsStructurallyRejected() {
        val c=ready(); val context=c.snapshot().context
        val first=BrowserActionRequest(BrowserCommandId.create(context,100),context,BrowserAction.Back,100)
        assertEquals(ActionDecision.Accepted(first.commandId),c.admitAction(ControlOwner.RG,first))
        assertEquals(ActionDecision.Rejected(ActionRejection.INVALID_COMMAND_ID),
            c.admitAction(ControlOwner.RG,first.copy(commandSequence=101,action=BrowserAction.Forward)))
        // A rejected alias must not consume the valid next ordinal or introduce an ID cache.
        for (sequence in listOf(101L,102L,500L)) {
            val next=BrowserActionRequest(BrowserCommandId.create(context,sequence),context,BrowserAction.Forward,sequence)
            assertNotEquals(first.commandId,next.commandId)
            assertEquals(ActionDecision.Accepted(next.commandId),c.admitAction(ControlOwner.RG,next))
        }
    }

    @Test fun duplicateCommandIdWithSameSequenceIsReplayRejected() {
        val c=ready(); val context=c.snapshot().context
        val first=BrowserActionRequest(BrowserCommandId.create(context,100),context,BrowserAction.Back,100)
        assertTrue(c.admitAction(ControlOwner.RG,first) is ActionDecision.Accepted)
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),c.admitAction(ControlOwner.RG,first))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),
            c.admitAction(ControlOwner.RG,first.copy(action=BrowserAction.Reload)))
    }

    @Test fun commandIdentityIsCanonicalAndScopedToLifetimeAndControlEpoch() {
        val context=ControlContext("host:with:separators",1,"doc",2,3)
        val id=BrowserCommandId.create(context,23)
        assertEquals("v2:1:23:2:3:20:host:with:separators3:doc",id)
        val different=listOf(
            BrowserCommandId.create(context,24),
            BrowserCommandId.create(context.copy(controlEpoch=12),3),
            BrowserCommandId.create(context.copy(lifetimeId="other:host"),23),
            BrowserCommandId.create(context.copy(documentId="new"),23),
            BrowserCommandId.create(context.copy(viewportEpoch=9),23),
            BrowserCommandId.create(context.copy(hostingGeneration=7),23),
            BrowserCommandId.create(context.copy(hostingGeneration=null),23),
            BrowserCommandId.create(context.copy(hostingGeneration=0),23),
        )
        assertEquals(9,(different+id).toSet().size)
        val joinedA=context.copy(lifetimeId="a",documentId="12:x")
        val joinedB=context.copy(lifetimeId="a1",documentId="2:x")
        assertEquals(joinedA.lifetimeId+joinedA.documentId,joinedB.lifetimeId+joinedB.documentId)
        assertNotEquals(BrowserCommandId.create(joinedA,23),BrowserCommandId.create(joinedB,23))
        for (alias in listOf(id.replace("v2:1:","v2:01:"),id.replace(":23:",":023:"),
            id.replace(":23:",":+23:"),id.replace(":20:",":020:"),"x")) {
            assertFalse(BrowserCommandId.matches(alias,context,23))
        }
        assertFalse(BrowserCommandId.matches(id,context.copy(controlEpoch=2),23))
        assertFalse(BrowserCommandId.matches(id,context.copy(lifetimeId="other:host"),23))
        assertFalse(BrowserCommandId.matches(id,context,0))
        assertThrows(IllegalArgumentException::class.java) { BrowserCommandId.create(context,0) }
    }

    @Test fun wireRejectsCommandIdSequenceAndNamespaceAliases() {
        val context=ready().snapshot().context
        val message=BrowserActionMessage(BrowserCommandId.create(context,100),context,BrowserAction.Back,100)
        val encoded=LinkMessageCodec.encode(message).toString(Charsets.UTF_8)
        val newSequence=encoded.replace("\"commandSequence\":100","\"commandSequence\":101")
        assertNotEquals(encoded,newSequence)
        assertTrue(LinkMessageCodec.decode(newSequence.toByteArray()).isFailure)
        val newEpoch=encoded.replace("\"controlEpoch\":${context.controlEpoch}","\"controlEpoch\":${context.controlEpoch+1}")
        assertNotEquals(encoded,newEpoch)
        assertTrue(LinkMessageCodec.decode(newEpoch.toByteArray()).isFailure)
        val alias=encoded.replace("\"commandId\":\"${message.commandId}\"","\"commandId\":\"x\"")
        assertNotEquals(encoded,alias)
        assertTrue(LinkMessageCodec.decode(alias.toByteArray()).isFailure)
        assertThrows(IllegalArgumentException::class.java) { message.copy(commandSequence=101) }
        assertThrows(IllegalArgumentException::class.java) { message.copy(context=context.copy(lifetimeId="another-lifetime")) }
    }

    @Test fun maximumCanonicalCommandIdFitsBoundedActionAndResultWire() {
        val context=ControlContext("\u03bb:".repeat(64),Long.MAX_VALUE,"\u03bb:".repeat(128),Long.MAX_VALUE,Long.MAX_VALUE)
        val id=BrowserCommandId.create(context,Long.MAX_VALUE)
        assertEquals(BrowserCommandId.MAX_LENGTH,id.length)
        val action=BrowserActionMessage(id,context,BrowserAction.Back,Long.MAX_VALUE)
        val result=BrowserActionResultMessage(id,true,null)
        for (message in listOf(action,result)) {
            val encoded=LinkMessageCodec.encode(message)
            assertTrue(encoded.size < LinkProtocol.FRAME_MAX_BYTES)
            assertEquals(message,(LinkMessageCodec.decode(encoded).getOrThrow() as LinkMessageCodec.Incoming.Known).message)
        }
        assertThrows(IllegalArgumentException::class.java) { result.copy(commandId=id+"x") }
    }

    @Test fun staleContextRejectionDoesNotReserveCurrentSequence() {
        val c=ready(); val old=c.snapshot().context
        fun request(context: ControlContext,sequence: Long)=
            BrowserActionRequest(BrowserCommandId.create(context,sequence),context,BrowserAction.Back,sequence)
        assertTrue(c.admitAction(ControlOwner.RG,request(old,99)) is ActionDecision.Accepted)
        c.setDocumentIdentity("doc-2")
        val current=c.snapshot().context
        assertEquals(old.controlEpoch,current.controlEpoch)
        assertTrue(c.markPresentationReady(current))
        val staleContexts=listOf(old,current.copy(documentId="old"),current.copy(viewportEpoch=0),
            current.copy(hostingGeneration=null),current.copy(hostingGeneration=6),
            current.copy(lifetimeId="old"),current.copy(controlEpoch=0))
        for (context in staleContexts) {
            assertNotEquals(request(context,100).commandId,request(current,100).commandId)
            for (sequence in listOf(100L,Long.MAX_VALUE)) {
                assertEquals(ActionDecision.Rejected(ActionRejection.STALE_CONTEXT),
                    c.admitAction(ControlOwner.RG,request(context,sequence)))
            }
        }
        assertEquals(ActionDecision.Accepted(request(current,100).commandId),
            c.admitAction(ControlOwner.RG,request(current,100)))
        assertTrue(c.admitAction(ControlOwner.RG,request(current,101)) is ActionDecision.Accepted)
    }

    @Test fun currentReadinessRejectionConsumesOrdinalBeforeReadinessRecovers() {
        val c=ready(); val context=c.snapshot().context
        val request=BrowserActionRequest(BrowserCommandId.create(context,100),context,BrowserAction.Back,100)
        assertTrue(c.markPresentationStale(context))
        assertEquals(ActionDecision.Rejected(ActionRejection.PRESENTATION_NOT_READY),c.admitAction(ControlOwner.RG,request))
        assertTrue(c.markPresentationReady(context))
        assertEquals(context,c.snapshot().context) // Policy changed, not command identity.
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),c.admitAction(ControlOwner.RG,request))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),
            c.admitAction(ControlOwner.RG,request.copy(action=BrowserAction.Reload)))
        assertTrue(c.admitAction(ControlOwner.RG,request.copy(
            commandId=BrowserCommandId.create(context,101),commandSequence=101)) is ActionDecision.Accepted)
    }

    @Test fun currentPolicyRejectionCannotBeReusedForDifferentEffect() {
        val c=ready(); val context=c.snapshot().context
        val request=BrowserActionRequest(BrowserCommandId.create(context,100),context,
            BrowserAction.ActivateAt(profile.width.toFloat(),0f),100)
        assertEquals(ActionDecision.Rejected(ActionRejection.OUTSIDE_VIEWPORT),c.admitAction(ControlOwner.RG,request))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),c.admitAction(ControlOwner.RG,request))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),
            c.admitAction(ControlOwner.RG,request.copy(action=BrowserAction.ActivateAt(1f,1f))))
        val next=request.copy(commandId=BrowserCommandId.create(context,101),commandSequence=101,action=BrowserAction.Back)
        assertEquals(ActionDecision.Rejected(ActionRejection.WRONG_OWNER),c.admitAction(ControlOwner.PHONE,next))
        assertEquals(ActionDecision.Rejected(ActionRejection.STALE_COMMAND_SEQUENCE),c.admitAction(ControlOwner.RG,next))
        assertTrue(c.admitAction(ControlOwner.RG,next.copy(
            commandId=BrowserCommandId.create(context,102),commandSequence=102)) is ActionDecision.Accepted)
    }

    @Test fun malformedCurrentContextCannotConsumeOrdinal() {
        val c=ready(); val context=c.snapshot().context
        val request=BrowserActionRequest(BrowserCommandId.create(context,100),context,BrowserAction.Back,100)
        assertEquals(ActionDecision.Rejected(ActionRejection.INVALID_COMMAND_ID),
            c.admitAction(ControlOwner.RG,request.copy(commandSequence=Long.MAX_VALUE)))
        assertEquals(ActionDecision.Rejected(ActionRejection.INVALID_COMMAND_SEQUENCE),
            c.admitAction(ControlOwner.RG,request.copy(commandSequence=0)))
        assertTrue(c.admitAction(ControlOwner.RG,request) is ActionDecision.Accepted)
    }

    @Test fun wireIdentityIncludesEachActionTargetContextField() {
        val context=ready().snapshot().context
        val message=BrowserActionMessage(BrowserCommandId.create(context,100),context,BrowserAction.Back,100)
        val encoded=LinkMessageCodec.encode(message).toString(Charsets.UTF_8)
        val changes=listOf(
            "\"documentId\":\"${context.documentId}\"" to "\"documentId\":\"different\"",
            "\"viewportEpoch\":${context.viewportEpoch}" to "\"viewportEpoch\":${context.viewportEpoch+1}",
            "\"hostingGeneration\":${context.hostingGeneration}" to "\"hostingGeneration\":null",
            "\"hostingGeneration\":${context.hostingGeneration}" to "\"hostingGeneration\":0",
        )
        for ((from,to) in changes) {
            val forged=encoded.replace(from,to)
            assertNotEquals(encoded,forged)
            assertTrue(LinkMessageCodec.decode(forged.toByteArray()).isFailure)
        }
        for (changed in listOf(context.copy(documentId="other"),context.copy(viewportEpoch=0),
            context.copy(hostingGeneration=null),context.copy(hostingGeneration=0))) {
            assertThrows(IllegalArgumentException::class.java) { message.copy(context=changed) }
        }
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
            val message=BrowserActionMessage(BrowserCommandId.create(ctx,1),ctx,action,1)
            assertEquals(message,(LinkMessageCodec.decode(LinkMessageCodec.encode(message)).getOrThrow() as LinkMessageCodec.Incoming.Known).message)
            val encoded=LinkMessageCodec.encode(message).toString(Charsets.UTF_8)
            assertTrue(LinkMessageCodec.decode(encoded.replace("\"commandSequence\":1", "\"commandSequence\":0").toByteArray()).isFailure)
            assertTrue(LinkMessageCodec.decode(encoded.replace(",\"commandSequence\":1", "").toByteArray()).isFailure)
        }
    }
}
