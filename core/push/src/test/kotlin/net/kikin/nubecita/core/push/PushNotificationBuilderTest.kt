package net.kikin.nubecita.core.push

import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.deepLinkFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.groupKeyFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.notifyIdFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.summaryNotifyIdFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PushNotificationBuilderTest {
    @Test
    fun `notifyIdFor returns payload uri hashCode`() {
        val payload = likePayload(uri = "at://did:plc:alice/app.bsky.feed.like/3kabc")

        assertEquals("at://did:plc:alice/app.bsky.feed.like/3kabc".hashCode(), notifyIdFor(payload))
    }

    @Test
    fun `notifyIdFor differs between payloads with distinct uris so they stack in the shade`() {
        val first = likePayload(uri = "at://did:plc:alice/app.bsky.feed.like/aaa")
        val second = likePayload(uri = "at://did:plc:alice/app.bsky.feed.like/bbb")

        assertNotEquals(notifyIdFor(first), notifyIdFor(second))
    }

    @Test
    fun `summaryNotifyIdFor is stable across calls so the summary updates in place`() {
        assertEquals(
            summaryNotifyIdFor(PushPayload.Reason.Like),
            summaryNotifyIdFor(PushPayload.Reason.Like),
        )
    }

    @Test
    fun `summaryNotifyIdFor differs across reasons so per-reason summaries don't collide`() {
        assertNotEquals(
            summaryNotifyIdFor(PushPayload.Reason.Like),
            summaryNotifyIdFor(PushPayload.Reason.Reply),
        )
    }

    @Test
    fun `groupKeyFor uses the wire-reason channel id so individual + summary share a group`() {
        assertEquals("nubecita:${NotificationChannelInstaller.CHANNEL_LIKE}", groupKeyFor(PushPayload.Reason.Like))
        assertEquals("nubecita:${NotificationChannelInstaller.CHANNEL_VERIFIED}", groupKeyFor(PushPayload.Reason.Verified))
    }

    @Test
    fun `deepLinkFor a like uses the subject AT-URI (the post being liked) over the action AT-URI`() {
        val payload =
            likePayload(
                uri = "at://did:plc:alice/app.bsky.feed.like/3kabc",
                subject = "at://did:plc:bob/app.bsky.feed.post/3kxyz",
            )

        assertEquals("nubecita://profile/did:plc:bob/post/3kxyz", deepLinkFor(payload))
    }

    @Test
    fun `deepLinkFor a follow falls back to the uri AT-URI (follow record itself)`() {
        val payload =
            PushPayload(
                reason = PushPayload.Reason.Follow,
                uri = "at://did:plc:alice/app.bsky.graph.follow/3kabc",
                subject = null,
                actorDid = "did:plc:alice",
                actorHandle = "alice.bsky.social",
                actorDisplayName = "Alice",
                recipientDid = "did:plc:bob",
            )

        assertEquals("nubecita://profile/did:plc:alice", deepLinkFor(payload))
    }

    @Test
    fun `deepLinkFor a verification routes to recipientDid not the verifier in the uri authority`() {
        val payload =
            PushPayload(
                reason = PushPayload.Reason.Verified,
                uri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.graph.verification/3kvrf",
                subject = null,
                actorDid = "did:plc:z72i7hdynmk6r22z27h6tvur",
                actorHandle = "bsky.app",
                actorDisplayName = "Bluesky",
                recipientDid = "did:plc:alice",
            )

        assertEquals("nubecita://profile/did:plc:alice", deepLinkFor(payload))
    }

    @Test
    fun `deepLinkFor a malformed AT-URI returns null so the notification posts without a tap intent`() {
        val payload = likePayload(uri = "not-an-at-uri", subject = "also-not-an-at-uri")

        assertNull(deepLinkFor(payload))
    }

    private fun likePayload(
        uri: String,
        subject: String? = null,
    ): PushPayload =
        PushPayload(
            reason = PushPayload.Reason.Like,
            uri = uri,
            subject = subject,
            actorDid = "did:plc:alice",
            actorHandle = "alice.bsky.social",
            actorDisplayName = "Alice",
            recipientDid = "did:plc:bob",
        )
}
