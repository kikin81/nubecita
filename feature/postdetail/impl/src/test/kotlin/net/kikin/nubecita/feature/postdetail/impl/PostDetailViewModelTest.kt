package net.kikin.nubecita.feature.postdetail.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.postinteractions.InteractionEffect
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class PostDetailViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `initial Load success populates items and returns to Idle`() =
        runTest(mainDispatcher.dispatcher) {
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(items, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
            assertEquals(listOf("at://focus"), repo.invocations)
        }

    @Test
    fun `initial Load success with empty thread surfaces InitialError(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(results = listOf(Result.success(persistentListOf())))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.NotFound, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `IOException maps to InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(results = listOf(Result.failure(IOException("network down"))))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.Network, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `NoSessionException maps to InitialError(Unauthenticated)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(results = listOf(Result.failure(NoSessionException())))
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.Unauthenticated, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `XrpcError with errorName=NotFound maps to InitialError(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    results = listOf(Result.failure(XrpcError.Unknown(name = "NotFound", message = null, status = 400))),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.NotFound, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `XrpcError with status=404 maps to InitialError(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    results = listOf(Result.failure(XrpcError.Unknown(name = "Other", message = null, status = 404))),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            assertEquals(PostDetailError.NotFound, (status as PostDetailLoadStatus.InitialError).error)
        }

    @Test
    fun `unknown XrpcError maps to InitialError(Unknown) carrying errorName`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    results = listOf(Result.failure(XrpcError.Unknown(name = "ServerError", message = null, status = 500))),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is PostDetailLoadStatus.InitialError)
            val error = (status as PostDetailLoadStatus.InitialError).error
            assertTrue(error is PostDetailError.Unknown)
            assertEquals("ServerError", (error as PostDetailError.Unknown).cause)
        }

    @Test
    fun `Retry after InitialError re-runs the initial load`() =
        runTest(mainDispatcher.dispatcher) {
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.failure(IOException("transient")),
                            Result.success(items),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is PostDetailLoadStatus.InitialError)

            vm.handleEvent(PostDetailEvent.Retry)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(items, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
            assertEquals(2, repo.invocations.size)
        }

    @Test
    fun `Refresh success replaces items`() =
        runTest(mainDispatcher.dispatcher) {
            val firstItems = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus", text = "v1")))
            val refreshedItems =
                persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus", text = "v2")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.success(firstItems),
                            Result.success(refreshedItems),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(PostDetailEvent.Refresh)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(refreshedItems, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh failure preserves items and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val firstItems = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.success(firstItems),
                            Result.failure(IOException("refresh failed")),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.Refresh)
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.ShowError)
                assertEquals(PostDetailError.Network, (effect as PostDetailEffect.ShowError).error)
            }

            val state = vm.uiState.value
            assertEquals(firstItems, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh with empty result preserves prior items`() =
        runTest(mainDispatcher.dispatcher) {
            val firstItems = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val repo =
                FakeRepo(
                    results =
                        listOf(
                            Result.success(firstItems),
                            Result.success(persistentListOf()),
                        ),
                )
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(PostDetailEvent.Refresh)
            advanceUntilIdle()

            val state = vm.uiState.value
            // Empty refresh response keeps the prior items and stays Idle
            // — better than wiping the screen mid-read.
            assertEquals(firstItems, state.items)
            assertEquals(PostDetailLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Load while InitialLoading is idempotent (no second repo call)`() =
        runTest(mainDispatcher.dispatcher) {
            val deferred = CompletableDeferred<Result<ImmutableList<ThreadItem>>>()
            val repo = FakeRepo(producer = { deferred.await() })
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            // Don't yet complete the deferred; while it's pending the second
            // Load must be a no-op.
            vm.handleEvent(PostDetailEvent.Load)
            vm.handleEvent(PostDetailEvent.Load)

            deferred.complete(
                Result.success(persistentListOf(ThreadItem.Focus(samplePost("at://focus")))),
            )
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `Refresh while InitialLoading is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val deferred = CompletableDeferred<Result<ImmutableList<ThreadItem>>>()
            val repo = FakeRepo(producer = { deferred.await() })
            val vm = newVm(repo)

            vm.handleEvent(PostDetailEvent.Load)
            // VM is now in InitialLoading; Refresh must be dropped.
            vm.handleEvent(PostDetailEvent.Refresh)

            deferred.complete(
                Result.success(persistentListOf(ThreadItem.Focus(samplePost("at://focus")))),
            )
            advanceUntilIdle()

            assertEquals(1, repo.invocations.size)
        }

    @Test
    fun `OnPostTapped emits NavigateToPost with the URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnPostTapped("at://target"))

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToPost)
                assertEquals("at://target", (effect as PostDetailEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `OnAuthorTapped emits NavigateToAuthor with the DID`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnAuthorTapped("did:plc:alice"))

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToAuthor)
                assertEquals("did:plc:alice", (effect as PostDetailEffect.NavigateToAuthor).authorDid)
            }
        }

    @Test
    fun `OnQuotedPostTapped emits NavigateToPost with the quoted post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnQuotedPostTapped("at://quoted-target"))

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToPost)
                assertEquals("at://quoted-target", (effect as PostDetailEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `tapping the same focus URI emits NavigateToPost (no special-case)`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo(), focusUri = "at://focus")

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnPostTapped("at://focus"))
                val effect = awaitItem()
                // VM doesn't dedupe — re-entering the same focus is harmless
                // (the new NavEntry's state holder picks up at the same scroll
                // position) and keeping it dumb keeps the contract small.
                assertTrue(effect is PostDetailEffect.NavigateToPost)
                assertEquals("at://focus", (effect as PostDetailEffect.NavigateToPost).postUri)
            }
        }

    @Test
    fun `OnReplyClicked emits NavigateToComposer with the focus post's canonical URI, not the route URI`() =
        runTest(mainDispatcher.dispatcher) {
            // Deep links open post-detail with a handle-based route URI
            // (PostDeepLinkKey.toPostDetailRoute), while the appview returns the
            // canonical DID-based URI as the focus post's id. The reply target
            // must be the canonical id so the composer's reply-ref resolution
            // doesn't choke on a handle URI — assert the two differ and we emit
            // the canonical one.
            val canonicalUri = "at://did:plc:abcdefghijklmnopqrstuvwx/app.bsky.feed.post/3lkb"
            val routeUri = "at://alice.bsky.social/app.bsky.feed.post/3lkb"
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost(canonicalUri)))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, focusUri = routeUri)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnReplyClicked)
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToComposer)
                assertEquals(canonicalUri, (effect as PostDetailEffect.NavigateToComposer).parentPostUri)
            }
        }

    @Test
    fun `OnReplyClicked while no Focus is loaded is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            // VM is in default Idle state with no items — the FAB should
            // still be visible (it lives in Scaffold's slot regardless of
            // load status), but tapping it before a Focus resolves is a
            // silent drop, not an arbitrary effect.
            val vm = newVm(FakeRepo())

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnReplyClicked)
                expectNoEvents()
            }
        }

    @Test
    fun `OnReplyClicked on a reply-gated focus is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            // Focus post's threadgate disallows this viewer (canViewerReply
            // = false). The FAB is hidden, but defend the handler too: a
            // gated reply must never open the composer.
            val items =
                persistentListOf<ThreadItem>(
                    ThreadItem.Focus(samplePost("at://focus", canViewerReply = false)),
                )
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, focusUri = "at://focus")

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnReplyClicked)
                expectNoEvents()
            }
        }

    @Test
    fun `showReplyFab tracks the focus post's canViewerReply`() =
        runTest(mainDispatcher.dispatcher) {
            // Allowed focus → FAB shows.
            val allowed =
                persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost("at://focus")))
            val allowedVm = newVm(FakeRepo(results = listOf(Result.success(allowed))), focusUri = "at://focus")
            allowedVm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()
            assertTrue(allowedVm.uiState.value.showReplyFab)

            // Reply-gated focus → FAB hidden.
            val gated =
                persistentListOf<ThreadItem>(
                    ThreadItem.Focus(samplePost("at://focus", canViewerReply = false)),
                )
            val gatedVm = newVm(FakeRepo(results = listOf(Result.success(gated))), focusUri = "at://focus")
            gatedVm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()
            assertFalse(gatedVm.uiState.value.showReplyFab)

            // No focus resolved yet → FAB hidden.
            assertFalse(newVm(FakeRepo()).uiState.value.showReplyFab)
        }

    @Test
    fun `OnFocusImageClicked emits NavigateToMediaViewer with the focus post's canonical URI`() =
        runTest(mainDispatcher.dispatcher) {
            // Deep links open post-detail with a handle-based route URI; the media
            // viewer's getPost only resolves DID-based URIs, so the tap must carry
            // the focus post's canonical id, not route.postUri (else "Post not
            // found"). Assert the two differ and the canonical one is emitted.
            val canonicalUri = "at://did:plc:abcdefghijklmnopqrstuvwx/app.bsky.feed.post/3lkb"
            val routeUri = "at://alice.bsky.social/app.bsky.feed.post/3lkb"
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(samplePost(canonicalUri)))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, focusUri = routeUri)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnFocusImageClicked(imageIndex = 2))
                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.NavigateToMediaViewer)
                val nav = effect as PostDetailEffect.NavigateToMediaViewer
                assertEquals(canonicalUri, nav.postUri)
                assertEquals(2, nav.imageIndex)
            }
        }

    @Test
    fun `OnVideoTapped emits NavigateToVideoPlayer with the tapped URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(FakeRepo())

            vm.effects.test {
                // Per-PostCard videoEmbedSlot dispatches with the URI of
                // the post whose embed carries the video (or, when the
                // tap lands inside a quoted-record video, the quoted
                // post's URI). The VM forwards it verbatim.
                vm.handleEvent(PostDetailEvent.OnVideoTapped(postUri = "at://video"))
                val effect = awaitItem()
                assertEquals(
                    PostDetailEffect.NavigateToVideoPlayer(postUri = "at://video"),
                    effect,
                )
            }
        }

    // ---------- overflow-menu delegation tests ----------

    @Test
    fun `OnOverflowAction emits ShowComingSoon on interactionEffects for stubbed variants`() =
        // Block graduated to real in PR4 (block→real). The four remaining coming-soon
        // variants (UnblockAuthor/MuteThread/UnmuteThread/CopyPostText) delegate to the
        // handler which emits InteractionEffect.ShowComingSoon on interactionEffects.
        // ReportPost/BlockAuthor now emit navigate effects (covered by separate tests).
        // MuteAuthor/UnmuteAuthor are intercepted by the VM's onOverflowAction override.
        runTest(mainDispatcher.dispatcher) {
            val handler = FakePostInteractionHandler()
            val vm = newVm(FakeRepo(), handler = handler)
            val post = samplePost("at://post-overflow")
            val stubbedVariants =
                listOf(
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.UnblockAuthor,
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteThread,
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteThread,
                    net.kikin.nubecita.designsystem.component.PostOverflowAction.CopyPostText,
                )

            vm.interactionEffects.test {
                for (action in stubbedVariants) {
                    vm.handleEvent(PostDetailEvent.OnOverflowAction(post = post, action = action))
                    assertEquals(
                        InteractionEffect.ShowComingSoon(action),
                        awaitItem(),
                    )
                }
            }
        }

    @Test
    fun `OnOverflowAction(BlockAuthor) emits NavigateToBlock on interactionEffects`() =
        // Block→real in PR4: BlockAuthor delegates to the handler which emits
        // NavigateToBlock (real Block dialog), not ShowComingSoon.
        runTest(mainDispatcher.dispatcher) {
            val handler = FakePostInteractionHandler()
            val vm = newVm(FakeRepo(), handler = handler)
            val authorDid = "did:plc:blockme"
            val authorHandle = "blockme.bsky.social"
            val post = samplePost("at://did:plc:fake/app.bsky.feed.post/blk1", authorDid = authorDid, handle = authorHandle)

            vm.interactionEffects.test {
                vm.handleEvent(
                    PostDetailEvent.OnOverflowAction(
                        post = post,
                        action = net.kikin.nubecita.designsystem.component.PostOverflowAction.BlockAuthor,
                    ),
                )
                val effect = awaitItem()
                assertEquals(
                    InteractionEffect.NavigateToBlock(did = authorDid, handle = authorHandle),
                    effect,
                )
            }
        }

    @Test
    fun `OnOverflowAction(ReportPost) emits NavigateToReport on interactionEffects`() =
        // ReportPost delegates to the handler → InteractionEffect.NavigateToReport on
        // vm.interactionEffects. The screen's rememberPostInteractions then constructs
        // Report.forPost(post) and pushes it onto MainShell's inner back stack.
        runTest(mainDispatcher.dispatcher) {
            val handler = FakePostInteractionHandler()
            val vm = newVm(FakeRepo(), handler = handler)
            val post =
                samplePost(
                    id = "at://did:plc:author/app.bsky.feed.post/rprt1",
                    cid = "bafyreitestreportcid",
                )

            vm.interactionEffects.test {
                vm.handleEvent(
                    PostDetailEvent.OnOverflowAction(
                        post = post,
                        action = net.kikin.nubecita.designsystem.component.PostOverflowAction.ReportPost,
                    ),
                )
                val effect = awaitItem()
                assertTrue(
                    effect is InteractionEffect.NavigateToReport,
                    "expected NavigateToReport, got $effect",
                )
                assertEquals(
                    post,
                    (effect as InteractionEffect.NavigateToReport).post,
                )
            }
        }

    // ---------- cache merge test ----------

    @Test
    fun `cache emission projects onto Focus and Reply thread items`() =
        // Pin: after a thread loads, emitting a cache snapshot updates the liked-state
        // and counts on Ancestor / Focus / Reply items atomically. This tests the VM's
        // own cache.state → items merge (NOT covered by DefaultPostInteractionHandlerTest,
        // which only tests the write side). Blocked / NotFound / Fold pass through unchanged.
        runTest(mainDispatcher.dispatcher) {
            val ancestorPost = samplePost("at://ancestor", cid = "cidAncestor")
            val focusPost = samplePost("at://focus", cid = "cidFocus")
            val replyPost = samplePost("at://reply", cid = "cidReply")
            val items =
                persistentListOf<ThreadItem>(
                    ThreadItem.Ancestor(ancestorPost),
                    ThreadItem.Focus(focusPost),
                    ThreadItem.Reply(replyPost, depth = 1),
                )
            val cache = FakePostInteractionsCache()
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, postInteractionsCache = cache)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            // Emit a cache snapshot: reply post is liked (count 5), focus post is reposted (count 10).
            cache.emit(
                persistentMapOf(
                    "at://reply" to
                        PostInteractionState(
                            viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/1",
                            likeCount = 5,
                        ),
                    "at://focus" to
                        PostInteractionState(
                            viewerRepostUri = "at://did:plc:viewer/app.bsky.feed.repost/1",
                            repostCount = 10,
                        ),
                ),
            )
            advanceUntilIdle()

            val state = vm.uiState.value
            val ancestor = state.items.filterIsInstance<ThreadItem.Ancestor>().first()
            val focus = state.items.filterIsInstance<ThreadItem.Focus>().first()
            val reply = state.items.filterIsInstance<ThreadItem.Reply>().first()

            // Ancestor is not in the cache snapshot — its state should be unchanged.
            assertFalse(ancestor.post.viewer.isLikedByViewer, "ancestor post should be unchanged")

            // Reply is liked per the cache snapshot.
            assertTrue(reply.post.viewer.isLikedByViewer, "reply post should show as liked after cache emission")
            assertEquals(5, reply.post.stats.likeCount, "reply like count should reflect cache value")

            // Focus is reposted per the cache snapshot.
            assertTrue(focus.post.viewer.isRepostedByViewer, "focus post should show as reposted after cache emission")
            assertEquals(10, focus.post.stats.repostCount, "focus repost count should reflect cache value")
        }

    // ---------- tap-marker mirror test ----------

    @Test
    fun `handler tapMarkers are mirrored into PostDetailState`() =
        // Pin: when onLike/onRepost is called (delegated to the handler), the handler
        // updates tapMarkers synchronously; the VM's tapMarkers collector mirrors those
        // into lastLikeTapPostUri / lastRepostTapPostUri for the count ±1 animation.
        runTest(mainDispatcher.dispatcher) {
            val handler = FakePostInteractionHandler()
            val vm = newVm(FakeRepo(), handler = handler)
            val likePost = samplePost("at://like-me")
            val repostPost = samplePost("at://repost-me")

            assertNull(vm.uiState.value.lastLikeTapPostUri, "initially null")
            assertNull(vm.uiState.value.lastRepostTapPostUri, "initially null")

            vm.onLike(likePost)
            advanceUntilIdle()
            assertEquals("at://like-me", vm.uiState.value.lastLikeTapPostUri)

            vm.onRepost(repostPost)
            advanceUntilIdle()
            assertEquals("at://repost-me", vm.uiState.value.lastRepostTapPostUri)
        }

    @Test
    fun `handler bind is called with PostDetail surface on init`() =
        runTest(mainDispatcher.dispatcher) {
            val handler = FakePostInteractionHandler()
            newVm(FakeRepo(), handler = handler)
            advanceUntilIdle()
            assertEquals(
                net.kikin.nubecita.core.analytics.PostSurface.PostDetail,
                handler.boundSurface,
            )
        }

    // ---------- oftc.5 mute/unmute tests ----------

    @Test
    fun `OnOverflowAction(MuteAuthor) flips isAuthorMutedByViewer=true on thread posts by that author and calls muteActor`() =
        // Pin (a): MuteAuthor resolves the author DID, optimistically flips
        // isAuthorMutedByViewer to true on every post by that author in the
        // thread, then calls muteRepository.muteActor(did). Unlike the feed
        // surface, posts are NOT removed — the thread view keeps the author's
        // posts visible but marked muted.
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:alice"
            val muteRepo = FakeMuteRepository()
            val focusPost = samplePost("at://focus", authorDid = authorDid)
            val replyPost = samplePost("at://reply", authorDid = authorDid)
            val otherPost = samplePost("at://other", authorDid = "did:plc:other")
            val items =
                persistentListOf<ThreadItem>(
                    ThreadItem.Focus(focusPost),
                    ThreadItem.Reply(replyPost, depth = 1),
                    ThreadItem.Reply(otherPost, depth = 1),
                )
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, muteRepository = muteRepo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(PostDetailEvent.OnOverflowAction(post = focusPost, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor))
            advanceUntilIdle()

            val state = vm.uiState.value
            val focusItem = state.items.filterIsInstance<ThreadItem.Focus>().first()
            val replyItems = state.items.filterIsInstance<ThreadItem.Reply>()
            assertTrue(focusItem.post.viewer.isAuthorMutedByViewer, "focus post should be muted")
            assertTrue(
                replyItems
                    .first { it.post.id == "at://reply" }
                    .post.viewer.isAuthorMutedByViewer,
                "reply by alice should be muted",
            )
            assertFalse(
                replyItems
                    .first { it.post.id == "at://other" }
                    .post.viewer.isAuthorMutedByViewer,
                "reply by other author should not be muted",
            )

            assertEquals(1, muteRepo.muteActorCalls.size)
            assertEquals(authorDid, muteRepo.muteActorCalls.first())
        }

    @Test
    fun `OnOverflowAction(MuteAuthor) failure rolls back items and emits ShowError`() =
        // Pin (b): when muteActor returns failure, the optimistically-flipped
        // items are restored to their previous state and ShowError is emitted.
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:alice"
            val muteRepo =
                FakeMuteRepository().apply {
                    nextMuteResult = Result.failure(java.io.IOException("network error"))
                }
            val focusPost = samplePost("at://focus", authorDid = authorDid)
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(focusPost))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, muteRepository = muteRepo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnOverflowAction(post = focusPost, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.MuteAuthor))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.ShowError, "expected ShowError, got $effect")
                assertEquals(PostDetailError.Network, (effect as PostDetailEffect.ShowError).error)
            }

            val focusItem =
                vm.uiState.value.items
                    .filterIsInstance<ThreadItem.Focus>()
                    .first()
            assertFalse(focusItem.post.viewer.isAuthorMutedByViewer, "flag should be rolled back on failure")
        }

    @Test
    fun `OnOverflowAction(UnmuteAuthor) flips isAuthorMutedByViewer=false on thread posts by that author and calls unmuteActor`() =
        // Pin (c): UnmuteAuthor resolves the author DID, optimistically flips
        // isAuthorMutedByViewer to false on every post by that author in the
        // thread, then calls muteRepository.unmuteActor(did).
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:alice"
            val muteRepo = FakeMuteRepository()
            val focusPost = samplePost("at://focus", authorDid = authorDid, isAuthorMutedByViewer = true)
            val replyPost = samplePost("at://reply", authorDid = authorDid, isAuthorMutedByViewer = true)
            val items =
                persistentListOf<ThreadItem>(
                    ThreadItem.Focus(focusPost),
                    ThreadItem.Reply(replyPost, depth = 1),
                )
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, muteRepository = muteRepo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(PostDetailEvent.OnOverflowAction(post = focusPost, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteAuthor))
            advanceUntilIdle()

            val state = vm.uiState.value
            val focusItem = state.items.filterIsInstance<ThreadItem.Focus>().first()
            val replyItem = state.items.filterIsInstance<ThreadItem.Reply>().first()
            assertFalse(focusItem.post.viewer.isAuthorMutedByViewer, "focus post mute flag should be false after unmute")
            assertFalse(replyItem.post.viewer.isAuthorMutedByViewer, "reply post mute flag should be false after unmute")

            assertEquals(1, muteRepo.unmuteActorCalls.size)
            assertEquals(authorDid, muteRepo.unmuteActorCalls.first())
        }

    @Test
    fun `OnOverflowAction(UnmuteAuthor) failure rolls back items and emits ShowError`() =
        // Pin (d): when unmuteActor returns failure, items are restored to
        // their pre-optimistic state (isAuthorMutedByViewer back to true)
        // and ShowError is emitted.
        runTest(mainDispatcher.dispatcher) {
            val authorDid = "did:plc:alice"
            val muteRepo =
                FakeMuteRepository().apply {
                    nextUnmuteResult = Result.failure(java.io.IOException("network error"))
                }
            val focusPost = samplePost("at://focus", authorDid = authorDid, isAuthorMutedByViewer = true)
            val items = persistentListOf<ThreadItem>(ThreadItem.Focus(focusPost))
            val repo = FakeRepo(results = listOf(Result.success(items)))
            val vm = newVm(repo, muteRepository = muteRepo)

            vm.handleEvent(PostDetailEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(PostDetailEvent.OnOverflowAction(post = focusPost, action = net.kikin.nubecita.designsystem.component.PostOverflowAction.UnmuteAuthor))
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is PostDetailEffect.ShowError, "expected ShowError, got $effect")
                assertEquals(PostDetailError.Network, (effect as PostDetailEffect.ShowError).error)
            }

            val focusItem =
                vm.uiState.value.items
                    .filterIsInstance<ThreadItem.Focus>()
                    .first()
            assertTrue(focusItem.post.viewer.isAuthorMutedByViewer, "flag should be rolled back to true on unmute failure")
        }

    // ---------- helpers ----------

    private fun newVm(
        repo: PostThreadRepository,
        focusUri: String = "at://focus",
        handler: FakePostInteractionHandler = FakePostInteractionHandler(),
        muteRepository: MuteRepository = FakeMuteRepository(),
        postInteractionsCache: PostInteractionsCache = FakePostInteractionsCache(),
    ): PostDetailViewModel =
        PostDetailViewModel(
            route = PostDetailRoute(postUri = focusUri),
            postThreadRepository = repo,
            postInteractionsCache = postInteractionsCache,
            handler = handler,
            muteRepository = muteRepository,
        )

    private fun samplePost(
        id: String,
        text: String = "sample text",
        cid: String = "bafyreifakefakefakefakefakefakefakefakefakefake",
        handle: String = "test.bsky.social",
        authorDid: String = "did:plc:test",
        canViewerReply: Boolean = true,
        isAuthorMutedByViewer: Boolean = false,
    ): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author =
                AuthorUi(
                    did = authorDid,
                    handle = handle,
                    displayName = "Test",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-25T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(canViewerReply = canViewerReply, isAuthorMutedByViewer = isAuthorMutedByViewer),
            repostedBy = null,
        )

    private class FakeRepo(
        results: List<Result<ImmutableList<ThreadItem>>> = emptyList(),
        private val producer: (suspend (uri: String) -> Result<ImmutableList<ThreadItem>>)? = null,
    ) : PostThreadRepository {
        private val queue = ArrayDeque(results)
        val invocations = mutableListOf<String>()

        override suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>> {
            invocations += uri
            return producer?.invoke(uri)
                ?: queue.removeFirstOrNull()
                ?: error("FakeRepo got an unexpected getPostThread($uri) call")
        }
    }
}
