package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.feature.videos.impl.R
import net.kikin.nubecita.feature.videos.impl.VideoFeedViewModel

/**
 * Wires the vertical feed's chrome to the ViewModel's delegated
 * `PostInteractionHandler`.
 *
 * The returned callbacks come from the shared helper, which also OWNS the
 * interaction-effect collector — share sheet, clipboard, error snackbars, and
 * composer / report / block navigation. The ViewModel must not forward those
 * onto its own effect channel; that channel carries screen navigation only.
 *
 * All 13 strings are resolved here at composition time rather than inside the
 * effect: lint's `LocalContextGetResourceValueCall` forbids `context.getString`
 * in that position, and `:core:post-interactions-ui` ships no resources of its
 * own, so every surface supplies its own copy.
 */
@Composable
internal fun rememberVideoFeedInteractions(
    viewModel: VideoFeedViewModel,
    snackbarHostState: SnackbarHostState,
): PostCallbacks {
    val strings =
        InteractionStrings(
            errorNetwork = stringResource(R.string.videos_snackbar_error_network),
            errorUnauthenticated = stringResource(R.string.videos_snackbar_error_unauthenticated),
            errorUnknown = stringResource(R.string.videos_snackbar_error_unknown),
            linkCopied = stringResource(R.string.videos_snackbar_link_copied),
            clipLabel = stringResource(R.string.videos_clipboard_label_post_link),
            reportComingSoon = stringResource(R.string.videos_snackbar_overflow_report_coming_soon),
            muteComingSoon = stringResource(R.string.videos_snackbar_overflow_mute_coming_soon),
            unmuteComingSoon = stringResource(R.string.videos_snackbar_overflow_unmute_coming_soon),
            blockComingSoon = stringResource(R.string.videos_snackbar_overflow_block_coming_soon),
            unblockComingSoon = stringResource(R.string.videos_snackbar_overflow_unblock_coming_soon),
            muteThreadComingSoon = stringResource(R.string.videos_snackbar_overflow_mute_thread_coming_soon),
            unmuteThreadComingSoon = stringResource(R.string.videos_snackbar_overflow_unmute_thread_coming_soon),
            textCopied = stringResource(R.string.videos_snackbar_text_copied),
        )
    return rememberPostInteractions(
        handler = viewModel,
        snackbarHostState = snackbarHostState,
        strings = strings,
    ).callbacks
}
