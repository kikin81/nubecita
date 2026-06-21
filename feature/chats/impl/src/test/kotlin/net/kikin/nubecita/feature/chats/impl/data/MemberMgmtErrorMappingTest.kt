package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.feature.chats.impl.ChatError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemberMgmtErrorMappingTest {
    private fun xrpc(name: String) = XrpcError(errorName = name, errorMessage = "$name: detail", status = 400)

    @Test fun `member limit reached maps to GroupFull`() = assertEquals(ChatError.GroupFull, xrpc("MemberLimitReached").toMemberMgmtError())

    @Test fun `not followed by sender maps to FollowRequiredToAdd`() = assertEquals(ChatError.FollowRequiredToAdd, xrpc("NotFollowedBySender").toMemberMgmtError())

    @Test fun `insufficient role maps to InsufficientPermission`() = assertEquals(ChatError.InsufficientPermission, xrpc("InsufficientRole").toMemberMgmtError())

    @Test fun `unrecognised falls through to toChatError Unknown`() = assertTrue(xrpc("SomethingElse").toMemberMgmtError() is ChatError.Unknown)
}
