package net.kikin.nubecita.designsystem.icon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for [NubecitaIconName]. Catches typos in the
 * vendored hex codepoints — every entry must resolve to exactly one
 * Unicode scalar value (a single glyph), and no entry may collide
 * with another.
 */
class NubecitaIconNameTest {
    @Test
    fun every_codepoint_isASingleScalar() {
        NubecitaIconName.entries.forEach { entry ->
            val cp = entry.codepoint
            val scalarCount = cp.codePointCount(0, cp.length)
            assertEquals(
                1,
                scalarCount,
                "${entry.name}'s codepoint '$cp' resolved to $scalarCount scalars; " +
                    "expected exactly 1 (single glyph)",
            )
        }
    }

    @Test
    fun no_codepointCollisions() {
        // Two enum entries pointing at the same codepoint is almost
        // certainly a copy-paste typo. Every glyph in the picker has
        // a distinct identity in Material Symbols.
        val byCodepoint = NubecitaIconName.entries.groupBy { it.codepoint }
        val collisions =
            byCodepoint
                .filter { it.value.size > 1 }
                .map { (cp, entries) ->
                    val hex =
                        cp
                            .codePointAt(0)
                            .toString(16)
                            .uppercase()
                            .padStart(4, '0')
                    "\\u$hex -> ${entries.map { it.name }}"
                }
        assertTrue(
            collisions.isEmpty(),
            "Codepoint collisions detected: $collisions",
        )
    }

    @Test
    fun enum_isNonEmpty() {
        assertTrue(
            NubecitaIconName.entries.isNotEmpty(),
            "NubecitaIconName must declare at least one glyph",
        )
    }
}
