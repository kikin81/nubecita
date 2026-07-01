package net.kikin.nubecita.core.common.text

/**
 * Shared URL detection used by BOTH post composition (link facet extraction in
 * `:core:posting`) and profile-bio linkification (`:feature:profile:impl`), so
 * the two stay consistent instead of drifting as copy-pasted regexes.
 *
 * The URL is capture group [URL_GROUP]; the leading `(?:^|[^\w@])` keeps the
 * match from starting inside an email address or an `@handle`.
 *
 * Known limitation (shared by both consumers): the TLD is capped at 6 chars,
 * so bare 7+-char pseudo-TLDs aren't linkified. Real TLDs (`.com`, `.app`,
 * `.social`, `.design`, …) match. `String.replaceFirst`-style edits here change
 * behaviour for posts too — keep this the single source of truth.
 */
object LinkPatterns {
    val URL_REGEX =
        Regex(
            """(?:^|[^\w@])(https?://(?:www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_+.~#?&/=]*[-a-zA-Z0-9@%_+~#/=])?)""",
        )

    /** Regex capture group holding the URL itself (the outer match includes the prefix guard). */
    const val URL_GROUP = 1
}
