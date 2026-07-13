package net.kikin.nubecita.core.postinteractions.internal

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.InteractPost
import net.kikin.nubecita.core.analytics.PostAction
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.Share
import net.kikin.nubecita.core.analytics.ShareMethod
import net.kikin.nubecita.core.postinteractions.InteractionEffect
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultPostInteractionHandlerTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val analytics = RecordingAnalyticsClient()
    private val fakeCache = FakePostInteractionsCacheForHandler()
    private val fakeMuteRepo = FakeMuteRepositoryForHandler()

    private fun makeHandler(): DefaultPostInteractionHandler =
        DefaultPostInteractionHandler(
            cache = fakeCache,
            muteRepository = fakeMuteRepo,
            analytics = analytics,
        )

    private fun unlikedPost(
        id: String = "at://did:plc:author/app.bsky.feed.post/post-1",
        cid: String = "bafy123",
        authorDid: String = "did:plc:author",
        handle: String = "alice.bsky.social",
    ): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author =
                AuthorUi(
                    did = authorDid,
                    handle = handle,
                    displayName = "Alice",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-25T12:00:00Z"),
            text = "Hello from test.",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(isLikedByViewer = false),
            repostedBy = null,
        )

    private fun likedPost(
        id: String = "at://did:plc:author/app.bsky.feed.post/post-1",
        cid: String = "bafy123",
    ): PostUi =
        unlikedPost(id = id, cid = cid).copy(
            viewer = ViewerStateUi(isLikedByViewer = true),
        )

    private fun unrepostedPost(
        id: String = "at://did:plc:author/app.bsky.feed.post/post-1",
        cid: String = "bafy123",
    ): PostUi =
        unlikedPost(id = id, cid = cid).copy(
            viewer = ViewerStateUi(isRepostedByViewer = false),
        )

    private fun repostedPost(
        id: String = "at://did:plc:author/app.bsky.feed.post/post-1",
        cid: String = "bafy123",
    ): PostUi =
        unlikedPost(id = id, cid = cid).copy(
            viewer = ViewerStateUi(isRepostedByViewer = true),
        )

    // ──────────────────────────────────────────────────────────────────────────
    // Like / Unlike
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onLike on unliked post calls toggleLike, fires Like analytics, sets tapMarker`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.onLike(post)
            advanceUntilIdle()

            assertEquals(1, fakeCache.toggleLikeCalls)
            assertEquals(post.id, fakeCache.lastToggleLikeArgs.first().first)
            assertEquals(listOf(InteractPost(PostAction.Like, PostSurface.Feed)), analytics.events)
            assertEquals(post.id, handler.tapMarkers.value.lastLikeTapPostUri)
        }

    @Test
    fun `onLike on liked post fires Unlike analytics`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.onLike(likedPost())
            advanceUntilIdle()

            assertEquals(listOf(InteractPost(PostAction.Unlike, PostSurface.Feed)), analytics.events)
        }

    @Test
    fun `onBookmark on un-bookmarked post calls toggleBookmark and fires Bookmark analytics`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost() // isBookmarked defaults to false

            handler.onBookmark(post)
            advanceUntilIdle()

            assertEquals(1, fakeCache.toggleBookmarkCalls)
            assertEquals(post.id, fakeCache.lastToggleBookmarkArgs.first().first)
            assertEquals(listOf(InteractPost(PostAction.Bookmark, PostSurface.Feed)), analytics.events)
        }

    @Test
    fun `two rapid onLike calls for same post fire toggleLike and analytics exactly once`() =
        runTest(mainDispatcher.dispatcher) {
            // Make the first toggleLike suspend until we release it
            val latch = CompletableDeferred<Unit>()
            fakeCache.toggleLikeLatch = latch

            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            // First tap — launches, suspends in cache
            handler.onLike(post)
            // Second tap arrives before latch releases (job still active)
            handler.onLike(post)

            // Release the latch so the first call can complete
            latch.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, fakeCache.toggleLikeCalls, "toggleLike MUST be called exactly once")
            assertEquals(1, analytics.events.size, "analytics MUST fire exactly once")
        }

    @Test
    fun `onLike failure emits ShowError effect`() =
        runTest(mainDispatcher.dispatcher) {
            fakeCache.nextToggleLikeResult = Result.failure(RuntimeException("oops"))
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onLike(unlikedPost())
                advanceUntilIdle()

                val effect = awaitItem()
                assert(effect is InteractionEffect.ShowError) {
                    "expected ShowError but got $effect"
                }
            }
        }

    @Test
    fun `onLike with CancellationException does not emit ShowError effect`() =
        runTest(mainDispatcher.dispatcher) {
            // CancellationException must be rethrown (cooperative cancellation),
            // not mapped to InteractionError. No ShowError should reach the
            // collector when the coroutine is cancelled mid-flight.
            fakeCache.nextToggleLikeResult = Result.failure(CancellationException("cancelled"))
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onLike(unlikedPost())
                advanceUntilIdle()

                expectNoEvents()
            }
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Repost / Unrepost
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onRepost on unreposted post calls toggleRepost, fires Repost analytics, sets tapMarker`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unrepostedPost()

            handler.onRepost(post)
            advanceUntilIdle()

            assertEquals(1, fakeCache.toggleRepostCalls)
            assertEquals(post.id, fakeCache.lastToggleRepostArgs.first().first)
            assertEquals(listOf(InteractPost(PostAction.Repost, PostSurface.Feed)), analytics.events)
            assertEquals(post.id, handler.tapMarkers.value.lastRepostTapPostUri)
        }

    @Test
    fun `onRepost on reposted post fires Unrepost analytics`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.onRepost(repostedPost())
            advanceUntilIdle()

            assertEquals(listOf(InteractPost(PostAction.Unrepost, PostSurface.Feed)), analytics.events)
        }

    @Test
    fun `two rapid onRepost calls for same post fire toggleRepost and analytics exactly once`() =
        runTest(mainDispatcher.dispatcher) {
            val latch = CompletableDeferred<Unit>()
            fakeCache.toggleRepostLatch = latch

            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unrepostedPost()

            handler.onRepost(post)
            handler.onRepost(post)

            latch.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, fakeCache.toggleRepostCalls, "toggleRepost MUST be called exactly once")
            assertEquals(1, analytics.events.size, "analytics MUST fire exactly once")
        }

    @Test
    fun `onRepost failure emits ShowError effect`() =
        runTest(mainDispatcher.dispatcher) {
            fakeCache.nextToggleRepostResult = Result.failure(RuntimeException("net"))
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onRepost(unrepostedPost())
                advanceUntilIdle()

                val effect = awaitItem()
                assert(effect is InteractionEffect.ShowError) {
                    "expected ShowError but got $effect"
                }
            }
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Share
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onShare emits SharePost effect and fires Share analytics`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.interactionEffects.test {
                handler.onShare(post)

                val effect = awaitItem()
                assert(effect is InteractionEffect.SharePost) {
                    "expected SharePost but got $effect"
                }
            }

            assertEquals(listOf(Share(ShareMethod.ShareSheet, PostSurface.Feed)), analytics.events)
        }

    @Test
    fun `onShareLongPress emits CopyPermalink effect and fires CopyLink analytics`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.interactionEffects.test {
                handler.onShareLongPress(post)

                val effect = awaitItem()
                assert(effect is InteractionEffect.CopyPermalink) {
                    "expected CopyPermalink but got $effect"
                }
            }

            assertEquals(listOf(Share(ShareMethod.CopyLink, PostSurface.Feed)), analytics.events)
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Reply / Quote
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onReply emits NavigateToComposer with replyToUri set`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.interactionEffects.test {
                handler.onReply(post)

                val effect = awaitItem() as InteractionEffect.NavigateToComposer
                assertEquals(post.id, effect.replyToUri)
                assertNull(effect.quoteUri)
            }
        }

    @Test
    fun `onQuote emits NavigateToComposer with quoteUri set`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.interactionEffects.test {
                handler.onQuote(post)

                val effect = awaitItem() as InteractionEffect.NavigateToComposer
                assertNull(effect.replyToUri)
                assertEquals(post.id, effect.quoteUri)
            }
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Overflow actions
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `onOverflowAction MuteAuthor calls muteRepository`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.onOverflowAction(post, PostOverflowAction.MuteAuthor)
            advanceUntilIdle()

            assertEquals(listOf(post.author.did), fakeMuteRepo.muteActorCalls)
        }

    @Test
    fun `onOverflowAction ReportPost emits NavigateToReport`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.interactionEffects.test {
                handler.onOverflowAction(post, PostOverflowAction.ReportPost)

                val effect = awaitItem() as InteractionEffect.NavigateToReport
                assertEquals(post, effect.post)
            }
        }

    @Test
    fun `onOverflowAction BlockAuthor emits NavigateToBlock`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)
            val post = unlikedPost()

            handler.interactionEffects.test {
                handler.onOverflowAction(post, PostOverflowAction.BlockAuthor)

                val effect = awaitItem() as InteractionEffect.NavigateToBlock
                assertEquals(post.author.did, effect.did)
                assertEquals(post.author.handle, effect.handle)
            }
        }

    @Test
    fun `onOverflowAction UnblockAuthor emits ShowComingSoon`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onOverflowAction(unlikedPost(), PostOverflowAction.UnblockAuthor)

                val effect = awaitItem() as InteractionEffect.ShowComingSoon
                assertEquals(PostOverflowAction.UnblockAuthor, effect.action)
            }
        }

    @Test
    fun `onOverflowAction MuteThread emits ShowComingSoon`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onOverflowAction(unlikedPost(), PostOverflowAction.MuteThread)

                val effect = awaitItem() as InteractionEffect.ShowComingSoon
                assertEquals(PostOverflowAction.MuteThread, effect.action)
            }
        }

    @Test
    fun `onOverflowAction UnmuteThread emits ShowComingSoon`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onOverflowAction(unlikedPost(), PostOverflowAction.UnmuteThread)

                val effect = awaitItem() as InteractionEffect.ShowComingSoon
                assertEquals(PostOverflowAction.UnmuteThread, effect.action)
            }
        }

    @Test
    fun `onOverflowAction CopyPostText emits ShowComingSoon`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.Feed, this)

            handler.interactionEffects.test {
                handler.onOverflowAction(unlikedPost(), PostOverflowAction.CopyPostText)

                val effect = awaitItem() as InteractionEffect.ShowComingSoon
                assertEquals(PostOverflowAction.CopyPostText, effect.action)
            }
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Surface attribution
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `analytics event carries the bound surface`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = makeHandler()
            handler.bind(PostSurface.PostDetail, this)

            handler.onLike(unlikedPost())
            advanceUntilIdle()

            assertEquals(
                listOf(InteractPost(PostAction.Like, PostSurface.PostDetail)),
                analytics.events,
            )
        }
}
