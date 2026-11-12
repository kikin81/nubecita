package net.kikin.nubecita.wear.feed

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchFeedStateTest {
    private val post =
        WatchPostUi(
            uri = "at://did:plc:ana/app.bsky.feed.post/1",
            authorName = "Ana Ruiz",
            authorHandle = "ana.example.social",
            avatarPath = null,
            text = "Golden hour",
            createdAtMs = 0L,
            replyCount = 0,
            repostCount = 0,
            likeCount = 0,
            liked = false,
            bookmarked = false,
            repostedBy = null,
            imageCount = 0,
            isVideo = false,
            hasContentWarning = false,
            thumbnailPath = null,
        )

    @Test
    fun loading_hasNotLostThePost_evenThoughPostsAreEmpty() {
        // After process death the restored post route sees this state first.
        val restoring = WatchFeedState(status = WatchFeedStatus.Loading)

        assertFalse(restoring.hasLostPost(post.uri))
    }

    @Test
    fun ready_withThePost_hasNotLostIt() {
        val ready = WatchFeedState(status = WatchFeedStatus.Ready, posts = persistentListOf(post))

        assertFalse(ready.hasLostPost(post.uri))
    }

    @Test
    fun ready_withoutThePost_hasLostIt() {
        // A newer snapshot landed without it.
        val ready = WatchFeedState(status = WatchFeedStatus.Ready, posts = persistentListOf())

        assertTrue(ready.hasLostPost(post.uri))
    }

    @Test
    fun noFeeds_hasLostIt() {
        assertTrue(WatchFeedState(status = WatchFeedStatus.NoFeeds).hasLostPost(post.uri))
    }
}
