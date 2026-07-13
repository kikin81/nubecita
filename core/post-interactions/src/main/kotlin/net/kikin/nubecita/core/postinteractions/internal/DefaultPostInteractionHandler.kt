package net.kikin.nubecita.core.postinteractions.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.InteractPost
import net.kikin.nubecita.core.analytics.PostAction
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.Share
import net.kikin.nubecita.core.analytics.ShareMethod
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.postinteractions.InteractionEffect
import net.kikin.nubecita.core.postinteractions.InteractionError
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.core.postinteractions.sharing.toShareIntent
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Default implementation of [PostInteractionHandler].
 *
 * Inject one instance per ViewModel (unscoped). Call [bind] once from
 * the owning VM's `init` block before dispatching any interaction.
 *
 * # Per-URI double-tap guard
 *
 * [onLike] / [onRepost] guard against duplicate taps on the same post URI
 * via [activeLikeJobs] / [activeRepostJobs]: if a job for [PostUi.id] is
 * already active when the second tap arrives, the second call returns
 * immediately without firing analytics or a network call. This matches the
 * pattern in the existing `PostInteractionsCache` (which already
 * single-flights the network layer) and prevents duplicate analytics
 * logging that a timing race would otherwise cause.
 *
 * # Effect delivery
 *
 * [interactionEffects] is backed by an [Channel.UNLIMITED]-capacity
 * [Channel], matching the [MviViewModel][net.kikin.nubecita.core.common.mvi.MviViewModel]
 * effect pattern: emitters never suspend or drop, and each element is
 * consumed by exactly one collector.
 *
 * # Analytics
 *
 * Analytics events fire optimistically at the call site (before the
 * network resolves), consistent with the existing `FeedViewModel`
 * behavior. The per-URI guard ensures each user tap produces at most one
 * analytics event.
 */
internal class DefaultPostInteractionHandler
    @Inject
    constructor(
        private val cache: PostInteractionsCache,
        private val muteRepository: MuteRepository,
        private val analytics: AnalyticsClient,
    ) : PostInteractionHandler {
        // ──────────────────────────────────────────────────────────────────────
        // Binding state — set once per VM lifecycle via bind()
        // ──────────────────────────────────────────────────────────────────────

        private var surface: PostSurface = PostSurface.Feed
        private var scope: CoroutineScope? = null

        // ──────────────────────────────────────────────────────────────────────
        // Public streams
        // ──────────────────────────────────────────────────────────────────────

        private val _tapMarkers = MutableStateFlow(PostTapMarkers())
        override val tapMarkers: StateFlow<PostTapMarkers> = _tapMarkers.asStateFlow()

        /**
         * Unlimited-capacity channel: emitters never suspend or drop, each
         * element is delivered to exactly one collector. Mirrors the
         * [net.kikin.nubecita.core.common.mvi.MviViewModel] effects channel.
         */
        private val _interactionEffects = Channel<InteractionEffect>(Channel.UNLIMITED)
        override val interactionEffects: Flow<InteractionEffect> = _interactionEffects.receiveAsFlow()

        // ──────────────────────────────────────────────────────────────────────
        // Per-URI job guards (prevent double-tap duplicate analytics + calls)
        // ──────────────────────────────────────────────────────────────────────

        private val activeLikeJobs = ConcurrentHashMap<String, Job>()
        private val activeRepostJobs = ConcurrentHashMap<String, Job>()
        private val activeBookmarkJobs = ConcurrentHashMap<String, Job>()

        // ──────────────────────────────────────────────────────────────────────
        // PostInteractionHandler contract
        // ──────────────────────────────────────────────────────────────────────

        override fun bind(
            surface: PostSurface,
            scope: CoroutineScope,
        ) {
            this.surface = surface
            this.scope = scope
        }

        override fun onLike(post: PostUi) {
            // Per-URI guard: ignore a second tap while the first is in flight.
            if (activeLikeJobs[post.id]?.isActive == true) return

            val action = if (post.viewer.isLikedByViewer) PostAction.Unlike else PostAction.Like
            analytics.log(InteractPost(action = action, surface = surface))
            _tapMarkers.value = _tapMarkers.value.copy(lastLikeTapPostUri = post.id)

            val job =
                requireScope().launch {
                    cache
                        .toggleLike(post.id, post.cid)
                        .onFailure { emitError(it) }
                }
            activeLikeJobs[post.id] = job
            job.invokeOnCompletion { activeLikeJobs.remove(post.id, job) }
        }

        override fun onBookmark(post: PostUi) {
            // Per-URI guard: ignore a second tap while the first is in flight.
            if (activeBookmarkJobs[post.id]?.isActive == true) return

            val action = if (post.viewer.isBookmarked) PostAction.Unbookmark else PostAction.Bookmark
            analytics.log(InteractPost(action = action, surface = surface))

            val job =
                requireScope().launch {
                    cache
                        .toggleBookmark(post.id, post.cid)
                        .onFailure { emitError(it) }
                }
            activeBookmarkJobs[post.id] = job
            job.invokeOnCompletion { activeBookmarkJobs.remove(post.id, job) }
        }

        override fun onRepost(post: PostUi) {
            if (activeRepostJobs[post.id]?.isActive == true) return

            val action = if (post.viewer.isRepostedByViewer) PostAction.Unrepost else PostAction.Repost
            analytics.log(InteractPost(action = action, surface = surface))
            _tapMarkers.value = _tapMarkers.value.copy(lastRepostTapPostUri = post.id)

            val job =
                requireScope().launch {
                    cache
                        .toggleRepost(post.id, post.cid)
                        .onFailure { emitError(it) }
                }
            activeRepostJobs[post.id] = job
            job.invokeOnCompletion { activeRepostJobs.remove(post.id, job) }
        }

        override fun onReply(post: PostUi) {
            emit(InteractionEffect.NavigateToComposer(replyToUri = post.id, quoteUri = null))
        }

        override fun onQuote(post: PostUi) {
            emit(InteractionEffect.NavigateToComposer(replyToUri = null, quoteUri = post.id))
        }

        override fun onShare(post: PostUi) {
            analytics.log(Share(method = ShareMethod.ShareSheet, surface = surface))
            emit(InteractionEffect.SharePost(post.toShareIntent()))
        }

        override fun onShareLongPress(post: PostUi) {
            analytics.log(Share(method = ShareMethod.CopyLink, surface = surface))
            emit(InteractionEffect.CopyPermalink(post.toShareIntent().permalink))
        }

        override fun onOverflowAction(
            post: PostUi,
            action: PostOverflowAction,
        ) {
            when (action) {
                PostOverflowAction.ReportPost ->
                    emit(InteractionEffect.NavigateToReport(post))

                PostOverflowAction.BlockAuthor ->
                    emit(
                        InteractionEffect.NavigateToBlock(
                            did = post.author.did,
                            handle = post.author.handle,
                        ),
                    )

                PostOverflowAction.MuteAuthor -> {
                    val did = post.author.did
                    requireScope().launch {
                        muteRepository
                            .muteActor(did)
                            .onFailure { emitError(it) }
                    }
                }

                PostOverflowAction.UnmuteAuthor -> {
                    val did = post.author.did
                    requireScope().launch {
                        muteRepository
                            .unmuteActor(did)
                            .onFailure { emitError(it) }
                    }
                }

                // These actions are not yet implemented on the server side
                // (block removal, thread muting) or are pending a separate
                // milestone (CopyPostText). Surface a coming-soon snackbar.
                PostOverflowAction.UnblockAuthor,
                PostOverflowAction.MuteThread,
                PostOverflowAction.UnmuteThread,
                PostOverflowAction.CopyPostText,
                -> emit(InteractionEffect.ShowComingSoon(action))
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // Helpers
        // ──────────────────────────────────────────────────────────────────────

        /**
         * Returns the bound [CoroutineScope], throwing if [bind] was never called.
         * The VM contract guarantees `bind()` runs in `init`; this guard surfaces
         * mis-wiring early in tests rather than producing a cryptic NPE.
         */
        private fun requireScope(): CoroutineScope =
            checkNotNull(scope) {
                "PostInteractionHandler.bind() must be called before dispatching interactions"
            }

        /**
         * Send [effect] onto the unlimited-capacity channel. Never suspends.
         */
        private fun emit(effect: InteractionEffect) {
            _interactionEffects.trySend(effect)
        }

        /**
         * Map a raw [Throwable] to an [InteractionError] variant and emit a
         * [InteractionEffect.ShowError]. Mirrors `FeedViewModel.toFeedError()`.
         *
         * [CancellationException] is rethrown without mapping so cooperative
         * cancellation is preserved and the VM-cleared / screen-disposed path
         * does not surface a spurious error snackbar.
         */
        private fun emitError(throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            val error =
                when (throwable) {
                    is NoSessionException -> InteractionError.Unauthenticated
                    is IOException -> InteractionError.Network
                    else -> InteractionError.Unknown
                }
            emit(InteractionEffect.ShowError(error))
        }
    }
