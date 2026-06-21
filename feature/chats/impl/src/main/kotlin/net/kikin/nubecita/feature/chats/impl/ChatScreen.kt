package net.kikin.nubecita.feature.chats.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

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
    onNavigateToGroupDetails: (convoId: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToGroupDetails by rememberUpdatedState(onNavigateToGroupDetails)
    // Pre-resolve snackbar copy at composition so locale + dark-mode changes
    // participate in recomposition (reading via context.getString inside the
    // LaunchedEffect would bypass Compose's resource tracking). Mirrors
    // ChatsScreen's ShowRefreshError wiring.
    val networkErrorMsg = stringResource(R.string.chat_send_error_network)
    val messagesDisabledErrorMsg = stringResource(R.string.chat_send_error_messages_disabled)
    val genericErrorMsg = stringResource(R.string.chat_send_error_generic)
    val reactionErrorMsg = stringResource(R.string.chat_reaction_error)

    LaunchedEffect(Unit) {
        // The effect collector drains a single stream that carries both
        // navigation (NavigateToPost) and the send-error snackbar. showSnackbar
        // suspends until the snackbar is dismissed (~4s), so showing it inline
        // would head-of-line-block a NavigateToPost queued right behind it —
        // e.g. tapping a quoted-post embed just after a send fails. Launch the
        // snackbar in a child coroutine so navigation effects dispatch promptly.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is ChatEffect.NavigateToGroupDetails -> currentOnNavigateToGroupDetails(effect.convoId)
                is ChatEffect.ShowSendError -> {
                    val message =
                        when (effect.error) {
                            ChatError.Network -> networkErrorMsg
                            ChatError.MessagesDisabled -> messagesDisabledErrorMsg
                            ChatError.NotEnrolled,
                            ChatError.ConvoNotFound,
                            ChatError.GroupFull,
                            ChatError.FollowRequiredToAdd,
                            ChatError.InsufficientPermission,
                            is ChatError.Unknown,
                            -> genericErrorMsg
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }
                ChatEffect.ShowReactionError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(reactionErrorMsg)
                    }
            }
        }
    }

    ChatScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = { event ->
            if (event is ChatEvent.BackPressed) {
                currentOnNavigateBack()
            } else {
                viewModel.handleEvent(event)
            }
        },
        textFieldState = viewModel.textFieldState,
        modifier = modifier,
    )
}
