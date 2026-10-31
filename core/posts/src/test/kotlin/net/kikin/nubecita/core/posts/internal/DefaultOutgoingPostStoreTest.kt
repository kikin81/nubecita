package net.kikin.nubecita.core.posts.internal

import android.net.Uri
import io.github.kikin81.atproto.runtime.AtUri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.OutgoingPost
import net.kikin.nubecita.core.posting.OutgoingPostStatus
import net.kikin.nubecita.core.posting.OutgoingPostStore
import net.kikin.nubecita.core.posting.PostDraft
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.SharedMediaStore
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.profile.CurrentAccount
import net.kikin.nubecita.core.profile.CurrentAccountRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultOutgoingPostStoreTest {
    private val did = "did:plc:me"
    private val postingRepository = mockk<PostingRepository>()
    private val postRepository = mockk<PostRepository>()
    private val sharedMediaStore = mockk<SharedMediaStore>(relaxed = true)
    private val postInteractionsCache = mockk<PostInteractionsCache>(relaxed = true)

    // Seeded with the handle, as the real repository is before the profile loads.
    private val accountFlow =
        MutableStateFlow<CurrentAccount?>(CurrentAccount(did = did, handle = "me.test", displayName = "me.test", avatarUrl = null))
    private val currentAccountRepository =
        mockk<CurrentAccountRepository> {
            every { account } returns accountFlow
        }
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    @Test
    fun `submit shows a sending local post at the top, then marks it sent with the real uri`() =
        runTest {
            val created = CompletableDeferred<Result<AtUri>>()
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers { created.await() }
            noProfile()
            noServerCopy()
            val store = store()

            val localId = store.submit(draft("hello"))
            runCurrent()

            val sending = store.only()
            assertEquals(localId, sending.localId)
            assertEquals(OutgoingPostStatus.Sending, sending.status)
            assertEquals("hello", sending.post.text)
            assertEquals(now, sending.post.createdAt)
            assertTrue(sending.post.viewer.isOwnPost)
            assertEquals("me.test", sending.post.author.handle)

            created.complete(Result.success(AtUri(POST_URI)))
            advanceUntilIdle()

            val sent = store.only()
            assertEquals(OutgoingPostStatus.Sent(POST_URI), sent.status)
            assertEquals(POST_URI, sent.post.id)
            assertEquals(localId, sent.localId)
        }

    @Test
    fun `a reply counts on its parent right away and is uncounted if discarded after failing`() =
        runTest {
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Result.failure(ComposerError.Network(RuntimeException()))
            noProfile()
            val store = store()

            val localId = store.submit(draft("reply").copy(replyToUri = PARENT_URI))
            verify { postInteractionsCache.noteReplySent(PARENT_URI) }
            advanceUntilIdle()
            store.discard(localId)

            verify { postInteractionsCache.noteReplyDiscarded(PARENT_URI) }
        }

    @Test
    fun `newest submit comes first`() =
        runTest {
            createSucceeds()
            noProfile()
            noServerCopy()
            val store = store()

            store.submit(draft("first"))
            store.submit(draft("second"))

            assertEquals(listOf("second", "first"), store.posts.value.map { it.post.text })
        }

    @Test
    fun `a failed send is kept with its error and can be retried`() =
        runTest {
            val error = ComposerError.Network(RuntimeException("offline"))
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(error)
            noProfile()
            noServerCopy()
            val store = store()

            val localId = store.submit(draft("hello"))
            advanceUntilIdle()
            assertEquals(OutgoingPostStatus.Failed(error), store.only().status)

            createSucceeds()
            store.retry(localId)
            assertEquals(OutgoingPostStatus.Sending, store.only().status)
            advanceUntilIdle()

            assertEquals(OutgoingPostStatus.Sent(POST_URI), store.only().status)
        }

    @Test
    fun `an untyped failure becomes RecordCreationFailed`() =
        runTest {
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Result.failure(IllegalStateException("boom"))
            noProfile()
            val store = store()

            store.submit(draft("hello"))
            advanceUntilIdle()

            val status = store.only().status as OutgoingPostStatus.Failed
            assertTrue(status.error is ComposerError.RecordCreationFailed)
        }

    @Test
    fun `discard drops a failed post and deletes its owned media copies`() =
        runTest {
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                Result.failure(ComposerError.Network(RuntimeException()))
            noProfile()
            val copy = mockk<Uri>()
            val store = store()

            val localId = store.submit(draft("hello").copy(ownedCopyUris = listOf(copy)))
            advanceUntilIdle()
            store.discard(localId)
            advanceUntilIdle()

            assertTrue(store.posts.value.isEmpty())
            coVerify { sharedMediaStore.delete(copy) }
        }

    @Test
    fun `discard leaves a sent post in place`() =
        runTest {
            createSucceeds()
            noProfile()
            noServerCopy()
            val store = store()

            val localId = store.submit(draft("hello"))
            advanceUntilIdle()
            store.discard(localId)

            assertEquals(1, store.posts.value.size)
        }

    @Test
    fun `a sent post deletes its owned media copies`() =
        runTest {
            createSucceeds()
            noProfile()
            noServerCopy()
            val copy = mockk<Uri>()
            val store = store()

            store.submit(draft("hello").copy(ownedCopyUris = listOf(copy)))
            advanceUntilIdle()

            coVerify { sharedMediaStore.delete(copy) }
        }

    @Test
    fun `the server copy replaces the local post once the AppView has indexed it`() =
        runTest {
            createSucceeds()
            noProfile()
            val server = serverPost()
            // The AppView hasn't indexed the record on the first read.
            coEvery { postRepository.getPost(POST_URI) } returnsMany
                listOf(Result.failure(RuntimeException("not found")), Result.success(server))
            val store = store()

            store.submit(draft("hello"))
            advanceUntilIdle()

            assertEquals(server, store.only().post)
            coVerify(exactly = 2) { postRepository.getPost(POST_URI) }
        }

    @Test
    fun `a server copy of a different post is ignored`() =
        runTest {
            createSucceeds()
            noProfile()
            coEvery { postRepository.getPost(POST_URI) } returns Result.success(serverPost().copy(id = "at://someone/else"))
            val store = store()

            store.submit(draft("hello"))
            advanceUntilIdle()

            val post = store.only().post
            assertEquals(POST_URI, post.id)
            assertEquals("", post.cid)
        }

    @Test
    fun `the local post picks up the viewer's avatar and display name`() =
        runTest {
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers
                { CompletableDeferred<Result<AtUri>>().await() }
            // The shared account was enriched at sign-in, before the post is written.
            accountFlow.value = CurrentAccount(did = did, handle = "me.test", displayName = "Me", avatarUrl = "https://cdn/avatar")
            val store = store(backgroundScope)

            store.submit(draft("hello"))
            runCurrent()

            val author = store.only().post.author
            assertEquals("Me", author.displayName)
            assertEquals("https://cdn/avatar", author.avatarUrl)
        }

    @Test
    fun `images render from their local uris while sending`() =
        runTest {
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers
                { CompletableDeferred<Result<AtUri>>().await() }
            noProfile()
            val image = mockk<Uri> { every { this@mockk.toString() } returns "content://media/1" }
            val store = store(backgroundScope)

            store.submit(draft("hello").copy(attachments = listOf(ComposerAttachment(uri = image, mimeType = "image/jpeg", alt = "a cat"))))
            runCurrent()

            val embed = store.only().post.embed as EmbedUi.Images
            assertEquals("content://media/1", embed.items.single().fullsizeUrl)
            assertEquals("a cat", embed.items.single().altText)
        }

    @Test
    fun `only the newest sent posts are kept`() =
        runTest {
            var n = 0
            coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } answers
                { Result.success(AtUri("at://$did/app.bsky.feed.post/${n++}")) }
            noProfile()
            coEvery { postRepository.getPost(any()) } returns Result.failure(RuntimeException())
            val store = store()

            repeat(OutgoingPostStore.MAX_SENT + 3) {
                store.submit(draft("post $it"))
                advanceUntilIdle()
            }

            assertEquals(OutgoingPostStore.MAX_SENT, store.posts.value.size)
            assertEquals(
                "post ${OutgoingPostStore.MAX_SENT + 2}",
                store.posts.value
                    .first()
                    .post.text,
            )
        }

    /** [scope] is the test scope; pass `backgroundScope` when a send never completes. */
    private fun TestScope.store(scope: CoroutineScope = this) =
        DefaultOutgoingPostStore(
            postingRepository = postingRepository,
            postRepository = postRepository,
            currentAccountRepository = currentAccountRepository,
            sharedMediaStore = sharedMediaStore,
            postInteractionsCache = postInteractionsCache,
            scope = scope,
            clock = clock,
        )

    private fun OutgoingPostStore.only(): OutgoingPost = posts.value.single()

    private fun createSucceeds() {
        coEvery { postingRepository.createPost(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(AtUri(POST_URI))
    }

    /** The account stays at its handle-only seed (profile never loaded). */
    private fun noProfile() = Unit

    private fun noServerCopy() {
        coEvery { postRepository.getPost(any()) } returns Result.failure(RuntimeException("not indexed"))
    }

    private fun draft(text: String) = PostDraft(text = text, attachments = emptyList(), replyTo = null, replyToUri = null)

    private fun serverPost() =
        PostUi(
            id = POST_URI,
            cid = "bafyserver",
            author = AuthorUi(did = did, handle = "me.test", displayName = "Me", avatarUrl = null),
            createdAt = now,
            text = "hello",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(isOwnPost = true),
            repostedBy = null,
        )

    private companion object {
        const val POST_URI = "at://did:plc:me/app.bsky.feed.post/3abc"
        const val PARENT_URI = "at://did:plc:alice/app.bsky.feed.post/parent"
    }
}
