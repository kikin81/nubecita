package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.posting.LinkPreview

/**
 * Mutually-exclusive lifecycle for the composer's external link-preview card.
 *
 * Starts [Idle] (no card). When the user types/pastes the first eligible URL the
 * field transitions to [Loading] while the CardyB fetch is in flight, then to
 * [Loaded] on a usable preview. A failed/empty fetch returns to [Idle] silently
 * (no error surfaced) — there is no `Failed` state because it would never render.
 *
 * The card is mutually exclusive with image attachments (images win the media
 * slot): while images are present no card is fetched, and adding images clears a
 * loaded card. A card may coexist with a quoted post (emitted as
 * `recordWithMedia` at post time).
 *
 * Mirrors [QuoteLoadStatus] — a per-screen sealed status sum, not a generic
 * remote-data wrapper.
 */
sealed interface ExternalLinkStatus {
    /** No card. */
    data object Idle : ExternalLinkStatus

    /**
     * A preview fetch for [url] is in flight. The card renders a spinner. [url] is
     * the detected (typed) URL; the resolved URL arrives with [Loaded].
     */
    data class Loading(
        val url: String,
    ) : ExternalLinkStatus

    /** Preview resolved; the card renders title/description/thumbnail/domain. */
    data class Loaded(
        val preview: LinkPreview,
    ) : ExternalLinkStatus
}
