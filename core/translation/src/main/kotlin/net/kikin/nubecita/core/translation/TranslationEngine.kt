package net.kikin.nubecita.core.translation

/**
 * Translates text on the device.
 *
 * The whole module exists to keep the translation SDK on one side of this
 * line, the same way `:core:billing` keeps RevenueCat off the rest of the app.
 * No SDK type appears in any signature here or in anything that depends on
 * this module, so swapping engines is a rewrite of one module rather than a
 * grep across the codebase.
 *
 * Language arguments are bare BCP-47 subtags, already normalized by
 * `LanguageTags`.
 */
interface TranslationEngine {
    /**
     * [text] rendered in [targetLanguage], or a failure.
     *
     * Failures are ordinary and expected — an unsupported pair, a pack that
     * was deleted between the check and the call — so they come back as a
     * `Result` rather than an exception. A caller that cannot translate shows
     * the original, which is always a correct thing to show.
     *
     * Suspends; the implementation does the work off the main thread. Measured
     * at 36–311ms per post with the pack already on disk.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<String>

    /**
     * Whether this engine can translate between the two languages at all.
     *
     * Checked before an affordance is offered, so a reader is never invited to
     * translate something that cannot be translated.
     */
    fun supports(
        sourceLanguage: String,
        targetLanguage: String,
    ): Boolean

    /**
     * Drop any loaded model and free its memory.
     *
     * Called when the app backgrounds, by `TranslationLifecycle`. With five
     * translators resident the process measured around 400MB RSS, which is
     * enough to make the app an appealing low-memory-kill target while it sits
     * in the background doing nothing. Safe to call at any time: the next
     * [translate] reloads what it needs.
     */
    fun release()
}
