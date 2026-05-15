package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.PostCallbacks
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
        // Layout strategy: the LazyColumn fills the FULL Scaffold area
        // (extending behind the topBar). The bar's top inset flows through
        // `contentPadding.top` — so the sticky pill row docks just below
        // the bar — and the hero item (item 0) uses `Modifier.layout` to
        // shrink its slot by `topInsetPx` while pulling its drawn position
        // up by the same amount. Net: the hero's gradient draws edge-to-
        // edge from the very top of the screen (under the alpha-modulated
        // bar), no empty band on landing; the pills still dock cleanly
        // below the bar; and `PullToRefreshBox`'s indicator anchors at the
        // bar's bottom edge (its bounds are also full-screen now, but the
        // contentPadding flows through the LazyColumn's drag offset).
        // Hoist the refresh state so we can wire it to a custom indicator.
        // Default PullToRefreshBox positions its indicator at the box's
        // top edge (= screen top here, since the box bounds extend to
        // screen top to let the hero gradient draw behind the bar). That
        // anchors the spinner under the status bar / camera cutout. We
        // offset the indicator down by the bar's reserved height so it
        // appears just below the bar instead.
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
                            .offset(y = padding.calculateTopPadding()),
                    isRefreshing = activeTabIsRefreshing,
                    state = pullState,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                item(key = "hero", contentType = "hero") {
                    val density = LocalDensity.current
                    val topInsetDp = padding.calculateTopPadding()
                    val topInsetPx = with(density) { topInsetDp.roundToPx() }
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
                        // [topInset] reserves space inside the hero's content for
                        // the bar's vertical reservation. Combined with the
                        // [Modifier.layout] shift below, the gradient backdrop
                        // extends edge-to-edge from screen top while the avatar
                        // / display-name / handle / loading skeleton / error
                        // body sit at the post-bar position they'd occupy in a
                        // no-bar layout — keeping the avatar clear of the
                        // camera cutout.
                        topInset = topInsetDp,
                        modifier =
                            Modifier.layout { measurable, constraints ->
                                // Measure the hero at its natural (now larger by
                                // topInsetPx, because the inner content reserves
                                // that vertical space) size, then shrink its
                                // layout slot by `topInsetPx` (so the next item —
                                // the pills stickyHeader — starts at the hero's
                                // visible bottom, not `topInsetPx` below it) and
                                // pull its placed position up by `topInsetPx`
                                // (so it draws from the LazyColumn's bounds top,
                                // which is the screen top behind the transparent
                                // bar).
                                val placeable = measurable.measure(constraints)
                                val slotHeight =
                                    (placeable.height - topInsetPx).coerceAtLeast(0)
                                layout(placeable.width, slotHeight) {
                                    placeable.place(0, -topInsetPx)
                                }
                            },
                    )
                }
                stickyHeader(key = "tabs", contentType = "tabs") {
                    // Solid surface backdrop matches the bar above it once
                    // the user has scrolled past the hero — bar + pills read
                    // as one continuous header surface when stuck. While the
                    // hero is in view, the pills sit on the same surface
                    // tone the bar will fade to, which is intentionally NOT
                    // the gradient color (an earlier iteration sampled the
                    // hero gradient here, but contrast against the pill
                    // chips' content was unworkable on saturated banners).
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
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
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Replies)) },
                            lastLikeTapPostUri = state.lastLikeTapPostUri,
                            lastRepostTapPostUri = state.lastRepostTapPostUri,
                        )
                    ProfileTab.Media ->
                        profileMediaTabBody(
                            status = state.mediaStatus,
                            // Media cell tap routes directly to MediaViewer
                            // (not PostDetail) so the user lands on the gallery
                            // matching the thumb they just tapped.
                            onMediaTap = { uri -> onEvent(ProfileEvent.OnMediaCellTapped(uri)) },
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
