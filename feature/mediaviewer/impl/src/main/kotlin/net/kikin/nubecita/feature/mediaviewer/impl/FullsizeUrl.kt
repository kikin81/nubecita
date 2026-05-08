package net.kikin.nubecita.feature.mediaviewer.impl

import net.kikin.nubecita.data.models.ImageUi

/**
 * Bluesky CDN URL token used for the feed-thumbnail image variant.
 * The mapper in `:core:feed-mapping` projects each `ImagesView` item's
 * `thumb` field — which carries this token — into [ImageUi.url].
 */
private const val FEED_THUMBNAIL_TOKEN = "@feed_thumbnail"

/**
 * Bluesky CDN URL token used for the fullsize image variant. The
 * fullscreen viewer paints this size for maximum quality; the feed
 * embed paints `@feed_thumbnail` for bandwidth/decode efficiency.
 */
private const val FULLSIZE_TOKEN = "@fullsize"

/**
 * Returns the `@fullsize` CDN URL for this image, derived by swapping
 * the feed-thumbnail token in [ImageUi.url].
 *
 * Bluesky's blob CDN serves the same blob under multiple size tokens
 * (`…/<did>/<cid>@feed_thumbnail`, `…/<did>/<cid>@fullsize`, etc.).
 * The token swap is deterministic from the existing thumbnail URL,
 * so the [ImageUi] data model carries only the thumbnail variant; the
 * viewer applies this transform locally.
 *
 * **Defensive fall-through.** If the URL doesn't carry the
 * `@feed_thumbnail` token (a future Bluesky CDN URL shape, an empty
 * string, a non-Bluesky URL), this returns [ImageUi.url] unchanged.
 * The viewer renders at thumbnail quality rather than crashing — a
 * graceful degradation while a follow-up updates the helper.
 */
internal fun ImageUi.fullsizeUrl(): String =
    if (url.contains(FEED_THUMBNAIL_TOKEN)) {
        url.replace(FEED_THUMBNAIL_TOKEN, FULLSIZE_TOKEN)
    } else {
        url
    }
