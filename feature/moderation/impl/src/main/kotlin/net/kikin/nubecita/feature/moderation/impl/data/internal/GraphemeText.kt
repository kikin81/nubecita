package net.kikin.nubecita.feature.moderation.impl.data.internal

import java.text.BreakIterator

/**
 * Grapheme-cluster-aware string truncation.
 *
 * The lexicon's `com.atproto.moderation.createReport` caps `reason` at
 * 2000 graphemes — not UTF-16 code units, not codepoints. A naive
 * `text.take(n)` would split inside a ZWJ-joined family emoji or
 * truncate a flag-pair regional indicator on the wrong half, leaving
 * the wire payload with a lone surrogate that the PDS will reject as
 * malformed UTF-8.
 *
 * Implementation mirrors
 * [feature.composer.impl.internal.GraphemeCounter]: JVM stdlib
 * `BreakIterator.getCharacterInstance()` is Unicode-aware (ICU-backed
 * on Android API 24+). The two helpers are sibling consumers of the
 * same BreakIterator primitive — if a single home for grapheme work
 * emerges in `:core:common`, both collapse into it.
 */
internal object GraphemeText {
    /**
     * Returns [text] truncated to at most [max] extended grapheme
     * clusters. If [text] is shorter than [max] graphemes, returns
     * the original string unchanged. [max] of 0 returns an empty
     * string.
     */
    fun truncate(
        text: String,
        max: Int,
    ): String {
        if (text.isEmpty() || max <= 0) return if (max <= 0) "" else text
        val iter = BreakIterator.getCharacterInstance()
        iter.setText(text)
        var boundary = 0
        var count = 0
        while (count < max) {
            val next = iter.next()
            if (next == BreakIterator.DONE) return text
            boundary = next
            count++
        }
        return text.substring(0, boundary)
    }

    /**
     * Counts extended grapheme clusters in [text]. Symmetric with
     * [truncate]: `count(truncate(s, max)) <= max` always holds. Used by
     * the Details step's `n/300` counter and by the [canSubmit] gate to
     * reject empty-but-required free-text input.
     */
    fun count(text: String): Int {
        if (text.isEmpty()) return 0
        val iter = BreakIterator.getCharacterInstance()
        iter.setText(text)
        var count = 0
        while (iter.next() != BreakIterator.DONE) {
            count++
        }
        return count
    }
}
