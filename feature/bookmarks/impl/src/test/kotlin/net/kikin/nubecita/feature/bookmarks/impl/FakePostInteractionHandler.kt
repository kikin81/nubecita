package net.kikin.nubecita.feature.bookmarks.impl

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
 * Test double for [PostInteractionHandler] in the Bookmarks feature.
 *
 * Routes like / repost through the supplied [cache] so tests that share a
 * [FakePostInteractionsCache] instance between the VM and the handler can
 * still assert on [FakePostInteractionsCache.toggleLikeCalls] etc.
 *
 * Unlike the Feed's version, Bookmarks does NOT override [onOverflowAction]
 * in the VM — all overflow actions are fully delegated to the handler
 * (Mute/Unmute are handled by the shared DefaultPostInteractionHandler in
 * production; in tests they are recorded via [overflowActions]).
 */
internal class FakePostInteractionHandler(
    private val cache: FakePostInteractionsCache = FakePostInteractionsCache(),
) : PostInteractionHandler {
    private val _tapMarkers = MutableStateFlow(PostTapMarkers())
    override val tapMarkers: StateFlow<PostTapMarkers> = _tapMarkers.asStateFlow()

    private val _interactionEffects = Channel<InteractionEffect>(Channel.UNLIMITED)
    override val interactionEffects: Flow<InteractionEffect> = _interactionEffects.receiveAsFlow()

    /** All (post, action) pairs received via [onOverflowAction] — for test assertions. */
    val overflowActions: MutableList<Pair<PostUi, PostOverflowAction>> = mutableListOf()

    private var surface: PostSurface = PostSurface.Bookmarks
    private var scope: CoroutineScope? = null

    override fun bind(
        surface: PostSurface,
        scope: CoroutineScope,
    ) {
        this.surface = surface
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

    override fun onBookmark(post: PostUi) {
        requireScope().launch {
            cache
                .toggleBookmark(post.id, post.cid)
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
        overflowActions += post to action
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
