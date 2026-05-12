package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.profile.impl.ui.ProfileHero
import net.kikin.nubecita.feature.profile.impl.ui.ProfileTabPlaceholder
import net.kikin.nubecita.feature.profile.impl.ui.profileFeedTabBody

private const val PREFETCH_DISTANCE = 5

/**
 * Stateless screen body. Takes the canonical
 * [ProfileScreenViewState] from Bead C plus the small set of
 * callbacks the host wires to VM events. Previews and screenshot
 * tests invoke this directly with fixture inputs — no ViewModel, no
 * Hilt graph, no live network.
 *
 * Renders one LazyColumn for the whole screen: hero as the first
 * item, sticky pill tabs as a stickyHeader, and the active tab body
 * contributed via LazyListScope extensions (Posts) or single items
 * (Replies / Media placeholders).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ProfileScreenContent(
    state: ProfileScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    postCallbacks: PostCallbacks,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillTabs = rememberProfilePillTabs()
    val activeTabIsRefreshing = state.activeTabIsRefreshing()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = activeTabIsRefreshing,
            onRefresh = { onEvent(ProfileEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item(key = "hero", contentType = "hero") {
                    ProfileHero(
                        header = state.header,
                        headerError = state.headerError,
                        ownProfile = state.ownProfile,
                        onRetryHeader = { onEvent(ProfileEvent.Refresh) },
                        onEditTap = { onEvent(ProfileEvent.EditTapped) },
                        onOverflowTap = { /* Bead F wires Settings */ },
                    )
                }
                stickyHeader(key = "tabs", contentType = "tabs") {
                    ProfilePillTabs(
                        tabs = pillTabs,
                        selectedValue = state.selectedTab,
                        onSelect = { tab -> onEvent(ProfileEvent.TabSelected(tab)) },
                    )
                }
                when (state.selectedTab) {
                    ProfileTab.Posts ->
                        profileFeedTabBody(
                            tab = ProfileTab.Posts,
                            status = state.postsStatus,
                            callbacks = postCallbacks,
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Posts)) },
                        )
                    ProfileTab.Replies ->
                        item(key = "replies-placeholder", contentType = "placeholder") {
                            ProfileTabPlaceholder(tab = ProfileTab.Replies)
                        }
                    ProfileTab.Media ->
                        item(key = "media-placeholder", contentType = "placeholder") {
                            ProfileTabPlaceholder(tab = ProfileTab.Media)
                        }
                }
            }
        }
    }

    // Pagination: gate the LoadMore dispatch on the active tab being Posts.
    // Without the gate, landing on the Replies / Media placeholder (which
    // contributes one item to the LazyColumn) immediately satisfies
    // `lastVisible > totalItemCount - PREFETCH_DISTANCE` and would fire
    // a stray LoadMore event for the Posts tab. All values captured by
    // the LaunchedEffect (keyed only on [listState]) are funneled through
    // [rememberUpdatedState] so a re-bound onEvent / selectedTab / status
    // is observed by the still-running effect without restarting it.
    val currentSelectedTab by rememberUpdatedState(state.selectedTab)
    val currentPostsStatus by rememberUpdatedState(state.postsStatus)
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
                val status = currentPostsStatus
                if (
                    pastThreshold &&
                    currentSelectedTab == ProfileTab.Posts &&
                    status is TabLoadStatus.Loaded &&
                    status.hasMore &&
                    !status.isAppending
                ) {
                    currentOnEvent(ProfileEvent.LoadMore(ProfileTab.Posts))
                }
            }
    }
}

/**
 * Returns true when the currently-selected tab is in a `Loaded`
 * status with `isRefreshing = true`. PullToRefreshBox uses this to
 * drive its spinner.
 */
private fun ProfileScreenViewState.activeTabIsRefreshing(): Boolean {
    val status =
        when (selectedTab) {
            ProfileTab.Posts -> postsStatus
            ProfileTab.Replies -> repliesStatus
            ProfileTab.Media -> mediaStatus
        }
    return status is TabLoadStatus.Loaded && status.isRefreshing
}
