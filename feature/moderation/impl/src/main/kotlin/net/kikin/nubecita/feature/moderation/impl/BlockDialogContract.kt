package net.kikin.nubecita.feature.moderation.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * One frame's worth of state for the Block-account confirmation dialog.
 *
 * A single-step confirm (no multi-step flow like Report): show who's being
 * blocked, take a confirm/cancel. [isSubmitting] gates the buttons while the
 * block record is created; [hasError] surfaces an inline retryable error (the
 * dialog is self-contained — it owns its error rather than routing to the
 * caller's snackbar, which lives behind the modal sheet).
 */
@Immutable
internal data class BlockDialogState(
    val handle: String,
    val isSubmitting: Boolean = false,
    val hasError: Boolean = false,
) : UiState

internal sealed interface BlockDialogEvent : UiEvent {
    data object OnConfirmClicked : BlockDialogEvent

    data object OnCancelClicked : BlockDialogEvent
}

internal sealed interface BlockDialogEffect : UiEffect {
    /** Close the sheet + pop the NavKey (cancel, or after a successful block). */
    data object RequestDismiss : BlockDialogEffect
}
