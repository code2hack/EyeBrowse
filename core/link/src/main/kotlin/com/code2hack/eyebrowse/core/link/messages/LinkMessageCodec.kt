package com.code2hack.eyebrowse.core.link.messages

import com.code2hack.eyebrowse.core.link.framing.LinkFrameCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * JSON encode/decode for the bounded v1 message set. Every payload is size-bounded by the frame
 * codec before decoding; malformed JSON and unknown types are reported/ignorable respectively.
 */
object LinkMessageCodec {

    private const val TYPE_FIELD: String = "t"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = TYPE_FIELD
    }

    /** Envelope for a received message; [UnknownMessage] is explicitly ignorable (minor drift). */
    sealed class Incoming {
        data class Known(val message: Any) : Incoming()
        data class Unknown(val type: String) : Incoming()
    }

    object UnknownMessage

    fun encode(message: Any): ByteArray {
        val (type, body) = when (message) {
            is HelloMessage -> "hello" to json.encodeToJsonElement(HelloMessage.serializer(), message)
            is PairAuthMessage -> "pair_auth" to json.encodeToJsonElement(PairAuthMessage.serializer(), message)
            is ReconnectAuthMessage -> "reconnect_auth" to json.encodeToJsonElement(ReconnectAuthMessage.serializer(), message)
            is ChallengeMessage -> "challenge" to json.encodeToJsonElement(ChallengeMessage.serializer(), message)
            is AuthOkMessage -> "auth_ok" to buildJsonObject { }
            is AuthErrMessage -> "auth_err" to json.encodeToJsonElement(AuthErrMessage.serializer(), message)
            is StatusMessage -> "status" to json.encodeToJsonElement(StatusMessage.serializer(), message)
            is PingMessage -> "ping" to buildJsonObject { }
            is PongMessage -> "pong" to buildJsonObject { }
            is ForgetNoticeMessage -> "forget" to buildJsonObject { }
            else -> throw IllegalArgumentException("unsupported message type ${message::class.simpleName}")
        }
        val envelope = buildJsonObject {
            put(TYPE_FIELD, type)
            for ((k, v) in (body as JsonObject)) put(k, v)
        }.let { json.encodeToString(JsonObject.serializer(), it) }
        return envelope.toByteArray(Charsets.UTF_8)
    }

    fun decode(framePayload: ByteArray): Result<Incoming> {
        return try {
            val text = LinkFrameCodec.decodeUtf8(framePayload)
            val obj = json.parseToJsonElement(text).jsonObject
            val type = (obj[TYPE_FIELD] as? JsonPrimitive)?.content
                ?: return Result.failure(IllegalArgumentException("missing type discriminator"))
            val message: Any? = when (type) {
                "hello" -> json.decodeFromJsonElement(HelloMessage.serializer(), obj)
                "pair_auth" -> json.decodeFromJsonElement(PairAuthMessage.serializer(), obj)
                "reconnect_auth" -> json.decodeFromJsonElement(ReconnectAuthMessage.serializer(), obj)
                "challenge" -> json.decodeFromJsonElement(ChallengeMessage.serializer(), obj)
                "auth_ok" -> json.decodeFromJsonElement(AuthOkMessage.serializer(), obj)
                "auth_err" -> json.decodeFromJsonElement(AuthErrMessage.serializer(), obj)
                "status" -> json.decodeFromJsonElement(StatusMessage.serializer(), obj)
                "ping" -> json.decodeFromJsonElement(PingMessage.serializer(), obj)
                "pong" -> json.decodeFromJsonElement(PongMessage.serializer(), obj)
                "forget" -> json.decodeFromJsonElement(ForgetNoticeMessage.serializer(), obj)
                else -> null
            }
            if (message == null) Result.success(Incoming.Unknown(type))
            else Result.success(Incoming.Known(message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
