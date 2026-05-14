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
    fun `XrpcError with MessagesDisabled token maps to ChatError MessagesDisabled`() {
        // Wire shape observed end-to-end via the Profile->Message routing path
        // (nubecita-a7a): "MessagesDisabled: recipient has disabled incoming messages".
        val xrpc =
            XrpcError.Unknown(
                name = "InvalidRequest",
                message = "MessagesDisabled: recipient has disabled incoming messages",
                status = 400,
            )
        val result = xrpc.toChatError()
        assertEquals(ChatError.MessagesDisabled, result)
    }

    @Test
    fun `XrpcError with NotFollowedBySender token maps to ChatError MessagesDisabled`() {
        // Same user-facing outcome as MessagesDisabled. Fires when the recipient
        // accepts follows-only DMs and the chat appview's view of the follow
        // graph is stale relative to ProfileViewDetailed.viewer.followedBy
        // (so canMessage was true but the convo-open call still rejects).
        val xrpc =
            XrpcError.Unknown(
                name = "InvalidRequest",
                message = "NotFollowedBySender: recipient requires incoming messages to come from someone they follow",
                status = 400,
            )
        val result = xrpc.toChatError()
        assertEquals(ChatError.MessagesDisabled, result)
    }

    @Test
    fun `XrpcError with unrecognized message maps to ChatError Unknown`() {
        val xrpc = XrpcError.Unknown(name = "ServerError", message = "weird server-side error", status = 500)
        val result = xrpc.toChatError()
        assertTrue(result is ChatError.Unknown)
    }
}
