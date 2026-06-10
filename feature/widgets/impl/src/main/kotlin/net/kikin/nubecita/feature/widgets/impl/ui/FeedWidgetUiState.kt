package net.kikin.nubecita.feature.widgets.impl.ui

import android.graphics.Bitmap
import net.kikin.nubecita.feature.widgets.impl.model.WidgetPostItem

/**
 * What a feed widget renders right now (D-C8). A widget is update-driven
 * `RemoteViews`, not a live observer, so the widget class resolves this once per
 * `provideGlance` from the cache + thumbnail store and hands it to
 * [FeedWidgetContent].
 *
 * The widget NEVER renders blank: [Loading] before the first cache read,
 * [SignedOut] with no session, and [Loaded] with an empty row list drives the
 * "no posts yet" empty state.
 */
internal sealed interface FeedWidgetUiState {
    data object Loading : FeedWidgetUiState

    data object SignedOut : FeedWidgetUiState

    data class Loaded(
        val rows: List<WidgetRow>,
    ) : FeedWidgetUiState
}

/**
 * A post row plus its pre-decoded [thumbnail] (null = text-only or not yet
 * prefetched). The bitmap is loaded off the composition in `provideGlance`;
 * Glance can't decode a URL at render time.
 */
internal data class WidgetRow(
    val item: WidgetPostItem,
    val thumbnail: Bitmap?,
)
