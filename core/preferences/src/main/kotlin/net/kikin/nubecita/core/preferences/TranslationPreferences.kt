package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * The reader's choices about on-device post translation.
 *
 * Deliberately separate from [UserPreferencesRepository] rather than three
 * more members on it. They share a DataStore, so nothing is duplicated on
 * disk, but the consumers do not overlap: `:core:translation` and the
 * Translation settings page need these and nothing else, while the seven
 * existing fakes of [UserPreferencesRepository] — in `:core:video`,
 * `:feature:settings`, `:feature:onboarding` — care about none of them and
 * would each have had to grow three no-op members to say so.
 */
interface TranslationPreferences {
    /**
     * Whether posts in a language the reader does not read are translated
     * automatically. Defaults to `false`.
     *
     * Off is the honest default: a language pack is ~45MB on disk (62MB for
     * Japanese), so nobody should be downloading one they did not ask for.
     *
     * This flag does **not** gate the translate affordance itself. With it
     * off, a post that *declares* a foreign language still offers to be
     * translated — a string comparison, no model loaded, no inference, nothing
     * resident — which is how the feature stays discoverable at zero cost to a
     * reader who never uses it. Turning it on buys the on-device
     * language-identification fallback for posts that declare nothing, plus
     * automatic translation of [autoTranslateLanguages] once the feed settles.
     */
    val autoTranslateEnabled: Flow<Boolean>

    /** Persist the automatic-translation master switch. */
    suspend fun setAutoTranslateEnabled(enabled: Boolean)

    /**
     * Languages the reader has opted into translating automatically, as bare
     * BCP-47 subtags.
     *
     * Grown from Settings or from the in-post prompt after the reader taps a
     * translate chip; a language is dropped when its pack is deleted, because
     * an automatic translation with no model on disk is just a spinner.
     */
    val autoTranslateLanguages: Flow<Set<String>>

    /** Replace the set of languages translated automatically. */
    suspend fun setAutoTranslateLanguages(languages: Set<String>)

    /**
     * Languages the reader reads, and therefore never wants translated, as
     * bare BCP-47 subtags.
     *
     * One list doing two jobs on purpose. There is no separate "languages I
     * read" setting, because it would always hold the same values and a reader
     * who disagreed with one would have to find the other. It is unioned with
     * the device locales by [ReaderLanguages] — which is also why it has to be
     * editable: a device locale list is not a reliable statement of what
     * someone reads.
     */
    val neverTranslateLanguages: Flow<Set<String>>

    /** Replace the set of languages that are never translated. */
    suspend fun setNeverTranslateLanguages(languages: Set<String>)

    /**
     * Languages the reader has already been offered as automatic, as bare
     * BCP-47 subtags.
     *
     * Recorded whether they accepted or declined, which is the point: the
     * offer appears after a translate tap, and a reader who reads one Japanese
     * post a week would otherwise be asked the same question every time. One
     * ask per language is enough to make the feature discoverable; a second is
     * nagging.
     *
     * Deliberately not cleared when a language leaves the auto list. Removing
     * it in Settings is an answer, and re-asking would read as the app
     * second-guessing a decision the reader just made.
     */
    val autoTranslateOffered: Flow<Set<String>>

    /** Remember that [languages] have been offered as automatic. */
    suspend fun setAutoTranslateOffered(languages: Set<String>)
}
