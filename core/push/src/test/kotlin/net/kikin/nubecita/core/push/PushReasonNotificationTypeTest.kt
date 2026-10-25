package net.kikin.nubecita.core.push

import net.kikin.nubecita.core.analytics.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PushReasonNotificationTypeTest {
    @Test
    fun `every push reason buckets into its notification type`() {
        val expected =
            mapOf(
                PushPayload.Reason.Like to NotificationType.Like,
                PushPayload.Reason.LikeViaRepost to NotificationType.Like,
                PushPayload.Reason.Repost to NotificationType.Repost,
                PushPayload.Reason.RepostViaRepost to NotificationType.Repost,
                PushPayload.Reason.Reply to NotificationType.Reply,
                PushPayload.Reason.Mention to NotificationType.Mention,
                PushPayload.Reason.Quote to NotificationType.Quote,
                PushPayload.Reason.Follow to NotificationType.Follow,
                PushPayload.Reason.Verified to NotificationType.Verification,
                PushPayload.Reason.Unverified to NotificationType.Verification,
            )
        expected.forEach { (reason, type) -> assertEquals(type, reason.toNotificationType(), "$reason") }
    }
}
