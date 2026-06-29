package net.kikin.nubecita.core.postinteractions.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.postinteractions.InteractionEffect
import net.kikin.nubecita.core.postinteractions.InteractionError
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.core.postinteractions.sharing.launchPostShare
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.moderation.api.Block
import net.kikin.nubecita.feature.moderation.api.Report

/**
 * Pre-resolved snackbar / clipboard strings for [rememberPostInteractions].
 *
 * Callers resolve every field via `stringResource(...)` at composition time
 * and pass the resulting [InteractionStrings] in. This keeps
 * `:core:post-interactions-ui` resource-free (no `R.string.*` of its own)
 * and lets each surface keep its OWN existing string values — PR1's feed
 * migration passes its current `R.string.feed_snackbar_*` values so
 * snackbar text stays byte-identical, while later surfaces pass theirs.
 *
 * @property errorNetwork Copy shown when a like/repost toggle fails due to a
 *   network/transport error.
 * @property errorUnauthenticated Copy shown when the user's session has expired.
 * @property errorUnknown Copy shown for all other toggle failures.
 * @property linkCopied Copy shown after a permalink is copied to the clipboard.
 * @property clipLabel Label used as the `ClipData` description (visible in
 *   clipboard history on Android 13+).
 * @property reportComingSoon "Coming soon" copy for [PostOverflowAction.ReportPost].
 * @property muteComingSoon "Coming soon" copy for [PostOverflowAction.MuteAuthor].
 * @property unmuteComingSoon "Coming soon" copy for [PostOverflowAction.UnmuteAuthor].
 * @property blockComingSoon "Coming soon" copy for [PostOverflowAction.BlockAuthor].
 * @property unblockComingSoon "Coming soon" copy for [PostOverflowAction.UnblockAuthor].
 * @property muteThreadComingSoon "Coming soon" copy for [PostOverflowAction.MuteThread].
 * @property unmuteThreadComingSoon "Coming soon" copy for [PostOverflowAction.UnmuteThread].
 * @property copyTextComingSoon "Coming soon" copy for [PostOverflowAction.CopyPostText].
 */
data class InteractionStrings(
    val errorNetwork: String,
    val errorUnauthenticated: String,
    val errorUnknown: String,
    val linkCopied: String,
    val clipLabel: String,
    val reportComingSoon: String,
    val muteComingSoon: String,
    val unmuteComingSoon: String,
    val blockComingSoon: String,
    val unblockComingSoon: String,
    val muteThreadComingSoon: String,
    val unmuteThreadComingSoon: String,
    val copyTextComingSoon: String,
)

/**
 * Derived state returned by [rememberPostInteractions].
 *
 * @property callbacks The [PostCallbacks] block to pass to every [net.kikin.nubecita.designsystem.component.PostCard]
 *   on the screen.
 * @property tapMarkers The most-recently-tapped post URIs for like / repost,
 *   updated synchronously on each tap so the count ±1 animation fires before
 *   the network resolves.
 */
data class PostInteractions(
    val callbacks: PostCallbacks,
    val tapMarkers: PostTapMarkers,
)

/**
 * Feature-agnostic Compose helper that wires a [PostInteractionHandler] into
 * a screen's [PostCallbacks] block, tap-marker state, and effect collector
 * (snackbars + navigation + share sheet + clipboard).
 *
 * This is the generalization of `rememberFeedInteractions`'s post-interaction
 * half (excluding the feed-local video-coordinator and composer-submit bus).
 * Each feed-like surface calls this once at the top of its `@Composable`
 * function and unpacks the returned [PostInteractions] into the stateless
 * content composable below.
 *
 * **Effect mapping:**
 * - [InteractionEffect.ShowError] → replace-not-stack snackbar (dismisses any
 *   current snackbar then shows the error copy from [strings]).
 * - [InteractionEffect.SharePost] → fire the system share sheet via
 *   `Context.launchPostShare`.
 * - [InteractionEffect.CopyPermalink] → write to [ClipboardManager] + show a
 *   replace-not-stack snackbar with [InteractionStrings.linkCopied].
 * - [InteractionEffect.ShowComingSoon] → replace-not-stack snackbar with the
 *   per-action copy from [strings].
 * - [InteractionEffect.NavigateToComposer] → push [ComposerRoute] onto
 *   [LocalMainShellNavState].
 * - [InteractionEffect.NavigateToReport] → push [Report.forPost] onto
 *   [LocalMainShellNavState].
 * - [InteractionEffect.NavigateToBlock] → push [Block.forAccount] onto
 *   [LocalMainShellNavState].
 *
 * @param handler The [PostInteractionHandler] bound in the ViewModel's `init`.
 * @param snackbarHostState The screen's [SnackbarHostState]; snackbars from
 *   this helper replace any currently visible snackbar (replace-not-stack).
 * @param strings All snackbar / clipboard strings pre-resolved via
 *   `stringResource(...)` by the caller so locale changes participate in
 *   recomposition.
 */
@Composable
fun rememberPostInteractions(
    handler: PostInteractionHandler,
    snackbarHostState: SnackbarHostState,
    strings: InteractionStrings,
): PostInteractions {
    val context = LocalContext.current
    val navState = LocalMainShellNavState.current

    val callbacks =
        remember(handler) {
            PostCallbacks(
                onLike = { post -> handler.onLike(post) },
                onRepost = { post -> handler.onRepost(post) },
                onQuote = { post -> handler.onQuote(post) },
                onReply = { post -> handler.onReply(post) },
                onShare = { post -> handler.onShare(post) },
                onShareLongPress = { post -> handler.onShareLongPress(post) },
                onOverflowAction = { post, action -> handler.onOverflowAction(post, action) },
            )
        }

    val tapMarkers by handler.tapMarkers.collectAsStateWithLifecycle()

    val clipboardManager =
        remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }

    LaunchedEffect(handler, snackbarHostState) {
        handler.interactionEffects.collect { effect ->
            when (effect) {
                is InteractionEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            InteractionError.Network -> strings.errorNetwork
                            InteractionError.Unauthenticated -> strings.errorUnauthenticated
                            InteractionError.Unknown -> strings.errorUnknown
                        }
                    // Replace, don't stack — successive errors during a flapping
                    // network spell would otherwise queue snackbars indefinitely.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }

                is InteractionEffect.SharePost -> context.launchPostShare(effect.intent)

                is InteractionEffect.CopyPermalink -> {
                    clipboardManager.setPrimaryClip(
                        ClipData.newPlainText(strings.clipLabel, effect.permalink),
                    )
                    // Replace any pending error snackbar — a fresh "link
                    // copied" confirmation outranks a stale error message
                    // for the moment the user just took action.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = strings.linkCopied)
                }

                is InteractionEffect.ShowComingSoon -> {
                    val message =
                        when (effect.action) {
                            PostOverflowAction.ReportPost -> strings.reportComingSoon
                            PostOverflowAction.MuteAuthor -> strings.muteComingSoon
                            PostOverflowAction.UnmuteAuthor -> strings.unmuteComingSoon
                            PostOverflowAction.BlockAuthor -> strings.blockComingSoon
                            PostOverflowAction.UnblockAuthor -> strings.unblockComingSoon
                            PostOverflowAction.MuteThread -> strings.muteThreadComingSoon
                            PostOverflowAction.UnmuteThread -> strings.unmuteThreadComingSoon
                            PostOverflowAction.CopyPostText -> strings.copyTextComingSoon
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }

                is InteractionEffect.NavigateToComposer ->
                    navState.add(
                        ComposerRoute(
                            replyToUri = effect.replyToUri,
                            quotePostUri = effect.quoteUri,
                        ),
                    )

                is InteractionEffect.NavigateToReport ->
                    navState.add(Report.forPost(effect.post))

                is InteractionEffect.NavigateToBlock ->
                    navState.add(Block.forAccount(did = effect.did, handle = effect.handle))
            }
        }
    }

    return PostInteractions(
        callbacks = callbacks,
        tapMarkers = tapMarkers,
    )
}
