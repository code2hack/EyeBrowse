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

    @Test fun nativeNetworkRetryMeasuresPostTcpAuthentication() {
        val before=trustFile.readBytes();val ownIdentity=fingerprint()
        val scenario=ActivityScenario.launch(RgPairingActivity::class.java)
        cleanupActions += { scenario.close() }
        cleanupActions += { phase.delete();assertFalse(phase.exists()) }
        lateinit var client:RgLinkClient
        scenario.onActivity { activity ->
            client=RgPairingActivity::class.java.getDeclaredField("client").apply { isAccessible=true }.get(activity) as RgLinkClient
        }
        val listenerField=RgLinkClient::class.java.getDeclaredField("listener").apply { isAccessible=true }
        val original=listenerField.get(client) as RgLinkClient.Listener
        val connectedNs=java.util.concurrent.atomic.AtomicLong()
        val protectedEvents=java.util.concurrent.atomic.AtomicInteger()
        val statuses=LinkedBlockingDeque<HostStatusValue>()
        val failures=LinkedBlockingDeque<LinkError>()
        val observer=object:RgLinkClient.Listener by original {
            override fun onStateChange(state:PairingState) {
                if(state==PairingState.CONNECTED) connectedNs.compareAndSet(0,SystemClock.elapsedRealtimeNanos())
                original.onStateChange(state)
            }
            override fun onConnectFailed(error:LinkError) { failures.add(error);original.onConnectFailed(error) }
            override fun onStatus(status:HostStatusValue) { protectedEvents.incrementAndGet();statuses.add(status);original.onStatus(status) }
            override fun onControl(message:BrowserControlMessage) { protectedEvents.incrementAndGet();original.onControl(message) }
            override fun onPresentation(frame:PresentationFrame) { protectedEvents.incrementAndGet();original.onPresentation(frame) }
        }
        listenerField.set(client,observer)
        cleanupActions += { listenerField.set(client,original) }
        val engine=RgLinkClient::class.java.getDeclaredMethod("startEngine").apply { isAccessible=true }.invoke(client) as LinkClientEngine
        val seam=LinkClientEngine::class.java.getDeclaredField("tcpConnectForTest").apply { isAccessible=true }
        val previous=seam.get(engine)
        cleanupActions += { seam.set(engine,previous) }
        val blocked=java.util.concurrent.atomic.AtomicBoolean(true)
        val blockedAttempts=java.util.concurrent.atomic.AtomicInteger()
        val successfulConnects=java.util.concurrent.atomic.AtomicInteger()
        val tcpStartNs=java.util.concurrent.atomic.AtomicLong()
        val tcpReturnNs=java.util.concurrent.atomic.AtomicLong()
        val locator=java.util.concurrent.atomic.AtomicReference<String>()
        val connect:(java.net.Socket,java.net.InetSocketAddress,Int)->Unit={socket,address,timeout ->
            if(blocked.get()) {
                blockedAttempts.incrementAndGet()
                throw java.net.ConnectException("I12 declared unavailable TCP fault")
            }
            val start=SystemClock.elapsedRealtimeNanos()
            socket.connect(address,timeout)
            tcpStartNs.set(start);tcpReturnNs.set(SystemClock.elapsedRealtimeNanos())
            locator.set("${address.address.hostAddress}:${address.port}")
            successfulConnects.incrementAndGet()
        }
        seam.set(engine,connect)
        scenario.onActivity { it.findViewById<Button>(R.id.rg_button_retry).performClick() }
        assertEquals(LinkError.NetworkUnreachable,failures.poll(10,TimeUnit.SECONDS))
        await("native unreachable prompt") {
            var shown=false
            scenario.onActivity { shown=it.findViewById<TextView>(R.id.rg_pairing_status).text.toString()==RgPairingActivity.NETWORK_NOTE }
            shown
        }
        await("failed operation quiescent",2_000) { !engine.isBusy }
        assertTrue(blockedAttempts.get()>0);assertEquals(0L,connectedNs.get())
        assertEquals(0,protectedEvents.get());assertArrayEquals(before,trustFile.readBytes())
        Log.i("EyeBrowseRgRecovery","NETWORK_NEGATIVE mission=$mission injectedTcp=true blockedAttempts=${blockedAttempts.get()} noProtectedEvents=true nativePrompt=true")
        blocked.set(false)
        var dispatchNs=0L
        scenario.onActivity { dispatchNs=SystemClock.elapsedRealtimeNanos();it.findViewById<Button>(R.id.rg_button_retry).performClick() }
        await("real authenticated Retry",10_000) { connectedNs.get()!=0L }
        val completed=connectedNs.get();val tcp=tcpReturnNs.get()
        assertEquals(1,successfulConnects.get())
        assertTrue("same-operation TCP timestamp",tcp>=dispatchNs && tcpStartNs.get()>=dispatchNs && completed>=tcp)
        assertTrue("Retry operation <=10s",completed-dispatchNs<=10_000_000_000L)
        assertTrue("post-TCP TLS/auth/commit <=5s",completed-tcp<=5_000_000_000L)
        assertEquals(HostStatusValue.HOST_INACTIVE,statuses.poll(2,TimeUnit.SECONDS))
        assertArrayEquals(before,trustFile.readBytes());assertEquals(ownIdentity,fingerprint())
        Log.i("EyeBrowseRgRecovery","TLS_STAGE mission=$mission clock=elapsedRealtimeNanos operation=1 dispatchNs=$dispatchNs tcpStartNs=${tcpStartNs.get()} tcpReturnNs=$tcp connectedNs=$completed locator=${locator.get()} operationMs=${(completed-dispatchNs)/1_000_000.0} postTcpMs=${(completed-tcp)/1_000_000.0}")
        phase.writeText("network-authenticated")
        await("Phone independently verified inactive host",10_000) { phase.readText().trim()=="network-verified" }
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
