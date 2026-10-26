package net.kikin.nubecita.wear.data

import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostActionOverlayTest {
    private fun post(
        likeUri: String? = null,
        repostUri: String? = null,
        bookmarked: Boolean = false,
    ) = WatchPostEntity(
        accountDid = "did:plc:alice",
        feedKey = "following",
        position = 0,
        uri = "p1",
        cid = "cid",
        authorDid = "did:plc:bob",
        authorHandle = "bob",
        authorDisplayName = null,
        authorAvatarPath = null,
        text = "hi",
        createdAtMs = 1,
        replyCount = 0,
        repostCount = 3,
        likeCount = 10,
        repostedBy = null,
        likeUri = likeUri,
        repostUri = repostUri,
        bookmarked = bookmarked,
        imageCount = 0,
        isVideo = false,
        hasContentWarning = false,
        thumbnailPath = null,
    )

    private fun action(
        kind: String,
        at: Long,
        confirmed: Boolean = false,
        record: String? = null,
        completedAtMs: Long? = null,
    ) = WatchPendingActionEntity(actionId = "$kind@$at", accountDid = "did:plc:alice", postUri = "p1", kind = kind, createdAtMs = at, confirmed = confirmed, recordUri = record, completedAtMs = completedAtMs)

    @Test
    fun noActions_showsTheSyncedState() {
        val v = effectiveViewer(post(likeUri = "at://like/1", bookmarked = true), emptyList())

        assertTrue(v.liked)
        assertEquals("at://like/1", v.likeUri)
        assertFalse(v.reposted)
        assertTrue(v.bookmarked)
        assertEquals(10, v.likeCount)
        assertFalse(v.likePending || v.repostPending || v.bookmarkPending)
    }

    @Test
    fun pendingLike_showsLikedAtOnce_withTheCountUp_andIsPending() {
        val v = effectiveViewer(post(), listOf(action("like", 1)))

        assertTrue(v.liked)
        assertTrue(v.likePending)
        assertEquals(11, v.likeCount)
        assertNull(v.likeUri)
    }

    @Test
    fun confirmedLike_carriesItsRecord_soItCanBeUndone() {
        val v = effectiveViewer(post(), listOf(action("like", 1, confirmed = true, record = "at://like/new")))

        assertTrue(v.liked)
        assertFalse(v.likePending)
        assertEquals("at://like/new", v.likeUri)
    }

    @Test
    fun newestActionPerToggleWins_regardlessOfListOrder() {
        val v = effectiveViewer(post(repostUri = "at://repost/1"), listOf(action("repost", 5, confirmed = true), action("unrepost", 2, confirmed = true)))

        assertTrue(v.reposted)
        assertEquals(3, v.repostCount)
    }

    @Test
    fun undoingASyncedLike_dropsTheCountAndTheRecord() {
        val v = effectiveViewer(post(likeUri = "at://like/1"), listOf(action("unlike", 1)))

        assertFalse(v.liked)
        assertNull(v.likeUri)
        assertEquals(9, v.likeCount)
    }

    @Test
    fun togglesAreIndependent() {
        val v = effectiveViewer(post(), listOf(action("bookmark", 1)))

        assertTrue(v.bookmarked)
        assertTrue(v.bookmarkPending)
        assertFalse(v.likePending)
        assertFalse(v.liked)
    }

    @Test
    fun settled_onlyConfirmedActionsTheSnapshotShows_orStaleOnes() {
        val shown = action("like", 1, confirmed = true, completedAtMs = 100)
        val notShown = action("bookmark", 2, confirmed = true, completedAtMs = 100)
        val unanswered = action("repost", 3)
        val stale = action("unbookmark", 4, confirmed = true, completedAtMs = 100).copy(postUri = "gone")

        val ids = settledActions(listOf(post(likeUri = "at://like/1")), listOf(shown, notShown, unanswered, stale), nowMs = 100 + SETTLE_MAX_AGE_MS + 1)

        assertEquals(setOf(shown.actionId, stale.actionId, notShown.actionId), ids.toSet())
    }

    @Test
    fun settled_keepsAConfirmedActionTheSnapshotDoesNotShowYet() {
        val notShown = action("bookmark", 2, confirmed = true, completedAtMs = 100)

        assertTrue(settledActions(listOf(post()), listOf(notShown), nowMs = 200).isEmpty())
    }

    @Test
    fun settled_neverDropsAnUnansweredAction_evenWhenOld() {
        val unanswered = action("repost", 3)

        assertTrue(settledActions(emptyList(), listOf(unanswered), nowMs = Long.MAX_VALUE / 2).isEmpty())
    }
}
