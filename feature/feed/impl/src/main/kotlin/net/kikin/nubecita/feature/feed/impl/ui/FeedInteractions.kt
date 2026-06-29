package net.kikin.nubecita.feature.feed.impl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.media.AudioManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.collectLatest
import net.kikin.nubecita.core.common.haptic.PostHaptics
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEvents
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.feature.feed.impl.FeedEffect
import net.kikin.nubecita.feature.feed.impl.FeedError
import net.kikin.nubecita.feature.feed.impl.FeedEvent
import net.kikin.nubecita.feature.feed.impl.FeedViewModel
import net.kikin.nubecita.feature.feed.impl.R
import net.kikin.nubecita.feature.feed.impl.video.FeedVideoPlayerCoordinator
import net.kikin.nubecita.feature.feed.impl.video.createFeedVideoPlayerCoordinator
import android.net.Uri as AndroidUri

/**
 * Shared derived state + side-effect cluster for a [FeedViewModel]-backed screen.
 *
 * Holds all items that are produced identically by both [net.kikin.nubecita.feature.feed.impl.FeedScreen]
 * and [net.kikin.nubecita.feature.feed.impl.FeedViewScreen]: the [PostCallbacks] block, derived
 * dispatch lambdas, the [FeedVideoPlayerCoordinator], and the shared [LaunchedEffect]s
 * (composer-submit bus + screen-nav effects collector).
 *
 * Returned by [rememberFeedInteractions]; callers unpack the fields they
 * need and pass them to the stateless content composable.
 */
internal data class FeedInteractions(
    val callbacks: PostCallbacks,
    val onImageTap: (PostUi, Int) -> Unit,
    val onVideoTap: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val coordinator: FeedVideoPlayerCoordinator?,
)

/**
 * Builds the shared post callbacks + derived dispatch lambdas + video
 * coordinator for a [FeedViewModel]-backed screen, AND wires the
 * composer-submit and screen-nav-effect collectors.
 *
 * Post-interaction effects (share sheet, clipboard, snackbars for errors /
 * coming-soon / link-copied, composer / report / block navigation) are
 * handled by the shared [rememberPostInteractions] helper which observes
 * [net.kikin.nubecita.core.postinteractions.PostInteractionHandler.interactionEffects]
 * directly. The VM no longer forwards those to [FeedEffect].
 *
 * The [LaunchedEffect] keying is preserved exactly as in the original
 * per-screen implementations:
 * - Composer bus: `(composerSubmitEvents, viewModel, snackbarHostState)`.
 * - Screen-nav effects collector: `(viewModel, snackbarHostState)` — only
 *   [FeedEffect.ShowError] (mute/refresh/append failures) and the four
 *   nav effects remain here; interaction effects are owned by
 *   [rememberPostInteractions].
 * - [DisposableEffect] on coordinator: `(viewModel, coordinator)`.
 *
 * Screen-specific triggers ([FeedEvent.Load] / [FeedEvent.Bind]) are
 * deliberately NOT here — each screen wires its own binding [LaunchedEffect].
 */
@Composable
internal fun rememberFeedInteractions(
    viewModel: FeedViewModel,
    snackbarHostState: SnackbarHostState,
    haptics: PostHaptics,
    onReplyClick: (String) -> Unit,
    onQuoteClick: (String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToMediaViewer: (String, Int) -> Unit,
    onNavigateToVideoPlayer: (String) -> Unit,
): FeedInteractions {
    val context = LocalContext.current

    // Reply / quote taps are screen-level (per unified-composer spec step 10).
    // Stable references via rememberUpdatedState so the long-lived PostCallbacks
    // block below always calls the most recent host-supplied lambda. Declared
    // before `callbacks` because the lambdas inside PostCallbacks close over them.
    val currentOnReplyClick by rememberUpdatedState(onReplyClick)
    val currentOnQuoteClick by rememberUpdatedState(onQuoteClick)

    // Pre-resolve snackbar copy via stringResource() at composition time
    // so locale + dark-mode changes participate in recomposition. Reading
    // them via context.getString(...) inside the LaunchedEffect would
    // bypass Compose's resource tracking (lint: LocalContextGetResourceValueCall).
    val networkErrorMessage = stringResource(R.string.feed_snackbar_error_network)
    val unauthErrorMessage = stringResource(R.string.feed_snackbar_error_unauthenticated)
    val unknownErrorMessage = stringResource(R.string.feed_snackbar_error_unknown)
    val linkCopiedMessage = stringResource(R.string.feed_snackbar_link_copied)
    val clipLabel = stringResource(R.string.feed_clipboard_label_post_link)
    val postPublishedMessage = stringResource(R.string.feed_snackbar_post_published)
    val replyPublishedMessage = stringResource(R.string.feed_snackbar_reply_published)
    // Pre-resolve each overflow-menu "coming soon" snackbar via
    // stringResource() at composition time so locale changes
    // participate in recomposition (lint: LocalContextGetResourceValueCall).
    val overflowReportComingSoon = stringResource(R.string.feed_snackbar_overflow_report_coming_soon)
    val overflowMuteComingSoon = stringResource(R.string.feed_snackbar_overflow_mute_coming_soon)
    val overflowUnmuteComingSoon = stringResource(R.string.feed_snackbar_overflow_unmute_coming_soon)
    val overflowBlockComingSoon = stringResource(R.string.feed_snackbar_overflow_block_coming_soon)
    val overflowUnblockComingSoon = stringResource(R.string.feed_snackbar_overflow_unblock_coming_soon)
    val overflowMuteThreadComingSoon =
        stringResource(R.string.feed_snackbar_overflow_mute_thread_coming_soon)
    val overflowUnmuteThreadComingSoon =
        stringResource(R.string.feed_snackbar_overflow_unmute_thread_coming_soon)
    val overflowCopyTextComingSoon =
        stringResource(R.string.feed_snackbar_overflow_copy_text_coming_soon)

    // Build the shared InteractionStrings from the feed's existing string
    // resources so snackbar text stays byte-identical across the migration.
    val interactionStrings =
        InteractionStrings(
            errorNetwork = networkErrorMessage,
            errorUnauthenticated = unauthErrorMessage,
            errorUnknown = unknownErrorMessage,
            linkCopied = linkCopiedMessage,
            clipLabel = clipLabel,
            reportComingSoon = overflowReportComingSoon,
            muteComingSoon = overflowMuteComingSoon,
            unmuteComingSoon = overflowUnmuteComingSoon,
            blockComingSoon = overflowBlockComingSoon,
            unblockComingSoon = overflowUnblockComingSoon,
            muteThreadComingSoon = overflowMuteThreadComingSoon,
            unmuteThreadComingSoon = overflowUnmuteThreadComingSoon,
            copyTextComingSoon = overflowCopyTextComingSoon,
        )

    // Wire the shared helper — it collects handler.interactionEffects directly
    // (share sheet, clipboard, error/coming-soon snackbars, composer/report/block
    // navigation) so the VM no longer needs to forward those onto its own
    // effects channel.
    //
    // onInteractionError restores the deliberate "toggle-was-rejected" haptic
    // cue that previously fired inside the FeedEffect.ShowError arm. Like /
    // repost failures route through InteractionEffect.ShowError (not FeedEffect),
    // so without this hook the reject haptic would be silently lost for the
    // most common failure path.
    val interactions =
        rememberPostInteractions(
            handler = viewModel,
            snackbarHostState = snackbarHostState,
            strings = interactionStrings,
            onInteractionError = { haptics.rejected() },
        )

    // Merge the shared interaction callbacks with feed-local overrides:
    //  - onLike / onRepost / onShare — add haptics then delegate to the VM.
    //  - onReply / onQuote — haptics + screen-level callback routing.
    //  - onShareLongPress / onOverflowAction — delegate directly to the VM.
    //  - onTap / onAuthorTap / onExternalEmbedTap / onQuotedPostTap — feed
    //    screen-nav slots not present in the shared helper.
    val callbacks =
        remember(viewModel, context, haptics, interactions.callbacks) {
            interactions.callbacks.copy(
                onLike = { post ->
                    if (post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()
                    viewModel.onLike(post)
                },
                onRepost = { post ->
                    if (post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()
                    viewModel.onRepost(post)
                },
                onReply = { post ->
                    haptics.lightTap()
                    currentOnReplyClick(post.id)
                },
                onQuote = { post ->
                    haptics.lightTap()
                    currentOnQuoteClick(post.id)
                },
                onShare = { post ->
                    haptics.lightTap()
                    viewModel.onShare(post)
                },
                // Long-press already fires the system long-press haptic via
                // combinedClickable — don't double-tap the motor.
                onShareLongPress = { viewModel.onShareLongPress(it) },
                onTap = { viewModel.handleEvent(FeedEvent.OnPostTapped(it)) },
                onAuthorTap = { viewModel.handleEvent(FeedEvent.OnAuthorTapped(it.did)) },
                onExternalEmbedTap = { uri ->
                    // Narrowed catch: silent no-op only for the documented
                    // "no CCT-capable browser installed" case (per
                    // nubecita-aku scope). Other launch failures (rare
                    // SecurityException / RuntimeException from the
                    // browser lib) propagate so genuine bugs surface in
                    // logcat instead of being hidden by a blanket catch.
                    try {
                        CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, AndroidUri.parse(uri))
                    } catch (_: ActivityNotFoundException) {
                        // No browser available — silent no-op.
                    }
                },
                onQuotedPostTap = { quoted ->
                    viewModel.handleEvent(FeedEvent.OnQuotedPostTapped(quoted.uri))
                },
                onOverflowAction = { post, action ->
                    viewModel.onOverflowAction(post, action)
                },
            )
        }

    // Wrap nav callbacks so the long-lived effect collector below keys
    // on `(viewModel, snackbarHostState)` (one collector for the screen's
    // lifetime) but always calls the most recent lambda the host supplied.
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToAuthor by rememberUpdatedState(onNavigateToAuthor)
    val currentOnNavigateToMediaViewer by rememberUpdatedState(onNavigateToMediaViewer)
    val currentOnNavigateToVideoPlayer by rememberUpdatedState(onNavigateToVideoPlayer)

    // Per-PostCard image tap dispatcher. The PostCard.onImageClick slot
    // is `(Int) -> Unit` (index only); we close over each PostCard's
    // own `post` at the call site to form a `(PostUi, Int) -> Unit`
    // dispatch that the VM can turn into a NavigateToMediaViewer effect.
    val onImageTap =
        remember(viewModel) {
            { post: PostUi, index: Int -> viewModel.handleEvent(FeedEvent.OnImageTapped(post, index)) }
        }
    // Per-video tap dispatcher. Each PostCard's videoSlot lambda closes
    // over its leaf URI (parent video) or the quoted post's URI (quoted
    // video). The VM turns the event into NavigateToVideoPlayer.
    val onVideoTap =
        remember(viewModel) {
            { postUri: String -> viewModel.handleEvent(FeedEvent.OnVideoTapped(postUri)) }
        }
    // Hoist the VM-dispatching callbacks. Inline lambdas at the call site
    // would create new instances per recomposition; with the content body
    // skip-friendly (all params @Stable / @Immutable), preserving lambda
    // identity here lets it skip recomposition when only viewState changes.
    val onRefresh = remember(viewModel) { { viewModel.handleEvent(FeedEvent.Refresh) } }
    val onRetry = remember(viewModel) { { viewModel.handleEvent(FeedEvent.Retry) } }
    val onLoadMore = remember(viewModel) { { viewModel.handleEvent(FeedEvent.LoadMore) } }

    // Coordinator is hoisted here so it can receive the Hilt-injected
    // SharedVideoPlayer from the ViewModel. Keyed on viewModel so a VM
    // re-creation (process death + restore) rebuilds the coordinator
    // with the fresh holder reference. Inspection mode skips construction
    // — ExoPlayer in layoutlib is unsafe.
    val inInspection = LocalInspectionMode.current
    val appContext = context.applicationContext
    val audioManager =
        remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
    val coordinator: FeedVideoPlayerCoordinator? =
        remember(viewModel) {
            if (inInspection) {
                null
            } else {
                createFeedVideoPlayerCoordinator(
                    context = appContext,
                    audioManager = audioManager,
                    sharedVideoPlayer = viewModel.sharedVideoPlayer,
                )
            }
        }
    // Unconditional DisposableEffect — avoids calling an effect inside an
    // `if` block (a Compose anti-pattern where effect presence depends on a
    // runtime condition). coordinator is null only in LocalInspectionMode,
    // so the null-safe release is a no-op in previews/screenshot tests.
    DisposableEffect(viewModel, coordinator) {
        onDispose { coordinator?.release() }
    }

    // Composer submit-success bus. Fires when either composer host
    // (Compact NavDisplay route or Medium / Expanded Dialog overlay)
    // emits `OnSubmitSuccess`. Two side effects:
    //  - dispatch the optimistic `replyCount + 1` event when the
    //    submit was a reply, so the parent post in the feed reflects
    //    the new count without waiting for the next refresh.
    //  - show a confirmation snackbar with reply- vs new-post copy.
    //
    // `collectLatest` (not `collect`) — `showSnackbar` suspends until
    // dismissal, so back-to-back submits with plain `collect` would
    // queue behind the still-visible snackbar. `collectLatest` cancels
    // the in-flight body when a new submit arrives, dismissing the prior
    // snackbar and showing the new confirmation immediately. Keyed on
    // the flow + viewModel + snackbarHostState so the collector restarts
    // cleanly across recompositions that re-create any of those references.
    val composerSubmitEvents = LocalComposerSubmitEvents.current
    LaunchedEffect(composerSubmitEvents, viewModel, snackbarHostState) {
        composerSubmitEvents.collectLatest { event ->
            event.replyToUri?.let { parentUri ->
                viewModel.handleEvent(FeedEvent.OnReplySubmittedToParent(parentUri))
            }
            val message =
                if (event.replyToUri != null) replyPublishedMessage else postPublishedMessage
            // Replace any pending snackbar — fresh confirmation outranks
            // any stale error message lingering from a prior load.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message)
        }
    }

    // Screen-nav + mute/refresh/append failure effects. Interaction
    // effects (share, clipboard, error snackbars, composer/report/block
    // navigation) are now handled by rememberPostInteractions above —
    // only the five screen-nav variants and ShowError (from
    // mute / unmute / refresh / append failures) remain here.
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FeedEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            FeedError.Network -> networkErrorMessage
                            FeedError.Unauthenticated -> unauthErrorMessage
                            is FeedError.Unknown -> unknownErrorMessage
                        }
                    haptics.rejected()
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is FeedEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is FeedEffect.NavigateToAuthor -> currentOnNavigateToAuthor(effect.authorDid)
                is FeedEffect.NavigateToMediaViewer ->
                    currentOnNavigateToMediaViewer(effect.postUri, effect.imageIndex)
                is FeedEffect.NavigateToVideoPlayer -> currentOnNavigateToVideoPlayer(effect.postUri)
            }
        }
    }

    return FeedInteractions(
        callbacks = callbacks,
        onImageTap = onImageTap,
        onVideoTap = onVideoTap,
        onRefresh = onRefresh,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        coordinator = coordinator,
    )
}
