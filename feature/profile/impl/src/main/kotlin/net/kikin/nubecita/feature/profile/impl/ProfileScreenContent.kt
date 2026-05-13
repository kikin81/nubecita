package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.hero.rememberBoldHeroGradient
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.profile.impl.ui.ProfileHero
import net.kikin.nubecita.feature.profile.impl.ui.ProfileTopBar
import net.kikin.nubecita.feature.profile.impl.ui.profileFeedTabBody
import net.kikin.nubecita.feature.profile.impl.ui.profileMediaTabBody

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
 * contributed via LazyListScope extensions — [profileFeedTabBody]
 * for Posts and Replies, [profileMediaTabBody] for the row-packed
 * 3-column media grid.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ProfileTopBar(header = state.header, listState = listState, onBack = onBack) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = activeTabIsRefreshing,
            onRefresh = { onEvent(ProfileEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(padding),
                contentPadding = padding,
            ) {
                item(key = "hero", contentType = "hero") {
                    ProfileHero(
                        header = state.header,
                        headerError = state.headerError,
                        ownProfile = state.ownProfile,
                        viewerRelationship = state.viewerRelationship,
                        onRetryHeader = { onEvent(ProfileEvent.Refresh) },
                        onEditTap = { onEvent(ProfileEvent.EditTapped) },
                        onFollowTap = { onEvent(ProfileEvent.FollowTapped) },
                        onMessageTap = { onEvent(ProfileEvent.MessageTapped) },
                        onOverflowAction = { action -> onEvent(ProfileEvent.StubActionTapped(action)) },
                        onSettingsTap = { onEvent(ProfileEvent.SettingsTapped) },
                    )
                }
                stickyHeader(key = "tabs", contentType = "tabs") {
                    val header = state.header
                    val pillsBackdrop =
                        if (header != null) {
                            rememberBoldHeroGradient(
                                banner = header.bannerUrl,
                                avatarHue = header.avatarHue,
                            ).top
                        } else {
                            Color.Transparent
                        }
                    Box(modifier = Modifier.background(pillsBackdrop)) {
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
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Posts)) },
                        )
                    ProfileTab.Replies ->
                        profileFeedTabBody(
                            tab = ProfileTab.Replies,
                            status = state.repliesStatus,
                            callbacks = postCallbacks,
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Replies)) },
                        )
                    ProfileTab.Media ->
                        profileMediaTabBody(
                            status = state.mediaStatus,
                            onMediaTap = { uri -> onEvent(ProfileEvent.PostTapped(uri)) },
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Media)) },
                        )
                }
            }
        }
    }

    // Pagination: fire LoadMore for whichever tab is currently active when
    // the LazyColumn's last-visible item passes the prefetch threshold and
    // the active tab's status is `Loaded` with `hasMore && !isAppending`.
    // All values captured by the LaunchedEffect (keyed only on [listState])
    // are funneled through [rememberUpdatedState] so a re-bound
    // onEvent / selectedTab / activeTabStatus is observed by the
    // still-running effect without restarting it.
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

/**
 * Returns the [TabLoadStatus] of the currently-selected tab. Used by
 * the pagination gate to evaluate `Loaded && hasMore && !isAppending`
 * for whichever tab is active — Bead E generalized this from Bead D's
 * Posts-only hardcoded gate.
 */
private fun ProfileScreenViewState.activeTabStatus(): TabLoadStatus =
    when (selectedTab) {
        ProfileTab.Posts -> postsStatus
        ProfileTab.Replies -> repliesStatus
        ProfileTab.Media -> mediaStatus
    }

/**
 * Returns true when the currently-selected tab is in a `Loaded`
 * status with `isRefreshing = true`. PullToRefreshBox uses this to
 * drive its spinner.
 */
private fun ProfileScreenViewState.activeTabIsRefreshing(): Boolean {
    val status = activeTabStatus()
    return status is TabLoadStatus.Loaded && status.isRefreshing
}
