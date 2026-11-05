package net.kikin.nubecita.core.translation

/**
 * Guesses what language a piece of text is in.
 *
 * Advisory, and treated as such by every caller. Measured against the post
 * that motivated this feature — a German greeting followed by nine English
 * hashtags — it answers "English", because by token count that is what the
 * text mostly is. A design that trusted it would show no translate affordance
 * on exactly the post a reader needs one for.
 *
 * So identification is the *fallback*, consulted only when the record itself
 * declares nothing. See `PostUi.langs`.
 */
interface LanguageIdentifier {
    /**
     * The bare BCP-47 subtag [text] appears to be in, or `null` when the
     * answer is undetermined.
     *
     * `null` rather than a low-confidence guess on purpose: a wrong language
     * produces a wrong translation, which is worse than no translation, and a
     * caller cannot sensibly weigh a confidence score it did not ask for.
     */
    suspend fun identify(text: String): String?

    /**
     * Drop the loaded identification model and free its memory.
     *
     * Called when the app backgrounds, by `TranslationLifecycle`. Safe to call
     * at any time: the next [identify] reloads it.
     */
    fun release()
}
