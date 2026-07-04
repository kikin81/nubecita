package net.kikin.nubecita.feature.profile.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.postinteractions.InteractionEffect
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction

/**
 * Minimal androidTest double for [PostInteractionHandler].
 *
 * The overflow-report instrumentation tests only exercise
 * `onOverflowAction(ReportPost)` — the screen's `rememberPostInteractions`
 * collector observes [InteractionEffect.NavigateToReport] and pushes the Report
 * route onto the provided nav state. Everything else is a no-op. The richer
 * like/repost-routing double lives in `src/test`, which the androidTest source
 * set can't reference — hence this small local copy.
 */
internal class FakePostInteractionHandler : PostInteractionHandler {
    private val _tapMarkers = MutableStateFlow(PostTapMarkers())
    override val tapMarkers: StateFlow<PostTapMarkers> = _tapMarkers.asStateFlow()

    private val _interactionEffects = Channel<InteractionEffect>(Channel.UNLIMITED)
    override val interactionEffects: Flow<InteractionEffect> = _interactionEffects.receiveAsFlow()

    override fun bind(
        surface: PostSurface,
        scope: CoroutineScope,
    ) = Unit

    override fun onLike(post: PostUi) = Unit

    override fun onRepost(post: PostUi) = Unit

    override fun onReply(post: PostUi) = Unit

    override fun onQuote(post: PostUi) = Unit

    override fun onShare(post: PostUi) = Unit

    override fun onShareLongPress(post: PostUi) = Unit

    override fun onOverflowAction(
        post: PostUi,
        action: PostOverflowAction,
    ) {
        when (action) {
            PostOverflowAction.ReportPost ->
                _interactionEffects.trySend(InteractionEffect.NavigateToReport(post))
            PostOverflowAction.BlockAuthor ->
                _interactionEffects.trySend(
                    InteractionEffect.NavigateToBlock(did = post.author.did, handle = post.author.handle),
                )
            else -> Unit
        }
    }
}
