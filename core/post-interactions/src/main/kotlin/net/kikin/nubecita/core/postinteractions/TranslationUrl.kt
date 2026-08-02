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
    val target = targetLanguage.ifBlank { "en" }
    val encoded = URLEncoder.encode(text, Charsets.UTF_8.name()).replace("+", "%20")
    return "https://translate.google.com/?sl=auto&tl=$target&op=translate&text=$encoded"
}
