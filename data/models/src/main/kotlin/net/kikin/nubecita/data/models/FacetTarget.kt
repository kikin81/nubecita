package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * A tappable rich-text span in a post body — the destination of a facet tap.
 *
 * Posts render Bluesky rich-text facets (mentions, links, tags) as styled spans;
 * this is the UI-layer projection of *what a tap on one should do*, so the
 * shared post card can expose a single `onFacetTap` callback and each host maps
 * the target to its own navigation. Modeling the target (not "a handle") keeps
 * the interaction generic: a new facet kind is a new variant here plus one
 * `when` branch per host, with no change to the tap plumbing.
 *
 * Only the two kinds wired today are represented. `#tag` facets are still styled
 * but not yet tappable (tag search needs a query-carrying Search route); add a
 * `Tag(val tag: String)` variant when that lands.
 */
@Immutable
public sealed interface FacetTarget {
    /**
     * A `@mention` — [did] is the mentioned account's DID (not the post author).
     * Hosts open that account's profile. `Profile` resolves a DID or a handle
     * interchangeably, so the raw DID routes directly.
     */
    @Immutable
    public data class Mention(
        val did: String,
    ) : FacetTarget

    /** An inline link — [uri] opens in the app's in-app browser (Custom Tab). */
    @Immutable
    public data class Link(
        val uri: String,
    ) : FacetTarget
}
