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

/**
 * Stateful Chats tab-home entry. Owns the [ChatsViewModel] + effect
 * collector + snackbar host. Delegates rendering to [ChatsScreenContent].
 *
 * [onNavigateToChat] is invoked when the user taps a convo row or the
 * VM emits `NavigateToChat`. The caller (the `:feature:chats:impl/di`
 * EntryProviderInstaller for nn3.1; nn3.2's entry provider when that
 * lands) is responsible for translating the DID into a real navigation
 * call (e.g. `MainShellNavState.add(Chat(did))` once the Chat NavKey
 * exists).
 */
@Composable
internal fun ChatsScreen(
    onNavigateToChat: (otherUserDid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshErrorMsg = stringResource(R.string.chats_error_network_body)
    val currentOnNavigateToChat by rememberUpdatedState(onNavigateToChat)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatsEffect.NavigateToChat -> currentOnNavigateToChat(effect.otherUserDid)
                is ChatsEffect.ShowRefreshError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(refreshErrorMsg)
                }
            }
        }
    }

    ChatsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
