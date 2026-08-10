package net.kikin.nubecita.core.posting

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList

/**
 * Parses user-authored text into the `app.bsky.richtext.facet`
 * annotations that turn `@handle` tokens into linked mentions and
 * `https://…` tokens into clickable links. Without this, those tokens
 * render as inert plain text — no link, no DID reference — and the
 * message or post looks broken to users coming from the official
 * client.
 *
 * Lives in `:core:posting` rather than a feature module so the parsing
 * stays a pure-Kotlin transformation testable without Compose / Hilt
 * scaffolding, and so every write path that produces faceted text
 * reuses the same extraction. Two call sites today: the composer's
 * `Post` records, and `chat.bsky.convo.sendMessage` — DMs carry
 * `facets` on the wire exactly like posts do, and a message sent
 * without them is un-tappable in **every** client, not just ours
 * (nubecita-io24.1).
 *
 * The Facet record's `index.byteStart` / `byteEnd` are **UTF-8 byte
 * offsets**, not character or codepoint offsets. The AT Protocol
 * `app.bsky.richtext.facet#byteSlice` lexicon explicitly notes:
 *
 * > Indices are zero-indexed, counting bytes of the UTF-8 encoded
 * > text. NOTE: some languages, like Javascript, use UTF-16 or
 * > Unicode codepoints for string slice indexing; in these languages,
 * > convert to byte arrays before working with facets.
 *
 * Kotlin's `Regex` matches over `String`, which is UTF-16 internally,
 * so the implementation walks each match's UTF-16 char range and
 * computes the corresponding byte offsets via a precomputed prefix-sum
 * table. The extra pass is `O(n)` over the text length and runs once
 * per submit; the overhead is invisible against the network round trip
 * that follows.
 *
 * The interface is public (the implementation stays `internal` to this
 * module) so downstream features can inject it — mirroring
 * [LocaleProvider] / [SharedMediaStore] / [PostingRepository].
 */
interface FacetExtractor {
    /**
     * Extract facets from [text]. Returns an empty list when the text
     * has no mentions or URLs (the caller should pass `AtField.Missing`
     * to the record's `facets` field rather than `AtField.Defined(emptyList())`
     * — both are wire-equivalent but the lexicon convention is the
     * former).
     *
     * `suspend` because resolving a `@handle` to its DID is a network
     * call. Text with no mentions completes without touching the
     * network.
     */
    suspend fun extract(text: String): ImmutableList<Facet>
}
