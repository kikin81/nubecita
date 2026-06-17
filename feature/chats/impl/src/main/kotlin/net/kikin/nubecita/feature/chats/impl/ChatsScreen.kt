package net.kikin.nubecita.feature.chats.impl

import androidx.compose.material3.SnackbarHostState
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
    onNavigateToChat: (otherUserDid: String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    selectedOtherUserDid: String? = null,
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
    val currentOnNavigateToChat by rememberUpdatedState(onNavigateToChat)
    val currentOnNavigateToChatSettings by rememberUpdatedState(onNavigateToChatSettings)
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)

    LaunchedEffect(Unit) {
        fun messageFor(error: ChatsError) =
            when (error) {
                ChatsError.Network -> networkErrorMsg
                ChatsError.NotEnrolled -> notEnrolledErrorMsg
                is ChatsError.Unknown -> unknownErrorMsg
            }
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatsEffect.NavigateToChat -> currentOnNavigateToChat(effect.otherUserDid)
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
            }
        }
    }

    ChatsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::handleEvent,
        onNewChat = onNewChat,
        selectedOtherUserDid = selectedOtherUserDid,
        modifier = modifier,
    )
}
