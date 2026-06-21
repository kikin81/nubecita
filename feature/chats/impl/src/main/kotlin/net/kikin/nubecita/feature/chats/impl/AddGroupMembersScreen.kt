package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.impl.ui.RecipientRow

/**
 * Stateful entry for the add-group-members recipient picker. Owns the
 * [AddGroupMembersViewModel], collects [AddMembersEffect]s into a snackbar
 * host, and delegates rendering to the stateless [AddGroupMembersScreenContent].
 *
 * Error copy is pre-resolved at composition (mirroring [ChatScreen]) so the
 * effect collector — which runs outside the composition — can map a
 * [ChatError] to a localized message without a `stringResource` call. On
 * [AddMembersEffect.MembersAdded] the current picked count is read from
 * `viewModel.uiState.value.selected.size` (not the captured [state]) to avoid a
 * stale snapshot, then handed to `onAdded`.
 *
 * `onAdded` keeps its past-tense name intentionally (it fires after the add
 * succeeds), so the present-tense lambda-naming rule is suppressed here.
 */
@Suppress("ktlint:compose:parameter-naming")
@Composable
internal fun AddGroupMembersScreen(
    viewModel: AddGroupMembersViewModel,
    onAdded: (count: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val fullMsg = stringResource(R.string.add_members_error_full)
    val followMsg = stringResource(R.string.add_members_error_follow_required)
    val permMsg = stringResource(R.string.add_members_error_permission)
    val genericMsg = stringResource(R.string.add_members_error_generic)

    val currentOnAdded by rememberUpdatedState(onAdded)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(Unit) {
        // showSnackbar suspends until the snackbar is dismissed (~4s); launch it in a
        // child coroutine so a MembersAdded (pop) queued right behind a ShowError —
        // e.g. a fail → re-select → succeed within that window — isn't head-of-line
        // blocked. Mirrors ChatScreen's effect collector.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddMembersEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ChatError.GroupFull -> fullMsg
                            ChatError.FollowRequiredToAdd -> followMsg
                            ChatError.InsufficientPermission -> permMsg
                            else -> genericMsg
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                AddMembersEffect.MembersAdded ->
                    currentOnAdded(viewModel.uiState.value.selected.size)
            }
        }
    }

    AddGroupMembersScreenContent(
        state = state,
        queryFieldState = viewModel.queryFieldState,
        onEvent = viewModel::handleEvent,
        onClose = currentOnBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless content for the add-group-members recipient picker. Accepts fixture
 * inputs for @Preview and screenshot-test rendering.
 *
 * Layout:
 * - [TopAppBar] with a close nav icon, "Add members" title, and an "Add" action
 *   (a [CircularProgressIndicator] while [AddGroupMembersViewState.isSubmitting]).
 * - Search [OutlinedTextField] pinned below the top bar.
 * - A [FlowRow] of picked recipients as removable [InputChip]s (hidden when
 *   nothing is selected).
 * - An at-capacity hint when [AddGroupMembersViewState.atCapacity].
 * - Body driven by [AddMembersStatus]: Recent / Results lists reuse
 *   [RecipientRow]; Searching / NoResults / Error render centered bodies.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddGroupMembersScreenContent(
    state: AddGroupMembersViewState,
    queryFieldState: TextFieldState,
    onEvent: (AddMembersEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_members_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.add_members_close),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onEvent(AddMembersEvent.AddTapped) },
                        enabled = state.selected.isNotEmpty() && !state.isSubmitting,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.add_members_action))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            OutlinedTextField(
                state = queryFieldState,
                lineLimits = TextFieldLineLimits.SingleLine,
                placeholder = { Text(stringResource(R.string.add_members_search_placeholder)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (state.selected.isNotEmpty()) {
                FlowRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.selected.forEach { r ->
                        InputChip(
                            selected = true,
                            onClick = { onEvent(AddMembersEvent.RecipientRemoved(r.did)) },
                            label = { Text(r.displayName ?: r.handle, maxLines = 1) },
                            avatar = {
                                NubecitaAvatar(
                                    model = r.avatarUrl,
                                    contentDescription = null,
                                    size = InputChipDefaults.AvatarSize,
                                    fallback =
                                        avatarFallbackFor(
                                            did = r.did,
                                            handle = r.handle,
                                            displayName = r.displayName,
                                        ),
                                )
                            },
                            trailingIcon = {
                                NubecitaIcon(
                                    name = NubecitaIconName.Close,
                                    contentDescription = stringResource(R.string.add_members_remove_chip),
                                )
                            },
                        )
                    }
                }
            }

            if (state.atCapacity) {
                Text(
                    text = stringResource(R.string.add_members_at_capacity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when (val s = state.status) {
                is AddMembersStatus.Recent -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = s.items,
                            key = { it.did },
                            contentType = { "recipient" },
                        ) { actor ->
                            RecipientRow(
                                actor = actor,
                                enabled = !state.atCapacity,
                                respectCanMessage = false,
                                onClick = { onEvent(AddMembersEvent.RecipientToggled(actor.did)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                AddMembersStatus.Searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is AddMembersStatus.Results -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = s.items,
                            key = { it.did },
                            contentType = { "recipient" },
                        ) { actor ->
                            RecipientRow(
                                actor = actor,
                                enabled = !state.atCapacity,
                                respectCanMessage = false,
                                onClick = { onEvent(AddMembersEvent.RecipientToggled(actor.did)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                AddMembersStatus.NoResults -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.add_members_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                AddMembersStatus.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.add_members_error_generic),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(
                                onClick = { onEvent(AddMembersEvent.RetryClicked) },
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
        ActorUi(
            did = "did:plc:carol",
            handle = "carol.bsky.social",
            displayName = "Carol",
            avatarUrl = null,
        ),
    )

private val FIXTURE_SELECTED =
    persistentListOf(
        RecipientUi(
            did = "did:plc:alice",
            handle = "alice.bsky.social",
            displayName = "Alice Liddell",
            avatarUrl = null,
        ),
        RecipientUi(
            did = "did:plc:bob",
            handle = "bob.bsky.social",
            displayName = null,
            avatarUrl = null,
        ),
    )

@Preview(name = "Add members — recent / empty", showBackground = true)
@Composable
private fun AddGroupMembersRecentPreview() {
    NubecitaCanvasPreviewTheme {
        AddGroupMembersScreenContent(
            state = AddGroupMembersViewState(status = AddMembersStatus.Recent(FIXTURE_ACTORS)),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "Add members — selected + results", showBackground = true)
@Composable
private fun AddGroupMembersSelectedPreview() {
    NubecitaCanvasPreviewTheme {
        AddGroupMembersScreenContent(
            state =
                AddGroupMembersViewState(
                    selected = FIXTURE_SELECTED,
                    status = AddMembersStatus.Results(FIXTURE_ACTORS),
                ),
            queryFieldState = TextFieldState(initialText = "ca"),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "Add members — at capacity", showBackground = true)
@Composable
private fun AddGroupMembersAtCapacityPreview() {
    NubecitaCanvasPreviewTheme {
        AddGroupMembersScreenContent(
            state =
                AddGroupMembersViewState(
                    selected = FIXTURE_SELECTED,
                    atCapacity = true,
                    status = AddMembersStatus.Recent(FIXTURE_ACTORS),
                ),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}
