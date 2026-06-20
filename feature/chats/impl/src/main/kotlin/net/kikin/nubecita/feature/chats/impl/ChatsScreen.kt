package net.kikin.nubecita.feature.chats.impl

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Stateful Chats tab-home entry. Owns the [ChatsViewModel] + effect
 * collector + snackbar host. Delegates rendering to [ChatsScreenContent].
 *
 * [onNavigateToChat] is invoked when the user taps a convo row or the
 * VM emits `NavigateToChat`. [onNavigateTo] forwards a tab-internal
 * sub-route NavKey (Profile / Report / Block from the contextual action
 * menu) — the entry provider wires it to `navState.add(key)`, mirroring
 * the Feed overflow pattern.
 */
@Composable
internal fun ChatsScreen(
    onNavigateToChat: (convoId: String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    selectedConvoId: String? = null,
    onNavigateToChatSettings: () -> Unit = {},
    onNavigateTo: (NavKey) -> Unit = {},
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Pre-resolve snackbar copy via stringResource() at composition time so locale + dark-mode
    // changes participate in recomposition. Reading via context.getString(...) inside the
    // LaunchedEffect would bypass Compose's resource tracking.
    val networkErrorMsg = stringResource(R.string.chats_error_network_body)
    val notEnrolledErrorMsg = stringResource(R.string.chats_error_not_enrolled_body)
    val unknownErrorMsg = stringResource(R.string.chats_error_unknown_body)
    // Pre-resolved leave-undo copy. Singular/plural picked by count (no number in
    // the text), so two fixed strings suffice — keeps locale + dark-mode tracking
    // (no context.getString in the effect collector, no count-bearing plural).
    val leaveUndoOneMsg = stringResource(R.string.chats_leave_undo_one)
    val leaveUndoManyMsg = stringResource(R.string.chats_leave_undo_many)
    val leaveUndoActionLabel = stringResource(R.string.chats_leave_undo_action)
    val currentOnNavigateToChat by rememberUpdatedState(onNavigateToChat)
    val currentOnNavigateToChatSettings by rememberUpdatedState(onNavigateToChatSettings)
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)

    LaunchedEffect(Unit) {
        val effectScope = this
        // The single in-flight leave-undo snackbar coroutine; cancelled before a new
        // one shows (supersede) and on HideLeaveUndo, so stale coroutines never queue.
        var leaveUndoJob: Job? = null

        fun messageFor(error: ChatsError) =
            when (error) {
                ChatsError.Network -> networkErrorMsg
                ChatsError.NotEnrolled -> notEnrolledErrorMsg
                is ChatsError.Unknown -> unknownErrorMsg
            }
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatsEffect.NavigateToChat -> currentOnNavigateToChat(effect.convoId)
                ChatsEffect.NavigateToChatSettings -> currentOnNavigateToChatSettings()
                is ChatsEffect.NavigateTo -> currentOnNavigateTo(effect.key)
                is ChatsEffect.ShowRefreshError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(messageFor(effect.error))
                }
                is ChatsEffect.ShowActionError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(messageFor(effect.error))
                }
                is ChatsEffect.ShowLeaveUndo -> {
                    // Launch on the effect's own scope so the suspending showSnackbar
                    // (Indefinite — the VM's timer drives commit/dismissal) doesn't
                    // block the effect collector for the whole undo window. Cancel any
                    // prior snackbar coroutine first so they can't pile up on supersede.
                    leaveUndoJob?.cancel()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val message = if (effect.count == 1) leaveUndoOneMsg else leaveUndoManyMsg
                    leaveUndoJob =
                        effectScope.launch {
                            val result =
                                snackbarHostState.showSnackbar(
                                    message = message,
                                    actionLabel = leaveUndoActionLabel,
                                    duration = SnackbarDuration.Indefinite,
                                )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.handleEvent(ChatsEvent.UndoLeaveTapped(effect.token))
                            }
                        }
                }
                ChatsEffect.HideLeaveUndo -> {
                    leaveUndoJob?.cancel()
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
            }
        }
    }

    ChatsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::handleEvent,
        onNewChat = onNewChat,
        selectedConvoId = selectedConvoId,
        modifier = modifier,
    )
}
