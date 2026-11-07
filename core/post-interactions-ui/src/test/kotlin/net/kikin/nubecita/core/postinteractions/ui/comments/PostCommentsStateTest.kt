package net.kikin.nubecita.core.postinteractions.ui.comments

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class PostCommentsStateTest {
    @Test
    fun `initial state has expected defaults`() {
        val state = PostCommentsState()

        assertNull(state.postUri)
        assertEquals(PostCommentsDetent.Hidden, state.detent)
        assertEquals(PostCommentsStatus.Idle, state.status)
        assertTrue(state.comments.isEmpty())
        assertFalse(state.isRefreshing)
        assertFalse(state.isVisible)
        assertFalse(state.isEmpty)
        assertFalse(state.isLoading)
        assertFalse(state.isError)
    }

    @Test
    fun `detent transitions reflect visibility`() {
        val hidden = PostCommentsState(detent = PostCommentsDetent.Hidden)
        assertFalse(hidden.isVisible)

        val partial = PostCommentsState(detent = PostCommentsDetent.PartiallyExpanded)
        assertTrue(partial.isVisible)

        val expanded = PostCommentsState(detent = PostCommentsDetent.Expanded)
        assertTrue(expanded.isVisible)
    }

    @Test
    fun `status states compute loading, error, and empty flags`() {
        val loadingState = PostCommentsState(status = PostCommentsStatus.Loading)
        assertTrue(loadingState.isLoading)
        assertFalse(loadingState.isEmpty)
        assertFalse(loadingState.isError)

        val emptyState = PostCommentsState(status = PostCommentsStatus.Empty)
        assertTrue(emptyState.isEmpty)
        assertFalse(emptyState.isLoading)
        assertFalse(emptyState.isError)

        val loadedEmptyState =
            PostCommentsState(
                status = PostCommentsStatus.Loaded(persistentListOf()),
                comments = persistentListOf(),
            )
        assertTrue(loadedEmptyState.isEmpty)

        val errorState =
            PostCommentsState(
                status = PostCommentsStatus.Error(message = "Network timeout"),
            )
        assertTrue(errorState.isError)
        assertFalse(errorState.isLoading)
        assertFalse(errorState.isEmpty)

        val comment = sampleComment("at://reply1")
        val loadedNonEmpty =
            PostCommentsState(
                status = PostCommentsStatus.Loaded(persistentListOf(comment)),
                comments = persistentListOf(comment),
            )
        assertFalse(loadedNonEmpty.isEmpty)
        assertFalse(loadedNonEmpty.isLoading)
        assertFalse(loadedNonEmpty.isError)
    }

    @Test
    fun `ThreadItem Reply correctly projects into CommentItemUi`() {
        val post =
            samplePost(
                id = "at://did:plc:author/app.bsky.feed.post/reply1",
                cid = "bafyreply1",
                text = "Great video!",
                likeCount = 42,
                isLiked = true,
            )
        val reply = ThreadItem.Reply(post = post, depth = 2)

        val commentItem = reply.toCommentItemUi()

        assertEquals("at://did:plc:author/app.bsky.feed.post/reply1", commentItem.id)
        assertEquals("bafyreply1", commentItem.cid)
        assertEquals("Great video!", commentItem.text)
        assertEquals(42L, commentItem.likeCount)
        assertTrue(commentItem.isLiked)
        assertEquals(2, commentItem.depth)
        assertEquals(post, commentItem.post)
        assertEquals(post.author, commentItem.author)
        assertEquals(post.createdAt, commentItem.createdAt)
    }

    private fun sampleComment(id: String): CommentItemUi =
        CommentItemUi(
            id = id,
            cid = "cid_$id",
            author = AuthorUi(did = "did:plc:author", handle = "author.bsky.social", displayName = "Author", avatarUrl = null),
            text = "A comment",
            createdAt = Instant.parse("2026-10-20T12:00:00Z"),
            likeCount = 0L,
            isLiked = false,
            depth = 1,
            post = samplePost(id),
        )

    private fun samplePost(
        id: String,
        cid: String = "cid_$id",
        text: String = "Post text",
        likeCount: Int = 0,
        isLiked: Boolean = false,
    ): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author = AuthorUi(did = "did:plc:author", handle = "author.bsky.social", displayName = "Author", avatarUrl = null),
            createdAt = Instant.parse("2026-10-20T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(likeCount = likeCount),
            viewer = ViewerStateUi(isLikedByViewer = isLiked, likeUri = if (isLiked) "at://like" else null),
            repostedBy = null,
        )
}
