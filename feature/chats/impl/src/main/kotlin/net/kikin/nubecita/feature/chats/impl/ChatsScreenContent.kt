package net.kikin.nubecita.feature.chats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.tabs.PillTab
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.chats.impl.ui.ConvoListItem

/**
 * Stateless content for the Chats tab home. The stateful entry
 * [ChatsScreen] hosts the ViewModel; previews + screenshot tests
 * render this composable directly with fixture inputs.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatsScreenContent(
    state: ChatsScreenViewState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ChatsEvent) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    // The open conversation's otherUserDid (or null) — highlights the matching
    // list row in the tablet list-detail layout. Null on phones / when no
    // thread is open.
    selectedOtherUserDid: String? = null,
) {
    val selectionIds = state.selection
    val inSelection = selectionIds != null
    // Resolve the selected rows against the active segment's loaded list to
    // derive the mute/unmute label (does the toggle mute or unmute?). Empty
    // unless we're both selecting AND on a Loaded list.
    val selectedConvos =
        when {
            selectionIds == null -> emptyList()
            else ->
                (state.status as? ChatsLoadStatus.Loaded)
                    ?.items
                    ?.filter { it.convoId in selectionIds }
                    .orEmpty()
        }
    val selectionCount = selectionIds?.size ?: 0
    // Toggle target mirrors ChatsViewModel.toggleMuteSelected: when EVERY
    // selected convo is already muted the action unmutes; otherwise it mutes.
    val allSelectedMuted = selectedConvos.isNotEmpty() && selectedConvos.all { it.muted }

    // A back press exits selection mode before it falls through to the inner
    // NavDisplay's back handling. Disabled (transparent) when not selecting.
    BackHandler(enabled = inSelection) { onEvent(ChatsEvent.ClearSelection) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (inSelection) {
                ChatsSelectionBar(
                    count = selectionCount,
                    segment = state.activeSegment,
                    allSelectedMuted = allSelectedMuted,
                    onEvent = onEvent,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.chats_title)) },
                    actions = {
                        IconButton(onClick = { onEvent(ChatsEvent.SettingsTapped) }) {
                            NubecitaIcon(
                                name = NubecitaIconName.Settings,
                                contentDescription = stringResource(R.string.chat_settings_gear_content_description),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // Hidden while selecting — the contextual bar owns the screen's
            // actions then. Otherwise: a non-DM-enrolled account can't start a
            // chat, so don't offer "new chat" — it would dead-end on the same
            // not-enrolled screen. ChatsErrorMapping stays the authoritative
            // post-tap backstop for recipients we can't message.
            val status = state.status
            val notEnrolled =
                status is ChatsLoadStatus.InitialError && status.error == ChatsError.NotEnrolled
            if (!inSelection && !notEnrolled) {
                FloatingActionButton(onClick = onNewChat) {
                    NubecitaIcon(
                        name = NubecitaIconName.Edit,
                        contentDescription = stringResource(R.string.new_chat_fab_content_description),
                        filled = true,
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Hidden while selecting: switching segments would change the action
            // set + the underlying list out from under the selection (the VM
            // clears selection on a segment change anyway).
            if (!inSelection) {
                ChatsSegmentTabs(
                    activeSegment = state.activeSegment,
                    requestCount = state.requestCount,
                    onSelect = { segment -> onEvent(ChatsEvent.SegmentSelected(segment)) },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            // weight(1f), not fillMaxSize(): the segment row above already took its
            // height, so the body fills the REMAINING column space (fillMaxSize here
            // would demand full height and clip the bottom).
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val status = state.status) {
                    ChatsLoadStatus.Loading -> LoadingBody()
                    is ChatsLoadStatus.Loaded ->
                        // PullToRefreshBox wraps both empty and non-empty Loaded states so a user
                        // sitting on the empty state can pull to refresh and discover newly-started
                        // conversations without leaving the screen. Loading and InitialError have
                        // their own affordances (spinner / Retry button), so they stay outside.
                        PullToRefreshBox(
                            isRefreshing = status.isRefreshing,
                            onRefresh = { onEvent(ChatsEvent.Refresh) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (status.items.isEmpty()) {
                                EmptyBody(segment = state.activeSegment)
                            } else {
                                LoadedBody(
                                    items = status.items,
                                    selection = selectionIds,
                                    onEvent = onEvent,
                                    selectedOtherUserDid = selectedOtherUserDid,
                                )
                            }
                        }
                    is ChatsLoadStatus.InitialError ->
                        ErrorBody(error = status.error, segment = state.activeSegment, onRetry = { onEvent(ChatsEvent.RetryClicked) })
                }
            }
        }
    }
}

/**
 * Contextual app bar shown in place of the normal toolbar while multi-select
 * is active (Google-Messages CAB pattern). The close affordance exits
 * selection; the title shows the count. Inline actions are bulk-capable
 * (Accept on Requests, Mute/Unmute on Chats, Leave on both); the single-only
 * actions (Profile / Report / Block) live behind the overflow and appear only
 * at exactly one selection — the VM's `navigateSingle` no-ops past one anyway,
 * but hiding them keeps the affordance honest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsSelectionBar(
    count: Int,
    segment: ChatsSegment,
    allSelectedMuted: Boolean,
    onEvent: (ChatsEvent) -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = { onEvent(ChatsEvent.ClearSelection) }) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.chats_selection_close),
                )
            }
        },
        title = { Text(stringResource(R.string.chats_selection_count, count)) },
        actions = {
            if (segment == ChatsSegment.Requests) {
                // Accepting a request moves it into the accepted Chats list.
                SelectionAction(
                    label = stringResource(R.string.chats_action_accept),
                    icon = NubecitaIconName.Check,
                    onClick = { onEvent(ChatsEvent.AcceptSelected) },
                )
            } else {
                val muteLabel =
                    stringResource(
                        if (allSelectedMuted) R.string.chats_action_unmute else R.string.chats_action_mute,
                    )
                SelectionAction(
                    label = muteLabel,
                    icon = if (allSelectedMuted) NubecitaIconName.Notifications else NubecitaIconName.NotificationsOff,
                    onClick = { onEvent(ChatsEvent.ToggleMuteSelected) },
                )
            }
            // Leave is bulk-capable in both segments (delete chat / decline request).
            SelectionAction(
                label = stringResource(R.string.chats_action_leave),
                icon = NubecitaIconName.Logout,
                onClick = { onEvent(ChatsEvent.LeaveSelected) },
            )
            if (count == 1) {
                SelectionOverflowMenu(onEvent = onEvent)
            }
        },
    )
}

/**
 * An icon-button toolbar action with a hover/long-press [PlainTooltip] (the
 * icons are unlabeled, so the tooltip + matching `contentDescription` carry
 * the action name for both sighted long-press and TalkBack).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionAction(
    label: String,
    icon: NubecitaIconName,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            NubecitaIcon(name = icon, contentDescription = label)
        }
    }
}

/**
 * Overflow (⋮) for the single-select-only actions. Owns its `expanded`
 * state; each item closes the menu before dispatching its event.
 */
@Composable
private fun SelectionOverflowMenu(onEvent: (ChatsEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.chats_action_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chats_action_profile)) },
                leadingIcon = { NubecitaIcon(name = NubecitaIconName.Person, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEvent(ChatsEvent.ProfileSelected)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chats_action_report)) },
                leadingIcon = { NubecitaIcon(name = NubecitaIconName.Flag, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEvent(ChatsEvent.ReportSelected)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chats_action_block)) },
                leadingIcon = { NubecitaIcon(name = NubecitaIconName.Block, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEvent(ChatsEvent.BlockSelected)
                },
            )
        }
    }
}

/**
 * The Chats / Requests segmented toggle. Reuses the design-system
 * [ProfilePillTabs] button group; the Requests pill shows a badge with the
 * pending-request [requestCount] (no badge when zero).
 */
@Composable
private fun ChatsSegmentTabs(
    activeSegment: ChatsSegment,
    requestCount: Int,
    onSelect: (ChatsSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve labels outside remember (stringResource is a composable read), then
    // remember the list keyed on the inputs so it isn't reallocated every recomposition.
    val chatsLabel = stringResource(R.string.chats_segment_chats)
    val requestsLabel = stringResource(R.string.chats_segment_requests)
    val tabs =
        remember(chatsLabel, requestsLabel, requestCount) {
            persistentListOf(
                PillTab(
                    value = ChatsSegment.Chats,
                    label = chatsLabel,
                    iconName = NubecitaIconName.ChatBubble,
                ),
                PillTab(
                    value = ChatsSegment.Requests,
                    label = requestsLabel,
                    iconName = NubecitaIconName.Inbox,
                    badgeCount = requestCount.takeIf { it > 0 },
                ),
            )
        }
    ProfilePillTabs(
        tabs = tabs,
        selectedValue = activeSegment,
        onSelect = onSelect,
        modifier = modifier,
    )
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBody(segment: ChatsSegment) {
    val (titleRes, bodyRes) =
        when (segment) {
            ChatsSegment.Chats -> R.string.chats_empty_title to R.string.chats_empty_body
            ChatsSegment.Requests -> R.string.chats_requests_empty_title to R.string.chats_requests_empty_body
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadedBody(
    items: kotlinx.collections.immutable.ImmutableList<ConvoListItemUi>,
    selection: ImmutableSet<String>?,
    onEvent: (ChatsEvent) -> Unit,
    selectedOtherUserDid: String?,
) {
    // Arrangement.spacedBy(ListItemDefaults.SegmentedGap) — the framework's
    // canonical gap between rows in a segmented section. Lets the rounded-
    // corner profile from segmentedShapes(index, count) read as one grouped
    // surface instead of a continuous rectangle.
    //
    // contentPadding 8dp horizontal — matches Google Chat's grouped-list inset
    // so the rounded surface breathes against the device edge instead of
    // bleeding all the way to the bezel. contentPadding (not Modifier.padding)
    // keeps the scrollable surface itself edge-to-edge while only the items
    // get pushed inward — overscroll glow and pull-to-refresh indicator still
    // reach the screen edges.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.convoId },
            contentType = { _, _ -> "convo-row" },
        ) { index, item ->
            val inSelection = selection != null
            // In selection mode a tap toggles membership; otherwise it opens the
            // thread. Long-press always enters/extends selection.
            ConvoListItem(
                item = item,
                index = index,
                count = items.size,
                onClick = {
                    if (inSelection) {
                        onEvent(ChatsEvent.SelectionToggled(item.convoId))
                    } else {
                        onEvent(ChatsEvent.ConvoTapped(item.otherUserDid))
                    }
                },
                onLongClick = { onEvent(ChatsEvent.ConvoLongPressed(item.convoId)) },
                // Highlight by membership while selecting; otherwise reflect the
                // tablet list-detail open thread.
                selected =
                    if (inSelection) {
                        selection.contains(item.convoId)
                    } else {
                        item.otherUserDid == selectedOtherUserDid
                    },
            )
        }
    }
}

@Composable
private fun ErrorBody(
    error: ChatsError,
    segment: ChatsSegment,
    onRetry: () -> Unit,
) {
    // On the Requests segment, retryable failures (Network/Unknown) get
    // requests-specific body copy so it doesn't read like the accepted-chats
    // error next to the requests-specific empty state. NotEnrolled is
    // account-level and keeps its shared copy.
    val requestsBody = segment == ChatsSegment.Requests
    val (titleRes, bodyRes, showRetry) =
        when (error) {
            ChatsError.Network ->
                Triple(
                    R.string.chats_error_network_title,
                    if (requestsBody) R.string.chats_requests_error_body else R.string.chats_error_network_body,
                    true,
                )
            ChatsError.NotEnrolled -> Triple(R.string.chats_error_not_enrolled_title, R.string.chats_error_not_enrolled_body, false)
            is ChatsError.Unknown ->
                Triple(
                    R.string.chats_error_unknown_title,
                    if (requestsBody) R.string.chats_requests_error_body else R.string.chats_error_unknown_body,
                    true,
                )
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (showRetry) {
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.chats_error_retry))
            }
        }
    }
}
