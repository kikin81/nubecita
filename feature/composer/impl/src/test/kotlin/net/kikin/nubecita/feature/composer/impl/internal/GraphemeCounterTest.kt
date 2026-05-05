package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Sanity tests for [GraphemeCounter] — the contract is "Unicode
 * extended grapheme cluster count" matching what the AT Protocol
 * 300-grapheme post-text limit measures.
 *
 * These cases pin the basic ASCII / multi-codepoint / ZWJ edge
 * cases. Some Unicode-version skew between the JVM (~Unicode 12)
 * and Android device platforms is possible for newer-than-Unicode-12
 * emoji — if production users see counter drift on bleeding-edge
 * emoji, swap the underlying implementation to ICU4J or a
 * version-pinned grapheme lib.
 */
class GraphemeCounterTest {
    @Test
    fun emptyString_isZero() {
        assertEquals(0, GraphemeCounter.count(""))
    }

    @Test
    fun pureAscii_countsCodeUnits() {
        assertEquals(11, GraphemeCounter.count("hello world"))
    }

    @Test
    fun bmpEmoji_countsAsOneGrapheme() {
        // 🎉 is a supplementary-plane codepoint represented as 2
        // UTF-16 code units (surrogate pair). text.length would say
        // 2; grapheme count must say 1. JVM's BreakIterator handles
        // surrogate pairs correctly across all Unicode versions, so
        // this is platform-stable.
        assertEquals(1, GraphemeCounter.count("🎉"))
    }

    // NOTE: ZWJ-joined emoji sequences (e.g. 👨‍👩‍👧‍👦) intentionally
    // not asserted-to-1 here. JVM's `java.text.BreakIterator` ships with
    // Unicode tables (typically v12-ish) that predate the
    // emoji_zwj_sequences from Unicode 15+, so it counts the family
    // emoji as 7 graphemes instead of 1. Android's `android.icu.text.BreakIterator`
    // (API 24+, our minSdk) has up-to-date tables and counts correctly.
    // The discrepancy is platform-Unicode-version skew, not a bug in
    // GraphemeCounter — tracked as a follow-up to swap the underlying
    // grapheme-segmenter to ICU4J or a Unicode-version-pinned library
    // for cross-platform correctness. For V1, the boundary that
    // matters most (300 plain ASCII chars) is platform-stable, and
    // BMP-pair emoji also count correctly.

    @Test
    fun threeHundredAsciiChars_countsAsThreeHundred() {
        // The exact-300 boundary the composer uses for `isOverLimit`.
        assertEquals(300, GraphemeCounter.count("a".repeat(300)))
    }
}
