package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful entry for the chat thread screen. Owns the [ChatViewModel],
 * forwards events, and translates `BackPressed` into [onNavigateBack].
 *
 * The VM is passed in (not resolved via `hiltViewModel()` here) because
 * it's assisted-injected with the [net.kikin.nubecita.feature.chats.api.Chat]
 * NavKey — the navigation entry point in `ChatsNavigationModule` wires up
 * the factory with the route's `otherUserDid`. Mirrors the precedent set
 * by `ComposerScreen` / `ComposerNavigationModule`.
 */
@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
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
