package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinLinkView
import io.github.kikin81.atproto.runtime.Datetime
import net.kikin.nubecita.feature.chats.impl.JoinRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class JoinLinkMapperTest {
    private fun view(
        code: String = "abc123",
        enabledStatus: String = "enabled",
        joinRule: String = "anyone",
        requireApproval: Boolean = true,
    ) = JoinLinkView(
        code = code,
        createdAt = Datetime("2026-05-13T12:00:00Z"),
        enabledStatus = enabledStatus,
        joinRule = joinRule,
        requireApproval = requireApproval,
    )

    @Test
    fun `maps url enabled and timestamp`() {
        val ui = view(code = "xyz").toJoinLinkUi()
        assertEquals("https://nubecita.app/group/join/xyz", ui.url)
        assertEquals("xyz", ui.code)
        assertEquals(true, ui.enabled)
        assertEquals(Instant.parse("2026-05-13T12:00:00Z"), ui.createdAt)
        assertEquals(true, ui.requireApproval)
    }

    @Test
    fun `disabled status maps to enabled false`() {
        assertEquals(false, view(enabledStatus = "disabled").toJoinLinkUi().enabled)
    }

    @Test
    fun `known join rules map`() {
        assertEquals(JoinRule.Anyone, view(joinRule = "anyone").toJoinLinkUi().joinRule)
        assertEquals(JoinRule.FollowedByOwner, view(joinRule = "followedByOwner").toJoinLinkUi().joinRule)
    }

    @Test
    fun `unknown join rule fails closed to Unsupported`() {
        assertEquals(JoinRule.Unsupported, view(joinRule = "adminsOnly").toJoinLinkUi().joinRule)
    }

    @Test
    fun `toWire round-trips the two supported rules`() {
        assertEquals("anyone", JoinRule.Anyone.toWire())
        assertEquals("followedByOwner", JoinRule.FollowedByOwner.toWire())
    }
}
