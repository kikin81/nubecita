package net.kikin.nubecita.feature.chats.impl

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
        },
        floatingActionButton = {
            // A non-DM-enrolled account can't start a chat, so don't offer "new chat" —
            // it would dead-end on the same not-enrolled screen. ChatsErrorMapping stays
            // the authoritative post-tap backstop for recipients we can't message.
            val status = state.status
            val notEnrolled =
                status is ChatsLoadStatus.InitialError && status.error == ChatsError.NotEnrolled
            if (!notEnrolled) {
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
            ChatsSegmentTabs(
                activeSegment = state.activeSegment,
                requestCount = state.requestCount,
                onSelect = { segment -> onEvent(ChatsEvent.SegmentSelected(segment)) },
                modifier = Modifier.padding(vertical = 8.dp),
            )
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
                                    onTap = { did -> onEvent(ChatsEvent.ConvoTapped(did)) },
                                    selectedOtherUserDid = selectedOtherUserDid,
                                )
                            }
                        }
                    is ChatsLoadStatus.InitialError -> ErrorBody(error = status.error, onRetry = { onEvent(ChatsEvent.RetryClicked) })
                }
            }
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
    onTap: (otherUserDid: String) -> Unit,
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
            ConvoListItem(
                item = item,
                index = index,
                count = items.size,
                onTap = onTap,
                selected = item.otherUserDid == selectedOtherUserDid,
            )
        }
    }
}

@Composable
private fun ErrorBody(
    error: ChatsError,
    onRetry: () -> Unit,
) {
    val (titleRes, bodyRes, showRetry) =
        when (error) {
            ChatsError.Network -> Triple(R.string.chats_error_network_title, R.string.chats_error_network_body, true)
            ChatsError.NotEnrolled -> Triple(R.string.chats_error_not_enrolled_title, R.string.chats_error_not_enrolled_body, false)
            is ChatsError.Unknown -> Triple(R.string.chats_error_unknown_title, R.string.chats_error_unknown_body, true)
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
