package net.kikin.nubecita.shell

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.kikin.nubecita.R
import net.kikin.nubecita.core.common.navigation.ComposerSubmitEventsBus
import net.kikin.nubecita.core.common.navigation.LocalComposerLauncher
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEvents
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEventsEmitter
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.core.common.navigation.rememberMainShellNavState
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
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
 * expanded widths). Drawer mode is suppressed: with only five
 * destinations, a permanent drawer wastes screen real estate.
 *
 * The five top-level destinations — Feed, Search, Notifications, Chats,
 * You — are registered via the `@MainShell`-qualified
 * `EntryProviderInstaller` set bound from `:feature:*:impl` modules.
 * The Notifications icon wraps in a `BadgedBox` reading from the
 * `NotificationsUnreadCountStore` (`:feature:notifications:impl`) so the
 * unread count from the foregrounded polling observer surfaces on the
 * bottom-nav chrome without any per-screen wiring.
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
    val deepLinkRouter = remember(entryPoint) { entryPoint.deepLinkRouter() }
    val notificationsUnreadCountStore =
        remember(entryPoint) { entryPoint.notificationsUnreadCountStore() }

    // Unread-count badge source. The store is populated by
    // `NotificationsPollingObserver` (a `ProcessLifecycleOwner`-scoped
    // observer wired in `NubecitaApplication.onCreate`) while the app is
    // foregrounded. `collectAsStateWithLifecycle` releases the collector
    // when MainShell leaves composition (e.g. on logout's outer
    // Navigator transition to Login) so the badge doesn't keep an
    // extra reference to the store across the auth-state boundary.
    val notificationsUnreadCount by
        notificationsUnreadCountStore.unreadCount.collectAsStateWithLifecycle()

    val mainShellNavState =
        rememberMainShellNavState(
            startRoute = Feed,
            topLevelRoutes = TopLevelDestinations.map { it.key },
        )

    // Drain deep-link targets resolved by `MainActivity.handleIntent` onto
    // the inner back stack. The router is a Hilt singleton with a buffered
    // Channel, so a cold-start intent published before MainShell entered
    // composition is held until this collector subscribes — same shape as
    // `OAuthRedirectBroker` for the login path. ViewModels cannot reach
    // the nav state holder (per the MVI conventions in CLAUDE.md), so the
    // Activity-to-shell handoff goes through this router rather than a
    // direct `add(...)` call from `MainActivity`. See nubecita-kf6k.4 for
    // the design decision.
    LaunchedEffect(deepLinkRouter, mainShellNavState) {
        deepLinkRouter.pendingDeepLinks.collect { target ->
            mainShellNavState.add(target)
        }
    }

    // Hot SharedFlow that fires `Unit` on bottom-nav tab RE-TAP. Each
    // active tab's screen interprets the re-tap differently (Feed →
    // scroll to top, Search → focus + IME, Profile → scroll to top —
    // see `TabReTapSignal.kt` KDoc for the full consumer list).
    //
    // `extraBufferCapacity = 1` + `BufferOverflow.DROP_OLDEST` means
    // `tryEmit` always succeeds (no rendezvous semantics): if a tap fires
    // while the collector's lambda is mid-suspend (e.g. running an
    // animateScrollToItem from a previous emission), the new emission
    // buffers; rapid double-taps collapse into a single action
    // (DROP_OLDEST keeps the most recent). With pure replay=0+buffer=0
    // (rendezvous), `tryEmit` returns false during the brief
    // LaunchedEffect-restart window and the user's tap is silently
    // dropped.
    //
    // The asSharedFlow() wrapper is `remember`-d so the CompositionLocal
    // value is stable across recompositions and feature LaunchedEffects
    // keyed on the SharedFlow don't restart unnecessarily.
    val tabReTapSignal =
        remember {
            MutableSharedFlow<Unit>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        }
    val readOnlyTabReTapSignal = remember(tabReTapSignal) { tabReTapSignal.asSharedFlow() }

    // MainShell-scoped composer submit-events bus. Emitted by both
    // composer hosts (the Compact NavDisplay route registered by
    // `ComposerNavigationModule` and the Medium / Expanded
    // `ComposerOverlay` Dialog) on `ComposerEffect.OnSubmitSuccess`.
    // Collected by feature screens (today: the feed) to surface a
    // confirmation snackbar and, when the submit was a reply, run an
    // optimistic `replyCount + 1` on the parent post.
    //
    // Both the read side (`bus.events`) and the write side
    // (`bus.emitter`) are provided shell-wide via separate
    // CompositionLocals below — see `ComposerSubmitEventsBus` kdoc for
    // why the producer/consumer separation is by naming/type rather
    // than visibility scoping.
    val composerSubmitEvents = remember { ComposerSubmitEventsBus() }

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
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    // The default `calculatePaneScaffoldDirective` only enables two-pane
    // layout at Expanded widths (≥840dp). Bluesky/X/Threads convention —
    // and what design.md predicts — is two-pane at Medium widths (≥600dp,
    // i.e. tablet portrait) too, so use the explicit
    // `…WithTwoPanesOnMediumWidth` directive variant.
    //
    // Override `defaultPanePreferredWidth`: the M3 default of 360dp is too
    // narrow on tablets — the Profile screen's pill-tabs row wraps
    // "Replies" to two lines. 412dp at Medium and 440dp at Expanded gives
    // the list-pane chrome room to breathe while leaving sensible room
    // for the detail pane.
    val baseDirective = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo)
    val widthClass = adaptiveInfo.windowSizeClass
    val listDetailDirective =
        baseDirective.copy(
            defaultPanePreferredWidth =
                when {
                    widthClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> 440.dp
                    widthClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> 412.dp
                    else -> baseDirective.defaultPanePreferredWidth
                },
        )
    val sceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(
            directive = listDetailDirective,
            // User-draggable divider between list and detail panes.
            // `paneExpansionState = null` lets the scene strategy create a
            // default expansion state internally. Visual handle follows the
            // M3 sample: a `VerticalDragHandle` with `paneExpansionDraggable`
            // wiring the touch target to the expansion state.
            paneExpansionDragHandle = { state ->
                val interactionSource = remember { MutableInteractionSource() }
                VerticalDragHandle(
                    modifier =
                        Modifier.paneExpansionDraggable(
                            state = state,
                            minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                            interactionSource = interactionSource,
                        ),
                    interactionSource = interactionSource,
                )
            },
        )

    // Default `calculateFromAdaptiveInfo` returns:
    //  - `NavigationBar` at compact widths — the legacy 80dp Material 3
    //    navigation bar. We swap to the M3 Expressive `ShortNavigationBarCompact`
    //    (~64dp, what Play Store and most modern M3 apps use). The
    //    expressive theme is already wired in `:designsystem/Theme.kt`,
    //    so this is the consistent default for our phone-first social
    //    client.
    //  - `NavigationDrawer` at expanded widths on some form factors.
    //    With only four destinations, a permanent drawer is overkill —
    //    collapse to rail in that case.
    val defaultLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val layoutType =
        when (defaultLayoutType) {
            NavigationSuiteType.NavigationBar -> NavigationSuiteType.ShortNavigationBarCompact
            NavigationSuiteType.NavigationDrawer -> NavigationSuiteType.NavigationRail
            else -> defaultLayoutType
        }

    CompositionLocalProvider(
        LocalMainShellNavState provides mainShellNavState,
        LocalTabReTapSignal provides readOnlyTabReTapSignal,
        LocalComposerLauncher provides composerLauncher,
        LocalComposerSubmitEvents provides composerSubmitEvents.events,
        LocalComposerSubmitEventsEmitter provides composerSubmitEvents.emitter,
    ) {
        MainShellChrome(
            activeKey = mainShellNavState.topLevelKey,
            notificationsUnreadCount = notificationsUnreadCount,
            onTabClick = { tapped ->
                // Re-tap on the active tab fires the generic tab-re-tap
                // signal. Each tab's screen interprets it: Feed/Profile
                // scroll their list to top, Search focuses the EditText
                // and opens the IME. Switching tabs navigates as before
                // — the destination tab restores its last scroll position
                // via Nav3's per-tab back-stack.
                // `mainShellNavState.topLevelKey` resolves the post-
                // mutation active tab so a rapid double-tap during a tab
                // switch animation behaves correctly (per the change's
                // design Decision 3).
                if (tapped == mainShellNavState.topLevelKey) {
                    tabReTapSignal.tryEmit(Unit)
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
 * screenshot tests can exercise the bar/rail swap, selected-state
 * indicators, and badge overlay without standing up an entry-point or
 * a back stack.
 *
 * @param activeKey The currently selected top-level destination's [NavKey].
 *   Drives which item renders in selected (filled icon) state.
 * @param notificationsUnreadCount The current unread-count from
 *   `NotificationsUnreadCountStore`. Drives the [BadgedBox] overlay on
 *   the Notifications tab — the badge is omitted entirely when the count
 *   is zero, renders the digit verbatim for 1–99, and clamps to "99+"
 *   per M3 `BadgedBox` overflow convention beyond that. Passed as a
 *   plain `Int` so previews can sweep each rendering threshold without
 *   touching Hilt or the store.
 * @param onTabClick Invoked when the user taps a navigation item.
 * @param layoutType Forces a specific [NavigationSuiteType]. Production
 *   callers compute this from `currentWindowAdaptiveInfoV2()`; previews and
 *   tests pass a fixed value to assert each layout independently.
 * @param content The inner content rendered alongside the bar/rail —
 *   typically the inner `NavDisplay`.
 */
@Composable
internal fun MainShellChrome(
    activeKey: NavKey,
    notificationsUnreadCount: Int,
    onTabClick: (NavKey) -> Unit,
    layoutType: NavigationSuiteType,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Use the `navigationItems` / `navigationSuiteType` overload (not the
    // older `navigationSuiteItems` / `layoutType` one). The M3 1.5.0-alpha19
    // library itself recommends this — see NavigationSuiteScaffold.kt:274
    // ("It is recommended to use the NavigationSuiteScaffold function with
    // the navigationItems param that accepts NavigationSuiteItems instead
    // of this one"). The recommended overload internally applies
    // `navigationSuiteScaffoldConsumeWindowInsets` (NavigationSuiteScaffold.kt:1521)
    // which correctly consumes the bottom system inset for
    // `ShortNavigationBarCompact` / `ShortNavigationBarMedium`; the older
    // overload's inline switch misses those variants and leaves child
    // Scaffolds anchoring FABs above the system inset rather than above
    // the visually-rendered bar (creating a ~32dp gap).
    NavigationSuiteScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        navigationSuiteType = layoutType,
        navigationItems = {
            TopLevelDestinations.forEach { destination ->
                val isSelected = activeKey == destination.key
                // Use NavigationSuiteItem's built-in `badge` slot (M3
                // 1.5.0-alpha20+) rather than wrapping the icon in
                // `BadgedBox` manually. The library positions the badge
                // correctly for each NavigationSuiteType (compact bar vs
                // medium rail vs expanded rail), which a hand-rolled
                // BadgedBox doesn't track.
                val badge: @Composable (() -> Unit)? =
                    if (destination.key == NotificationsTab && notificationsUnreadCount > 0) {
                        { Badge { Text(text = formatUnreadCount(notificationsUnreadCount)) } }
                    } else {
                        null
                    }
                NavigationSuiteItem(
                    navigationSuiteType = layoutType,
                    selected = isSelected,
                    onClick = { onTabClick(destination.key) },
                    icon = {
                        NubecitaIcon(
                            name = destination.iconName,
                            // The accessible name comes from `label` below; setting
                            // `contentDescription` to the same string would make
                            // TalkBack announce the destination twice.
                            contentDescription = null,
                            filled = isSelected,
                        )
                    },
                    badge = badge,
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
        content = content,
    )
}

/**
 * M3 `BadgedBox` truncates large counts to "99+" — the digit cap is
 * what the bottom-nav real estate can fit without pushing the icon
 * off-center. Format the count here so previews can sweep across the
 * 1 / 9 / 99 / 100 boundaries deterministically without depending on
 * `BadgedBox`'s internal heuristic.
 */
private fun formatUnreadCount(count: Int): String = if (count > NOTIFICATIONS_BADGE_OVERFLOW) "$NOTIFICATIONS_BADGE_OVERFLOW+" else count.toString()

private const val NOTIFICATIONS_BADGE_OVERFLOW = 99

internal data class TopLevelDestination(
    val key: NavKey,
    val iconName: NubecitaIconName,
    val labelRes: Int,
)

/**
 * The five top-level destinations rendered in the navigation suite, in
 * display order (Feed first, You last). Order is load-bearing: the inner
 * `NavDisplay`'s flattened back stack is computed using Feed as the start
 * route per the recipe's "exit through home" rule, and the
 * `NavigationSuiteScaffold` items render in iteration order.
 *
 * Notifications sits between Search and Chats per Bluesky's official
 * layout (see openspec change `add-feature-notifications` design D7) —
 * splitting the discovery surfaces (Feed, Search) on the left from the
 * personal-activity surfaces (Notifications, Chats, You) on the right.
 */
internal val TopLevelDestinations: List<TopLevelDestination> =
    listOf(
        TopLevelDestination(
            key = Feed,
            iconName = NubecitaIconName.Home,
            labelRes = R.string.main_shell_tab_feed,
        ),
        TopLevelDestination(
            key = Search,
            iconName = NubecitaIconName.Search,
            labelRes = R.string.main_shell_tab_search,
        ),
        TopLevelDestination(
            key = NotificationsTab,
            iconName = NubecitaIconName.Notifications,
            labelRes = R.string.main_shell_tab_notifications,
        ),
        TopLevelDestination(
            key = Chats,
            iconName = NubecitaIconName.ChatBubble,
            labelRes = R.string.main_shell_tab_chats,
        ),
        TopLevelDestination(
            key = Profile(handle = null),
            iconName = NubecitaIconName.Person,
            labelRes = R.string.main_shell_tab_you,
        ),
    )
