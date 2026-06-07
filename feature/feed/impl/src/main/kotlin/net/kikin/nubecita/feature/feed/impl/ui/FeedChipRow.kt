package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.shimmer
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.feed.impl.FeedHostStatus
import net.kikin.nubecita.feature.feed.impl.R

/**
 * Horizontally scrollable chip row switcher for the main Feed.
 * Displays pinned feeds (`Following` + `Generator`) as individual [FilterChip]s,
 * collapses pinned lists behind a single disclosure chip, and appends a
 * trailing `[＋]` button to manage feeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedChipRow(
    feedChips: ImmutableList<PinnedFeedUi>,
    pinnedLists: ImmutableList<PinnedFeedUi>,
    selectedFeedUri: String?,
    status: FeedHostStatus,
    onSelectFeed: (String) -> Unit,
    onSelectList: (String) -> Unit,
    onRetry: () -> Unit,
    onManageFeedsClick: () -> Unit,
    onOpenListsSheet: () -> Unit,
    modifier: Modifier = Modifier,
    // Hoisted by FeedHost (above its per-feed SaveableStateProvider) so the row's
    // scroll is a single global position, not saved/restored per feed — otherwise
    // a switch would jump to the new feed's saved offset before animating. Default
    // is a local state for previews/tests.
    chipListState: LazyListState = rememberLazyListState(),
) {
    if (status == FeedHostStatus.Loading) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(count = 3, key = { "shimmer-chip-$it" }) { index ->
                val width =
                    when (index) {
                        0 -> 80.dp
                        1 -> 100.dp
                        else -> 60.dp
                    }
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(32.dp)
                            .clip(MaterialTheme.shapes.small)
                            .shimmer(),
                )
            }
        }
    } else {
        val activeList =
            remember(selectedFeedUri, pinnedLists) {
                pinnedLists.firstOrNull { it.uri == selectedFeedUri }
            }
        val isListSelected = activeList != null
        val listsLabel =
            if (activeList != null) {
                stringResource(R.string.feed_lists_chip_selected, activeList.displayName)
            } else {
                stringResource(R.string.feed_lists_chip_collapsed)
            }

        // Animate the selected chip to the start on selection so the active chip
        // stays prominent near the beginning. [chipListState] is hoisted to
        // FeedHost so this scrolls from the row's persisted position (no per-feed
        // reset/flicker).
        val selectedChipIndex = selectedFeedChipIndex(feedChips, pinnedLists, selectedFeedUri)
        LaunchedEffect(selectedChipIndex) {
            if (selectedChipIndex >= 0) chipListState.animateScrollToItem(selectedChipIndex)
        }

        LazyRow(
            state = chipListState,
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(feedChips, key = { it.uri }) { feed ->
                val isSelected = feed.uri == selectedFeedUri
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFeed(feed.uri) },
                    label = { Text(feed.displayName) },
                    leadingIcon = {
                        when (feed.kind) {
                            FeedKind.Following -> {
                                NubecitaIcon(
                                    name = NubecitaIconName.Home,
                                    contentDescription = null,
                                )
                            }
                            FeedKind.Generator -> {
                                if (feed.avatarUrl != null) {
                                    NubecitaAvatar(
                                        model = feed.avatarUrl,
                                        contentDescription = null,
                                        size = 18.dp,
                                    )
                                } else {
                                    NubecitaIcon(
                                        name = NubecitaIconName.LocalFireDepartment,
                                        contentDescription = null,
                                    )
                                }
                            }
                            FeedKind.List -> null
                        }
                    },
                    modifier =
                        Modifier.semantics {
                            role = Role.Tab
                        },
                )
            }

            if (pinnedLists.isNotEmpty()) {
                item(key = "lists-disclosure-chip") {
                    val a11yDescription =
                        if (activeList != null) {
                            "Pinned lists, active: ${activeList.displayName}. Tap to select a different list."
                        } else {
                            "Pinned lists. Tap to expand list picker."
                        }
                    FilterChip(
                        selected = isListSelected,
                        onClick = onOpenListsSheet,
                        label = { Text(listsLabel) },
                        trailingIcon = {
                            NubecitaIcon(
                                name = NubecitaIconName.ExpandMore,
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.semantics {
                                role = Role.Button
                                contentDescription = a11yDescription
                            },
                    )
                }
            }

            if (status == FeedHostStatus.ErrorFallback) {
                item(key = "retry-chip") {
                    FilterChip(
                        selected = false,
                        onClick = onRetry,
                        label = { Text("Retry") },
                        leadingIcon = {
                            NubecitaIcon(
                                name = NubecitaIconName.Error,
                                contentDescription = null,
                                filled = true,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                    )
                }
            }

            item(key = "add-feeds-button") {
                IconButton(
                    onClick = onManageFeedsClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    NubecitaIcon(
                        name = NubecitaIconName.Add,
                        contentDescription = stringResource(R.string.feed_add_feeds_button_content_description),
                        filled = true,
                    )
                }
            }
        }
    }
}

/**
 * The [FeedChipRow] item index to scroll the selected feed/list to the start.
 *
 * - A selected feed chip maps to its position in [feedChips].
 * - A selected pinned **list** is represented by the single "lists" disclosure
 *   chip, which renders right after the feed chips → index [feedChips]`.size`.
 * - `-1` when nothing matches (no selection, or a uri not present) → no scroll.
 *
 * Extracted (and `internal`) so the index logic is unit-tested without a Compose
 * harness; the row's `LaunchedEffect` just consumes the result.
 */
internal fun selectedFeedChipIndex(
    feedChips: List<PinnedFeedUi>,
    pinnedLists: List<PinnedFeedUi>,
    selectedFeedUri: String?,
): Int {
    if (selectedFeedUri == null) return -1
    val feedIdx = feedChips.indexOfFirst { it.uri == selectedFeedUri }
    if (feedIdx >= 0) return feedIdx
    if (pinnedLists.any { it.uri == selectedFeedUri }) return feedChips.size
    return -1
}

/**
 * Bottom sheet picker for pinned lists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PinnedListsSheet(
    pinnedLists: ImmutableList<PinnedFeedUi>,
    selectedFeedUri: String?,
    onSelectList: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("pinned_lists_sheet"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.feed_lists_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            // Rows span the full sheet width (no horizontal contentPadding):
            // the header's 16dp content margin then aligns exactly with the
            // SegmentedListItem's own internal leading inset, so the title
            // sits flush with the row labels without reproducing M3's
            // (non-public) ListItemStartPadding as a magic number.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(pinnedLists, key = { _, list -> list.uri }) { index, list ->
                    val isSelected = list.uri == selectedFeedUri
                    val itemClick = {
                        scope
                            .launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    onDismiss()
                                    onSelectList(list.uri)
                                }
                            }
                    }
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { itemClick() },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = pinnedLists.size),
                        colors =
                            ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        trailingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = { itemClick() },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(list.displayName)
                    }
                }
            }
        }
    }
}
