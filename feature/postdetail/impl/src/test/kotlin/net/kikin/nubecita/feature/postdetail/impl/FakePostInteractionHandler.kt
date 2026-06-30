package net.kikin.nubecita.feature.postdetail.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.postinteractions.InteractionEffect
import net.kikin.nubecita.core.postinteractions.InteractionError
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.core.postinteractions.sharing.toShareIntent
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction

/**
 * Test double for [PostInteractionHandler] used in [PostDetailViewModelTest].
 *
 * Routes like / repost through the supplied [cache] so tests that share a
 * [FakePostInteractionsCache] instance between the VM and the handler can
 * still assert on [FakePostInteractionsCache.toggleLikeCalls] etc.
 *
 * [onOverflowAction] throws for [PostOverflowAction.MuteAuthor] and
 * [PostOverflowAction.UnmuteAuthor] — the VM's [PostDetailViewModel.onOverflowAction]
 * override intercepts those before delegation reaches this handler.
 * [PostOverflowAction.BlockAuthor] is NOT intercepted — it delegates to this
 * handler, which emits [InteractionEffect.NavigateToBlock] (block→real, PR4).
 */
internal class FakePostInteractionHandler(
    private val cache: FakePostInteractionsCache = FakePostInteractionsCache(),
) : PostInteractionHandler {
    private val _tapMarkers = MutableStateFlow(PostTapMarkers())
    override val tapMarkers: StateFlow<PostTapMarkers> = _tapMarkers.asStateFlow()

    private val _interactionEffects = Channel<InteractionEffect>(Channel.UNLIMITED)
    override val interactionEffects: Flow<InteractionEffect> = _interactionEffects.receiveAsFlow()

    /** Records the [PostSurface] passed to [bind]; null until [bind] is called. */
    var boundSurface: PostSurface? = null
    private var scope: CoroutineScope? = null

    override fun bind(
        surface: PostSurface,
        scope: CoroutineScope,
    ) {
        boundSurface = surface
        this.scope = scope
    }

    override fun onLike(post: PostUi) {
        _tapMarkers.value = _tapMarkers.value.copy(lastLikeTapPostUri = post.id)
        requireScope().launch {
            cache
                .toggleLike(post.id, post.cid)
                .onFailure { emit(InteractionEffect.ShowError(InteractionError.Network)) }
        }
    }

    override fun onRepost(post: PostUi) {
        _tapMarkers.value = _tapMarkers.value.copy(lastRepostTapPostUri = post.id)
        requireScope().launch {
            cache
                .toggleRepost(post.id, post.cid)
                .onFailure { emit(InteractionEffect.ShowError(InteractionError.Network)) }
        }
    }

    override fun onReply(post: PostUi) {
        emit(InteractionEffect.NavigateToComposer(replyToUri = post.id, quoteUri = null))
    }

    override fun onQuote(post: PostUi) {
        emit(InteractionEffect.NavigateToComposer(replyToUri = null, quoteUri = post.id))
    }

    override fun onShare(post: PostUi) {
        emit(InteractionEffect.SharePost(post.toShareIntent()))
    }

    override fun onShareLongPress(post: PostUi) {
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
                // block→real in PR4: emit NavigateToBlock so the Block dialog
                // opens (real) rather than a coming-soon snackbar.
                emit(
                    InteractionEffect.NavigateToBlock(
                        did = post.author.did,
                        handle = post.author.handle,
                    ),
                )
            PostOverflowAction.MuteAuthor,
            PostOverflowAction.UnmuteAuthor,
            ->
                // The VM's onOverflowAction override intercepts these before
                // delegation can reach this handler — reaching here is a test
                // wiring bug.
                error(
                    "FakePostInteractionHandler: $action must be intercepted by " +
                        "PostDetailViewModel.onOverflowAction before delegation",
                )
            else ->
                emit(InteractionEffect.ShowComingSoon(action))
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun emit(effect: InteractionEffect) {
        _interactionEffects.trySend(effect)
    }

    private fun requireScope(): CoroutineScope =
        checkNotNull(scope) {
            "FakePostInteractionHandler.bind() must be called before dispatching interactions"
        }
}
