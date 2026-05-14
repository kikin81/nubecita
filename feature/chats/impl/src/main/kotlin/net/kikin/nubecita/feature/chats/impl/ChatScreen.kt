package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful entry for the chat thread screen. Owns the [ChatViewModel],
 * forwards events, and translates `BackPressed` into [onNavigateBack].
 */
@Composable
internal fun ChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    ChatScreenContent(
        state = state,
        onEvent = { event ->
            if (event is ChatEvent.BackPressed) {
                currentOnNavigateBack()
            } else {
                viewModel.handleEvent(event)
            }
        },
        modifier = modifier,
    )
}
