package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * One image inside a post's `app.bsky.embed.images` embed.
 *
 * `url` is the resolved CDN URL (e.g., `https://cdn.bsky.app/img/...`) — the
 * mapper turns the lexicon's `Blob` reference into the appropriate CDN URL
 * shape. UI consumers pass `url` straight to Coil via `NubecitaAsyncImage`.
 *
 * `altText` is null when the author didn't provide alt text. Accessibility
 * code reads this as the `contentDescription`; null is rendered as no
 * description (TalkBack will skip the image).
 *
 * `aspectRatio` is the post-time width/height ratio if the lexicon record
 * carries one (Bluesky added these in 2024); null falls back to a default
 * aspect for layout. Renderers may use it to reserve space before the image
 * loads, avoiding layout jank.
 */
@Stable
public data class ImageUi(
    val url: String,
    val altText: String?,
    val aspectRatio: Float?,
)
