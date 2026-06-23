package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.feature.chats.impl.ChatError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class JoinErrorMappingTest {
    private fun xrpc(name: String) = XrpcError(errorName = name, errorMessage = "", status = 400)

    @Test
    fun `invalid code and disabled link map to InvalidInviteLink`() {
        assertEquals(ChatError.InvalidInviteLink, xrpc("InvalidCode").toJoinError())
        assertEquals(ChatError.InvalidInviteLink, xrpc("LinkDisabled").toJoinError())
    }

    @Test
    fun `member limit maps to GroupFull`() {
        assertEquals(ChatError.GroupFull, xrpc("MemberLimitReached").toJoinError())
    }

    @Test
    fun `follow required maps to FollowRequiredToJoin`() {
        assertEquals(ChatError.FollowRequiredToJoin, xrpc("FollowRequired").toJoinError())
    }

    @Test
    fun `user kicked maps to CannotRejoin`() {
        assertEquals(ChatError.CannotRejoin, xrpc("UserKicked").toJoinError())
    }

    @Test
    fun `unknown error name falls through to Unknown`() {
        assertEquals(ChatError.Unknown::class, xrpc("ConvoLocked").toJoinError()::class)
    }

    @Test
    fun `io exception maps to Network`() {
        assertEquals(ChatError.Network, IOException("x").toJoinError())
    }
}
