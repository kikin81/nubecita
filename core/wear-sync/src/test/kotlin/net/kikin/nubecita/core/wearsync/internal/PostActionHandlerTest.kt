package net.kikin.nubecita.core.wearsync.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.wearcontract.WearAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostActionHandlerTest {
    private val post = "at://did:plc:bob/app.bsky.feed.post/3k"
    private val ref = StrongRef(uri = AtUri(post), cid = Cid("cid"))
    private val likeUri = "at://did:plc:alice/app.bsky.feed.like/1"
    private val repostUri = "at://did:plc:alice/app.bsky.feed.repost/1"
    private val likes = mockk<LikeRepostRepository>()
    private val bookmarks = mockk<BookmarkRepository>()
    private val handler = PostActionHandler(likes, bookmarks)

    @Test
    fun like_and_repost_returnTheRecordTheyCreated() =
        runTest {
            coEvery { likes.like(ref) } returns Result.success(AtUri(likeUri))
            coEvery { likes.repost(ref) } returns Result.success(AtUri(repostUri))

            assertEquals(Result.success(likeUri), handler.execute(WearAction.Like(post, "cid")))
            assertEquals(Result.success(repostUri), handler.execute(WearAction.Repost(post, "cid")))
        }

    @Test
    fun undos_deleteTheGivenRecord_andReturnNoRecord() =
        runTest {
            coEvery { likes.unlike(AtUri(likeUri)) } returns Result.success(Unit)
            coEvery { likes.unrepost(AtUri(repostUri)) } returns Result.success(Unit)

            assertEquals(Result.success(null), handler.execute(WearAction.Unlike(post, likeUri)))
            assertEquals(Result.success(null), handler.execute(WearAction.Unrepost(post, repostUri)))
            coVerify { likes.unlike(AtUri(likeUri)) }
            coVerify { likes.unrepost(AtUri(repostUri)) }
            confirmVerified(likes)
        }

    @Test
    fun bookmark_and_unbookmark_passTheStrongRef() =
        runTest {
            coEvery { bookmarks.bookmark(ref) } returns Result.success(Unit)
            coEvery { bookmarks.unbookmark(ref) } returns Result.success(Unit)

            assertEquals(Result.success(null), handler.execute(WearAction.Bookmark(post, "cid")))
            assertEquals(Result.success(null), handler.execute(WearAction.Unbookmark(post, "cid")))
            coVerify { bookmarks.bookmark(ref) }
            coVerify { bookmarks.unbookmark(ref) }
        }

    @Test
    fun repositoryFailure_isPassedThrough() =
        runTest {
            coEvery { likes.like(ref) } returns Result.failure(IllegalStateException("post deleted"))

            assertTrue(handler.execute(WearAction.Like(post, "cid")).isFailure)
        }

    @Test
    fun handlesEveryPostAction() {
        listOf(
            WearAction.Like(post, "cid"),
            WearAction.Unlike(post, likeUri),
            WearAction.Repost(post, "cid"),
            WearAction.Unrepost(post, repostUri),
            WearAction.Bookmark(post, "cid"),
            WearAction.Unbookmark(post, "cid"),
        ).forEach { assertTrue(handler.canHandle(it), it.toString()) }
    }
}
