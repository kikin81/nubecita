package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.impl.ui.RecipientChipsRow
import net.kikin.nubecita.feature.chats.impl.ui.RecipientRow

/**
 * Stateful entry for the new-group creation screen. Owns the
 * [NewGroupViewModel], collects [NewGroupEffect]s into a snackbar host, and
 * delegates rendering to the stateless [NewGroupScreenContent].
 *
 * Error copy is pre-resolved at composition (mirroring [AddGroupMembersScreen])
 * so the effect collector — which runs outside the composition — can map a
 * [ChatError] to a localized message without a `stringResource` call. On
 * [NewGroupEffect.GroupCreated] the new convo id is handed to `onCreated`.
 *
 * `onCreated` keeps its past-tense name intentionally (it fires after creation
 * succeeds), so the present-tense lambda-naming rule is suppressed here.
 */
@Suppress("ktlint:compose:parameter-naming") // onCreated is past-tense — fires after creation
@Composable
internal fun NewGroupScreen(
    viewModel: NewGroupViewModel,
    onCreated: (convoId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val fullMsg = stringResource(R.string.add_members_error_full)
    val followMsg = stringResource(R.string.add_members_error_follow_required)
    val permMsg = stringResource(R.string.add_members_error_permission)
    val genericMsg = stringResource(R.string.new_group_error_generic)

    val currentOnCreated by rememberUpdatedState(onCreated)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(Unit) {
        // showSnackbar suspends until the snackbar is dismissed (~4s); launch it in a
        // child coroutine so a GroupCreated queued right behind a ShowError isn't
        // head-of-line blocked. Mirrors AddGroupMembersScreen's effect collector.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewGroupEffect.ShowError -> {
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

                is NewGroupEffect.GroupCreated -> currentOnCreated(effect.convoId)
            }
        }
    }

    NewGroupScreenContent(
        state = state,
        nameFieldState = viewModel.nameFieldState,
        queryFieldState = viewModel.queryFieldState,
        onEvent = viewModel::handleEvent,
        onClose = currentOnBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless content for the new-group creation screen. Accepts fixture inputs
 * for @Preview and screenshot-test rendering.
 *
 * Layout (centered, constrained to 600.dp on wide screens):
 * - [TopAppBar] with a close nav icon, "New group" title, and a jitter-free
 *   "Create" action ([CreateAction]).
 * - Name [OutlinedTextField] with a proactive grapheme counter in its supporting
 *   text once the count crosses [GROUP_NAME_COUNTER_THRESHOLD].
 * - A [RecipientChipsRow] of picked recipients (hidden when nothing is selected).
 * - An at-capacity hint when [NewGroupViewState.atCapacity].
 * - Search [OutlinedTextField].
 * - Body driven by [NewGroupStatus]: Recent / Results lists reuse [RecipientRow];
 *   Searching / NoResults / Error render centered bodies.
 *
 * All inputs are disabled while [NewGroupViewState.isSubmitting].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewGroupScreenContent(
    state: NewGroupViewState,
    nameFieldState: TextFieldState,
    queryFieldState: TextFieldState,
    onEvent: (NewGroupEvent) -> Unit,
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
                title = { Text(stringResource(R.string.new_group_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.new_group_close),
                        )
                    }
                },
                actions = {
                    CreateAction(state = state, onEvent = onEvent)
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
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .align(Alignment.CenterHorizontally),
            ) {
                OutlinedTextField(
                    state = nameFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    enabled = !state.isSubmitting,
                    isError = state.nameGraphemeCount > GROUP_NAME_MAX_GRAPHEMES,
                    placeholder = { Text(stringResource(R.string.new_group_name_placeholder)) },
                    supportingText = {
                        if (state.nameGraphemeCount >= GROUP_NAME_COUNTER_THRESHOLD) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.new_group_name_counter,
                                        state.nameGraphemeCount,
                                        GROUP_NAME_MAX_GRAPHEMES,
                                    ),
                                color =
                                    if (state.nameGraphemeCount > GROUP_NAME_MAX_GRAPHEMES) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                if (state.selected.isNotEmpty()) {
                    RecipientChipsRow(
                        selected = state.selected,
                        onRemove = { onEvent(NewGroupEvent.RecipientRemoved(it)) },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                if (state.atCapacity) {
                    Text(
                        text = stringResource(R.string.new_group_at_capacity, GROUP_MAX_MEMBERS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                OutlinedTextField(
                    state = queryFieldState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    enabled = !state.isSubmitting,
                    placeholder = { Text(stringResource(R.string.new_group_search_placeholder)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                when (val s = state.status) {
                    is NewGroupStatus.Recent -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(
                                items = s.items,
                                key = { it.did },
                                contentType = { "recipient" },
                            ) { actor ->
                                RecipientRow(
                                    actor = actor,
                                    enabled = !state.atCapacity && !state.isSubmitting,
                                    respectCanMessage = false,
                                    onClick = { onEvent(NewGroupEvent.RecipientToggled(actor.did)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    NewGroupStatus.Searching -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            NubecitaWavyProgressIndicator()
                        }
                    }

                    is NewGroupStatus.Results -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(
                                items = s.items,
                                key = { it.did },
                                contentType = { "recipient" },
                            ) { actor ->
                                RecipientRow(
                                    actor = actor,
                                    enabled = !state.atCapacity && !state.isSubmitting,
                                    respectCanMessage = false,
                                    onClick = { onEvent(NewGroupEvent.RecipientToggled(actor.did)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    NewGroupStatus.NoResults -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.new_group_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    NewGroupStatus.Error -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.new_group_error_generic),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(
                                    onClick = { onEvent(NewGroupEvent.RetryClicked) },
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
}

/**
 * The "Create" top-bar action, kept jitter-free across the submit transition.
 *
 * The [TextButton] stays mounted at a constant width whether or not a submit is
 * in flight: while [NewGroupViewState.isSubmitting] its label is rendered at
 * zero alpha (preserving the measured width) and a [CircularProgressIndicator]
 * is centered over it as a sibling in the [Box]. This avoids the layout jump
 * that a swap-out (button → spinner) would cause in the action slot.
 */
@Composable
private fun CreateAction(
    state: NewGroupViewState,
    onEvent: (NewGroupEvent) -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        TextButton(
            onClick = { onEvent(NewGroupEvent.CreateTapped) },
            enabled = state.canCreate,
        ) {
            Text(
                text = stringResource(R.string.new_group_create),
                modifier = Modifier.alpha(if (state.isSubmitting) 0f else 1f),
            )
        }
        if (state.isSubmitting) {
            // nubecita-allow-raw-progress: in-button micro-spinner with a tuned strokeWidth
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
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

private const val LONG_NAME =
    "Weekend trip planning crew for the big mountain getaway in early autumn season"

@Preview(name = "New group — empty / recent", showBackground = true)
@Composable
private fun NewGroupEmptyPreview() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state = NewGroupViewState(status = NewGroupStatus.Recent(FIXTURE_ACTORS)),
            nameFieldState = TextFieldState(),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "New group — named + selected + results", showBackground = true)
@Composable
private fun NewGroupNamedPreview() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected = FIXTURE_SELECTED,
                    nameGraphemeCount = 5,
                    isNameValid = true,
                    status = NewGroupStatus.Results(FIXTURE_ACTORS),
                ),
            nameFieldState = TextFieldState(initialText = "Trips"),
            queryFieldState = TextFieldState(initialText = "ca"),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "New group — at capacity", showBackground = true)
@Composable
private fun NewGroupAtCapacityPreview() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected = FIXTURE_SELECTED,
                    nameGraphemeCount = 5,
                    isNameValid = true,
                    atCapacity = true,
                    status = NewGroupStatus.Recent(FIXTURE_ACTORS),
                ),
            nameFieldState = TextFieldState(initialText = "Trips"),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "New group — name counter near limit", showBackground = true)
@Composable
private fun NewGroupNameCounterPreview() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected = FIXTURE_SELECTED,
                    nameGraphemeCount = 120,
                    isNameValid = true,
                    status = NewGroupStatus.Recent(FIXTURE_ACTORS),
                ),
            nameFieldState = TextFieldState(initialText = LONG_NAME),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "New group — submitting", showBackground = true)
@Composable
private fun NewGroupSubmittingPreview() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected = FIXTURE_SELECTED,
                    nameGraphemeCount = 5,
                    isNameValid = true,
                    isSubmitting = true,
                    status = NewGroupStatus.Recent(FIXTURE_ACTORS),
                ),
            nameFieldState = TextFieldState(initialText = "Trips"),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@Preview(name = "New group — wide", widthDp = 840)
@Composable
private fun NewGroupWidePreview() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected = FIXTURE_SELECTED,
                    nameGraphemeCount = 5,
                    isNameValid = true,
                    status = NewGroupStatus.Results(FIXTURE_ACTORS),
                ),
            nameFieldState = TextFieldState(initialText = "Trips"),
            queryFieldState = TextFieldState(initialText = "ca"),
            onEvent = {},
            onClose = {},
        )
    }
}
