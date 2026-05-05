package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetFeaturesUnion
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses composer text into the `app.bsky.richtext.facet` annotations
 * that turn `@handle` tokens into linked mentions and `https://…`
 * tokens into clickable links on Bluesky's appview. Without this,
 * those tokens render as inert plain text — no link, no DID
 * reference — and the post looks broken to users coming from the
 * official client.
 *
 * Lives in `:core:posting` rather than the composer VM so the parsing
 * stays a pure-Kotlin transformation testable without Compose / Hilt
 * scaffolding, and so every future write path that produces a `Post`
 * record (replies, quote posts) reuses the same facet extraction
 * without re-implementing it.
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
 * table. The extra pass is `O(n)` over the message length and runs
 * once per submit; the overhead is invisible against the network round
 * trip that follows.
 */
internal interface FacetExtractor {
    /**
     * Extract facets from [text]. Returns an empty list when the text
     * has no mentions or URLs (the caller should pass `AtField.Missing`
     * to the record's `facets` field rather than `AtField.Defined(emptyList())`
     * — both are wire-equivalent but the lexicon convention is the
     * former).
     */
    suspend fun extract(text: String): ImmutableList<Facet>
}

@Singleton
internal class DefaultFacetExtractor
    @Inject
    constructor(
        private val handleResolver: HandleResolver,
    ) : FacetExtractor {
        override suspend fun extract(text: String): ImmutableList<Facet> {
            if (text.isEmpty()) return persistentListOf()

            // Build the UTF-16 char index → UTF-8 byte offset prefix-
            // sum table once per call. `byteOffsets[i]` returns the
            // UTF-8 byte offset BEFORE the UTF-16 char at index `i`,
            // and `byteOffsets[text.length]` returns the total UTF-8
            // byte length of the message.
            val byteOffsets = utf8ByteOffsetTable(text)

            // Resolve mentions and collect URLs in document order. The
            // mention loop runs handle resolutions sequentially —
            // typically 0–2 per post, so parallelizing isn't worth the
            // structured-concurrency complexity at this volume. URLs
            // need no resolution.
            //
            // Plain `for` (rather than `Sequence.mapNotNull`) because
            // `handleResolver.resolve` is a `suspend` function and
            // sequence-style higher-order calls don't carry the
            // coroutine context.
            val collected = mutableListOf<Facet>()
            for (match in MENTION_REGEX.findAll(text)) {
                val handleGroup = match.groups[MENTION_HANDLE_GROUP] ?: continue
                // Strip the leading '@' for resolveHandle; the byte
                // range covers the '@' too so the appview can render
                // the entire token (including '@') as a link.
                val handle = handleGroup.value.removePrefix("@")
                val did = handleResolver.resolve(handle) ?: continue
                collected.add(
                    Facet(
                        index =
                            FacetByteSlice(
                                byteStart = byteOffsets[handleGroup.range.first].toLong(),
                                byteEnd = byteOffsets[handleGroup.range.last + 1].toLong(),
                            ),
                        features = listOf<FacetFeaturesUnion>(FacetMention(did = did)),
                    ),
                )
            }
            for (match in LINK_REGEX.findAll(text)) {
                val urlGroup = match.groups[LINK_URL_GROUP] ?: continue
                collected.add(
                    Facet(
                        index =
                            FacetByteSlice(
                                byteStart = byteOffsets[urlGroup.range.first].toLong(),
                                byteEnd = byteOffsets[urlGroup.range.last + 1].toLong(),
                            ),
                        features = listOf<FacetFeaturesUnion>(FacetLink(uri = Uri(urlGroup.value))),
                    ),
                )
            }

            // Sort by byteStart so the wire output reflects document
            // order. Not strictly required by the lexicon but matches
            // what every other client produces and keeps the record
            // diff-friendly for future inspection.
            return collected
                .sortedBy { it.index.byteStart }
                .toImmutableList()
        }

        private companion object {
            /**
             * Mention regex. Anchored to "start of string OR a non-word,
             * non-`@` boundary char" — keeps email addresses (`x@y.com`
             * preceded by a word char) from false-positive matching as
             * mentions. The bsky docs reference a `[$|\W]` character
             * class which is a Python idiom; the Kotlin equivalent is
             * `(?:^|[^\w@])` (non-capturing alternation). Group 1
             * captures the `@handle` literal including the `@`.
             *
             * Handle syntax follows
             * https://atproto.com/specs/handle#handle-identifier-syntax
             * — DNS-style segments separated by `.`, last segment
             * (TLD) is at least one alphabetical char.
             */
            private val MENTION_REGEX =
                Regex(
                    """(?:^|[^\w@])(@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)""",
                )
            private const val MENTION_HANDLE_GROUP = 1

            /**
             * URL regex. Same boundary-anchored shape as MENTION_REGEX.
             * Group 1 captures the URL itself (without the leading
             * boundary char). Mirrors the docs' recommended pattern,
             * tightened against trailing punctuation that the original
             * recommendation lets in.
             */
            private val LINK_REGEX =
                Regex(
                    """(?:^|[^\w@])(https?://(?:www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_+.~#?&/=]*[-a-zA-Z0-9@%_+~#/=])?)""",
                )
            private const val LINK_URL_GROUP = 1
        }
    }

/**
 * Build the UTF-16 char index → UTF-8 byte offset prefix-sum table for
 * [text]. The returned array has size `text.length + 1`:
 *  - `result[i]` (for `i < text.length`) is the UTF-8 byte offset of the
 *    UTF-16 char at position `i`. For surrogate pairs, the high and low
 *    surrogates share the same offset (the codepoint hasn't been emitted
 *    until BOTH surrogates have been seen).
 *  - `result[text.length]` is the total UTF-8 byte length of [text].
 *
 * Non-ASCII codepoints: 2 bytes for U+0080-U+07FF, 3 bytes for
 * U+0800-U+FFFF (BMP), 4 bytes for U+10000+ (surrogate pairs).
 */
private fun utf8ByteOffsetTable(text: String): IntArray {
    val table = IntArray(text.length + 1)
    var byteOffset = 0
    var i = 0
    while (i < text.length) {
        table[i] = byteOffset
        val cp = text.codePointAt(i)
        val charsInCp = Character.charCount(cp)
        if (charsInCp == 2) {
            // Low surrogate carries the same byte offset — the
            // codepoint hasn't been "emitted" until we move past the
            // surrogate pair.
            table[i + 1] = byteOffset
        }
        byteOffset += utf8ByteLengthOf(cp)
        i += charsInCp
    }
    table[text.length] = byteOffset
    return table
}

private fun utf8ByteLengthOf(codepoint: Int): Int =
    when {
        codepoint < 0x80 -> 1
        codepoint < 0x800 -> 2
        codepoint < 0x10000 -> 3
        else -> 4
    }
