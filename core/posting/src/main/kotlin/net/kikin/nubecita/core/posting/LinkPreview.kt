package net.kikin.nubecita.core.posting

/**
 * Link-preview metadata for an external URL, fetched at compose time to render a
 * link card and (later) build an `app.bsky.embed.external` record.
 *
 * @property uri the canonical URL the card points at — the redirect-resolved
 *   *final* destination returned by the preview service, NOT necessarily the
 *   string the user typed. The in-text link facet still uses what the user typed;
 *   only the card's `uri` is resolved (so a pasted `bit.ly`/`t.co` posts a card
 *   pointing at the real page).
 * @property title required by the embed record; may be empty but never null. A
 *   preview with no usable title is dropped (no card), so this is non-blank in
 *   practice when it reaches the card.
 * @property description required by the embed record; empty when the page has none
 *   (the card simply omits the description row).
 * @property imageUrl the preview thumbnail URL (the service's image proxy), or
 *   `null` when the page has no image. Loaded directly for the compose-time
 *   preview; downloaded + uploaded as the embed `thumb` blob at post time.
 */
data class LinkPreview(
    val uri: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
)
