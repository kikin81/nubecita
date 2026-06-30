package net.kikin.nubecita.feature.profile.impl

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import android.net.Uri as AndroidUri

/**
 * Stateful Profile screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * effects collector that surfaces snackbars + nav callbacks, the
 * LazyListState hoisted via rememberSaveable (for back-nav and
 * config-change retention), the screen-internal SnackbarHostState,
 * and the remember-d PostCallbacks that dispatch to the VM.
 * Delegates rendering to [ProfileScreenContent], which previews and
 * screenshot tests call directly with fixture inputs.
 *
 * Suppresses [compose:vm-forwarding-check] (ktlint) and
 * [ComposeViewModelForwarding] (slack compose-lints 1.5.0+).
 * Stateful-Screen / Stateless-Content split per CLAUDE.md §MVI:
 * `ProfileScreen` owns the VM and delegates rendering to
 * `ProfileScreenContent`, which takes typed state + callbacks. 1.5.0
 * broadened the rule to trace lambda captures, making the pattern
 * unsuppressible without refactoring every screen. Matches the
 * precedent on `ComposerScreen` and the other MVI Root/Content screens.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToPost: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMessage: (String) -> Unit,
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit,
    onNavigateToVideoPlayer: (postUri: String) -> Unit,
    /**
     * Generic tab-internal sub-route push callback. The host
     * (`ProfileNavigationModule`) wires it to
     * `LocalMainShellNavState.current.add(key)`. The screen stays
     * host-agnostic — the callback shape matches the equivalent slot
     * on `FeedScreen` (see `FeedNavigationModule` for the canonical
     * recipe).
     *
     * Today's only emission is `Report(...)` from the ProfileHero
     * overflow "Report account" row (`nubecita-oftc.3`). Future
     * moderation children (`oftc.4` Block / `oftc.5` Mute confirmation
     * sheets) will travel the same callback with their own NavKey
     * types — no per-feature callback proliferation needed.
     */
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }

    // Profile-tab re-tap: scroll the list back to position 0. Same shape
    // as FeedScreen's collector — see `LocalTabReTapSignal` KDoc for the
    // signal contract. The default empty SharedFlow (no provider in
    // previews / screenshot tests) never emits.
    val tabReTapSignal = LocalTabReTapSignal.current
    LaunchedEffect(tabReTapSignal, listState) {
        tabReTapSignal.collect { listState.animateScrollToItem(0) }
    }

    val context = LocalContext.current
    val haptics = rememberPostHaptics()

    // Pre-resolve snackbar copy via stringResource() so locale changes
    // recompose. Reading via context.getString() inside the LaunchedEffect
    // would bypass Compose's resource tracking (LocalContextGetResourceValueCall).
    val errorNetworkMsg = stringResource(R.string.profile_snackbar_error_network)
    val errorUnknownMsg = stringResource(R.string.profile_snackbar_error_unknown)
    val comingSoonEdit = stringResource(R.string.profile_snackbar_edit_coming_soon)
    val comingSoonBlock = stringResource(R.string.profile_snackbar_block_coming_soon)
    // BlockAuthor still routes through ProfileEffect.ShowPostOverflowComingSoon
    // (PR3 — block→real lands in PR4 nubecita-tgqv). The remaining overflow
    // coming-soon strings (unblock/mute-thread/unmute-thread/copy-text) now
    // live inside InteractionStrings and are shown via rememberPostInteractions.
    val postOverflowBlock =
        stringResource(R.string.profile_snackbar_post_overflow_block_coming_soon)

    // Per-action strings for the shared rememberPostInteractions helper.
    // Each field is resolved from the module's existing R.string values so
    // snackbar text stays byte-identical. Several fields are placeholder
    // values only — the corresponding InteractionEffect variants are never
    // emitted by the profile handler because the VM intercepts those actions
    // before they reach the handler (see ProfileViewModel.onOverflowAction).
    val interactionStrings =
        InteractionStrings(
            errorNetwork = errorNetworkMsg,
            // No profile-specific auth error string; fall back to the generic unknown copy.
            errorUnauthenticated = errorUnknownMsg,
            errorUnknown = errorUnknownMsg,
            linkCopied = stringResource(R.string.profile_snackbar_link_copied),
            clipLabel = stringResource(R.string.profile_clipboard_label_post_link),
            // ReportPost → NavigateToReport via handler; this string is never shown.
            reportComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_unblock_coming_soon),
            // MuteAuthor/UnmuteAuthor are intercepted by the VM override; never shown.
            muteComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_mute_thread_coming_soon),
            unmuteComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_unmute_thread_coming_soon),
            // BlockAuthor is intercepted by the VM override (coming-soon via ProfileEffect);
            // this field is never shown via the handler path.
            blockComingSoon = postOverflowBlock,
            unblockComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_unblock_coming_soon),
            muteThreadComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_mute_thread_coming_soon),
            unmuteThreadComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_unmute_thread_coming_soon),
            copyTextComingSoon =
                stringResource(R.string.profile_snackbar_post_overflow_copy_text_coming_soon),
        )

    // Wire the shared post-interaction helper. Handles share sheet, clipboard,
    // coming-soon snackbars, and navigation to Composer / Report / Block via
    // handler.interactionEffects. Returns base PostCallbacks + tapMarkers.
    val interactions =
        rememberPostInteractions(
            handler = viewModel,
            snackbarHostState = snackbarHostState,
            strings = interactionStrings,
            onInteractionError = { haptics.rejected() },
        )

    // Screen-specific overrides layered on top of the shared base callbacks.
    // Tap/author/embed/quoted-post routing is profile-local (these go through
    // ProfileEvent/ProfileEffect, not InteractionEffect). Like/repost/reply/
    // quote/share add haptic feedback and call viewModel.on*() directly
    // (delegation to the injected PostInteractionHandler via `by handler`).
    val callbacks =
        remember(viewModel, context, haptics, interactions.callbacks) {
            interactions.callbacks.copy(
                onTap = { post -> viewModel.handleEvent(ProfileEvent.PostTapped(post.id)) },
                onAuthorTap = { author ->
                    viewModel.handleEvent(ProfileEvent.HandleTapped(author.handle))
                },
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
                    viewModel.onReply(post)
                },
                onQuote = { post ->
                    haptics.lightTap()
                    viewModel.onQuote(post)
                },
                onShare = { post ->
                    haptics.lightTap()
                    viewModel.onShare(post)
                },
                // Long-press fires the system long-press haptic via
                // combinedClickable — don't double-tap the motor.
                onShareLongPress = { post -> viewModel.onShareLongPress(post) },
                onExternalEmbedTap = { uri ->
                    // Narrowed catch: silent no-op only for the "no CCT-capable
                    // browser installed" case. Other launch failures propagate
                    // so genuine bugs surface in logcat. Mirrors FeedScreen.
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
                    viewModel.handleEvent(ProfileEvent.OnQuotedPostTapped(quoted.uri))
                },
                onOverflowAction = { post, action ->
                    viewModel.handleEvent(ProfileEvent.OnPostOverflowAction(post, action))
                },
            )
        }

    // Wrap nav callbacks so the long-lived effect collector keys on Unit
    // (one collector for the screen's lifetime) but always calls the most
    // recent lambda the host supplied.
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToProfile by rememberUpdatedState(onNavigateToProfile)
    val currentOnNavigateToSettings by rememberUpdatedState(onNavigateToSettings)
    val currentOnNavigateToMessage by rememberUpdatedState(onNavigateToMessage)
    val currentOnNavigateToMediaViewer by rememberUpdatedState(onNavigateToMediaViewer)
    val currentOnNavigateToVideoPlayer by rememberUpdatedState(onNavigateToVideoPlayer)
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)
    // Wrap snackbar copy for the same reason: the Unit-keyed effect never
    // restarts, so a locale/config change mid-pending-snackbar would show
    // stale text without rememberUpdatedState. The stringResource() resolution
    // above stays as-is (used outside the effect for interactionStrings).
    val currentErrorNetworkMsg by rememberUpdatedState(errorNetworkMsg)
    val currentErrorUnknownMsg by rememberUpdatedState(errorUnknownMsg)
    val currentComingSoonEdit by rememberUpdatedState(comingSoonEdit)
    val currentComingSoonBlock by rememberUpdatedState(comingSoonBlock)
    val currentPostOverflowBlock by rememberUpdatedState(postOverflowBlock)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ProfileError.Network -> currentErrorNetworkMsg
                            is ProfileError.Unknown -> currentErrorUnknownMsg
                        }
                    // ProfileEffect.ShowError is most often a toggle / follow
                    // failure (user-tap rejected). Over-fires slightly on
                    // background header-refresh failures, which is acceptable
                    // noise — header refresh is rare and a reject buzz on a
                    // failed sync isn't misleading.
                    haptics.rejected()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.ShowComingSoon -> {
                    val msg =
                        when (effect.action) {
                            StubbedAction.Edit -> currentComingSoonEdit
                            StubbedAction.Block -> currentComingSoonBlock
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.ShowPostOverflowComingSoon -> {
                    // Only BlockAuthor still routes through this VM effect (PR3).
                    // UnblockAuthor/MuteThread/UnmuteThread/CopyPostText are now
                    // delegated to the handler and shown via rememberPostInteractions.
                    // ReportPost/MuteAuthor/UnmuteAuthor are intercepted by the VM's
                    // onOverflowAction override and never reach ShowPostOverflowComingSoon.
                    if (effect.action == PostOverflowAction.BlockAuthor) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message = currentPostOverflowBlock)
                    }
                }
                is ProfileEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is ProfileEffect.NavigateToProfile -> currentOnNavigateToProfile(effect.handle)
                ProfileEffect.NavigateToSettings -> currentOnNavigateToSettings()
                is ProfileEffect.NavigateToMessage ->
                    currentOnNavigateToMessage(effect.otherUserDid)
                is ProfileEffect.NavigateToMediaViewer ->
                    currentOnNavigateToMediaViewer(effect.postUri, effect.imageIndex)
                is ProfileEffect.NavigateToVideoPlayer ->
                    currentOnNavigateToVideoPlayer(effect.postUri)
                is ProfileEffect.NavigateTo -> currentOnNavigateTo(effect.key)
            }
        }
    }

    val mainShellNavState = LocalMainShellNavState.current
    val onBack: (() -> Unit)? =
        if (state.handle != null) {
            { mainShellNavState.removeLast() }
        } else {
            null
        }

    ProfileScreenContent(
        state = state,
        listState = listState,
        snackbarHostState = snackbarHostState,
        postCallbacks = callbacks,
        onEvent = viewModel::handleEvent,
        onBack = onBack,
        modifier = modifier,
    )
}
