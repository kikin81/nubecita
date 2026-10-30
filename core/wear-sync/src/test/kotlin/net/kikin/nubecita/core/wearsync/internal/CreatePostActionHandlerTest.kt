package net.kikin.nubecita.core.wearsync.internal

import io.github.kikin81.atproto.runtime.AtUri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearCreatePost
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CreatePostActionHandlerTest {
    private val postUri = "at://did:plc:alice/app.bsky.feed.post/3k"
    private val posting = mockk<PostingRepository>()
    private val handler = CreatePostActionHandler(posting)

    @Test
    fun claimsOnlyCreatePost_andExpiresAfterAnHour() {
        assertTrue(handler.canHandle(WearAction.CreatePost("hi")))
        assertFalse(handler.canHandle(WearAction.DmReply("c1", "hi")))
        assertFalse(handler.canHandle(WearAction.Like("at://did:plc:bob/app.bsky.feed.post/1", "cid")))
        assertEquals(WearCreatePost.MAX_AGE_MS, handler.maxAgeMs(WearAction.CreatePost("hi")))
        assertEquals(60 * 60_000L, WearCreatePost.MAX_AGE_MS)
    }

    @Test
    fun aPost_isATextOnlyTopLevelPost_withThePhonesDefaultLangs_andReturnsItsUri() =
        runTest {
            coEvery { posting.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(AtUri(postUri))

            assertEquals(Result.success(postUri), handler.execute(WearAction.CreatePost("  Finished my first 10k @bob.test  ")))
            coVerify { posting.createPost(text = "Finished my first 10k @bob.test", attachments = emptyList(), replyTo = null, langs = null) }
        }

    @Test
    fun exactlyTheLimit_isPosted_butOneOver_isRefusedWithoutCallingBluesky() =
        runTest {
            coEvery { posting.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(AtUri(postUri))
            // One grapheme each, two UTF-16 units: the limit counts graphemes.
            val atLimit = "🏃".repeat(WearCreatePost.MAX_GRAPHEMES)

            assertTrue(handler.execute(WearAction.CreatePost(atLimit)).isSuccess)
            assertTrue(handler.execute(WearAction.CreatePost(atLimit + "!")).isFailure)
            coVerify(exactly = 1) { posting.createPost(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun blankText_isRefused() =
        runTest {
            assertTrue(handler.execute(WearAction.CreatePost("   ")).isFailure)
            coVerify(exactly = 0) { posting.createPost(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun aPostingFailure_isPassedOn() =
        runTest {
            val error = ComposerError.Network(RuntimeException("offline"))
            coEvery { posting.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(error)

            assertEquals(error, handler.execute(WearAction.CreatePost("hi")).exceptionOrNull())
        }
}
