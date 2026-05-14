package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.feature.chats.impl.ChatError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

internal class ChatsErrorMappingTest {
    @Test
    fun `IOException maps to ChatError Network`() {
        val result = IOException("net down").toChatError()
        assertEquals(ChatError.Network, result)
    }

    @Test
    fun `NoSessionException maps to ChatError Unknown not-signed-in`() {
        val result = NoSessionException().toChatError()
        assertTrue(result is ChatError.Unknown)
        assertEquals("not-signed-in", (result as ChatError.Unknown).cause)
    }

    @Test
    fun `XrpcError with not-enrolled in message maps to ChatError NotEnrolled`() {
        val xrpc = XrpcError.Unknown(name = "InvalidRequest", message = "user is not enrolled in direct messaging", status = 400)
        val result = xrpc.toChatError()
        assertEquals(ChatError.NotEnrolled, result)
    }

    @Test
    fun `XrpcError mentioning convo not found maps to ChatError ConvoNotFound`() {
        val xrpc = XrpcError.Unknown(name = "InvalidRequest", message = "ConvoNotFound: no matching convo for members", status = 400)
        val result = xrpc.toChatError()
        assertEquals(ChatError.ConvoNotFound, result)
    }

    @Test
    fun `XrpcError with unrecognized message maps to ChatError Unknown`() {
        val xrpc = XrpcError.Unknown(name = "ServerError", message = "weird server-side error", status = 500)
        val result = xrpc.toChatError()
        assertTrue(result is ChatError.Unknown)
    }
}
