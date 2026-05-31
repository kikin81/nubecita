package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * The kind of feed a [PinnedFeedUi] chip switches the main Feed to.
 *
 * Mirrors the `SavedFeed.type` axis the saved-feeds preference exposes:
 * `"timeline"` → [Following], `"feed"` → [Generator], `"list"` → [List].
 * The mapper in `:core:feeds` performs that split; the UI only sees this
 * enum and branches on it (Following renders a local glyph, the others
 * render a remote avatar).
 */
public enum class FeedKind {
    Following,
    Generator,
    List,
}

/**
 * Display-ready snapshot of one pinned feed, rendered as a filter chip in
 * the main Feed's feed-switcher row.
 *
 * Produced by `PinnedFeedsRepository` in `:core:feeds` from the user's
 * `SavedFeedsPrefV2` (hydrating generator metadata via `getFeedGenerators`);
 * consumed by the feed-switcher chip row in `:feature:feed:impl`.
 *
 * `avatarUrl` is null for [FeedKind.Following] entries — those render the
 * local `Home` glyph rather than a remote image, so the chip is usable
 * offline and on first launch. Generator and list entries carry their
 * remote avatar URL (null only when the source has none).
 */
@Stable
public data class PinnedFeedUi(
    val id: String,
    val uri: String,
    val kind: FeedKind,
    val displayName: String,
    val avatarUrl: String?,
)
