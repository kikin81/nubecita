package net.kikin.nubecita.core.push

import android.content.Intent
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.TAP_INTENT_FLAGS
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.deepLinkFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.groupKeyFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.notifyIdFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.summaryNotifyIdFor
import net.kikin.nubecita.core.push.PushNotificationBuilder.Companion.tapIntentSpecFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `tapIntentSpecFor a translatable payload carries the deep-link uri, the app package, and the load-bearing flag pair`() {
        // Regression coverage for nubecita-1fy.6. NEW_TASK alone is
        // insufficient for a launchMode="singleTask" target: if a task for
        // MainActivity already exists (e.g. the user opened the app via the
        // launcher icon), Android silently delivers the task's BASE intent
        // (ACTION_MAIN, no data) on rebuild and the deep-link URI never
        // reaches `MainActivity.handleIntent`. CLEAR_TASK forces the task
        // to be destroyed and re-launched with this Intent as the new base.
        // Tested against the spec rather than the constructed Intent because
        // android.content.Intent / android.net.Uri throw "Method not mocked"
        // under the AGP unit-test stubs.
        val spec =
            tapIntentSpecFor(
                payload =
                    likePayload(
                        uri = "at://did:plc:alice/app.bsky.feed.like/3kabc",
                        subject = "at://did:plc:alice/app.bsky.feed.post/3kxyz",
                    ),
                packageName = "net.kikin.nubecita",
            )

        assertEquals("nubecita://profile/did:plc:alice/post/3kxyz", spec?.deepLinkUri)
        assertEquals("net.kikin.nubecita", spec?.packageName)
        val flags = spec?.flags ?: 0
        assertTrue(
            flags and Intent.FLAG_ACTIVITY_NEW_TASK == Intent.FLAG_ACTIVITY_NEW_TASK,
            "FLAG_ACTIVITY_NEW_TASK required for PendingIntent dispatched from system context",
        )
        assertTrue(
            flags and Intent.FLAG_ACTIVITY_CLEAR_TASK == Intent.FLAG_ACTIVITY_CLEAR_TASK,
            "FLAG_ACTIVITY_CLEAR_TASK required so a pre-existing singleTask task doesn't shadow the deep-link with its ACTION_MAIN base",
        )
    }

    @Test
    fun `TAP_INTENT_FLAGS pins the NEW_TASK plus CLEAR_TASK pair`() {
        // Belt-and-suspenders: the constant equality double-checks both bits
        // are set without relying on the spec being computed. If anyone
        // changes the constant in isolation, this catches it.
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            TAP_INTENT_FLAGS,
        )
    }

    @Test
    fun `tapIntentSpecFor returns null for a payload whose deep link cannot be translated`() {
        val payload = likePayload(uri = "not-an-at-uri", subject = "also-not-an-at-uri")

        assertNull(tapIntentSpecFor(payload, packageName = "net.kikin.nubecita"))
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
