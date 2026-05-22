package net.kikin.nubecita.feature.profile.impl

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Boundary tests for [isValidRkey] — the validator that gates which
 * `{rkey}` placeholders are allowed through the post-detail deep-link
 * matcher (`/profile/{handle}/post/{rkey}`).
 *
 * `app.bsky.feed.post` records use the TID rkey format
 * (https://atproto.com/specs/tid): 13 ASCII chars from the
 * base32-sortable alphabet `[2-7a-z]`.
 *
 * Validation is purely syntactic. A syntactically valid TID that
 * doesn't resolve to a post surfaces downstream as the existing
 * `PostDetail` `NotFound` error UX; the point of this validator is
 * to reject obviously malformed input before the XRPC call.
 */
class RkeyValidationTest {
    // -- Accept cases -----------------------------------------------------

    @Test
    fun typicalTidIsAccepted() {
        // A real-looking TID — 13 chars, all in the base32-sortable
        // alphabet. (The example here is structurally valid; whether
        // a specific post with this rkey exists is irrelevant for
        // syntactic acceptance.)
        assertTrue(isValidRkey("3lkbabcdefghi"))
    }

    @Test
    fun tidWithDigitsAndLettersIsAccepted() {
        assertTrue(isValidRkey("3jzfcijpj2z2a"))
    }

    @Test
    fun tidStartingWithLetterIsAccepted() {
        // The TID spec's first-char "high-bit-clear" sub-constraint
        // (`[234567a-h]`) is intentionally NOT enforced — see kdoc on
        // isValidRkey. A first-char `z` is still 13 chars from the
        // sortable alphabet and passes.
        assertTrue(isValidRkey("zzzzzzzzzzzzz"))
    }

    // -- Reject cases — wrong length --------------------------------------

    @Test
    fun emptyStringIsRejected() {
        assertFalse(isValidRkey(""))
    }

    @Test
    fun nullRkeyIsRejected() {
        assertFalse(isValidRkey(null))
    }

    @Test
    fun tidShorterThan13CharsIsRejected() {
        assertFalse(isValidRkey("3lkbabcdefgh"))
    }

    @Test
    fun tidLongerThan13CharsIsRejected() {
        assertFalse(isValidRkey("3lkbabcdefghij"))
    }

    // -- Reject cases — disallowed characters -----------------------------

    @Test
    fun tidWithZeroIsRejected() {
        // 0 is excluded from the base32-sortable alphabet (confusable
        // with `o`).
        assertFalse(isValidRkey("3lkbabcd0fghi"))
    }

    @Test
    fun tidWithOneIsRejected() {
        // 1 is excluded (confusable with `l`).
        assertFalse(isValidRkey("3lkbabcd1fghi"))
    }

    @Test
    fun tidWithEightIsRejected() {
        assertFalse(isValidRkey("3lkbabcd8fghi"))
    }

    @Test
    fun tidWithNineIsRejected() {
        assertFalse(isValidRkey("3lkbabcd9fghi"))
    }

    @Test
    fun tidWithUppercaseIsRejected() {
        // The TID alphabet is lowercase-only.
        assertFalse(isValidRkey("3LKBABCDEFGHI"))
    }

    @Test
    fun tidWithHyphenIsRejected() {
        // The broader `record-key` format permits `-`, but TID does
        // not, and `app.bsky.feed.post` uses TID specifically.
        assertFalse(isValidRkey("3lkb-bcdefghi"))
    }

    @Test
    fun tidWithSlashIsRejected() {
        // Path-traversal smuggling — URL-decoded `%2F` becomes `/`
        // after pathSegments splitting. Reject loudly.
        assertFalse(isValidRkey("3lkba/cdefghi"))
    }

    @Test
    fun tidWithSpaceIsRejected() {
        assertFalse(isValidRkey("3lkba cdefghi"))
    }

    @Test
    fun tidWithUnderscoreIsRejected() {
        assertFalse(isValidRkey("3lkba_cdefghi"))
    }
}
