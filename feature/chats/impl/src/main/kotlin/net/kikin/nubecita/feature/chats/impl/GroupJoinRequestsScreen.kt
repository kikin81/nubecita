package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.chats.impl.ui.EmptyStateContent
import net.kikin.nubecita.feature.chats.impl.ui.JoinRequestRow
import kotlin.time.Instant

/**
 * Stateful entry for the group join-requests screen. Owns the
 * [GroupJoinRequestsViewModel], collects [GroupJoinRequestsEffect]s into a snackbar
 * host, and delegates rendering to the stateless [GroupJoinRequestsScreenContent].
 *
 * Error copy is pre-resolved at composition (mirroring [AddGroupMembersScreen]) so the
 * effect collector — which runs outside the composition — can map a [ChatError] to a
 * localized message without a `stringResource` call. A [GroupJoinRequestsEffect.RosterChanged]
 * (fired after an approve succeeds) invokes `onRosterChanged`.
 *
 * `onRosterChanged` keeps its past-tense name intentionally (it fires after the roster
 * mutates), so the present-tense lambda-naming rule is suppressed here.
 */
@Suppress("ktlint:compose:parameter-naming")
@Composable
internal fun GroupJoinRequestsScreen(
    viewModel: GroupJoinRequestsViewModel,
    onRosterChanged: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = viewModel.joinRequests.collectAsLazyPagingItems()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val fullMsg = stringResource(R.string.add_members_error_full)
    val followMsg = stringResource(R.string.add_members_error_follow_required)
    val permMsg = stringResource(R.string.add_members_error_permission)
    val genericMsg = stringResource(R.string.add_members_error_generic)

    val currentOnRosterChanged by rememberUpdatedState(onRosterChanged)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(Unit) {
        // showSnackbar suspends until the snackbar is dismissed (~4s); launch it in a
        // child coroutine so a RosterChanged queued right behind a ShowError isn't
        // head-of-line blocked. Mirrors AddGroupMembersScreen's effect collector.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is GroupJoinRequestsEffect.ShowError -> {
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

                GroupJoinRequestsEffect.RosterChanged -> currentOnRosterChanged()
            }
        }
    }

    GroupJoinRequestsScreenContent(
        lazyItems = lazyItems,
        inFlightDids = state.inFlightDids,
        onApprove = { viewModel.handleEvent(GroupJoinRequestsEvent.ApproveTapped(it)) },
        onReject = { viewModel.handleEvent(GroupJoinRequestsEvent.RejectTapped(it)) },
        onClose = currentOnBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless content for the group join-requests screen. Accepts fixture inputs for
 * @Preview and screenshot-test rendering.
 *
 * Layout: a [TopAppBar] with a close nav icon and "Join requests" title, over a
 * width-constrained body that branches on the Paging refresh [LoadState] — a centered
 * [NubecitaWavyProgressIndicator] while loading, a centered retry on error, an
 * [EmptyStateContent] when settled-but-empty, and otherwise a [LazyColumn] of
 * [JoinRequestRow]s with an append-state footer (spinner / retry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupJoinRequestsScreenContent(
    lazyItems: LazyPagingItems<JoinRequestUi>,
    inFlightDids: ImmutableSet<String>,
    onApprove: (did: String) -> Unit,
    onReject: (did: String) -> Unit,
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
                title = { Text(stringResource(R.string.join_requests_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.join_requests_close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter),
            ) {
                when (lazyItems.loadState.refresh) {
                    is LoadState.Loading ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NubecitaWavyProgressIndicator()
                        }

                    is LoadState.Error ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.join_requests_error),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                TextButton(
                                    onClick = { lazyItems.retry() },
                                    modifier = Modifier.padding(top = 16.dp),
                                ) {
                                    Text(stringResource(R.string.new_chat_retry))
                                }
                            }
                        }

                    is LoadState.NotLoading ->
                        if (lazyItems.itemCount == 0) {
                            EmptyStateContent(
                                icon = NubecitaIconName.PersonAdd,
                                message = stringResource(R.string.join_requests_empty),
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(
                                    count = lazyItems.itemCount,
                                    key = lazyItems.itemKey { it.did },
                                    contentType = lazyItems.itemContentType { "request" },
                                ) { index ->
                                    val request = lazyItems[index]
                                    if (request != null) {
                                        JoinRequestRow(
                                            request = request,
                                            inFlight = request.did in inFlightDids,
                                            onApprove = { onApprove(request.did) },
                                            onReject = { onReject(request.did) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }

                                when (lazyItems.loadState.append) {
                                    is LoadState.Loading ->
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                NubecitaWavyProgressIndicator()
                                            }
                                        }

                                    is LoadState.Error ->
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                TextButton(onClick = { lazyItems.retry() }) {
                                                    Text(stringResource(R.string.new_chat_retry))
                                                }
                                            }
                                        }

                                    else -> Unit
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

private fun joinRequest(
    did: String,
    handle: String,
    displayName: String?,
): JoinRequestUi =
    JoinRequestUi(
        did = did,
        handle = handle,
        displayName = displayName,
        avatarUrl = null,
        requestedAt = Instant.parse("2026-06-22T10:00:00Z"),
    )

private val FIXTURE_REQUESTS =
    listOf(
        joinRequest("did:plc:alice", "alice.bsky.social", "Alice Liddell"),
        joinRequest("did:plc:bob", "bob.bsky.social", null),
        joinRequest("did:plc:carol", "carol.bsky.social", "Carol"),
    )

@Preview(name = "Join requests — list", showBackground = true)
@Composable
private fun GroupJoinRequestsListPreview() {
    val items = flowOf(PagingData.from(FIXTURE_REQUESTS)).collectAsLazyPagingItems()
    NubecitaCanvasPreviewTheme {
        GroupJoinRequestsScreenContent(
            lazyItems = items,
            inFlightDids = persistentSetOf(),
            onApprove = {},
            onReject = {},
            onClose = {},
        )
    }
}

@Preview(name = "Join requests — empty", showBackground = true)
@Composable
private fun GroupJoinRequestsEmptyPreview() {
    val items = flowOf(PagingData.empty<JoinRequestUi>()).collectAsLazyPagingItems()
    NubecitaCanvasPreviewTheme {
        GroupJoinRequestsScreenContent(
            lazyItems = items,
            inFlightDids = persistentSetOf(),
            onApprove = {},
            onReject = {},
            onClose = {},
        )
    }
}
