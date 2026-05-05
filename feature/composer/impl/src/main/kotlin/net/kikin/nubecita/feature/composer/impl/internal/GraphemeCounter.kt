package net.kikin.nubecita.feature.composer.impl.internal

import java.text.BreakIterator

/**
 * Counts Unicode extended grapheme clusters in a string — what the
 * AT Protocol's 300-character post limit measures, and what the
 * composer's character counter renders.
 *
 * Why not `text.length`? UTF-16 code units. A single emoji like 🎉
 * is 2 UTF-16 code units; a ZWJ-joined family emoji like 👨‍👩‍👧‍👦
 * is 11 code units but 1 grapheme. `text.length` overcounts.
 *
 * Why not `text.codePointCount(0, text.length)`? Codepoints. Better
 * than UTF-16 but still wrong for ZWJ sequences (each codepoint in
 * a multi-codepoint cluster counts separately). Overcounts.
 *
 * Why not the atproto SDK's `RichText` helper? The atproto-kotlin
 * 5.3.0 SDK doesn't ship one. If it lands later, swap this for the
 * SDK helper to keep counter behavior consistent with the lexicon
 * `MAX_GRAPHEMES` enforcement at the server.
 *
 * Implementation: `java.text.BreakIterator.getCharacterInstance()`
 * is JVM stdlib, no extra dep, Unicode-aware. Android's `BreakIterator`
 * is ICU-backed on API 24+ (our minSdk) — same algorithm. Some
 * Unicode-version skew is possible between the JVM (Unicode 12-ish)
 * and Android device platforms (newer); for known new-emoji edge
 * cases, the count may be off by one. If we see counter-drift
 * complaints in production, swap to ICU4J or a pinned-Unicode lib.
 */
internal object GraphemeCounter {
    /**
     * Returns the number of extended grapheme clusters in [text].
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
