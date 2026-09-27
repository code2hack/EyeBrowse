package com.code2hack.eyebrowse.rg

import android.graphics.BitmapFactory
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.code2hack.eyebrowse.core.link.*
import com.code2hack.eyebrowse.core.link.control.ControlOwner
import com.code2hack.eyebrowse.core.link.framing.PresentationFrame
import com.code2hack.eyebrowse.core.link.messages.*
import com.code2hack.eyebrowse.core.link.session.PeerTrustRead
import com.code2hack.eyebrowse.core.link.transport.LinkClientEngine
import com.code2hack.eyebrowse.rg.link.*
import com.code2hack.eyebrowse.rg.pairing.QrDecoder
import com.code2hack.eyebrowse.rg.pairing.RgPairingActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RgRecoveryInstrumentedTest {
    private val cleanupActions=mutableListOf<()->Unit>()
    @After fun cleanupOwnedResources() {
        org.junit.runners.model.MultipleFailureException.assertEmpty(cleanupActions.mapNotNull { runCatching(it).exceptionOrNull() })
    }
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val app=instrumentation.targetContext
    private val args=InstrumentationRegistry.getArguments()
    init {
        require(args.getString("restartOwner","rg") in listOf("rg","phone"))
        require(args.getString("forgetMode","online") in listOf("online","offline"))
    }
    private val mission=UUID.fromString(checkNotNull(args.getString("missionId"))).toString()
    private val phase=File(app.cacheDir,"i11-rg-$mission.phase")
    private val receipt=app.getSharedPreferences("i11-rg-$mission",0)
    private val canonical=RgPairingStore(app)
    private val trustFile=File(app.filesDir,"pairing/peer_trust.json")
    private fun fingerprint(): String {
        val keys=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue("existing identity alias required",keys.containsAlias(RgLinkIdentity.ALIAS))
        return MessageDigest.getInstance("SHA-256").digest(RgLinkIdentity().spki()).joinToString("") { "%02x".format(it) }
    }
    private fun await(label:String,bound:Long=10_000,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+bound
        while(SystemClock.elapsedRealtime()<end) { if(condition()) return;SystemClock.sleep(20) }
        fail(label)
    }
    private fun idle(client:RgLinkClient): Boolean {
        val field=RgLinkClient::class.java.getDeclaredField("engine").apply { isAccessible=true }
        return (field.get(client) as? LinkClientEngine)?.isBusy != true
    }
    private class Events : RgLinkClient.Listener {
        val states=LinkedBlockingDeque<PairingState>()
        val statuses=LinkedBlockingDeque<HostStatusValue>()
        val failures=LinkedBlockingDeque<LinkError>()
        @Volatile var browser:BrowserStateMessage?=null
        @Volatile var frames=0
        override fun onStateChange(state:PairingState) { states.add(state) }
        override fun onStatus(status:HostStatusValue) { statuses.add(status) }
        override fun onLinkLost() = Unit
        override fun onConnectFailed(error:LinkError) { failures.add(error) }
        override fun onControl(message:BrowserControlMessage) { if(message is BrowserStateMessage)browser=message }
        override fun onPresentation(frame:PresentationFrame) { frames++ }
    }

    @Test fun prepareActualRgProcessRestart() {
        assertTrue(canonical.isPaired())
        val originalTrust=trustFile.readBytes()
        val identity=fingerprint()
        phase.writeText("starting")
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var peer:RgPresentationController
        cleanupActions += { scenario.close() }
        cleanupActions += { phase.delete();assertFalse(phase.exists()) }
        scenario.onActivity { peer=it.presentation;it.findViewById<Button>(R.id.rg_retry).performClick() }
        await("current mission Phone state") { peer.canHandoff() && peer.profile()!=null && peer.browserState()?.url?.endsWith("case=$mission")==true }
        assertEquals(ControlOwner.PHONE,peer.browserState()!!.owner)
        scenario.onActivity { it.findViewById<Button>(R.id.rg_handoff).performClick() }
        await("fresh RG frame",2_000) { peer.canAct() }
        await("Phone has observed RG ownership") { phase.readText().trim()=="prepared" }
        val state=peer.browserState()!!
        val profile=peer.profile()!!
        assertTrue(receipt.edit().putLong("processStart",Process.getStartElapsedRealtime()).putInt("pid",Process.myPid())
            .putString("identity",identity).putString("lifetime",state.context.lifetimeId).putString("document",state.context.documentId)
            .putLong("controlEpoch",state.context.controlEpoch).putLong("viewportEpoch",state.context.viewportEpoch)
            .putLong("capture",peer.lastFrameHeader!!.captureTsMs).putInt("width",profile.width).putInt("height",profile.height)
            .putInt("density",profile.densityDpi).putLong("ordinal",checkNotNull(peer.reservationState().ordinal)).commit())
        assertArrayEquals(originalTrust,trustFile.readBytes())
        Log.i("EyeBrowseRgRecovery","RG_PREPARED mission=$mission pid=${Process.myPid()} owner=RG")
    }

    @Test fun actualNewRgProcessReconcilesExistingHostAndOwner() {
        assertTrue(receipt.contains("processStart"))
        assertNotEquals(receipt.getLong("processStart",-1),Process.getStartElapsedRealtime())
        assertEquals(receipt.getString("identity",null),fingerprint())
        val takeover=args.getString("restartOwner","rg")=="phone"
        val originalTrust=trustFile.readBytes()
        phase.writeText("verifying")
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        lateinit var peer:RgPresentationController
        cleanupActions += { scenario.close() }
        cleanupActions += { phase.delete();assertFalse(phase.exists()) }
        cleanupActions += { assertTrue(app.deleteSharedPreferences("i11-rg-$mission")) }
        val start=SystemClock.elapsedRealtime()
        scenario.onActivity { peer=it.presentation;assertFalse(peer.canAct());it.findViewById<Button>(R.id.rg_retry).performClick() }
        await("reconciled owner") { peer.canHandoff() && peer.browserState()?.let {
            it.context.viewportEpoch>receipt.getLong("viewportEpoch",-1) && it.owner==(if(takeover)ControlOwner.PHONE else ControlOwner.RG)
        }==true }
        val state=peer.browserState()!!
        assertEquals(receipt.getString("lifetime",null),state.context.lifetimeId)
        assertEquals(receipt.getString("document",null),state.context.documentId)
        if(takeover) {
            assertTrue(state.context.controlEpoch>receipt.getLong("controlEpoch",-1))
            assertFalse(peer.canAct());assertNull(peer.activateAt(10f,10f))
        } else {
            assertEquals(receipt.getLong("controlEpoch",-1),state.context.controlEpoch)
            await("current frame after restart",2_000) { peer.canAct() }
            assertEquals(peer.browserState()!!.context,peer.lastFrameHeader!!.context)
            assertTrue(peer.lastFrameHeader!!.captureTsMs>receipt.getLong("capture",-1))
            assertEquals(receipt.getInt("width",-1),peer.profile()!!.width)
            assertEquals(receipt.getInt("height",-1),peer.profile()!!.height)
            assertEquals(receipt.getInt("density",-1),peer.profile()!!.densityDpi)
            assertTrue("reserved ordinals cannot repeat across process restart",checkNotNull(peer.reservationState().ordinal)>receipt.getLong("ordinal",-1))
        }
        assertArrayEquals(originalTrust,trustFile.readBytes())
        await("Phone observed the reconciled live session") { phase.readText().trim()=="reconciled" }
        Log.i("EyeBrowseRgRecovery","RG_RESTART_PASS mission=$mission pid=${Process.myPid()} takeover=$takeover readyMs=${SystemClock.elapsedRealtime()-start}")
    }

    @Test fun differentPeerAtPreviouslyAuthenticatedTestLocatorIsRejected() {
        val originalTrust=trustFile.readBytes()
        val record=(canonical.read() as PeerTrustRead.Valid).record
        val identity=fingerprint()
        val file=File(app.cacheDir,"i11-rg-$mission.trust")
        val store=RgPairingStore(file)
        val positive=Events();val first=RgLinkClient(RgLinkIdentity(),store,positive)
        val negative=Events();val second=RgLinkClient(RgLinkIdentity(),store,negative)
        phase.writeText("starting")
        cleanupActions += { first.disconnect() }
        cleanupActions += { second.disconnect() }
        cleanupActions += { store.clear();assertFalse(file.exists()) }
        cleanupActions += { File(file.path+".tmp").delete();assertFalse(File(file.path+".tmp").exists()) }
        cleanupActions += { phase.delete();assertFalse(phase.exists()) }
        store.save(record.copy(lastLocators=listOf("127.0.0.1:27343")))
        first.reconnect()
        await("real Phone positive authentication") { positive.states.contains(PairingState.CONNECTED) }
        assertEquals(HostStatusValue.HOST_INACTIVE,positive.statuses.poll(2,TimeUnit.SECONDS))
        val remembered=file.readBytes()
        phase.writeText("positive")
        await("Phone observed positive link") { phase.readText().trim()=="disconnect" }
        first.disconnect();await("positive client settled",2_000) { idle(first) }
        phase.writeText("disconnected")
        await("owned alias switched") { phase.readText().trim()=="swapped" }
        second.reconnect()
        assertEquals(LinkError.WrongPhoneIdentity,negative.failures.poll(10,TimeUnit.SECONDS))
        assertFalse(negative.states.contains(PairingState.CONNECTED));assertTrue(negative.statuses.isEmpty())
        assertNull(negative.browser);assertEquals(0,negative.frames)
        assertArrayEquals(remembered,file.readBytes());assertArrayEquals(originalTrust,trustFile.readBytes())
        assertEquals(identity,fingerprint())
        Log.i("EyeBrowseRgRecovery","WRONG_PEER_PASS mission=$mission positiveAuthenticated=true sameSavedLocator=true rejectedBeforeStatus=true canonicalUnchanged=true")
    }

    @Test fun nativeForgetRevokesOnlyLocalCanonicalTrust() {
        val original=(canonical.read() as PeerTrustRead.Valid).record
        val identity=fingerprint()
        assertTrue(receipt.edit().putString("identity",identity).putString("phonePin",original.peerSpkiSha256Hex).commit())
        val online=args.getString("forgetMode","online")=="online"
        val scenario=ActivityScenario.launch(RgPairingActivity::class.java)
        cleanupActions += { scenario.close() }
        if(online) {
            scenario.onActivity { it.findViewById<Button>(R.id.rg_button_retry).performClick() }
            await("native authenticated host status") {
                var connected=false
                scenario.onActivity { connected=it.findViewById<TextView>(R.id.rg_pairing_status).text.contains("HOST_INACTIVE") }
                connected
            }
        }
        scenario.onActivity {
            it.findViewById<Button>(R.id.rg_button_forget).performClick()
            assertEquals(RgPairingActivity.FORGOTTEN_NOTE,it.findViewById<TextView>(R.id.rg_pairing_status).text.toString())
        }
        assertSame(PeerTrustRead.Absent,canonical.read());assertFalse(trustFile.exists())
        val events=Events();val probe=RgLinkClient(RgLinkIdentity(),canonical,events)
        try {
            probe.reconnect()
            assertEquals(LinkError.InvitationInvalid,events.failures.poll(1,TimeUnit.SECONDS))
            assertTrue(events.states.isEmpty());assertTrue(events.statuses.isEmpty())
        } finally { probe.disconnect() }
        assertEquals(identity,fingerprint())
        Log.i("EyeBrowseRgRecovery","FORGET_PASS mission=$mission online=$online canonicalAbsent=true identityPreserved=true")
    }

    @Test fun freshPrivateQrRepairsCanonicalPairingWithoutIdentityReplacement() {
        assertSame(PeerTrustRead.Absent,canonical.read())
        val qr=File(app.cacheDir,"i11-rg-$mission.png")
        val bitmap=checkNotNull(BitmapFactory.decodeFile(qr.absolutePath)) { "private mission QR missing" }
        val events=Events();val client=RgLinkClient(RgLinkIdentity(),canonical,events)
        cleanupActions += { client.disconnect() }
        cleanupActions += { bitmap.recycle() }
        cleanupActions += { qr.delete();assertFalse(qr.exists()) }
        val payload=checkNotNull(QrDecoder.decode(bitmap)) { "production QR decoder failed" }
        client.pairFromQr(payload)
        await("fresh authenticated pair") { events.states.contains(PairingState.CONNECTED) }
        assertEquals(HostStatusValue.HOST_INACTIVE,events.statuses.poll(2,TimeUnit.SECONDS))
        await("authoritative Phone ownership",2_000) { events.browser?.owner==ControlOwner.PHONE }
        assertEquals(receipt.getString("identity",null),fingerprint())
        val repaired=(canonical.read() as PeerTrustRead.Valid).record
        assertEquals(receipt.getString("phonePin",null),repaired.peerSpkiSha256Hex)
        Log.i("EyeBrowseRgRecovery","REPAIR_PASS mission=$mission authenticated=true identityPreserved=true owner=PHONE")
        assertTrue(app.deleteSharedPreferences("i11-rg-$mission"))
    }
}
