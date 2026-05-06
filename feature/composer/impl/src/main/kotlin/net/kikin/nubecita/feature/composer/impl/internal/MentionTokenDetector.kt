package net.kikin.nubecita.feature.composer.impl.internal

/**
 * Returns the active `@`-mention token at [cursor] within [text],
 * **without** the leading `@`, or `null` when no token is active.
 *
 * The composer's typeahead pipeline calls this on every snapshot
 * emission from the `TextFieldState` (text and/or selection change)
 * and uses the result to decide whether to fire a typeahead query.
 *
 * Algorithm — walk back from `cursor - 1` toward `0`:
 *
 * 1. Whitespace terminates the walk → no active token.
 * 2. `@` terminates the walk → candidate found.
 * 3. Otherwise keep walking.
 *
 * On a found `@` at position `i`, two further checks reject the
 * candidate:
 *
 * - If the char at `i - 1` is a word char `[A-Za-z0-9_]` (i.e. the
 *   `@` sits in the middle of a word), the candidate is an email-
 *   like context — reject. This matches the `[$|\W]` boundary in
 *   the official AT Protocol handle regex; `.@alice` and `-@alice`
 *   are accepted, `email@host` is not.
 * - If the substring `[i + 1, cursor)` is empty (cursor sits
 *   immediately after a bare `@`), reject — typeahead-on-empty would
 *   spam the API on every keystroke between typing `@` and the first
 *   handle char.
 *
 * Pure: no Compose, no coroutines, no I/O. JVM-only unit-testable.
 *
 * Defensive on out-of-range [cursor]: a cursor `<= 0` returns null
 * (nothing to walk back from), and a cursor beyond `text.length`
 * returns null too. `TextFieldState`'s selection should never produce
 * an out-of-range value, but the cost of guarding is one comparison.
 */
internal fun currentMentionToken(
    text: CharSequence,
    cursor: Int,
): String? {
    if (cursor <= 0 || cursor > text.length) return null

    var i = cursor - 1
    while (i >= 0) {
        val ch = text[i]
        if (ch.isWhitespace()) return null
        if (ch == '@') {
            if (i > 0) {
                val prev = text[i - 1]
                if (prev.isLetterOrDigit() || prev == '_') return null
            }
            if (i + 1 == cursor) return null
            return text.substring(i + 1, cursor)
        }
        i--
    }
    return null
}
