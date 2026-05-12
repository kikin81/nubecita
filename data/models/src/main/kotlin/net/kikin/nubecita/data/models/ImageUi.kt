package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable

/**
 * One image inside a post's `app.bsky.embed.images` embed.
 *
 * Bluesky's appview serves two pre-rendered CDN variants per image, both
 * ending in the `@jpeg` suffix — the variant is encoded in the path
 * segment, not the suffix:
 * - [fullsizeUrl] — large variant served from `.../img/feed_fullsize/...`.
 *   Use for full-screen media viewers, in-feed PostCard image embeds, and
 *   anywhere the rendered surface can occupy a significant chunk of the
 *   screen.
 * - [thumbUrl] — small variant served from `.../img/feed_thumbnail/...`.
 *   Use for n×n grids (the Profile Media tab is a 3-col grid) and any
 *   surface that draws the image at avatar-or-smaller scale. Falls back to
 *   [fullsizeUrl] when null — see [thumbOrFullsize] for the canonical pick.
 *
 * The atproto lexicon (`app.bsky.embed.images#viewImage`) lists BOTH as
 * required, so [thumbUrl] is non-null when [ImageUi] is constructed from
 * `:core:feed-mapping`'s `ImagesView.toImageUiList()`. The field is typed
 * nullable to keep the door open for non-lexicon sources (hand-built
 * fixtures, future local-picker code paths) that may only have one URL.
 *
 * [altText] is null when the author didn't provide alt text. Accessibility
 * code reads this as the `contentDescription`; null is rendered as no
 * description (TalkBack will skip the image).
 *
 * [aspectRatio] is the post-time width/height ratio if the lexicon record
 * carries one (Bluesky added these in 2024); null falls back to a default
 * aspect for layout. Renderers may use it to reserve space before the image
 * loads, avoiding layout jank.
 */
@Stable
public data class ImageUi(
    val fullsizeUrl: String,
    val thumbUrl: String?,
    val altText: String?,
    val aspectRatio: Float?,
)

/**
 * Returns the thumbnail variant if present, otherwise the fullsize URL.
 * Canonical pick for n×n grid surfaces (Profile Media tab) that want the
 * small CDN variant but can't fail if the source data lacks one.
 */
public fun ImageUi.thumbOrFullsize(): String = thumbUrl ?: fullsizeUrl
