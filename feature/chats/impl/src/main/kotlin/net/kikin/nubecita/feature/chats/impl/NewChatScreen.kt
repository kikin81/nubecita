package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.ui.RecipientRow

/**
 * Stateful entry for the NewChat recipient-picker screen. Owns the
 * [NewChatViewModel], collects [NewChatEffect]s, and delegates rendering
 * to the stateless [NewChatScreenContent].
 *
 * On [NewChatEffect.OpenChat], the IME focus is cleared before calling
 * `navState.replaceTop(Chat(did))` so the keyboard is dismissed cleanly
 * as the transition to the chat thread plays.
 */
@Composable
internal fun NewChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val focusManager = LocalFocusManager.current
    val currentNavState by rememberUpdatedState(navState)
    val currentFocusManager by rememberUpdatedState(focusManager)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewChatEffect.OpenChat -> {
                    currentFocusManager.clearFocus()
                    currentNavState.replaceTop(Chat(otherUserDid = effect.otherUserDid))
                }
            }
        }
    }

    NewChatScreenContent(
        state = state,
        queryFieldState = viewModel.queryFieldState,
        onEvent = viewModel::handleEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless content for the NewChat recipient-picker screen. Accepts
 * fixture inputs for @Preview and screenshot-test rendering.
 *
 * Layout:
 * - [TopAppBar] with a back nav icon and "New message" title.
 * - Search [OutlinedTextField] pinned below the top bar.
 * - Body driven by [NewChatState.status]:
 *   - [NewChatStatus.Recent]: sticky "Recent" header + [RecipientRow] list
 *     (empty → nothing shown).
 *   - [NewChatStatus.Searching]: centered [CircularProgressIndicator].
 *   - [NewChatStatus.Results]: [RecipientRow] list, no header.
 *   - [NewChatStatus.NoResults]: centered "No people found" message.
 *   - [NewChatStatus.Error]: centered error + Retry [Button].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewChatScreenContent(
    state: NewChatState,
    queryFieldState: TextFieldState,
    onEvent: (NewChatEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.new_chat_back_content_description),
                            filled = true,
                            modifier = Modifier.mirror(),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
        ) {
            OutlinedTextField(
                state = queryFieldState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.new_chat_search_placeholder)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = {
                    NubecitaIcon(
                        name = NubecitaIconName.Search,
                        contentDescription = null,
                    )
                },
            )

            when (val s = state.status) {
                is NewChatStatus.Recent -> {
                    if (s.items.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item(key = "recent_header") {
                                Text(
                                    text = stringResource(R.string.new_chat_recent_header),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier.padding(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 8.dp,
                                            bottom = 4.dp,
                                        ),
                                )
                            }
                            items(
                                items = s.items,
                                key = { it.did },
                            ) { actor ->
                                RecipientRow(
                                    actor = actor,
                                    onClick = { onEvent(NewChatEvent.RecipientSelected(actor.did)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                NewChatStatus.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is NewChatStatus.Results -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = s.items,
                            key = { it.did },
                        ) { actor ->
                            RecipientRow(
                                actor = actor,
                                onClick = { onEvent(NewChatEvent.RecipientSelected(actor.did)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                NewChatStatus.NoResults -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.new_chat_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                NewChatStatus.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.new_chat_error_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = { onEvent(NewChatEvent.RetryClicked) },
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.new_chat_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview helpers
// ---------------------------------------------------------------------------

private val FIXTURE_ACTORS =
    persistentListOf(
        ActorUi(
            did = "did:plc:alice",
            handle = "alice.bsky.social",
            displayName = "Alice Liddell",
            avatarUrl = null,
        ),
        ActorUi(
            did = "did:plc:bob",
            handle = "bob.bsky.social",
            displayName = "Bob",
            avatarUrl = null,
        ),
    )

@Preview(showBackground = true)
@Composable
private fun NewChatScreenRecentPreview() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Recent(FIXTURE_ACTORS)),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NewChatScreenSearchingPreview() {
    NubecitaTheme(dynamicColor = false) {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Searching),
            queryFieldState = TextFieldState(initialText = "ali"),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NewChatScreenResultsPreview() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Results(FIXTURE_ACTORS)),
            queryFieldState = TextFieldState(initialText = "alice"),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NewChatScreenNoResultsPreview() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.NoResults),
            queryFieldState = TextFieldState(initialText = "xyzzy"),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NewChatScreenErrorPreview() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Error),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onBack = {},
        )
    }
}
