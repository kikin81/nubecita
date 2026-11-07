package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.core.postinteractions.ui.InteractionStrings
import net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.R as DesignSystemR

/**
 * Wires the overlay's actions to the ViewModel's delegated handler. Strings are
 * resolved here because lint forbids `context.getString` inside the effect, and
 * the viewer sits off `MainShell`, so it supplies its own [onNavigate].
 */
@Composable
internal fun rememberMediaViewerInteractions(
    viewModel: MediaViewerViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigate: (NavKey) -> Unit,
): PostCallbacks {
    val strings =
        InteractionStrings(
            errorNetwork = stringResource(R.string.mediaviewer_snackbar_error_network),
            errorUnauthenticated = stringResource(R.string.mediaviewer_snackbar_error_unauthenticated),
            errorUnknown = stringResource(R.string.mediaviewer_snackbar_error_unknown),
            linkCopied = stringResource(R.string.mediaviewer_snackbar_link_copied),
            clipLabel = stringResource(R.string.mediaviewer_clipboard_label_post_link),
            reportComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_report_coming_soon),
            muteComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_mute_coming_soon),
            unmuteComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_unmute_coming_soon),
            blockComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_block_coming_soon),
            unblockComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_unblock_coming_soon),
            muteThreadComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_mute_thread_coming_soon),
            unmuteThreadComingSoon = stringResource(R.string.mediaviewer_snackbar_overflow_unmute_thread_coming_soon),
            textCopied = stringResource(R.string.mediaviewer_snackbar_text_copied),
            deleteTitle = stringResource(DesignSystemR.string.postcard_delete_dialog_title),
            deleteBody = stringResource(DesignSystemR.string.postcard_delete_dialog_body),
            deleteConfirm = stringResource(DesignSystemR.string.postcard_delete_dialog_confirm),
            deleteCancel = stringResource(DesignSystemR.string.postcard_delete_dialog_cancel),
            deleteSuccess = stringResource(DesignSystemR.string.postcard_delete_success),
            translationUnavailable = stringResource(DesignSystemR.string.postcard_translation_unavailable),
        )
    return rememberPostInteractions(
        handler = viewModel,
        snackbarHostState = snackbarHostState,
        strings = strings,
        onNavigate = onNavigate,
    ).callbacks
}
