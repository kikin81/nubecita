package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUi
import net.kikin.nubecita.data.models.previewAspectRatio
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.feature.composer.impl.KlipyPickerViewModel
import net.kikin.nubecita.feature.composer.impl.R

internal const val KLIPY_SEARCH_FIELD_TEST_TAG = "klipy_search_field"

// Distinct content types so the staggered grid doesn't reuse an image-cell node
// as the full-width append-loading spinner (protects scroll perf).
private const val CONTENT_TYPE_MEDIA = "klipy_media"
private const val CONTENT_TYPE_APPEND = "klipy_append"

/**
 * Stateless KLIPY picker content — the "Search KLIPY" field, GIF/Sticker tabs,
 * a category chip row, the paged staggered grid, and the required "Powered by
 * KLIPY" mark. Rendered inside both the [KlipyPicker] `ModalBottomSheet` and
 * `Popup` branches (inlined into both to match [AudiencePicker]/[LanguagePicker]).
 *
 * The grid is a [LazyPagingItems] the caller collects; this composable renders
 * its refresh/append load states (brand progress, retry, empty). A tap selects
 * an item; a long-press previews it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KlipyPickerContent(
    query: String,
    selectedTab: KlipyMediaType,
    categories: ImmutableList<String>,
    selectedCategory: String?,
    items: LazyPagingItems<KlipyMediaUi>,
    onQueryChange: (String) -> Unit,
    onSelectTab: (KlipyMediaType) -> Unit,
    onSelectCategory: (String) -> Unit,
    onItemClick: (KlipyMediaUi) -> Unit,
    onItemLongPress: (KlipyMediaUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            // A real label (not just a placeholder) so screen readers get a stable,
            // localized description — and it satisfies the "Search KLIPY" branding.
            label = { Text(stringResource(R.string.klipy_search_placeholder)) },
            modifier = Modifier.fillMaxWidth().testTag(KLIPY_SEARCH_FIELD_TEST_TAG),
        )

        Spacer(Modifier.height(8.dp))

        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            KlipyMediaType.entries.forEach { type ->
                Tab(
                    selected = type == selectedTab,
                    onClick = { onSelectTab(type) },
                    text = { Text(stringResource(type.tabLabelRes())) },
                )
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(categories, key = { it }) { category ->
                    FilterChip(
                        selected = category == selectedCategory,
                        onClick = { onSelectCategory(category) },
                        label = { Text(categoryLabel(category)) },
                    )
                }
            }
        }

        KlipyGrid(
            items = items,
            onItemClick = onItemClick,
            onItemLongPress = onItemLongPress,
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )

        Text(
            text = stringResource(R.string.klipy_powered_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KlipyGrid(
    items: LazyPagingItems<KlipyMediaUi>,
    onItemClick: (KlipyMediaUi) -> Unit,
    onItemLongPress: (KlipyMediaUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (items.loadState.refresh) {
            is LoadState.Loading -> NubecitaWavyProgressIndicator()
            is LoadState.Error ->
                TextButton(onClick = { items.retry() }) {
                    Text(stringResource(R.string.klipy_load_error_retry))
                }
            is LoadState.NotLoading ->
                if (items.itemCount == 0) {
                    Text(
                        text = stringResource(R.string.klipy_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    KlipyStaggeredGrid(items, onItemClick, onItemLongPress)
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KlipyStaggeredGrid(
    items: LazyPagingItems<KlipyMediaUi>,
    onItemClick: (KlipyMediaUi) -> Unit,
    onItemLongPress: (KlipyMediaUi) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.slug },
            contentType = items.itemContentType { CONTENT_TYPE_MEDIA },
        ) { index ->
            val item = items[index] ?: return@items
            NubecitaAsyncImage(
                model = item.previewUrl,
                contentDescription = item.title ?: stringResource(R.string.klipy_gif_description),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(item.previewAspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongPress(item) },
                        ),
            )
        }
        if (items.loadState.append is LoadState.Loading) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = CONTENT_TYPE_APPEND) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    NubecitaWavyProgressIndicator()
                }
            }
        }
    }
}

private fun KlipyMediaType.tabLabelRes(): Int =
    when (this) {
        KlipyMediaType.GIF -> R.string.klipy_tab_gifs
        KlipyMediaType.STICKER -> R.string.klipy_tab_stickers
    }

/**
 * The two leading categories arrive from the repository as English ids
 * ("Recents"/"Trending") that also key selection; localize only their display
 * here, leaving server categories as-is.
 */
@Composable
private fun categoryLabel(category: String): String =
    when (category) {
        KlipyPickerViewModel.RECENTS -> stringResource(R.string.klipy_category_recents)
        KlipyPickerViewModel.TRENDING -> stringResource(R.string.klipy_category_trending)
        else -> category
    }
