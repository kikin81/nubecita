package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.component.AvatarGroup
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.impl.ui.GroupMemberRow

/**
 * Stateful group-details screen. Owns the [GroupDetailsViewModel], snackbar host,
 * and the effect collector that translates navigation + error effects, and
 * delegates rendering to the stateless [GroupDetailsScreenContent].
 *
 * Both the toolbar back button and system back route through [onBack] (the
 * `BackPressed` event is short-circuited in the [onEvent] lambda rather than
 * reaching the VM); [onNavigateTo] forwards a member-tap → Profile NavKey to the
 * MainShell nav state. Mirrors `ChatScreen`.
 */
@Composable
internal fun GroupDetailsScreen(
    viewModel: GroupDetailsViewModel,
    convoId: String,
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navState = LocalMainShellNavState.current
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)
    // Pre-resolve at composition so locale/dark-mode changes track via Compose's
    // resource reads (reading inside the LaunchedEffect would bypass that). All
    // ChatError variants map to one generic group-action snackbar.
    val actionErrorMsg = stringResource(R.string.group_details_action_error)

    LaunchedEffect(Unit) {
        // The effect stream carries navigation + a transient error snackbar.
        // showSnackbar suspends until dismissed, so launch it in a child coroutine
        // to avoid head-of-line-blocking a NavigateBack queued right behind it.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                GroupDetailsEffect.NavigateBack -> currentOnBack()
                is GroupDetailsEffect.NavigateTo -> currentOnNavigateTo(effect.key)
                is GroupDetailsEffect.ShowError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(actionErrorMsg)
                    }
            }
        }
    }

    // Consume the one-shot count set by AddGroupMembers on its way back, then
    // deliberately refresh the roster and surface the Invitations-sent snackbar.
    val resultKey = "group_members_added:$convoId"
    val pendingAdd = navState.peekResult(resultKey) as? Int
    val invitesSentMsg = stringResource(R.string.group_details_invites_sent)
    LaunchedEffect(pendingAdd) {
        if (pendingAdd != null) {
            navState.consumeResult(resultKey)
            viewModel.handleEvent(GroupDetailsEvent.Refresh) // deliberate roster refresh
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(invitesSentMsg)
        }
    }

    GroupDetailsScreenContent(
        state = state,
        onEvent = { event ->
            if (event is GroupDetailsEvent.BackPressed) {
                currentOnBack()
            } else {
                viewModel.handleEvent(event)
            }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless group-details chrome — `Scaffold` + app bar + the
 * loading / loaded / error body — driven purely by [state] and [onEvent]. Split
 * out so previews and screenshot tests exercise every state without a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDetailsScreenContent(
    state: GroupDetailsViewState,
    onEvent: (GroupDetailsEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(GroupDetailsEvent.BackPressed) }) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val status = state.status) {
                GroupDetailsLoadStatus.Loading -> LoadingBody()
                is GroupDetailsLoadStatus.InitialError ->
                    ErrorBody(onRetry = { onEvent(GroupDetailsEvent.RetryClicked) })
                is GroupDetailsLoadStatus.Loaded -> LoadedBody(state = state, status = status, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBody(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.group_details_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.group_details_retry))
        }
    }
}

@Composable
private fun LoadedBody(
    state: GroupDetailsViewState,
    status: GroupDetailsLoadStatus.Loaded,
    onEvent: (GroupDetailsEvent) -> Unit,
) {
    // Keyed on the roster so it only rebuilds when membership changes — NOT on a
    // follow-state flip (which copies the Loaded status but leaves did/handle/
    // displayName/avatarUrl untouched). AvatarGroup itself still skips via the
    // persistent list's structural equality; this just drops the throwaway map.
    val facepile =
        remember(status.members) {
            status.members
                .map { member ->
                    AuthorUi(
                        did = member.did,
                        handle = member.handle,
                        displayName = member.displayName ?: member.handle,
                        avatarUrl = member.avatarUrl,
                    )
                }.toImmutableList()
        }

    var pendingRemoval by remember { mutableStateOf<GroupMemberUi?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(contentType = "header") {
            GroupHeader(state = state, status = status, facepile = facepile)
        }
        item(contentType = "actions") {
            GroupActionRow(muted = state.muted, viewerRole = state.viewerRole, onEvent = onEvent)
        }
        items(status.members, key = { it.did }, contentType = { "member" }) { member ->
            GroupMemberRow(
                member = member,
                viewerRole = state.viewerRole,
                onClick = { onEvent(GroupDetailsEvent.MemberTapped(member.did)) },
                onToggleFollow = { onEvent(GroupDetailsEvent.ToggleFollow(member.did)) },
                onRemove = { pendingRemoval = member },
            )
        }
    }

    pendingRemoval?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.group_details_remove_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.group_details_remove_confirm_body,
                        target.displayName ?: target.handle,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(GroupDetailsEvent.RemoveMember(target.did))
                        pendingRemoval = null
                    },
                ) {
                    Text(stringResource(R.string.group_details_remove_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.group_details_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupHeader(
    state: GroupDetailsViewState,
    status: GroupDetailsLoadStatus.Loaded,
    facepile: ImmutableList<AuthorUi>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarGroup(members = facepile, contentDescription = null, maxVisible = 5)
        Text(
            text = state.name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.group_details_member_count, status.memberCount, state.maxMembers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupActionRow(
    muted: Boolean,
    viewerRole: GroupRole?,
    onEvent: (GroupDetailsEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (viewerRole == GroupRole.Owner) {
            OutlinedButton(
                onClick = { onEvent(GroupDetailsEvent.AddMembersTapped) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.group_details_add_members))
            }
        }
        OutlinedButton(
            onClick = { onEvent(GroupDetailsEvent.ToggleMute) },
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(
                    if (muted) R.string.chats_action_unmute else R.string.chats_action_mute,
                ),
            )
        }
        OutlinedButton(
            onClick = { onEvent(GroupDetailsEvent.LeaveTapped) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.chats_action_leave))
        }
    }
}

// Co-located previews; screenshot baselines land in Task 8.

private val previewMembers =
    persistentListOf(
        GroupMemberUi(
            did = "did:plc:owner",
            handle = "ada.bsky.social",
            displayName = "Ada Lovelace",
            avatarUrl = null,
            role = GroupRole.Owner,
            addedByName = null,
            isViewer = false,
            followState = FollowState.Following,
            followUri = "at://did:plc:owner/app.bsky.graph.follow/1",
        ),
        GroupMemberUi(
            did = "did:plc:viewer",
            handle = "me.bsky.social",
            displayName = "Me",
            avatarUrl = null,
            role = GroupRole.Member,
            addedByName = "Ada Lovelace",
            isViewer = true,
            followState = FollowState.NotFollowing,
            followUri = null,
        ),
        GroupMemberUi(
            did = "did:plc:grace",
            handle = "grace.bsky.social",
            displayName = "Grace Hopper",
            avatarUrl = null,
            role = GroupRole.Member,
            addedByName = "Ada Lovelace",
            isViewer = false,
            followState = FollowState.NotFollowing,
            followUri = null,
        ),
        GroupMemberUi(
            did = "did:plc:alan",
            handle = "alan.bsky.social",
            displayName = null,
            avatarUrl = null,
            role = GroupRole.Member,
            addedByName = null,
            isViewer = false,
            followState = FollowState.InFlight,
            followUri = null,
        ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Group details — loaded", showBackground = true)
@Composable
private fun GroupDetailsLoadedPreview() {
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state =
                GroupDetailsViewState(
                    name = "Mathematicians",
                    muted = false,
                    status =
                        GroupDetailsLoadStatus.Loaded(
                            members = previewMembers,
                            memberCount = previewMembers.size,
                        ),
                ),
            onEvent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Group details — owner view", showBackground = true)
@Composable
private fun GroupDetailsOwnerPreview() {
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state =
                GroupDetailsViewState(
                    name = "Mathematicians",
                    muted = false,
                    viewerRole = GroupRole.Owner,
                    status =
                        GroupDetailsLoadStatus.Loaded(
                            members = previewMembers,
                            memberCount = previewMembers.size,
                        ),
                ),
            onEvent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Group details — error", showBackground = true)
@Composable
private fun GroupDetailsErrorPreview() {
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state =
                GroupDetailsViewState(
                    name = "Mathematicians",
                    status = GroupDetailsLoadStatus.InitialError(ChatError.Network),
                ),
            onEvent = {},
        )
    }
}
