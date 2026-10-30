package net.kikin.nubecita.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import net.kikin.nubecita.core.wearcontract.WearDmActions
import net.kikin.nubecita.wear.dms.ConvoListScreen
import net.kikin.nubecita.wear.dms.QuickReplyScreen
import net.kikin.nubecita.wear.dms.ReactionScreen
import net.kikin.nubecita.wear.dms.ThreadScreen
import net.kikin.nubecita.wear.dms.WatchDmEvent
import net.kikin.nubecita.wear.dms.WatchDmStatus
import net.kikin.nubecita.wear.dms.WatchDmViewModel
import net.kikin.nubecita.wear.feed.FeedPickerScreen
import net.kikin.nubecita.wear.feed.FeedScreen
import net.kikin.nubecita.wear.feed.PostScreen
import net.kikin.nubecita.wear.feed.WatchFeedEvent
import net.kikin.nubecita.wear.feed.WatchFeedViewModel
import net.kikin.nubecita.wear.feed.rememberNow
import net.kikin.nubecita.wear.gate.WearGate
import net.kikin.nubecita.wear.gate.WearGateEvent
import net.kikin.nubecita.wear.gate.WearGateViewModel
import net.kikin.nubecita.wear.input.TextReviewScreen
import net.kikin.nubecita.wear.input.rememberWearTextInput
import net.kikin.nubecita.wear.navigation.WearConvos
import net.kikin.nubecita.wear.navigation.WearFeedPicker
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.navigation.WearPost
import net.kikin.nubecita.wear.navigation.WearReact
import net.kikin.nubecita.wear.navigation.WearReply
import net.kikin.nubecita.wear.navigation.WearReplyReview
import net.kikin.nubecita.wear.navigation.WearThread
import net.kikin.nubecita.wear.ui.WearTimeText
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

/**
 * Root of the Wear app: the single [AppScaffold] (it owns `TimeText` and the
 * shared scaffold state every screen's `ScreenScaffold` plugs into), the
 * [WearGate] that explains a missing phone app or sign-in, and — once signed
 * in — a Navigation 3 back stack where swipe-to-dismiss pops.
 *
 * The back stack is keyed by account, so a different account never inherits
 * the previous account's open screens. [openMessages] starts it on the
 * conversation list, above the feed so back still lands there.
 */
@Composable
fun NubecitaWearApp(
    openMessages: Boolean = false,
    viewModel: WearGateViewModel = hiltViewModel(),
    feedViewModel: WatchFeedViewModel = hiltViewModel(),
    dmViewModel: WatchDmViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedState by feedViewModel.uiState.collectAsStateWithLifecycle()
    val dmState by dmViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.handleEvent(WearGateEvent.Resumed)
        onPauseOrDispose {}
    }
    NubecitaWearTheme {
        AppScaffold(timeText = { WearTimeText() }) {
            WearGate(state = state, onEvent = viewModel::handleEvent) { account ->
                LaunchedEffect(account.did) {
                    feedViewModel.handleEvent(WatchFeedEvent.AccountChanged(account.did))
                    dmViewModel.handleEvent(WatchDmEvent.AccountChanged(account.did))
                }
                val now = rememberNow()
                key(account.did) {
                    val backStack = rememberNavBackStack(*if (openMessages) arrayOf<NavKey>(WearHome, WearConvos) else arrayOf<NavKey>(WearHome))
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
                                        unreadMessages = dmState.unreadCount,
                                        onOpenMessages = if (dmState.status == WatchDmStatus.Ready) ({ backStack.add(WearConvos) }) else null,
                                    )
                                }
                                entry<WearConvos> {
                                    ConvoListScreen(
                                        state = dmState,
                                        nowMs = now,
                                        onOpen = { convo ->
                                            dmViewModel.handleEvent(WatchDmEvent.ThreadOpened(convo.convoId))
                                            backStack.add(WearThread(convo.convoId))
                                        },
                                    )
                                }
                                entry<WearThread> { route ->
                                    // A conversation can leave the synced ten; go back rather than show a blank page.
                                    val thread = dmState.thread?.takeIf { it.convoId == route.convoId }
                                    if (thread != null) {
                                        ThreadScreen(
                                            thread = thread,
                                            nowMs = now,
                                            onReply = if (dmState.actionsAvailable) ({ backStack.add(WearReply(thread.convoId)) }) else null,
                                            onReact = if (dmState.actionsAvailable) ({ message -> backStack.add(WearReact(thread.convoId, message.id)) }) else null,
                                            onRetry = { pending -> dmViewModel.handleEvent(WatchDmEvent.RetryTapped(pending.actionId)) },
                                        )
                                    } else if (dmState.status == WatchDmStatus.Ready && dmState.convos.none { it.convoId == route.convoId }) {
                                        LaunchedEffect(route) { backStack.remove(route) }
                                    } else {
                                        // The thread is still loading (or restoring): re-select it.
                                        LaunchedEffect(route) { dmViewModel.handleEvent(WatchDmEvent.ThreadOpened(route.convoId)) }
                                    }
                                }
                                entry<WearReply> { route ->
                                    val writeReply = rememberWearTextInput(stringResource(R.string.wear_input_reply_label)) { text -> backStack.add(WearReplyReview(route.convoId, text)) }
                                    QuickReplyScreen(
                                        onPick = { text ->
                                            dmViewModel.handleEvent(WatchDmEvent.ReplySent(route.convoId, text))
                                            backStack.removeLastOrNull()
                                        },
                                        onWrite = writeReply,
                                    )
                                }
                                entry<WearReplyReview> { route ->
                                    val rewrite =
                                        rememberWearTextInput(stringResource(R.string.wear_input_reply_label)) { text ->
                                            backStack[backStack.lastIndex] = route.copy(text = text)
                                        }
                                    TextReviewScreen(
                                        title = stringResource(R.string.wear_input_reply_label),
                                        text = route.text,
                                        maxGraphemes = WearDmActions.MAX_GRAPHEMES,
                                        onSend = {
                                            dmViewModel.handleEvent(WatchDmEvent.ReplySent(route.convoId, route.text))
                                            // Back to the thread, where the reply shows as "Sending…".
                                            backStack.removeAll { it is WearReplyReview || it is WearReply }
                                        },
                                        onChange = rewrite,
                                    )
                                }
                                entry<WearReact> { route ->
                                    ReactionScreen(
                                        onPick = { emoji ->
                                            dmViewModel.handleEvent(WatchDmEvent.ReactionSent(route.convoId, route.messageId, emoji))
                                            backStack.removeLastOrNull()
                                        },
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
                                        PostScreen(
                                            post = post,
                                            nowMs = now,
                                            actionsAvailable = feedState.actionsAvailable,
                                            onToggleLike = { feedViewModel.handleEvent(WatchFeedEvent.ToggleLike(post.uri)) },
                                            onToggleRepost = { feedViewModel.handleEvent(WatchFeedEvent.ToggleRepost(post.uri)) },
                                            onToggleBookmark = { feedViewModel.handleEvent(WatchFeedEvent.ToggleBookmark(post.uri)) },
                                            onOpenOnPhone = { feedViewModel.handleEvent(WatchFeedEvent.OpenOnPhone(post.uri)) },
                                        )
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
