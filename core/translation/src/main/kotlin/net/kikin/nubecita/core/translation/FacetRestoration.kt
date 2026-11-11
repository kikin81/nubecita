package net.kikin.nubecita.core.translation

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.app.bsky.richtext.FacetTag
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Re-attaches a post's links, mentions and hashtags to its translated body.
 *
 * ## Why this is not just offset arithmetic
 *
 * Facet offsets index the *original* bytes, and translation does not preserve
 * them — not even approximately, and not in a way that is consistent across
 * languages. Measured against ML Kit:
 *
 * ```
 * #PalacesandGardens        ->  #palaceandGarden     the tag TEXT changed
 * @alice.bsky.social        ->  @ alice.bsky.social  space inserted (de and ja)
 * https://example.com/a/b   ->  https: //example…    broken (ja)
 * #おはよう                   ->  # Good morning       translated (ja)
 * ```
 *
 * Emoji survive intact. ML Kit would collapse newlines to spaces too, but the
 * engine translates line by line, so they survive as well.
 *
 * The obvious escape hatch — swap each faceted span for a placeholder token,
 * translate, put the originals back — was probed and **fails**. `⟦0⟧` and
 * `{0}` survive German untouched but are destroyed in Japanese (`⟦0⟧` came
 * back as `Ogaki 0`). There is no token that is reliably opaque across
 * languages, so the strategy cannot rest on one.
 *
 * ## What this does instead
 *
 * Per facet kind, independently:
 *
 * 1. find candidate spans in the translated text, tolerating the artifacts
 *    above (`@ `, `# `, `https: //`);
 * 2. if the candidate count equals the original count **for that kind**,
 *    replace each candidate's text with the **original** span's text and
 *    attach the **original** feature, in order;
 * 3. if the counts disagree, that kind renders as plain text for this post.
 *
 * So the reader sees `#PalacesandGardens`, not `#palaceandGarden`, and tapping
 * it goes where the author pointed. A post that cannot be reconciled loses its
 * links rather than acquiring wrong ones — which is the whole point: a link
 * that goes somewhere the author did not write is worse than no link.
 *
 * Original byte offsets are never remapped onto translated text.
 */
public fun restoreFacets(
    originalText: String,
    translatedText: String,
    originalFacets: List<Facet>,
): RestoredTranslation {
    if (originalFacets.isEmpty()) {
        return RestoredTranslation(translatedText, persistentListOf())
    }

    val originals =
        originalFacets
            .mapNotNull { facet -> facet.toOriginalSpan(originalText) }
            .sortedBy { it.byteStart }
    if (originals.isEmpty()) {
        return RestoredTranslation(translatedText, persistentListOf())
    }

    val replacements =
        originals
            .groupBy { it.kind }
            .flatMap { (kind, spans) -> kind.matchCandidates(translatedText, spans) }
            .sortedBy { it.range.first }
            .dropOverlapping()

    if (replacements.isEmpty()) {
        return RestoredTranslation(translatedText, persistentListOf())
    }
    return spliceAndIndex(translatedText, replacements)
}

/**
 * The faceted kinds, with the pattern that finds them in translated output.
 *
 * Each pattern allows one optional space after the sigil, because that is what
 * translation actually produces (`@ alice`, `# tag`). Allowing it is what makes
 * the count match at all in Japanese; without it, a post's mentions would all
 * silently drop to plain text.
 */
private enum class FacetKind(
    val pattern: Regex,
) {
    /**
     * `https: //host/path` is the broken shape Japanese produces, so the
     * optional space has to sit between the scheme and the authority too.
     */
    Link(Regex("""https?:\s?//\S+""")),

    Mention(Regex("""@\s?[A-Za-z0-9][A-Za-z0-9.\-]*""")),

    /**
     * A tag candidate stops at whitespace.
     *
     * In Japanese the engine both translates the tag and inserts a space, so
     * `#おはよう` becomes `# Good morning` and only `# Good` is consumed — the
     * restored body reads `…#おはよう morning`, with a stray word. That is
     * deliberately preferred to consuming to end-of-line, which would swallow
     * real sentence text into a hashtag. The tap target is correct either way,
     * which is the property that matters.
     */
    Tag(Regex("""#\s?[^\s#]+""")),
    ;

    fun matchCandidates(
        translated: String,
        spans: List<OriginalSpan>,
    ): List<Replacement> {
        val candidates = pattern.findAll(translated).toList()
        // The count gate. Unequal counts mean we cannot say which candidate
        // corresponds to which original, and a guess here is a link pointing
        // somewhere the author did not write.
        if (candidates.size != spans.size) return emptyList()
        return candidates.zip(spans).map { (match, span) ->
            Replacement(range = match.range, text = span.text, feature = span.facet)
        }
    }
}

private data class OriginalSpan(
    val kind: FacetKind,
    val text: String,
    val byteStart: Long,
    val facet: Facet,
)

private data class Replacement(
    val range: IntRange,
    val text: String,
    val feature: Facet,
)

/**
 * A facet's own slice of the original text, or `null` when it cannot be read.
 *
 * A facet whose offsets do not land on a character boundary, or point outside
 * the string, is dropped rather than repaired: it was already not rendering
 * correctly before translation, and inventing a span for it would put a tap
 * target on text the author never marked.
 */
private fun Facet.toOriginalSpan(originalText: String): OriginalSpan? {
    val kind =
        when (features.firstOrNull()) {
            is FacetLink -> FacetKind.Link
            is FacetMention -> FacetKind.Mention
            is FacetTag -> FacetKind.Tag
            else -> null
        } ?: return null
    val text = originalText.sliceByUtf8(index.byteStart, index.byteEnd) ?: return null
    if (text.isEmpty()) return null
    return OriginalSpan(kind = kind, text = text, byteStart = index.byteStart, facet = this)
}

/**
 * The substring of this string spanning UTF-8 bytes `[start, end)`, or `null`
 * when the range is out of bounds or splits a character.
 */
private fun String.sliceByUtf8(
    start: Long,
    end: Long,
): String? {
    if (start < 0 || end < start) return null
    val bytes = toByteArray(Charsets.UTF_8)
    if (end > bytes.size) return null
    val slice = bytes.copyOfRange(start.toInt(), end.toInt())
    val decoded = slice.toString(Charsets.UTF_8)
    // A range that splits a multi-byte character decodes to U+FFFD. Round-trip
    // to detect it rather than scan for the replacement character, which could
    // legitimately appear in a post.
    return if (decoded.toByteArray(Charsets.UTF_8).contentEquals(slice)) decoded else null
}

/**
 * Drop any replacement that overlaps one already kept.
 *
 * Kinds are matched independently, so two patterns can claim the same
 * characters — a `#tag` sitting inside what the link pattern read as a URL,
 * say. Splicing both would corrupt the string, so the earlier one wins and the
 * other reverts to plain text.
 */
private fun List<Replacement>.dropOverlapping(): List<Replacement> {
    val kept = mutableListOf<Replacement>()
    var consumedThrough = -1
    for (replacement in this) {
        if (replacement.range.first <= consumedThrough) continue
        kept += replacement
        consumedThrough = replacement.range.last
    }
    return kept
}

/**
 * Build the final body and the facet list in one pass.
 *
 * Offsets are computed on the string being built, so they describe the text
 * the reader actually sees — the original offsets are never carried across.
 */
private fun spliceAndIndex(
    translated: String,
    replacements: List<Replacement>,
): RestoredTranslation {
    val builder = StringBuilder()
    val facets = mutableListOf<Facet>()
    var cursor = 0
    var byteCursor = 0L

    for (replacement in replacements) {
        val before = translated.substring(cursor, replacement.range.first)
        builder.append(before)
        byteCursor += before.utf8Size()

        builder.append(replacement.text)
        val byteEnd = byteCursor + replacement.text.utf8Size()
        facets +=
            Facet(
                features = replacement.feature.features,
                index = FacetByteSlice(byteStart = byteCursor, byteEnd = byteEnd),
            )
        byteCursor = byteEnd
        cursor = replacement.range.last + 1
    }
    builder.append(translated.substring(cursor))

    return RestoredTranslation(text = builder.toString(), facets = facets.toImmutableList())
}

private fun String.utf8Size(): Long = toByteArray(Charsets.UTF_8).size.toLong()
