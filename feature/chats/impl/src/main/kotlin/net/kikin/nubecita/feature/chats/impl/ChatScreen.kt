package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 *
 * [onNavigateToPost] receives the AT URI of a tapped quoted-post embed
 * under a message bubble; the entry point in `ChatsNavigationModule`
 * pushes `PostDetailRoute` onto `LocalMainShellNavState`.
 */
@Composable
internal fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPost: (postUri: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
            }
        }
    }

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
