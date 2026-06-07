package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Brand pull-to-refresh container. The single pull-to-refresh surface used
 * across the app (Feed, Profile, PostDetail, …) so every list gets the same
 * M3 Expressive [PullToRefreshDefaults.LoadingIndicator] — the morphing,
 * contained loading indicator from material.io's pull-to-refresh sample —
 * instead of each screen re-importing the experimental API and picking its
 * own indicator. New pull-to-refresh surfaces use this, not a raw
 * `PullToRefreshBox`.
 *
 * ## Positioning behind a `topBar`
 *
 * The default M3 indicator anchors to the **top-center of the box**, and a
 * `PullToRefreshBox` placed in a `Scaffold` content slot fills the whole
 * content area — starting at `y=0`, *underneath* the `topBar`. Left alone the
 * indicator renders behind the bar (the Feed's chip-row bug, nubecita-tfbc).
 *
 * The fix is not baked in: the call site passes the inset it already owns —
 * the `Scaffold`'s `innerPadding` — as [indicatorPadding], and the indicator
 * is offset down by it so it clears the bar. This mirrors the `LazyColumn`
 * `contentPadding` convention (a semantic [PaddingValues], not a second
 * `Modifier`): the same `innerPadding` is applied to the scrollable's own
 * `contentPadding` inside [content], keeping the list edge-to-edge behind the
 * system bars while the indicator sits below the bar. Only the **top** inset is
 * applied to the top-center indicator — the horizontal insets in a landscape
 * `innerPadding` (asymmetric display cutout) would otherwise shift it
 * off-center.
 *
 * @param isRefreshing whether a refresh is in progress (drives the indicator's
 *   determinate-pull → indeterminate-spin transition).
 * @param onRefresh invoked when the user pulls past the threshold.
 * @param modifier applied to the root [PullToRefreshBox]; defaults to
 *   `fillMaxSize()` so callers can override sizing (e.g. tests/previews).
 * @param state hoist when the call site needs the pull fraction; defaults to a
 *   remembered state.
 * @param indicatorPadding inset for the indicator only — pass the `Scaffold`
 *   `innerPadding` so it clears any `topBar`.
 * @param content the scrollable content (apply the same inset to its own
 *   `contentPadding`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NubecitaPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    state: PullToRefreshState = rememberPullToRefreshState(),
    indicatorPadding: PaddingValues = PaddingValues(),
    content: @Composable (BoxScope.() -> Unit),
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            // M3 Expressive contained LoadingIndicator (the morphing polygon),
            // pinned top-center per Material guidance and offset below the
            // caller's topBar by the TOP inset only (horizontal insets would
            // shift a top-center indicator off-center on landscape cutouts).
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorPadding.calculateTopPadding()),
            )
        },
        content = content,
    )
}
