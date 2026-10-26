package net.kikin.nubecita.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import net.kikin.nubecita.wear.feed.FeedPickerScreen
import net.kikin.nubecita.wear.feed.FeedScreen
import net.kikin.nubecita.wear.feed.PostScreen
import net.kikin.nubecita.wear.feed.WatchFeedEvent
import net.kikin.nubecita.wear.feed.WatchFeedViewModel
import net.kikin.nubecita.wear.feed.rememberNow
import net.kikin.nubecita.wear.gate.WearGate
import net.kikin.nubecita.wear.gate.WearGateEvent
import net.kikin.nubecita.wear.gate.WearGateViewModel
import net.kikin.nubecita.wear.navigation.WearFeedPicker
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.navigation.WearPost
import net.kikin.nubecita.wear.ui.WearTimeText
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

/**
 * Root of the Wear app: the single [AppScaffold] (it owns `TimeText` and the
 * shared scaffold state every screen's `ScreenScaffold` plugs into), the
 * [WearGate] that explains a missing phone app or sign-in, and — once signed
 * in — a Navigation 3 back stack where swipe-to-dismiss pops.
 *
 * The back stack is keyed by account, so a different account never inherits
 * the previous account's open screens.
 */
@Composable
fun NubecitaWearApp(
    viewModel: WearGateViewModel = hiltViewModel(),
    feedViewModel: WatchFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.handleEvent(WearGateEvent.Resumed)
        onPauseOrDispose {}
    }
    NubecitaWearTheme {
        AppScaffold(timeText = { WearTimeText() }) {
            WearGate(state = state, onEvent = viewModel::handleEvent) { account ->
                LaunchedEffect(account.did) { feedViewModel.handleEvent(WatchFeedEvent.AccountChanged(account.did)) }
                val now = rememberNow()
                key(account.did) {
                    val backStack = rememberNavBackStack(WearHome)
                    NavDisplay(
                        backStack = backStack,
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                        entryProvider =
                            entryProvider {
                                entry<WearHome> {
                                    FeedScreen(
                                        state = feedState,
                                        nowMs = now,
                                        onPostClick = { post -> backStack.add(WearPost(post.uri)) },
                                        onSwitchFeed = { backStack.add(WearFeedPicker) },
                                    )
                                }
                                entry<WearFeedPicker> {
                                    FeedPickerScreen(
                                        feeds = feedState.feeds,
                                        selectedKey = feedState.selectedKey,
                                        onSelect = { feed ->
                                            feedViewModel.handleEvent(WatchFeedEvent.FeedSelected(feed.key))
                                            backStack.removeLastOrNull()
                                        },
                                    )
                                }
                                entry<WearPost> { route ->
                                    // The post may vanish when a newer snapshot lands;
                                    // go back to the feed rather than show a blank page.
                                    val post = feedState.posts.firstOrNull { it.uri == route.uri }
                                    if (post != null) {
                                        PostScreen(post = post, nowMs = now)
                                    } else {
                                        LaunchedEffect(route) { backStack.remove(route) }
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
