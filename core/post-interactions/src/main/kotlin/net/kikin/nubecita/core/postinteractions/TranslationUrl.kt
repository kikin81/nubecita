package net.kikin.nubecita.core.postinteractions

import java.net.URLEncoder
import java.util.Locale

/**
 * A Google Translate URL for [text], translating into [targetLanguage].
 *
 * The source language is left as `auto`. A post's declared `langs` is not
 * consulted because it is author-supplied, frequently absent, and wrong often
 * enough that trusting it would mistranslate; the translator's own detection is
 * better than a bad hint. (Plumbing `langs` through for the *in-place* feature
 * is nubecita-s6xk.1, where it is used as a hint and never as a gate.)
 *
 * Spaces are escaped as `%20` rather than left as `+`. Both are legal in a
 * query string, but `+` is only a space by form-encoding convention, and the
 * text is being read by a human on the other side.
 */
fun googleTranslateUrl(
    text: String,
    targetLanguage: String = Locale.getDefault().language,
): String {
    val target = normalizeTargetLanguage(targetLanguage)
    val encoded = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
    return "https://translate.google.com/?sl=auto&tl=$target&op=translate&text=$encoded"
}

/**
 * A language tag Google Translate will accept.
 *
 * Lower-cased via [Locale.ROOT] so a caller passing `EN` cannot produce a
 * locale-folded tag (the Turkish dotless-i problem `LoginIdentifier` documents).
 *
 * The alias map is the interesting part: Android's `Locale` reports the
 * superseded ISO 639-1 codes for Hebrew, Indonesian and Yiddish — `iw`, `in`
 * and `ji` — where Google Translate expects `he`, `id` and `yi`. Passing the
 * legacy tag straight through would quietly translate into the wrong language,
 * or none, for those readers. Harmless if a platform already reports the
 * modern code.
 */
private fun normalizeTargetLanguage(raw: String): String {
    val tag = raw.trim().lowercase(Locale.ROOT).substringBefore('-')
    return when {
        tag.isEmpty() -> "en"
        else -> LEGACY_LANGUAGE_ALIASES[tag] ?: tag
    }
}

private val LEGACY_LANGUAGE_ALIASES =
    mapOf(
        "iw" to "he",
        "in" to "id",
        "ji" to "yi",
    )
