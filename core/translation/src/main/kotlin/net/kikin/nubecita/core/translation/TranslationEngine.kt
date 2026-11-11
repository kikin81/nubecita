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
     * Line breaks in [text] are preserved: each line is translated on its own,
     * because an engine handed several lines at once may fuse them into one
     * sentence and translate that instead.
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
     * Every language this engine can translate, as bare BCP-47 subtags.
     *
     * Exists so a settings surface can offer a reader the real set rather
     * than a hand-kept list that drifts from what the engine actually
     * supports — offering a language that cannot be translated is a dead end
     * the reader only discovers after choosing it.
     *
     * Returned unsorted: order is a presentation concern, and the only
     * sensible ordering is by display name in the reader's own locale, which
     * this module has no business knowing.
     */
    fun supportedLanguages(): List<String>

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
