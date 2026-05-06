package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.kikin.nubecita.R
import net.kikin.nubecita.core.common.navigation.LocalComposerLauncher
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalScrollToTopSignal
import net.kikin.nubecita.core.common.navigation.rememberMainShellNavState
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.navigation.NavigationEntryPoint
import net.kikin.nubecita.shell.composer.ComposerLauncherState
import net.kikin.nubecita.shell.composer.ComposerOverlay
import net.kikin.nubecita.shell.composer.rememberComposerLauncher

/**
 * Top-level adaptive navigation shell hosted by the `Main` `NavEntry`.
 *
 * Wraps an inner `NavDisplay` in `NavigationSuiteScaffold`, which auto-
 * swaps `NavigationBar` (compact widths) → `NavigationRail` (medium and
 * expanded widths). Drawer mode is suppressed: with only four
 * destinations, a permanent drawer wastes screen real estate.
 *
 * The four top-level destinations — Feed, Search, Chats, You — are
 * registered via the `@MainShell`-qualified `EntryProviderInstaller` set
 * collected from feature modules. `:feature:feed:impl` provides the Feed
 * entry; the other three are provided by `:app`'s
 * [MainShellPlaceholderModule] until each feature's own epic stands up
 * its `:impl` module.
 *
 * Per-tab back-stack state lives in `MainShellNavState`, created via
 * `rememberMainShellNavState(...)` in this composable's body. The state
 * is exposed to descendant Composables via [LocalMainShellNavState] so
 * tab-internal navigation can flow through the MVI `UiEffect.Navigate`
 * pattern without VMs ever touching the navigator (see the change
 * `add-adaptive-navigation-shell` design doc decision D2).
 *
 * The inner `NavDisplay` is supplied a `ListDetailSceneStrategy`. On
 * compact widths the strategy passes through to the existing single-pane
 * scene; on medium/expanded widths, entries marked with
 * `ListDetailSceneStrategy.listPane{}` metadata render in the left pane
 * with their `detailPlaceholder` (or the next `detailPane()` entry on the
 * stack) in the right pane. Entries with no metadata render full-screen
 * regardless of width — the strategy is opt-in per entry. See the
 * `adopt-list-detail-scene-strategy` change for the full requirement set.
 *
 * Lifecycle: when the outer `Navigator` transitions away from `Main`
 * (e.g. `replaceTo(Login)` on logout), this composable leaves
 * composition, the `remember`'d `MainShellNavState` is GC'd, and any
 * per-tab residue is discarded.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainShell(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                NavigationEntryPoint::class.java,
            )
        }
    val installers = remember(entryPoint) { entryPoint.mainShellEntryProviderInstallers() }

    val mainShellNavState =
        rememberMainShellNavState(
            startRoute = Feed,
            topLevelRoutes = TopLevelDestinations.map { it.key },
        )

    // Hot SharedFlow that fires `Unit` on bottom-nav tab RE-TAP. Feature
    // screens that opt in (today: FeedScreen) collect this in a
    // LaunchedEffect and call animateScrollToItem(0).
    //
    // `extraBufferCapacity = 1` + `BufferOverflow.DROP_OLDEST` means
    // `tryEmit` always succeeds (no rendezvous semantics): if a tap fires
    // while the collector's lambda is mid-suspend (e.g. running an
    // animateScrollToItem from a previous emission), the new emission
    // buffers; rapid double-taps collapse into a single scroll-to-top
    // (DROP_OLDEST keeps the most recent). With pure replay=0+buffer=0
    // (rendezvous), `tryEmit` returns false during the brief
    // LaunchedEffect-restart window and the user's tap is silently
    // dropped.
    //
    // The asSharedFlow() wrapper is `remember`-d so the CompositionLocal
    // value is stable across recompositions and feature LaunchedEffects
    // keyed on the SharedFlow don't restart unnecessarily.
    val scrollToTopSignal =
        remember {
            MutableSharedFlow<Unit>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    val readOnlyScrollToTopSignal = remember(scrollToTopSignal) { scrollToTopSignal.asSharedFlow() }

    // MainShell-scoped overlay launcher state for the composer at
    // Medium / Expanded widths. At Compact width this state stays
    // Closed forever — the launcher lambda below pushes a route onto
    // the inner NavDisplay instead.
    val composerLauncherState = remember { ComposerLauncherState() }
    val composerLauncher =
        rememberComposerLauncher(
            navState = mainShellNavState,
            launcherState = composerLauncherState,
        )

    // Shared between the scene strategy below and the bar/rail selector
    // further down — both need the same window-class signal.
    val adaptiveInfo = currentWindowAdaptiveInfo()

    // The default `calculatePaneScaffoldDirective` only enables two-pane
    // layout at Expanded widths (≥840dp). Bluesky/X/Threads convention —
    // and what design.md predicts — is two-pane at Medium widths (≥600dp,
    // i.e. tablet portrait) too, so use the explicit
    // `…WithTwoPanesOnMediumWidth` directive variant.
    val sceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(
            directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo),
        )

    // Default `calculateFromAdaptiveInfo` returns `NavigationDrawer` at
    // expanded widths on some form factors. With only four destinations,
    // a permanent drawer is overkill — collapse to rail in that case.
    val defaultLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val layoutType =
        if (defaultLayoutType == NavigationSuiteType.NavigationDrawer) {
            NavigationSuiteType.NavigationRail
        } else {
            defaultLayoutType
        }

    CompositionLocalProvider(
        LocalMainShellNavState provides mainShellNavState,
        LocalScrollToTopSignal provides readOnlyScrollToTopSignal,
        LocalComposerLauncher provides composerLauncher,
    ) {
        MainShellChrome(
            activeKey = mainShellNavState.topLevelKey,
            onTabClick = { tapped ->
                // Re-tap on the active tab fires the scroll-to-top signal
                // (any feature screen collecting LocalScrollToTopSignal
                // scrolls its list back to position 0). Switching tabs
                // navigates as before — the destination tab restores its
                // last scroll position via Nav3's per-tab back-stack.
                // `mainShellNavState.topLevelKey` resolves the post-mutation
                // active tab so a rapid double-tap during a tab switch
                // animation behaves correctly (per the change's design
                // Decision 3).
                if (tapped == mainShellNavState.topLevelKey) {
                    scrollToTopSignal.tryEmit(Unit)
                } else {
                    mainShellNavState.addTopLevel(tapped)
                }
            },
            layoutType = layoutType,
            modifier = modifier,
        ) {
            NavDisplay(
                backStack = mainShellNavState.backStack,
                onBack = { mainShellNavState.removeLast() },
                sceneStrategies = listOf(sceneStrategy),
                // SceneSetupNavEntryDecorator is internal in nav3-ui — NavDisplay applies
                // it itself. Supply only the public decorators required for hiltViewModel()
                // and saved state to work inside NavEntries.
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        installers.forEach { installer -> installer() }
                    },
            )
            // Centered-Dialog composer overlay for Medium / Expanded
            // widths. Renders nothing while launcher state is Closed.
            // Sibling-of-NavDisplay placement so the overlay's scrim
            // covers the full shell — including the navigation
            // suite's bar/rail — matching the modal-creation surface
            // intent.
            ComposerOverlay(
                state = composerLauncherState.state,
                onClose = { composerLauncherState.close() },
            )
        }
    }
}

/**
 * Hilt-free chrome wrapper isolated from `MainShell` so previews and
 * screenshot tests can exercise the bar/rail swap and selected-state
 * indicators without standing up an entry-point or a back stack.
 *
 * @param activeKey The currently selected top-level destination's [NavKey].
 *   Drives which item renders in selected (filled icon) state.
 * @param onTabClick Invoked when the user taps a navigation item.
 * @param layoutType Forces a specific [NavigationSuiteType]. Production
 *   callers compute this from `currentWindowAdaptiveInfo()`; previews and
 *   tests pass a fixed value to assert each layout independently.
 * @param content The inner content rendered alongside the bar/rail —
 *   typically the inner `NavDisplay`.
 */
@Composable
internal fun MainShellChrome(
    activeKey: NavKey,
    onTabClick: (NavKey) -> Unit,
    layoutType: NavigationSuiteType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestinations.forEach { destination ->
                val isSelected = activeKey == destination.key
                item(
                    selected = isSelected,
                    onClick = { onTabClick(destination.key) },
                    icon = {
                        Icon(
                            imageVector =
                                if (isSelected) {
                                    destination.iconSelected
                                } else {
                                    destination.iconUnselected
                                },
                            // The accessible name comes from `label` below; setting
                            // `contentDescription` to the same string would make
                            // TalkBack announce the destination twice.
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
        content = content,
    )
}

internal data class TopLevelDestination(
    val key: NavKey,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
    val labelRes: Int,
)

/**
 * The four top-level destinations rendered in the navigation suite, in
 * display order (Feed first, You last). Order is load-bearing: the inner
 * `NavDisplay`'s flattened back stack is computed using Feed as the start
 * route per the recipe's "exit through home" rule, and the
 * `NavigationSuiteScaffold` items render in iteration order.
 */
internal val TopLevelDestinations: List<TopLevelDestination> =
    listOf(
        TopLevelDestination(
            key = Feed,
            iconSelected = Icons.Filled.Home,
            iconUnselected = Icons.Outlined.Home,
            labelRes = R.string.main_shell_tab_feed,
        ),
        TopLevelDestination(
            key = Search,
            iconSelected = Icons.Filled.Search,
            iconUnselected = Icons.Outlined.Search,
            labelRes = R.string.main_shell_tab_search,
        ),
        TopLevelDestination(
            key = Chats,
            iconSelected = Icons.Filled.ChatBubble,
            iconUnselected = Icons.Outlined.ChatBubbleOutline,
            labelRes = R.string.main_shell_tab_chats,
        ),
        TopLevelDestination(
            key = Profile(handle = null),
            iconSelected = Icons.Filled.Person,
            iconUnselected = Icons.Outlined.Person,
            labelRes = R.string.main_shell_tab_you,
        ),
    )
