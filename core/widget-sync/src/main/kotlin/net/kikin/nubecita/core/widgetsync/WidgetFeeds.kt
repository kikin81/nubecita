package net.kikin.nubecita.core.widgetsync

/**
 * Shared widget feed constants. The fixed Discover ("what's-hot") generator
 * AT-URI is needed by both the refresh worker ([net.kikin.nubecita.core.widgetsync.worker.WidgetRefreshRunner],
 * which refreshes the partition) and the widgets sub-project (which renders it),
 * so it lives here rather than being duplicated.
 */
object WidgetFeeds {
    /** The Bluesky Discover / "what's-hot" feed-generator AT-URI (MVP fixed Discover). */
    const val DISCOVER_FEED_URI = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"
}
