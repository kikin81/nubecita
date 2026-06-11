package net.kikin.nubecita.feature.widgets.impl.ui

import net.kikin.nubecita.feature.widgets.impl.model.WidgetPostItem

/**
 * Hardcoded sample content for the widget picker preview (`providePreview`).
 * Static + text-only — the preview must not fetch the cache or network, and
 * avoiding media keeps it free of (undecoded) thumbnail placeholders. Purely a
 * "what this widget looks like" teaser, identical for Following and Discover.
 */
internal object WidgetPreviewSamples {
    fun loaded(): FeedWidgetUiState.Loaded = FeedWidgetUiState.Loaded(SAMPLE_ROWS)

    private val SAMPLE_ROWS =
        listOf(
            row("Alice Rivera", "alice.bsky.social", "Pinned the new feed widget — it refreshes in the background ☁️", "2m"),
            row("Bluesky", "bsky.app", "What's everyone building this weekend?", "18m"),
            row("Dev Diaries", "devdiaries.bsky.social", "TIL Jetpack Glance composables render to RemoteViews.", "1h"),
        )

    private fun row(
        name: String,
        handle: String,
        text: String,
        time: String,
    ): WidgetRow =
        WidgetRow(
            item =
                WidgetPostItem(
                    postUri = "",
                    authorDisplay = name,
                    handle = handle,
                    text = text,
                    relativeTime = time,
                    hasMedia = false,
                    extraImageCount = 0,
                    mediaContentDescription = null,
                ),
            thumbnail = null,
        )
}
