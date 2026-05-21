package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.profile.impl.ui.ProfileHero
import net.kikin.nubecita.feature.profile.impl.ui.ProfileTopBar
import net.kikin.nubecita.feature.profile.impl.ui.ProfileVerbsRow
import net.kikin.nubecita.feature.profile.impl.ui.profileFeedTabBody
import net.kikin.nubecita.feature.profile.impl.ui.profileMediaTabBody

private const val PREFETCH_DISTANCE = 5

/**
 * Stateless screen body. Takes the canonical
 * [ProfileScreenViewState] plus the small set of
 * callbacks the host wires to VM events.
 *
 * Updated to Material 3 Expressive design:
 * - TopAppBar manually overlayed for total Z-order control.
 * - Coordinated sticky header: docks exactly below the TopAppBar.
 * - Hero draws edge-to-edge behind the transparent bar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileScreenContent(
    state: ProfileScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    postCallbacks: PostCallbacks,
    onEvent: (ProfileEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pillTabs = rememberProfilePillTabs()
    val activeTabIsRefreshing = state.activeTabIsRefreshing()
    val onVideoTap =
        remember(onEvent) {
            { uri: String -> onEvent(ProfileEvent.OnVideoTapped(uri)) }
        }

    // Top Bar height = Status Bars + 64dp (Material 3 standard)
    val statusBarsPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarsPadding + 64.dp
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {},
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            val pullState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = activeTabIsRefreshing,
                onRefresh = { onEvent(ProfileEvent.Refresh) },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topBarHeight),
                        isRefreshing = activeTabIsRefreshing,
                        state = pullState,
                    )
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Using top contentPadding ensures stickyHeader pins below the TopAppBar.
                    contentPadding = PaddingValues(top = topBarHeight, bottom = navBarsPadding),
                ) {
                    item(key = "hero", contentType = "hero") {
                        ProfileHero(
                            header = state.header,
                            headerError = state.headerError,
                            onRetryHeader = { onEvent(ProfileEvent.Refresh) },
                            topInset = statusBarsPadding,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val topBarHeightPx = topBarHeight.roundToPx()
                                        // Pull the Hero up by topBarHeightPx to cancel the LazyColumn's
                                        // contentPadding, making it draw behind the TopAppBar.
                                        layout(placeable.width, placeable.height - topBarHeightPx) {
                                            placeable.place(0, -topBarHeightPx)
                                        }
                                    },
                        )
                    }
                    stickyHeader(key = "sticky_controls", contentType = "sticky_controls") {
                        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            ProfileVerbsRow(
                                ownProfile = state.ownProfile,
                                viewerRelationship = state.viewerRelationship,
                                canMessage = state.header?.canMessage ?: true,
                                onEdit = { onEvent(ProfileEvent.EditTapped) },
                                onFollow = { onEvent(ProfileEvent.FollowTapped) },
                                onMessage = { onEvent(ProfileEvent.MessageTapped) },
                                onReport = { onEvent(ProfileEvent.OnReportAccountRequested) },
                                onOverflowAction = { action -> onEvent(ProfileEvent.StubActionTapped(action)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ProfilePillTabs(
                                tabs = pillTabs,
                                selectedValue = state.selectedTab,
                                onSelect = { tab -> onEvent(ProfileEvent.TabSelected(tab)) },
                            )
                        }
                    }
                    when (state.selectedTab) {
                        ProfileTab.Posts ->
                            profileFeedTabBody(
                                tab = ProfileTab.Posts,
                                status = state.postsStatus,
                                callbacks = postCallbacks,
                                onImageTap = { post, idx -> onEvent(ProfileEvent.OnImageTapped(post, idx)) },
                                onVideoTap = onVideoTap,
                                onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Posts)) },
                                lastLikeTapPostUri = state.lastLikeTapPostUri,
                                lastRepostTapPostUri = state.lastRepostTapPostUri,
                            )
                        ProfileTab.Replies ->
                            profileFeedTabBody(
                                tab = ProfileTab.Replies,
                                status = state.repliesStatus,
                                callbacks = postCallbacks,
                                onImageTap = { post, idx -> onEvent(ProfileEvent.OnImageTapped(post, idx)) },
                                onVideoTap = onVideoTap,
                                onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Replies)) },
                                lastLikeTapPostUri = state.lastLikeTapPostUri,
                                lastRepostTapPostUri = state.lastRepostTapPostUri,
                            )
                        ProfileTab.Media ->
                            profileMediaTabBody(
                                status = state.mediaStatus,
                                onMediaTap = { cell ->
                                    onEvent(
                                        ProfileEvent.OnMediaCellTapped(
                                            postUri = cell.postUri,
                                            isVideo = cell.isVideo,
                                        ),
                                    )
                                },
                                onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Media)) },
                            )
                    }
                }
            }
        }
        ProfileTopBar(
            header = state.header,
            listState = listState,
            ownProfile = state.ownProfile,
            onBack = onBack,
            onSettings = { onEvent(ProfileEvent.SettingsTapped) },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    // Pagination
    val currentSelectedTab by rememberUpdatedState(state.selectedTab)
    val currentActiveTabStatus by rememberUpdatedState(state.activeTabStatus())
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible > total - PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                val status = currentActiveTabStatus
                if (
                    pastThreshold &&
                    status is TabLoadStatus.Loaded &&
                    status.hasMore &&
                    !status.isAppending
                ) {
                    currentOnEvent(ProfileEvent.LoadMore(currentSelectedTab))
                }
            }
    }
}

private fun ProfileScreenViewState.activeTabStatus(): TabLoadStatus =
    when (selectedTab) {
        ProfileTab.Posts -> postsStatus
        ProfileTab.Replies -> repliesStatus
        ProfileTab.Media -> mediaStatus
    }

private fun ProfileScreenViewState.activeTabIsRefreshing(): Boolean {
    val status = activeTabStatus()
    return status is TabLoadStatus.Loaded && status.isRefreshing
}
