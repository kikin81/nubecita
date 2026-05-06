package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for [currentMentionToken].
 *
 * These cases anchor the contract documented on the helper itself
 * and on `add-composer-mention-typeahead`'s `feature-composer/spec.md`.
 * The spec scenarios are reproduced verbatim, plus defensive cases
 * the spec doesn't enumerate (cursor-at-zero, cursor-out-of-range,
 * no-`@`-in-text, mid-word `@`, accept-after-`.`).
 */
class MentionTokenDetectorTest {
    // ---------- spec scenarios (reproduced verbatim) ----------

    @Test
    fun specScenario_singleCharToken_returnsThatChar() {
        // Spec: `currentMentionToken("@a", 2)` → "a"
        assertEquals("a", currentMentionToken("@a", 2))
    }

    @Test
    fun specScenario_bareAt_returnsNull() {
        // Spec: `currentMentionToken("@", 1)` → null
        // Don't fire on bare `@` — typeahead-on-empty would spam the
        // API the moment the user types `@` and before they type the
        // first handle char.
        assertNull(currentMentionToken("@", 1))
    }

    @Test
    fun specScenario_emailContext_returnsNull() {
        // Spec: `currentMentionToken("hi alice@host.com", 17)` → null
        // The `@` is preceded by `e` (word char) → email-like →
        // suppressed.
        assertNull(currentMentionToken("hi alice@host.com", 17))
    }

    @Test
    fun specScenario_cursorMidExistingToken_returnsLeftHalf() {
        // Spec: `currentMentionToken("@alice.bsky.social trailing", 6)` → "alice"
        // The user could be inserting in the middle of an existing
        // handle; we return the prefix up to the cursor so typeahead
        // can offer "did you mean…" against what's already typed.
        assertEquals("alice", currentMentionToken("@alice.bsky.social trailing", 6))
    }

    @Test
    fun specScenario_cursorAfterCompleteHandlePlusSpace_returnsNull() {
        // Spec: `currentMentionToken("@alice.bsky.social ", 19)` → null
        // The walk-back from `cursor - 1 = 18` immediately hits a
        // space → stop → return null. Suggestions disappear once the
        // user moves past the handle.
        assertNull(currentMentionToken("@alice.bsky.social ", 19))
    }

    @Test
    fun specScenario_multiByteCharsInToken_returnTokenIntact() {
        // Spec: `currentMentionToken("@aliçe", 6)` → "aliçe"
        // `ç` (U+00E7) is one Char in Kotlin Strings. Token detection
        // is character-based; the token survives multi-byte content
        // unchanged.
        assertEquals("aliçe", currentMentionToken("@aliçe", 6))
    }

    // ---------- defensive cases (not enumerated in spec) ----------

    @Test
    fun cursorAtZero_returnsNull() {
        // Nothing to walk back from. Common case: empty composer
        // immediately after focus, before any input.
        assertNull(currentMentionToken("", 0))
        assertNull(currentMentionToken("@alice", 0))
    }

    @Test
    fun cursorOutOfRange_returnsNull() {
        // Defensive — TextFieldState shouldn't produce this, but
        // guarding costs one comparison.
        assertNull(currentMentionToken("@a", 99))
    }

    @Test
    fun noAtInText_returnsNull() {
        assertEquals(null, currentMentionToken("hello world", 5))
        assertEquals(null, currentMentionToken("hello world", 11))
    }

    @Test
    fun midWordAt_returnsNull() {
        // `say@hi` cursor at end. The `@` is preceded by `y` (word
        // char) — email-like context — reject.
        assertNull(currentMentionToken("say@hi", 6))
    }

    @Test
    fun atPrecededByDot_isAccepted() {
        // `.@alice` cursor at end. The `@` is preceded by `.` (NOT
        // a word char) — accept. Matches the lexicon's `[$|\W]`
        // boundary, where `.` and `-` are non-word and therefore
        // valid boundaries.
        assertEquals("alice", currentMentionToken(".@alice", 7))
    }

    @Test
    fun atPrecededByHyphen_isAccepted() {
        // Same logic as `.@`. Hyphens are non-word.
        assertEquals("alice", currentMentionToken("-@alice", 7))
    }

    @Test
    fun atAfterWhitespace_isAccepted_withMidString() {
        // Mid-string `@` after a space → accept. This is the
        // most-common composer flow: "hello @al…".
        assertEquals("ali", currentMentionToken("hi @ali", 7))
    }

    @Test
    fun secondAtAdjacentToFirstHandle_isRejectedAsEmailLike() {
        // `@a@b` cursor at end. The second `@` (position 2) is
        // preceded by `a` (word char) → email-like → reject. This is
        // the same rule that suppresses typeahead inside `email@host`;
        // `@`-adjacent-to-handle-chars is treated identically. The
        // user has to insert whitespace (`@a @b`) for the second
        // mention to fire — same behavior as in Bluesky / Threads.
        assertNull(currentMentionToken("@a@b", 4))
    }

    @Test
    fun secondAtAfterWhitespace_isAccepted() {
        // `@a @b` cursor at end. Walking back from `cursor - 1 = 4`,
        // the FIRST `@` we hit is the one at position 3 — preceded
        // by space (not a word char) → accept. Token is `b`. The
        // earlier `@a` doesn't pollute the second mention's lookup.
        assertEquals("b", currentMentionToken("@a @b", 5))
    }

    @Test
    fun cursorInsideExistingTokenMidWalk_returnsTokenUpToCursor() {
        // `@alice.bsky.social trailing` cursor at 18 (right at the
        // space after `social`). Walk back picks up the full handle.
        // This is the "user clicked at end of an existing handle"
        // case — typeahead can re-fire with the canonical handle as
        // the query, which produces the same suggestion they
        // probably already typed.
        assertEquals(
            "alice.bsky.social",
            currentMentionToken("@alice.bsky.social trailing", 18),
        )
    }

    @Test
    fun handleCharsArePermissiveByConstruction() {
        // The walk-back only stops on whitespace or `@`. `.`, `-`,
        // alphanumerics, and even `_` pass through inside the token.
        // For non-whitespace/non-`@` punctuation (`,`, `!`, `?`),
        // this means the token CAN contain them — the API will
        // simply return no matches and the dropdown will collapse
        // to NoResults. Documented as acceptable noise rather than
        // a stricter token grammar in the design doc.
        assertEquals("a-b_c.d", currentMentionToken("@a-b_c.d", 8))
    }

    @Test
    fun underscorePrecedingAt_isRejectedAsWordChar() {
        // `_` is a regex word char per `\w`. `_@alice` is an email-
        // like context for our purposes. Reject.
        assertNull(currentMentionToken("_@alice", 7))
    }
}
