package net.kikin.nubecita.core.preferences

import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.common.language.LanguageTags
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The languages this reader understands, and the one a translation should
 * target.
 *
 * It is a *set*, not a language. A reader fluent in English and Spanish wants
 * neither translated, and `Locale.getDefault().language` can only ever name
 * one of them — which is how a bilingual reader ends up being offered a
 * translation of a post they just read perfectly well.
 *
 * **Why the device, and not the account.** The project's standing rule is to
 * prefer atproto state over app-derived state, and the original design for
 * this said to read the account's content-language preference. That
 * preference does not exist: `app.bsky.actor` defines thirteen preference
 * types — adult content, content labels, declared age, feed view, hidden
 * posts, interests, labelers, muted words, personal details, post-interaction
 * settings, saved feeds, thread view, app state — and not one of them is about
 * language. The official client keeps content languages client-local. The rule
 * cannot apply to state the protocol does not model, so the sources here are
 * the device locale list and the reader's own never-translate list.
 */
@Singleton
class ReaderLanguages
    @Inject
    constructor(
        private val preferences: TranslationPreferences,
        private val deviceLocales: DeviceLocales,
    ) {
        /**
         * Every language the reader understands, as bare subtags, ordered with
         * the device's own locales first.
         *
         * Order matters because [targetLanguage] reads the head of this list.
         * The never-translate entries come second: they say "do not translate
         * this", which is not the same as "translate *into* this", and a
         * reader who added Spanish to be left alone should not thereby change
         * what their German posts get translated into.
         */
        val languages: Flow<List<String>> =
            preferences.neverTranslateLanguages.map { never ->
                LanguageTags.normalizeAll(deviceLocales.current() + never)
            }

        /**
         * The language translations are produced in — the reader's primary
         * device locale.
         *
         * Falls back to English only when the device reports nothing usable,
         * which is a state Android does not normally produce; it exists so
         * that a translation target is always nameable rather than nullable at
         * every call site.
         */
        val targetLanguage: Flow<String> =
            languages.map { resolved -> resolved.firstOrNull() ?: FALLBACK_TARGET }

        private companion object {
            const val FALLBACK_TARGET = "en"
        }
    }

/**
 * The device's preferred locales, most-preferred first.
 *
 * An interface so the resolution rules above can be unit-tested on the JVM:
 * the real implementation reads a process-wide Android singleton that a test
 * cannot meaningfully vary.
 */
fun interface DeviceLocales {
    /** The reader's configured locales as BCP-47 tags, most-preferred first. */
    fun current(): List<String>
}

internal class SystemDeviceLocales
    @Inject
    constructor() : DeviceLocales {
        override fun current(): List<String> {
            // getAdjustedDefault, not getDefault: it reorders the system list
            // against the locales the app actually ships, so the head is a
            // language the reader both prefers and sees the UI in.
            val list = LocaleListCompat.getAdjustedDefault()
            return (0 until list.size()).mapNotNull { index -> list[index]?.language }
        }
    }
