package net.kikin.nubecita.feature.notifications.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.notification.ListNotificationsNotification
import io.github.kikin81.atproto.app.bsky.notification.ListNotificationsResponse
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import kotlinx.serialization.json.JsonObject
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.data.models.PostUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationsMapperTest {
    @Test
    fun `empty notifications page produces empty items and propagates cursor`() {
        val response = listNotificationsResponse(notifications = emptyList(), cursor = "next-cursor")

        val page = response.toNotificationsPage()

        assertTrue(page.items.isEmpty())
        assertEquals("next-cursor", page.nextCursor)
    }

    @Test
    fun `three same-subject likes collapse into one Aggregated row with three actors`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/post-1"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            actorHandle = "alice.bsky.social",
                            reason = "like",
                            reasonSubject = subject,
                            uri = "at://did:plc:alice/app.bsky.feed.like/r1",
                            indexedAt = "2026-05-26T10:00:00Z",
                        ),
                        notification(
                            actorDid = "did:plc:bob",
                            actorHandle = "bob.bsky.social",
                            reason = "like",
                            reasonSubject = subject,
                            uri = "at://did:plc:bob/app.bsky.feed.like/r2",
                            indexedAt = "2026-05-26T11:00:00Z",
                        ),
                        notification(
                            actorDid = "did:plc:carol",
                            actorHandle = "carol.bsky.social",
                            reason = "like",
                            reasonSubject = subject,
                            uri = "at://did:plc:carol/app.bsky.feed.like/r3",
                            indexedAt = "2026-05-26T12:00:00Z",
                        ),
                    ),
            )

        val page = response.toNotificationsPage()

        assertEquals(1, page.items.size)
        val row = page.items.single()
        assertTrue(row is NotificationItemUi.Aggregated, "expected Aggregated, got ${row::class.simpleName}")
        row as NotificationItemUi.Aggregated
        assertEquals(NotificationReason.Like, row.reason)
        assertEquals(3, row.actors.size)
        // Sorted by indexedAt descending: carol > bob > alice.
        assertEquals(listOf("did:plc:carol", "did:plc:bob", "did:plc:alice"), row.actors.map(AuthorUi::did))
    }

    @Test
    fun `two follows on the same day collapse into one Aggregated row`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            actorHandle = "alice.bsky.social",
                            reason = "follow",
                            reasonSubject = null,
                            uri = "at://did:plc:alice/app.bsky.graph.follow/r1",
                            indexedAt = "2026-05-26T08:00:00Z",
                        ),
                        notification(
                            actorDid = "did:plc:bob",
                            actorHandle = "bob.bsky.social",
                            reason = "follow",
                            reasonSubject = null,
                            uri = "at://did:plc:bob/app.bsky.graph.follow/r2",
                            indexedAt = "2026-05-26T20:00:00Z",
                        ),
                    ),
            )

        val page = response.toNotificationsPage()

        val row = page.items.single()
        assertTrue(row is NotificationItemUi.Aggregated)
        row as NotificationItemUi.Aggregated
        assertEquals(NotificationReason.Follow, row.reason)
        assertEquals(2, row.actors.size)
        assertNull(row.subjectPost, "follow rows have no subject post")
    }

    @Test
    fun `two follows on different days produce two Single rows`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "follow",
                            reasonSubject = null,
                            uri = "at://did:plc:alice/app.bsky.graph.follow/r1",
                            indexedAt = "2026-05-26T23:59:59Z",
                        ),
                        notification(
                            actorDid = "did:plc:bob",
                            reason = "follow",
                            reasonSubject = null,
                            uri = "at://did:plc:bob/app.bsky.graph.follow/r2",
                            indexedAt = "2026-05-27T00:00:01Z",
                        ),
                    ),
            )

        val page = response.toNotificationsPage()

        assertEquals(2, page.items.size)
        page.items.forEach { row ->
            assertTrue(row is NotificationItemUi.Single, "expected Single rows on day-boundary split")
        }
    }

    @Test
    fun `single reply renders as Single regardless of reasonSubject match`() {
        val sharedSubject = "at://did:plc:user/app.bsky.feed.post/post-1"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "reply",
                            reasonSubject = sharedSubject,
                            uri = "at://did:plc:alice/app.bsky.feed.post/reply-1",
                        ),
                    ),
            )

        val page = response.toNotificationsPage()

        val row = page.items.single()
        assertTrue(row is NotificationItemUi.Single)
        assertEquals(NotificationReason.Reply, row.reason)
    }

    @Test
    fun `two replies sharing reasonSubject still render as two Single rows`() {
        // Reason-conditional aggregation: even though both reply notifications
        // share `reasonSubject` (the user's own post), each carries unique
        // actor-authored content via its own `uri` and must remain
        // individually readable.
        val sharedSubject = "at://did:plc:user/app.bsky.feed.post/post-1"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "reply",
                            reasonSubject = sharedSubject,
                            uri = "at://did:plc:alice/app.bsky.feed.post/reply-1",
                            indexedAt = "2026-05-26T10:00:00Z",
                        ),
                        notification(
                            actorDid = "did:plc:bob",
                            reason = "reply",
                            reasonSubject = sharedSubject,
                            uri = "at://did:plc:bob/app.bsky.feed.post/reply-2",
                            indexedAt = "2026-05-26T11:00:00Z",
                        ),
                    ),
            )

        val page = response.toNotificationsPage()

        assertEquals(2, page.items.size)
        page.items.forEach { assertTrue(it is NotificationItemUi.Single) }
    }

    @Test
    fun `unknown reason value maps to Unknown enum and renders as Single`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "future-reason-not-in-known-list",
                            reasonSubject = null,
                            uri = "at://did:plc:alice/app.bsky.future.thing/r1",
                        ),
                    ),
            )

        val page = response.toNotificationsPage()

        val row = page.items.single()
        assertTrue(row is NotificationItemUi.Single)
        assertEquals(NotificationReason.Unknown, row.reason)
    }

    @Test
    fun `like row with deleted subject post emits with subjectPost null`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/deleted"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "like",
                            reasonSubject = subject,
                            uri = "at://did:plc:alice/app.bsky.feed.like/r1",
                        ),
                    ),
            )

        // hydratedPosts deliberately empty — subject was deleted.
        val page = response.toNotificationsPage(hydratedPosts = emptyMap())

        val row = page.items.single()
        assertTrue(row is NotificationItemUi.Single)
        assertNull(row.subjectPost)
    }

    @Test
    fun `like row with hydrated subject post attaches PostUi`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/p1"
        val hydrated = NotificationItemUiFixtures.singleLike().subjectPost!!
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "like",
                            reasonSubject = subject,
                            uri = "at://did:plc:alice/app.bsky.feed.like/r1",
                        ),
                    ),
            )

        val page = response.toNotificationsPage(hydratedPosts = mapOf(subject to hydrated))

        val row = page.items.single()
        assertTrue(row is NotificationItemUi.Single)
        assertEquals(hydrated, row.subjectPost)
    }

    @Test
    fun `reply row attaches subject post from notification uri, not reasonSubject`() {
        val replySubject = "at://did:plc:user/app.bsky.feed.post/being-replied-to"
        val newReplyUri = "at://did:plc:alice/app.bsky.feed.post/the-reply"
        val newReplyPost = NotificationItemUiFixtures.singleReply().subjectPost!!
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            reason = "reply",
                            reasonSubject = replySubject,
                            uri = newReplyUri,
                        ),
                    ),
            )

        // Hydration map keys on the actor's reply URI, not the user's post URI.
        val page = response.toNotificationsPage(hydratedPosts = mapOf(newReplyUri to newReplyPost))

        val row = page.items.single() as NotificationItemUi.Single
        assertEquals(newReplyPost, row.subjectPost)
    }

    @Test
    fun `aggregated row preserves all 10 actors in indexedAt-descending order`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/viral"
        val notifications =
            (1..10).map { i ->
                notification(
                    actorDid = "did:plc:actor$i",
                    actorHandle = "actor$i.bsky.social",
                    reason = "like",
                    reasonSubject = subject,
                    uri = "at://did:plc:actor$i/app.bsky.feed.like/r$i",
                    indexedAt = "2026-05-26T${"%02d".format(i)}:00:00Z",
                )
            }

        val page = listNotificationsResponse(notifications = notifications).toNotificationsPage()

        val row = page.items.single() as NotificationItemUi.Aggregated
        assertEquals(10, row.actors.size, "mapper preserves the full actor list; visual cap lives in the UI layer")
        // Newest (i=10) is first; oldest (i=1) is last.
        assertEquals("did:plc:actor10", row.actors.first().did)
        assertEquals("did:plc:actor1", row.actors.last().did)
    }

    @Test
    fun `aggregated isRead is false when any contributor is unread`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/p1"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(actorDid = "did:plc:a", reason = "like", reasonSubject = subject, uri = "at://a/like/1", isRead = true),
                        notification(actorDid = "did:plc:b", reason = "like", reasonSubject = subject, uri = "at://b/like/1", isRead = false),
                    ),
            )

        val row = response.toNotificationsPage().items.single() as NotificationItemUi.Aggregated
        assertFalse(row.isRead)
    }

    @Test
    fun `aggregated isRead is true only when every contributor is read`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/p1"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(actorDid = "did:plc:a", reason = "like", reasonSubject = subject, uri = "at://a/like/1", isRead = true),
                        notification(actorDid = "did:plc:b", reason = "like", reasonSubject = subject, uri = "at://b/like/1", isRead = true),
                    ),
            )

        val row = response.toNotificationsPage().items.single() as NotificationItemUi.Aggregated
        assertTrue(row.isRead)
    }

    @Test
    fun `like and like-via-repost do not cross-aggregate`() {
        // The mapper groups by `(reason, reasonSubject)` — distinct reasons
        // stay distinct even when the subject collides.
        val subject = "at://did:plc:user/app.bsky.feed.post/p1"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(actorDid = "did:plc:a", reason = "like", reasonSubject = subject, uri = "at://a/like/1"),
                        notification(actorDid = "did:plc:b", reason = "like-via-repost", reasonSubject = subject, uri = "at://b/like/1"),
                    ),
            )

        val page = response.toNotificationsPage()
        assertEquals(2, page.items.size)
        page.items.forEach { assertTrue(it is NotificationItemUi.Single) }
    }

    @Test
    fun `output is sorted by indexedAt descending across mixed reason rows`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(actorDid = "did:plc:a", reason = "follow", reasonSubject = null, uri = "at://a/follow/1", indexedAt = "2026-05-26T08:00:00Z"),
                        notification(actorDid = "did:plc:b", reason = "reply", reasonSubject = "at://user/post/1", uri = "at://b/post/1", indexedAt = "2026-05-26T12:00:00Z"),
                        notification(actorDid = "did:plc:c", reason = "mention", reasonSubject = "at://user/post/1", uri = "at://c/post/1", indexedAt = "2026-05-26T10:00:00Z"),
                    ),
            )

        val page = response.toNotificationsPage()
        val indexedAtList = page.items.map { it.indexedAt }
        assertEquals(indexedAtList.sortedDescending(), indexedAtList, "rows must be sorted by indexedAt descending")
    }

    @Test
    fun `nextCursor propagates from the wire response`() {
        val response = listNotificationsResponse(notifications = listOf(notification()), cursor = "page-2-cursor")
        assertEquals("page-2-cursor", response.toNotificationsPage().nextCursor)
    }

    @Test
    fun `Single itemKey uses the notification uri`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(uri = "at://did:plc:alice/app.bsky.feed.like/key123"),
                    ),
            )
        val row = response.toNotificationsPage().items.single() as NotificationItemUi.Single
        assertEquals("at://did:plc:alice/app.bsky.feed.like/key123", row.itemKey)
    }

    @Test
    fun `Aggregated itemKey is stable across multiple actors on same subject`() {
        val subject = "at://did:plc:user/app.bsky.feed.post/stable-key"
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(actorDid = "did:plc:a", reason = "like", reasonSubject = subject, uri = "at://a/like/1"),
                        notification(actorDid = "did:plc:b", reason = "like", reasonSubject = subject, uri = "at://b/like/1"),
                    ),
            )
        val row = response.toNotificationsPage().items.single() as NotificationItemUi.Aggregated
        assertEquals("agg:Like:$subject", row.itemKey)
    }

    @Test
    fun `verified row stays Single and has no subject post`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:verifier",
                            reason = "verified",
                            reasonSubject = null,
                            uri = "at://did:plc:verifier/app.bsky.graph.verification/v1",
                        ),
                    ),
            )
        val row = response.toNotificationsPage().items.single()
        assertTrue(row is NotificationItemUi.Single)
        assertEquals(NotificationReason.Verified, row.reason)
        assertNull(row.subjectPost)
    }

    @Test
    fun `actor displayName falls back to handle when blank`() {
        val response =
            listNotificationsResponse(
                notifications =
                    listOf(
                        notification(
                            actorDid = "did:plc:alice",
                            actorHandle = "alice.bsky.social",
                            actorDisplayName = "   ",
                            reason = "follow",
                            reasonSubject = null,
                            uri = "at://a/follow/1",
                        ),
                    ),
            )
        val row = response.toNotificationsPage().items.single() as NotificationItemUi.Single
        assertEquals("alice.bsky.social", row.actors.single().displayName)
    }

    // Helpers --------------------------------------------------------------

    private fun listNotificationsResponse(
        notifications: List<ListNotificationsNotification>,
        cursor: String? = null,
    ): ListNotificationsResponse = ListNotificationsResponse(cursor = cursor, notifications = notifications)

    private fun notification(
        actorDid: String = "did:plc:test-actor-default",
        actorHandle: String = "default.bsky.social",
        actorDisplayName: String? = "Default Actor",
        reason: String = "like",
        reasonSubject: String? = "at://did:plc:test-user/app.bsky.feed.post/default",
        uri: String = "at://did:plc:test-actor-default/app.bsky.feed.like/default",
        cid: String = "bafyfakefakefakefakefakefakefake",
        indexedAt: String = "2026-05-26T12:00:00Z",
        isRead: Boolean = false,
    ): ListNotificationsNotification =
        ListNotificationsNotification(
            author =
                ProfileView(
                    did = Did(actorDid),
                    handle = Handle(actorHandle),
                    displayName = actorDisplayName,
                    avatar = null,
                ),
            cid = Cid(cid),
            indexedAt = Datetime(indexedAt),
            isRead = isRead,
            reason = reason,
            reasonSubject = reasonSubject?.let(::AtUri),
            record = JsonObject(emptyMap()),
            uri = AtUri(uri),
        )
}

// Compile-time assertion the test fixtures referenced above continue to exist
// in :data:models — if these fail to resolve, the public fixture surface broke.
@Suppress("unused", "UNUSED_VARIABLE")
private fun fixturesSmokeCheck(): PostUi? {
    val placeholder: NotificationItemUi.Single = NotificationItemUiFixtures.singleLike()
    return placeholder.subjectPost
}
