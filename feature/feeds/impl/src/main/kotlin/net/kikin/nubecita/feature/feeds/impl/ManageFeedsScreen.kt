package net.kikin.nubecita.feature.feeds.impl

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pinned-feeds management screen — `@MainShell` sub-route for the `Feeds`
 * `NavKey`. Lists the user's pinned feeds in server order and lets them
 * drag-to-reorder and remove (unpin, non-destructive) feeds. The Following
 * timeline is reorderable but never removable. See
 * `docs/superpowers/specs/2026-07-05-manage-pinned-feeds-design.md`.
 */
@Composable
internal fun ManageFeedsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageFeedsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Wrapped so the long-lived effect below reads the current-locale string after a config change.
    val removeError by rememberUpdatedState(stringResource(R.string.feeds_manage_remove_error))

    LaunchedEffect(Unit) {
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                ManageFeedsEffect.ShowRemoveError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(removeError)
                    }
            }
        }
    }

    // Commit an in-progress reorder when the app backgrounds (ON_STOP), in
    // addition to onCleared() on a nav-pop. Both go through the VM's app-scoped
    // commit and are dirty-checked, so firing both is harmless.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.commitReorderIfDirty()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ManageFeedsContent(
        status = state.status,
        onEvent = viewModel::handleEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageFeedsContent(
    status: ManageFeedsLoadStatus,
    onEvent: (ManageFeedsEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.feeds_manage_back_content_desc),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (status) {
            ManageFeedsLoadStatus.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    NubecitaWavyProgressIndicator()
                }

            is ManageFeedsLoadStatus.Content ->
                PinnedFeedList(
                    feeds = status.feeds,
                    onEvent = onEvent,
                    contentPadding = innerPadding,
                )
        }
    }
}

@Composable
private fun PinnedFeedList(
    feeds: ImmutableList<PinnedFeedUi>,
    onEvent: (ManageFeedsEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    // rememberReorderableLazyListState captures its onMove lambda once; wrap onEvent so the
    // captured lambda always calls the current instance.
    val currentOnEvent by rememberUpdatedState(onEvent)
    val listState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            currentOnEvent(ManageFeedsEvent.Move(from.index, to.index))
        }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(feeds, key = { it.uri }) { feed ->
            ReorderableItem(reorderableState, key = feed.uri) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) DRAG_ELEVATION else 0.dp,
                    label = "manage-feeds-drag-elevation",
                )
                PinnedFeedRow(
                    feed = feed,
                    onRemove = { currentOnEvent(ManageFeedsEvent.Remove(feed.uri)) },
                    elevation = elevation,
                    dragHandle = {
                        // longPressDraggableHandle() is a ReorderableCollectionItemScope
                        // extension, so it must be resolved here inside the item lambda.
                        Box(
                            modifier = Modifier.longPressDraggableHandle().size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.Menu,
                                contentDescription = stringResource(R.string.feeds_manage_drag_handle, feed.displayName),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PinnedFeedRow(
    feed: PinnedFeedUi,
    onRemove: () -> Unit,
    elevation: Dp,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Drag handle — long-press to lift (the library drives the haptics).
            dragHandle()

            FeedLeadingIcon(feed)

            Spacer(Modifier.width(4.dp))

            Text(
                text = feed.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // Following can be reordered but never removed.
            if (feed.kind != FeedKind.Following) {
                IconButton(onClick = onRemove) {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.feeds_manage_remove, feed.displayName),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedLeadingIcon(feed: PinnedFeedUi) {
    when (feed.kind) {
        FeedKind.Following -> NubecitaIcon(name = NubecitaIconName.Home, contentDescription = null)
        FeedKind.Generator, FeedKind.List ->
            if (feed.avatarUrl != null) {
                NubecitaAvatar(model = feed.avatarUrl, contentDescription = null, size = 32.dp)
            } else {
                NubecitaIcon(name = NubecitaIconName.Public, contentDescription = null)
            }
    }
}

private val DRAG_ELEVATION = 6.dp
