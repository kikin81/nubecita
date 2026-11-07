package net.kikin.nubecita.feature.profile.impl

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.ReviewTrigger
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.core.review.requestReviewIfEligible
import net.kikin.nubecita.designsystem.component.NubecitaActorRow
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.designsystem.tabs.PillTab
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.profile.api.ProfileConnectionsTab
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi

/** Stateful entry: collects the lists and turns effects into navigation or a snackbar. */
@Composable
internal fun ProfileConnectionsScreen(
    viewModel: ProfileConnectionsViewModel,
    onNavigateToProfile: (handle: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val followers = viewModel.followers.collectAsLazyPagingItems()
    val following = viewModel.following.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.profile_connections_follow_error)
    val currentOnNavigateToProfile by rememberUpdatedState(onNavigateToProfile)
    val reviewActivity = LocalActivity.current
    LaunchedEffect(viewModel) {
        // showSnackbar suspends until dismissed, so awaiting it here would hold
        // a row tap queued behind the error for the snackbar's full duration.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileConnectionsEffect.NavigateToProfile -> currentOnNavigateToProfile(effect.handle)
                ProfileConnectionsEffect.FollowCommitted -> reviewActivity?.requestReviewIfEligible(ReviewTrigger.Follow)
                ProfileConnectionsEffect.ShowError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(errorMessage)
                    }
            }
        }
    }
    ProfileConnectionsContent(
        state = state,
        followers = followers,
        following = following,
        onEvent = viewModel::handleEvent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileConnectionsContent(
    state: ProfileConnectionsState,
    followers: LazyPagingItems<ConnectionRowUi>,
    following: LazyPagingItems<ConnectionRowUi>,
    onEvent: (ProfileConnectionsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Hoisted above the tab switch so each list keeps its place while hidden.
    val followersListState = rememberLazyListState()
    val followingListState = rememberLazyListState()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.profile_topbar_back_content_description),
                            modifier = Modifier.mirror(),
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
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                ConnectionTabs(
                    selectedTab = state.selectedTab,
                    followersCount = state.followersCount,
                    followsCount = state.followsCount,
                    onSelect = { onEvent(ProfileConnectionsEvent.TabSelected(it)) },
                )
                when (state.selectedTab) {
                    ProfileConnectionsTab.Followers ->
                        ConnectionList(
                            items = followers,
                            listState = followersListState,
                            overlay = state.overlay,
                            emptyMessage = stringResource(R.string.profile_connections_empty_followers),
                            onEvent = onEvent,
                        )
                    ProfileConnectionsTab.Following ->
                        ConnectionList(
                            items = following,
                            listState = followingListState,
                            overlay = state.overlay,
                            emptyMessage = stringResource(R.string.profile_connections_empty_following),
                            onEvent = onEvent,
                        )
                }
            }
        }
    }
}

/** The Followers / Following switch, each labelled with its count. */
@Composable
internal fun ConnectionTabs(
    selectedTab: ProfileConnectionsTab,
    followersCount: Long,
    followsCount: Long,
    onSelect: (ProfileConnectionsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val followersLabel =
        pluralStringResource(
            R.plurals.profile_connections_followers_tab,
            followersCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            rememberCompactCount(followersCount),
        )
    val followingLabel = stringResource(R.string.profile_connections_following_tab, rememberCompactCount(followsCount))
    val tabs =
        remember(followersLabel, followingLabel) {
            persistentListOf(
                PillTab(ProfileConnectionsTab.Followers, followersLabel, NubecitaIconName.Person),
                PillTab(ProfileConnectionsTab.Following, followingLabel, NubecitaIconName.PersonAdd),
            )
        }
    ProfilePillTabs(
        tabs = tabs,
        selectedValue = selectedTab,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ConnectionList(
    items: LazyPagingItems<ConnectionRowUi>,
    listState: LazyListState,
    overlay: ImmutableMap<String, ConnectionFollowState>,
    emptyMessage: String,
    onEvent: (ProfileConnectionsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = items.loadState.refresh
    when {
        items.itemCount > 0 ->
            LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.actor.did },
                    contentType = items.itemContentType { "connection" },
                ) { index ->
                    items[index]?.let { row ->
                        ConnectionRow(
                            row = row,
                            display = row.followDisplay(overlay[row.actor.did]),
                            onClick = { onEvent(ProfileConnectionsEvent.RowTapped(row)) },
                            onFollowClick = { onEvent(ProfileConnectionsEvent.FollowTapped(row)) },
                        )
                    }
                }
                when (items.loadState.append) {
                    is LoadState.Loading -> item(key = "append-loading") { LoadingBlock(Modifier.fillMaxWidth().padding(16.dp)) }
                    is LoadState.Error -> item(key = "append-error") { ErrorBlock(onRetry = items::retry) }
                    is LoadState.NotLoading -> Unit
                }
            }
        refresh is LoadState.Loading -> LoadingBlock(modifier.fillMaxSize())
        refresh is LoadState.Error -> ErrorBlock(onRetry = items::retry, modifier = modifier.fillMaxSize())
        else -> MessageBlock(text = emptyMessage, modifier = modifier.fillMaxSize())
    }
}

/** One account: the shared actor row plus its follow action, when it has one. */
@Composable
internal fun ConnectionRow(
    row: ConnectionRowUi,
    display: ConnectionFollowDisplay,
    onClick: () -> Unit,
    onFollowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NubecitaActorRow(
        actor = row.actor,
        onClick = onClick,
        modifier = modifier,
        showAvatarFallback = true,
        trailingContent =
            if (row.canFollow) {
                {
                    FollowButton(
                        name = row.actor.displayName ?: row.actor.handle,
                        following = display.following,
                        onClick = onFollowClick,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun FollowButton(
    name: String,
    following: Boolean,
    onClick: () -> Unit,
) {
    // A click label, not a contentDescription: the button already has visible
    // text, and every row says the same thing without naming the account.
    val clickLabel =
        stringResource(
            if (following) R.string.profile_connections_unfollow_label else R.string.profile_connections_follow_label,
            name,
        )
    val semantics = Modifier.semantics { onClick(label = clickLabel, action = null) }
    if (following) {
        OutlinedButton(onClick = onClick, modifier = semantics) {
            Text(stringResource(R.string.profile_action_following), maxLines = 1)
        }
    } else {
        Button(onClick = onClick, modifier = semantics) {
            Text(stringResource(R.string.profile_action_follow), maxLines = 1)
        }
    }
}

@Composable
private fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        NubecitaWavyProgressIndicator()
    }
}

@Composable
private fun MessageBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorBlock(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.profile_connections_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.profile_error_retry))
        }
    }
}
