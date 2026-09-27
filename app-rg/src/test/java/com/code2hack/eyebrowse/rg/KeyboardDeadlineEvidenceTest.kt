package com.code2hack.eyebrowse.rg

import com.code2hack.eyebrowse.core.link.control.*
import com.code2hack.eyebrowse.core.link.framing.PresentationFrameHeader
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.presentation.PresentationProfile
import org.junit.Assert.*
import org.junit.Test

class KeyboardDeadlineEvidenceTest {
    private val profile=PresentationProfile(480,344,204)
    private val old=ControlContext("life",1,"old",10,1)
    private val fresh=old.copy(documentId="new",viewportEpoch=12)
    private val request=KeyboardDeadlineEvidence.Request(ViewportUpdateMessage(2,old,profile),2000)
    private val reply=ViewportUpdateResultMessage(2,true,old.copy(viewportEpoch=11),profile)
    private val completion=KeyboardDeadlineEvidence.Completion("new",371,
        BrowserStateMessage(ControlOwner.RG,fresh,profile,stale=false),profile,
        PresentationFrameHeader(fresh,1,0,480,344),false,false,true,true)
    private fun check(c:KeyboardDeadlineEvidence.Completion=completion,
                      requests:List<KeyboardDeadlineEvidence.Request> = listOf(request),
                      replies:Map<Long,ViewportUpdateResultMessage> = mapOf(2L to reply),deadline:Long?=2000) =
        KeyboardDeadlineEvidence.witness(deadline,requests,replies,c)
    @Test fun acceptedOldProfileInheritedByFreshDocumentIsOnTimeEvidence() {
        assertEquals(KeyboardDeadlineEvidence.Witness.INHERITED_PROFILE,check())
    }
    @Test fun continuationRequestRetainsTheSameDeadline() {
        val next=request.copy(message=ViewportUpdateMessage(3,fresh.copy(viewportEpoch=11),profile))
        assertEquals(KeyboardDeadlineEvidence.Witness.CONTINUED_REQUEST,
            check(requests=listOf(request,next),replies=mapOf(2L to reply,3L to ViewportUpdateResultMessage(3,true,fresh,profile))))
    }
    @Test fun missingLateAndChangedDeadlinesNeverPass() {
        assertNull(check(deadline=null));assertNull(check(requests=emptyList()))
        assertNull(check(completion.copy(observedAt=2000)));assertNull(check(completion.copy(observedAt=2001)))
        assertNull(check(requests=listOf(request.copy(deadline=2001))))
        assertNull(check(requests=listOf(request,request.copy(message=request.message.copy(transitionId=3),deadline=2100))))
    }
    @Test fun wrongDocumentProfileFrameAndOwnershipNeverPass() {
        val state=completion.state!!;val frame=completion.frame!!
        for(c in listOf(completion.copy(document="wrong"),completion.copy(measured=profile.copy(height=345)),
            completion.copy(frame=frame.copy(context=old)),completion.copy(frame=frame.copy(width=479)),
            completion.copy(state=state.copy(owner=ControlOwner.PHONE)),completion.copy(state=state.copy(stale=true)),
            completion.copy(state=state.copy(loading=true)),completion.copy(state=state.copy(profile=null)))) assertNull(check(c))
        assertNull(check(replies=mapOf(2L to reply.copy(context=reply.context.copy(lifetimeId="other")))))
    }
    @Test fun unresolvedLayoutOrMissingAcceptanceCannotBecomeAWitness() {
        for(c in listOf(completion.copy(layoutPending=true),completion.copy(viewportPending=true),
            completion.copy(canAct=false),completion.copy(hidden=false)))assertNull(check(c))
        assertNull(check(replies=emptyMap()));assertNull(check(replies=mapOf(2L to reply.copy(accepted=false))))
        assertNull(check(replies=mapOf(2L to reply.copy(profile=profile.copy(height=345)))))
    }
    @Test fun oldOrFutureEpochCannotStandInForCurrentAuthority() {
        assertNull(check(replies=mapOf(2L to reply.copy(context=old))))
        assertNull(check(replies=mapOf(2L to reply.copy(context=old.copy(viewportEpoch=13)))))
        assertNull(check(requests=listOf(request.copy(message=request.message.copy(context=old.copy(controlEpoch=2))))))
    }
}
