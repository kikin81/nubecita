package net.kikin.nubecita.feature.notifications.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.notifications.impl.ui.ActorListSheet
import net.kikin.nubecita.feature.notifications.impl.ui.FilterChipRow
import net.kikin.nubecita.feature.notifications.impl.ui.NotificationRow
import net.kikin.nubecita.feature.notifications.impl.ui.NotificationsEmptyState
import net.kikin.nubecita.feature.notifications.impl.ui.NotificationsInitialError
import net.kikin.nubecita.feature.profile.api.Profile

private const val PREFETCH_DISTANCE = 5

/**
 * Hilt-aware Notifications tab screen.
 *
 * Splits into a Root (this composable) and a stateless [NotificationsContent]
 * body per the MVI convention. The Root:
 *
 * - Hoists [NotificationsViewModel] via `hiltViewModel()`.
 * - Collects state via `collectAsStateWithLifecycle`.
 * - Drains [NotificationsEffect] in a single outer `LaunchedEffect`.
 * - Reads `LocalMainShellNavState` for the cross-feature navigation push
 *   (`add(target)`) and for tab-exit detection via a
 *   `snapshotFlow { topLevelKey }.dropWhile { it == NotificationsTab }.filter { != NotificationsTab }`
 *   collector — design D6's mark-read-on-tab-exit handshake. The
 *   `dropWhile` is value-based rather than positional so the screen
 *   surviving composition-edge cases (predictive-back peek, list-detail
 *   off-tab composition) still gets the right initial-emission behavior.
 * - Reads `LocalTabReTapSignal` to scroll the LazyColumn to top on
 *   tab re-tap.
 *
 * Actor-list sheet visibility lives on `NotificationsState.actorListSheet`
 * (owned by the VM) rather than as local Compose state. Reading sheet
 * visibility off the VM-projected state means config changes survive via
 * the standard state-restoration path, and the unit-tested reducer is the
 * single source of truth for open/close transitions. Tapping an actor
 * inside the sheet dispatches `SheetDismissed` then pushes
 * `Profile(handle = author.did)` onto the MainShell's inner back stack;
 * the actor identity is the only input the navigation needs, so no
 * VM round-trip is required for the destination resolution.
 *
 * Suppresses VM-forwarding lints — see `FeedScreen` / `ComposerScreen`
 * for the rationale (slack compose-lints 1.5.0+ tightened
 * ComposeViewModelForwarding's data-flow analysis; conflicts with
 * ComposeViewModelInjection on stateful screens that hoist state).
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun NotificationsScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mainShellNavState = LocalMainShellNavState.current
    val tabReTapSignal = LocalTabReTapSignal.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // Stable lambdas — keyed on `viewModel` only because each `handleEvent`
    // call is a bound method reference whose identity doesn't change.
    // Sheet visibility lives on `state.actorListSheet` (owned by the VM)
    // rather than local Compose state — see the contract KDoc.
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val onActorTap =
        remember(mainShellNavState, onEvent) {
            { actor: AuthorUi ->
                onEvent(NotificationsEvent.SheetDismissed)
                mainShellNavState.add(Profile(handle = actor.did))
            }
        }
    val onSheetDismiss =
        remember(onEvent) {
            { onEvent(NotificationsEvent.SheetDismissed) }
        }

    // Resolve Snackbar copy from the Context inside the collector so each
    // emission reads the CURRENT locale / dark-mode / font-scale resources.
    // The earlier approach (three `stringResource()` reads captured by closure
    // at composition time) left the LaunchedEffect coroutine holding the
    // original-locale strings — a system-locale change recomposed the screen
    // but did NOT restart the collector (its only key is `viewModel`), so a
    // subsequent ShowError surfaced stale-locale copy. Resolving from
    // `LocalContext` per-emission removes that footgun and `rememberUpdatedState`
    // keeps the lambda reading the latest Context across recompositions
    // without restarting the collector.
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)

    // Single outer effects collector. Per MVI convention all effects funnel
    // here; the surface today carries exactly two branches: navigate
    // (push a NavKey onto the inner back stack) and show an error
    // (resolve the typed error to a localized Snackbar message). The
    // actor-list sheet is NOT driven through an effect — it lives on
    // `state.actorListSheet` (a reducer-managed field) and is rendered
    // outside this collector at the bottom of the Root composable.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NotificationsEffect.NavigateTo -> mainShellNavState.add(effect.target)
                is NotificationsEffect.ShowError -> {
                    val message = currentContext.getString(effect.error.snackbarMessageRes())
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
            }
        }
    }

    // Tab-exit detection (design D6). Watch the active top-level key; when
    // it transitions away from NotificationsTab fire `TabExited`.
    // `dropWhile { it == NotificationsTab }` is value-based rather than
    // positional (`.drop(1)`) — the latter would also drop a real exit
    // emission in edge scenarios where the screen is composed while the
    // topLevelKey is mid-transition (predictive-back peek, list-detail
    // two-pane composition off the active tab). The dropWhile only swallows
    // the leading run of NotificationsTab emissions, then lets the first
    // non-Notifications transition through.
    LaunchedEffect(onEvent, mainShellNavState) {
        snapshotFlow { mainShellNavState.topLevelKey }
            .dropWhile { it == NotificationsTab }
            .distinctUntilChanged()
            .filter { it != NotificationsTab }
            .collect { onEvent(NotificationsEvent.TabExited) }
    }

    // Tab re-tap (design D11). View-state-aware:
    //
    // - Loaded → scroll the list to top (canonical re-tap affordance).
    // - InitialError → dispatch Refresh so re-tap doubles as retry; a user
    //   who landed on the full-screen error often re-taps the tab habitually
    //   expecting a fresh fetch instead of having to scroll to find the
    //   Retry button below the error copy.
    // - InitialLoading / Empty → `animateScrollToItem(0)` against the
    //   non-composed LazyColumn is a no-op but harmless; no further work.
    //
    // `rememberUpdatedState(state)` ensures the collector lambda reads the
    // current state even though the LaunchedEffect's key set is fixed (we
    // don't want to restart the collector on every state change).
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(tabReTapSignal, listState, onEvent) {
        tabReTapSignal.collect {
            when (currentState.loadStatus) {
                is NotificationsLoadStatus.InitialError -> onEvent(NotificationsEvent.Refresh)
                else -> listState.animateScrollToItem(0)
            }
        }
    }

    NotificationsContent(
        viewState = state.toViewState(),
        activeFilter = state.activeFilter,
        listState = listState,
        snackbarHostState = snackbarHostState,
        onEvent = onEvent,
        modifier = modifier,
    )

    val sheetActors = state.actorListSheet
    if (sheetActors != null) {
        ActorListSheet(
            actors = sheetActors,
            onActorClick = onActorTap,
            onDismiss = onSheetDismiss,
        )
    }
}

/**
 * Stateless screen body. Takes the projected [NotificationsScreenViewState]
 * and a single [onEvent] callback the host wires to VM events. Previews
 * and Compose UI tests invoke this directly with fixture inputs — no
 * ViewModel, no Hilt graph, no live network, no MainShell hookup.
 *
 * Layout:
 * - `Scaffold` with `containerColor = surface` (CLAUDE.md hard rule).
 * - Body: `Column` with [FilterChipRow] above the per-state body.
 * - Per-state branches: shimmer placeholder (InitialLoading), full-screen
 *   error layout (InitialError), full-screen empty layout (Empty), or
 *   the LazyColumn (Loaded).
 *
 * The LazyColumn is wrapped in a [PullToRefreshBox] so a refresh maps to
 * `Refresh`. Tail-append (`LoadMore`) fires via a `snapshotFlow` on the
 * list's `lastVisibleItemIndex` crossing the prefetch threshold —
 * identical to the Feed screen's `LoadMore` wiring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationsContent(
    viewState: NotificationsScreenViewState,
    activeFilter: NotificationFilter,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onEvent: (NotificationsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `activeFilter` is passed in as a sibling param of `viewState` rather
    // than projected through `NotificationsScreenViewState`. The earlier
    // shape only carried `activeFilter` on the Loaded variant and fell
    // back to NotificationFilter.All for InitialLoading / Empty /
    // InitialError — which meant a filter-change transition (clears items
    // → InitialLoading) visibly snapped the chip strip back to All for
    // the duration of the load, lying about the user's intent. Passing
    // `activeFilter` straight through keeps the chip selection truthful
    // across every view-state branch.
    // PullToRefreshBox wraps EVERY per-state body — not just Loaded — so
    // pull-to-refresh works from the Empty / InitialError / InitialLoading
    // affordances too. Non-Loaded branches participate in the pull gesture
    // via `verticalScroll(rememberScrollState())` so the nested-scroll
    // pipeline that PullToRefreshBox listens on has something to bubble
    // from. `isRefreshing` only goes true while we have items to render the
    // indicator over (the Loaded.isRefreshing case); empty-state refresh
    // shows the centered shimmer in the InitialLoading branch instead.
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterChipRow(
                activeFilter = activeFilter,
                onFilterSelect = { onEvent(NotificationsEvent.FilterSelected(it)) },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PullToRefreshBox(
                isRefreshing = (viewState as? NotificationsScreenViewState.Loaded)?.isRefreshing == true,
                onRefresh = { onEvent(NotificationsEvent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (viewState) {
                    NotificationsScreenViewState.InitialLoading ->
                        NotificationsInitialLoading(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                        )
                    NotificationsScreenViewState.Empty ->
                        NotificationsEmptyState(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                        )
                    is NotificationsScreenViewState.InitialError ->
                        NotificationsInitialError(
                            error = viewState.error,
                            onRetry = { onEvent(NotificationsEvent.Refresh) },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                        )
                    is NotificationsScreenViewState.Loaded ->
                        LoadedNotifications(
                            items = viewState.items,
                            isAppending = viewState.isAppending,
                            listState = listState,
                            onEvent = onEvent,
                        )
                }
            }
        }
    }
}

@Composable
private fun LoadedNotifications(
    items: ImmutableList<NotificationItemUi>,
    isAppending: Boolean,
    listState: LazyListState,
    onEvent: (NotificationsEvent) -> Unit,
) {
    // No local PullToRefreshBox — the outer NotificationsContent wraps every
    // per-state body so pull-to-refresh works from Loaded AND from the
    // non-Loaded surfaces (Empty / InitialError / InitialLoading).
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(NotificationsTestTags.LIST),
    ) {
        items(
            items = items,
            key = { it.itemKey },
            contentType = {
                when (it) {
                    is NotificationItemUi.Single -> "single"
                    is NotificationItemUi.Aggregated -> "aggregated"
                }
            },
        ) { item ->
            NotificationRow(
                item = item,
                onClick = { onEvent(NotificationsEvent.RowTapped(item)) },
                onAvatarStackClick = {
                    if (item is NotificationItemUi.Aggregated) {
                        onEvent(NotificationsEvent.AvatarStackTapped(item))
                    }
                },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (isAppending) {
            item(key = "appending", contentType = "appending") {
                NotificationsAppendingIndicator()
            }
        }
    }

    // Pagination trigger — emits once per crossing of the
    // (lastVisibleIndex > items.size - PREFETCH_DISTANCE) boolean threshold.
    // Same shape as :feature:feed:impl's LoadMore wiring: the boolean is
    // distinctUntilChanged'd so we don't re-fire every visible-index change
    // past the threshold (without that, scroll would fire 10–30 LoadMores/s).
    //
    // Guard against short pages: when `items.size < PREFETCH_DISTANCE` the
    // threshold goes negative and `pastThreshold` would be true on the very
    // first frame, triggering LoadMore before the user has scrolled. The
    // `items.size >= PREFETCH_DISTANCE` precondition skips the trigger
    // entirely for short pages — the next refresh / append / filter switch
    // will re-evaluate once the list is large enough to warrant prefetch.
    val currentItems by rememberUpdatedState(items)
    LaunchedEffect(listState, onEvent) {
        snapshotFlow {
            val size = currentItems.size
            if (size < PREFETCH_DISTANCE) {
                false
            } else {
                val lastVisible =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: 0
                lastVisible > size - PREFETCH_DISTANCE
            }
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                if (pastThreshold) onEvent(NotificationsEvent.LoadMore)
            }
    }
}

@Composable
private fun NotificationsInitialLoading(modifier: Modifier = Modifier) {
    // Simple centered indicator — slice 1 stays cheap. A future change can
    // swap in a per-row shimmer matching the Feed's PostCardShimmer roster
    // once aggregation-aware shimmer fixtures exist.
    //
    // The caller passes `Modifier.verticalScroll(rememberScrollState())`
    // when this composable is hosted inside the outer PullToRefreshBox so
    // the pull gesture has a nested-scroll source to bubble from.
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaWavyProgressIndicator()
    }
}

@Composable
private fun NotificationsAppendingIndicator() {
    // fillMaxWidth (not fillMaxSize) — this lives inside a LazyColumn item
    // slot where the main-axis constraint is Constraints.Infinity. Asking
    // for max height there is misleading at best and a real bug if the
    // LazyColumn ever gets wrapped in a bounded-height container.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaWavyProgressIndicator()
    }
}
