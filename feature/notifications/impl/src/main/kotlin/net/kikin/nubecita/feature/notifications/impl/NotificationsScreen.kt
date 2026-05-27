package net.kikin.nubecita.feature.notifications.impl

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.LocalTabReTapSignal
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
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
 *   `snapshotFlow { topLevelKey }.drop(1).filter { != NotificationsTab }`
 *   collector — design D6's mark-read-on-tab-exit handshake.
 * - Reads `LocalTabReTapSignal` to scroll the LazyColumn to top on
 *   tab re-tap.
 *
 * The host actor-list bottom sheet lives at this layer so its state can
 * survive configuration changes via `rememberSaveable`. Tapping an actor
 * inside the sheet pushes `Profile(handle = author.did)` onto the
 * MainShell's inner back stack directly — there's no VM round-trip
 * because the actor identity is the only input needed.
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

    // Actor-list sheet visibility. Held as a (rememberSaveable does not
    // serialize `ImmutableList<AuthorUi>` out of the box) plain remember —
    // the sheet's content is purely derived from the latest VM-emitted
    // effect, and a configuration change inside the sheet's lifetime would
    // simply close and re-open it. Acceptable trade-off given the alternative
    // (writing a `Saver<ImmutableList<AuthorUi>>` for a UI-only fixture) is
    // out of scope for slice 1.
    var sheetActors by remember {
        mutableStateOf<ImmutableList<AuthorUi>?>(null)
    }

    // Stable lambdas — keyed on `viewModel` only because each `handleEvent`
    // call is a bound method reference whose identity doesn't change.
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    val onActorTap =
        remember(mainShellNavState) {
            { actor: AuthorUi ->
                sheetActors = null
                mainShellNavState.add(Profile(handle = actor.did))
            }
        }
    val onSheetDismiss = remember { { sheetActors = null } }

    // Pre-resolved Snackbar copy. Reading via stringResource() at
    // composition time keeps locale + dark-mode changes participating in
    // recomposition (lint: LocalContextGetResourceValueCall). Mirrors
    // FeedScreen's pattern.
    val networkErrorMessage = stringResource(R.string.notifications_snackbar_error_network)
    val unauthErrorMessage = stringResource(R.string.notifications_snackbar_error_unauthenticated)
    val unknownErrorMessage = stringResource(R.string.notifications_snackbar_error_unknown)

    // Single outer effects collector. Per MVI convention all effects funnel
    // here; the screen's only branches are: navigate (call .add() on the
    // nav state), surface an error (showSnackbar), or open the actor list
    // sheet (toggle the local Compose state).
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NotificationsEffect.NavigateTo -> mainShellNavState.add(effect.target)
                is NotificationsEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            NotificationsError.Network -> networkErrorMessage
                            NotificationsError.Unauthenticated -> unauthErrorMessage
                            NotificationsError.Unknown -> unknownErrorMessage
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
                is NotificationsEffect.ShowActorList -> {
                    sheetActors = effect.actors
                }
            }
        }
    }

    // Tab-exit detection (design D6). Watch the active top-level key; when
    // it transitions away from NotificationsTab fire `TabExited`. `.drop(1)`
    // is critical — the initial snapshot would otherwise fire TabExited
    // immediately on first composition (the user just opened the tab; that's
    // not an exit). `distinctUntilChanged` defends against duplicate
    // snapshot emissions if MainShellNavState's internal state list churns
    // without the topLevelKey actually changing.
    LaunchedEffect(viewModel, mainShellNavState) {
        snapshotFlow { mainShellNavState.topLevelKey }
            .drop(1)
            .distinctUntilChanged()
            .filter { it != NotificationsTab }
            .collect { viewModel.handleEvent(NotificationsEvent.TabExited) }
    }

    // Tab re-tap → scroll to top (design D11). Default empty SharedFlow in
    // previews / screenshot tests never emits, so this is a runtime no-op
    // outside MainShell.
    LaunchedEffect(tabReTapSignal, listState) {
        tabReTapSignal.collect { listState.animateScrollToItem(0) }
    }

    NotificationsContent(
        viewState = state.toViewState(),
        listState = listState,
        snackbarHostState = snackbarHostState,
        onEvent = onEvent,
        modifier = modifier,
    )

    val currentSheetActors = sheetActors
    if (currentSheetActors != null) {
        ActorListSheet(
            actors = currentSheetActors,
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
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onEvent: (NotificationsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeFilter =
        when (viewState) {
            is NotificationsScreenViewState.Loaded -> viewState.activeFilter
            // Empty / InitialLoading / InitialError preserve the user's
            // active filter on the chip strip — but those branches don't
            // carry a filter field (the state is folded away in the
            // projection). Default to All for those non-Loaded branches;
            // a filter change followed by an empty page will re-render the
            // chip row from the Loaded(...) branch as soon as it lands.
            else -> NotificationFilter.All
        }
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
            when (viewState) {
                NotificationsScreenViewState.InitialLoading ->
                    NotificationsInitialLoading()
                NotificationsScreenViewState.Empty ->
                    NotificationsEmptyState()
                is NotificationsScreenViewState.InitialError ->
                    NotificationsInitialError(
                        error = viewState.error,
                        onRetry = { onEvent(NotificationsEvent.Refresh) },
                    )
                is NotificationsScreenViewState.Loaded ->
                    LoadedNotifications(
                        items = viewState.items,
                        isRefreshing = viewState.isRefreshing,
                        isAppending = viewState.isAppending,
                        listState = listState,
                        onEvent = onEvent,
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedNotifications(
    items: ImmutableList<NotificationItemUi>,
    isRefreshing: Boolean,
    isAppending: Boolean,
    listState: LazyListState,
    onEvent: (NotificationsEvent) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onEvent(NotificationsEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
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
    }

    // Pagination trigger — emits once per crossing of the
    // (lastVisibleIndex > items.size - PREFETCH_DISTANCE) boolean threshold.
    // Same shape as :feature:feed:impl's LoadMore wiring: the boolean is
    // distinctUntilChanged'd so we don't re-fire every visible-index change
    // past the threshold (without that, scroll would fire 10–30 LoadMores/s).
    val currentItems by rememberUpdatedState(items)
    LaunchedEffect(listState, onEvent) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            lastVisible > currentItems.size - PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                if (pastThreshold) onEvent(NotificationsEvent.LoadMore)
            }
    }
}

@Composable
private fun NotificationsInitialLoading() {
    // Simple centered indicator — slice 1 stays cheap. A future change can
    // swap in a per-row shimmer matching the Feed's PostCardShimmer roster
    // once aggregation-aware shimmer fixtures exist.
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotificationsAppendingIndicator() {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Map a [NotificationsError] to its Snackbar-ready message. Lives as a
 * `Context` extension so the screen can resolve strings from the host
 * Activity Context without participating in the unstable lambda
 * recomposition. Mirrors `FeedError.toMessage` in :feature:feed:impl.
 *
 * Internal because the same mapping is exposed to test helpers that
 * exercise the Snackbar copy without standing up a full screen.
 */
internal fun NotificationsError.toMessage(context: Context): String =
    context.getString(
        when (this) {
            NotificationsError.Network -> R.string.notifications_snackbar_error_network
            NotificationsError.Unauthenticated -> R.string.notifications_snackbar_error_unauthenticated
            NotificationsError.Unknown -> R.string.notifications_snackbar_error_unknown
        },
    )
