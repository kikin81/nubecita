package net.kikin.nubecita.feature.composer.impl

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUi
import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.KlipyPickerContent

/**
 * Baselines for the KLIPY picker's stateless content across all width classes.
 * The adaptive wrapper [net.kikin.nubecita.feature.composer.impl.internal.KlipyPicker]
 * is NOT rendered directly (Layoutlib doesn't render `ModalBottomSheet` / `Popup`
 * reliably — same reason as the language/audience picker tests); the same
 * `KlipyPickerContent` is rendered inside both wrappers in production. Grid cells
 * render the placeholder `ColorPainter` (no network in the screenshot host), which
 * is deterministic.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun KlipyPickerContentGridPreviews() {
    NubecitaCanvasPreviewTheme {
        val items = flowOf(PagingData.from(KlipyMediaUiFixtures.page().items)).collectAsLazyPagingItems()
        KlipyPickerContent(
            query = "",
            selectedTab = KlipyMediaType.GIF,
            categories = persistentListOf("Recents", "Trending", "Love", "Happy"),
            selectedCategory = "Trending",
            items = items,
            onQueryChange = {},
            onSelectTab = {},
            onSelectCategory = {},
            onItemClick = {},
            onItemLongPress = {},
        )
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun KlipyPickerContentEmptyPreviews() {
    NubecitaCanvasPreviewTheme {
        val items = flowOf(PagingData.from(emptyList<KlipyMediaUi>())).collectAsLazyPagingItems()
        KlipyPickerContent(
            query = "unmatchable",
            selectedTab = KlipyMediaType.STICKER,
            categories = persistentListOf("Recents", "Trending"),
            selectedCategory = null,
            items = items,
            onQueryChange = {},
            onSelectTab = {},
            onSelectCategory = {},
            onItemClick = {},
            onItemLongPress = {},
        )
    }
}
