package net.kikin.nubecita.feature.composer.impl.internal

/**
 * Locates the position of the active `@`-mention sigil at [cursor] within
 * [text], or returns `null` when no mention is active. The returned index
 * points at the `@` character itself; the mention token (without the
 * leading `@`) is `text.substring(returnedIndex + 1, cursor)`.
 *
 * Algorithm — walk back from `cursor - 1` toward `0`:
 *
 * 1. Whitespace terminates the walk → no active mention.
 * 2. `@` terminates the walk → candidate found at this position.
 * 3. Otherwise keep walking.
 *
 * On a found `@` at position `i`, two further checks reject the
 * candidate:
 *
 * - If the char at `i - 1` is a regex word char `[A-Za-z0-9_]` (i.e.
 *   the `@` sits in the middle of a word), the candidate is an email-
 *   like context — reject. Matches the `[$|\W]` boundary in the
 *   official AT Protocol handle regex; `.@alice` and `-@alice` are
 *   accepted, `email@host` is not.
 * - If `i + 1 == cursor` (cursor sits immediately after a bare `@`),
 *   reject — the empty token case.
 *
 * Pure: no Compose, no coroutines, no I/O. JVM-only unit-testable.
 *
 * Used by both [currentMentionToken] (which returns the token string)
 * and the composer ViewModel's `handleTypeaheadResultClicked` (which
 * uses the `@`-position as the replacement start).
 */
internal fun findActiveMentionStart(
    text: CharSequence,
    cursor: Int,
): Int? {
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
            return i
        }
        i--
    }
    return null
}

/**
 * Returns the active `@`-mention token at [cursor] within [text],
 * **without** the leading `@`, or `null` when no token is active.
 * Convenience wrapper over [findActiveMentionStart].
 *
 * The composer's typeahead pipeline calls this on every snapshot
 * emission from the `TextFieldState` (text and/or selection change)
 * and uses the result to decide whether to fire a typeahead query.
 */
internal fun currentMentionToken(
    text: CharSequence,
    cursor: Int,
): String? {
    val start = findActiveMentionStart(text, cursor) ?: return null
    return text.substring(start + 1, cursor)
}
