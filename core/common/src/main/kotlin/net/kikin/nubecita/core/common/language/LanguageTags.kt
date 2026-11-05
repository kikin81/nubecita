package net.kikin.nubecita.core.common.language

import java.util.Locale

/**
 * BCP-47 language-tag handling shared by everything that reasons about what
 * language something is in.
 *
 * Two callers today — the Google Translate deep link in
 * `:core:post-interactions` and the reader-language resolution that drives
 * on-device translation — and they must agree, because a tag normalized one
 * way in one place and another way in the other is a bug nobody sees until a
 * reader gets the wrong language.
 */
public object LanguageTags {
    /**
     * The bare language subtag of [raw], lower-cased and alias-corrected, or
     * `null` when [raw] carries no usable subtag.
     *
     * Lower-casing goes through [Locale.ROOT] deliberately: the default locale
     * would fold `I` to `ı` on a Turkish device, which silently produces a tag
     * no service recognises.
     *
     * The alias map is the part that actually bites. Android's `Locale` still
     * reports the superseded ISO 639-1 codes for Hebrew, Indonesian and
     * Yiddish — `iw`, `in`, `ji` — where every modern service expects `he`,
     * `id`, `yi`. Passing the legacy tag straight through translates into the
     * wrong language, or none, for those readers. Harmless on a platform that
     * already reports the modern code.
     */
    public fun normalize(raw: String): String? {
        val subtag =
            raw
                .trim()
                .lowercase(Locale.ROOT)
                .substringBefore('-')
                .substringBefore('_')
        if (subtag.isEmpty()) return null
        return LEGACY_ALIASES[subtag] ?: subtag
    }

    /**
     * Whether [tag] names a language present in [languages].
     *
     * Matching is on the bare subtag, so `es-419` matches a reader who reads
     * `es`, and `en-GB` matches `en`. Region and script are deliberately
     * ignored: a reader who reads Spanish reads Latin American Spanish, and
     * treating them as different languages would offer to translate posts the
     * reader understands perfectly well.
     */
    public fun matchesAny(
        tag: String,
        languages: Collection<String>,
    ): Boolean {
        val normalized = normalize(tag) ?: return false
        return languages.any { normalize(it) == normalized }
    }

    /**
     * [tags] normalized, de-duplicated, with unusable entries dropped, in
     * first-seen order.
     *
     * Order is preserved because the caller's first entry is their primary
     * language — the one a translation targets — and sorting or set-ordering
     * here would quietly change which language that is.
     */
    public fun normalizeAll(tags: Iterable<String>): List<String> {
        val seen = LinkedHashSet<String>()
        for (tag in tags) {
            normalize(tag)?.let(seen::add)
        }
        return seen.toList()
    }

    private val LEGACY_ALIASES =
        mapOf(
            "iw" to "he",
            "in" to "id",
            "ji" to "yi",
        )
}
