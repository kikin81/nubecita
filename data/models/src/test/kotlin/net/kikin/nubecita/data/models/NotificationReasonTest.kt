package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationReasonTest {
    @Test
    fun `fromWireValue translates every documented knownValue`() {
        val expected =
            mapOf(
                "like" to NotificationReason.Like,
                "repost" to NotificationReason.Repost,
                "follow" to NotificationReason.Follow,
                "mention" to NotificationReason.Mention,
                "reply" to NotificationReason.Reply,
                "quote" to NotificationReason.Quote,
                "starterpack-joined" to NotificationReason.StarterpackJoined,
                "verified" to NotificationReason.Verified,
                "unverified" to NotificationReason.Unverified,
                "like-via-repost" to NotificationReason.LikeViaRepost,
                "repost-via-repost" to NotificationReason.RepostViaRepost,
                "subscribed-post" to NotificationReason.SubscribedPost,
                "contact-match" to NotificationReason.ContactMatch,
            )
        expected.forEach { (wire, enum) ->
            assertEquals(enum, NotificationReason.fromWireValue(wire), "wire value '$wire'")
        }
    }

    @Test
    fun `fromWireValue maps unrecognized strings to Unknown`() {
        assertEquals(NotificationReason.Unknown, NotificationReason.fromWireValue("future-reason"))
        assertEquals(NotificationReason.Unknown, NotificationReason.fromWireValue(""))
        assertEquals(NotificationReason.Unknown, NotificationReason.fromWireValue("LIKE"))
    }

    @Test
    fun `enum size includes Unknown and every wire reason`() {
        // 13 documented knownValues + Unknown = 14
        assertEquals(14, NotificationReason.entries.size)
    }
}
