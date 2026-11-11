package net.kikin.nubecita.core.posttranslation

import net.kikin.nubecita.data.models.PostTranslationUi

/**
 * Completed translations, kept across screens and restarts.
 *
 * Keyed by post and **target** language: a reader who changes their device
 * language must not be served the previous language's translation, and the
 * staleness would be invisible because the text reads fine in *a* language.
 *
 * Disposable by design. A failed read is a miss and a failed write is
 * forgotten; losing every row costs the reader a few hundred milliseconds of
 * re-translation, never a wrong result.
 *
 * An interface so tests run on the JVM against a map instead of Robolectric
 * and Room; the Room implementation stays internal to this module.
 */
interface TranslationCache {
    /** The cached translation of [postUri] into [targetLanguage], or null. */
    suspend fun find(
        postUri: String,
        targetLanguage: String,
    ): PostTranslationUi.Shown?

    /** Remember [translation] as [postUri]'s translation into [targetLanguage]. */
    suspend fun put(
        postUri: String,
        targetLanguage: String,
        translation: PostTranslationUi.Shown,
    )
}
