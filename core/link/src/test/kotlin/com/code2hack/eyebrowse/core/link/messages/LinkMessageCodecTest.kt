package com.code2hack.eyebrowse.core.link.messages

import com.code2hack.eyebrowse.core.link.HostStatusValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan §10-B: versioned message codec, unknown-field/type tolerance. */
class LinkMessageCodecTest {

    @Test
    fun `hello roundtrip`() {
        val hello = HelloMessage(1, 0, listOf("PAIRING_V1", "STATUS_V1"))
        val decoded = LinkMessageCodec.decode(LinkMessageCodec.encode(hello)).getOrThrow()
        assertEquals(hello, (decoded as LinkMessageCodec.Incoming.Known).message)
        assertTrue(hello.hasRequiredCapabilities())
    }

    @Test
    fun `pair auth roundtrip keeps base64url byte fields intact`() {
        val message = PairAuthMessage(
            iid = "iid",
            sec = "sec-b64",
            rgSpki = "c3BraQ",
            nonce = "bm9uY2U",
            sig = "c2ln",
            hello = HelloMessage(1, 0, listOf("PAIRING_V1", "STATUS_V1")),
        )
        val decoded = LinkMessageCodec.decode(LinkMessageCodec.encode(message)).getOrThrow()
        assertEquals(message, (decoded as LinkMessageCodec.Incoming.Known).message)
    }

    @Test
    fun `reconnect auth challenge status autherr roundtrip`() {
        for (message in listOf<Any>(
            ReconnectAuthMessage("c3BraQ", "bg", "cw", HelloMessage(1, 0, listOf("PAIRING_V1", "STATUS_V1"))),
            ChallengeMessage("nonce-b64"),
            StatusMessage.of(HostStatusValue.HOST_INACTIVE),
            StatusMessage.of(HostStatusValue.HOSTING),
            AuthErrMessage("INVITATION_EXPIRED"),
        )) {
            val decoded = LinkMessageCodec.decode(LinkMessageCodec.encode(message)).getOrThrow()
            assertEquals(message, (decoded as LinkMessageCodec.Incoming.Known).message)
        }
    }

    @Test
    fun `singleton messages encode as empty-bodied envelopes`() {
        for (message in listOf<Any>(AuthOkMessage, PingMessage, PongMessage, ForgetNoticeMessage)) {
            val decoded = LinkMessageCodec.decode(LinkMessageCodec.encode(message)).getOrThrow()
            assertEquals(message::class, (decoded as LinkMessageCodec.Incoming.Known).message::class)
        }
        val ping = String(LinkMessageCodec.encode(PingMessage), Charsets.UTF_8)
        assertEquals("""{"t":"ping"}""", ping)
    }

    @Test
    fun `unknown json fields are ignored by the v1 decoder`() {
        val payload = """{"t":"challenge","nonce":"abc","futureField":{"x":1}}"""
        val decoded = LinkMessageCodec.decode(payload.toByteArray()).getOrThrow()
        assertEquals(ChallengeMessage("abc"), (decoded as LinkMessageCodec.Incoming.Known).message)
    }

    @Test
    fun `unknown message types are reported as explicitly ignorable`() {
        val decoded = LinkMessageCodec.decode("""{"t":"future_thing","x":1}""".toByteArray()).getOrThrow()
        assertEquals(LinkMessageCodec.Incoming.Unknown("future_thing"), decoded)
    }

    @Test
    fun `malformed json is a failure not an exception escape`() {
        assertTrue(LinkMessageCodec.decode("{not json".toByteArray()).isFailure)
        assertTrue(LinkMessageCodec.decode("""{"x":1}""".toByteArray()).isFailure) // no discriminator
    }
}
