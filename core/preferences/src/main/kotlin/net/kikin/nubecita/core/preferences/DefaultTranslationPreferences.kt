package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.common.language.LanguageTags
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

internal class DefaultTranslationPreferences
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : TranslationPreferences {
        override val autoTranslateEnabled: Flow<Boolean> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Failed to read translation preferences; defaulting autoTranslateEnabled to false")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> prefs[Keys.AUTO_TRANSLATE_ENABLED] ?: false }

        override suspend fun setAutoTranslateEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[Keys.AUTO_TRANSLATE_ENABLED] = enabled }
        }

        override val autoTranslateLanguages: Flow<Set<String>> =
            languageSet(Keys.AUTO_TRANSLATE_LANGUAGES, label = "autoTranslateLanguages")

        override suspend fun setAutoTranslateLanguages(languages: Set<String>) {
            setLanguageSet(Keys.AUTO_TRANSLATE_LANGUAGES, languages)
        }

        override val neverTranslateLanguages: Flow<Set<String>> =
            languageSet(Keys.NEVER_TRANSLATE_LANGUAGES, label = "neverTranslateLanguages")

        override suspend fun setNeverTranslateLanguages(languages: Set<String>) {
            setLanguageSet(Keys.NEVER_TRANSLATE_LANGUAGES, languages)
        }

        /**
         * A stored language set, normalized on the way out.
         *
         * Normalizing on read as well as on write is not redundant: a set
         * written by an older build — or carried across a device whose locale
         * data has moved on — still has to come back as something the matcher
         * recognises. Entries that normalize to nothing are dropped rather
         * than carried as junk that can never match.
         */
        private fun languageSet(
            key: Preferences.Key<Set<String>>,
            label: String,
        ): Flow<Set<String>> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        Timber.w(error, "Failed to read translation preferences; defaulting $label to empty")
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map { prefs -> LanguageTags.normalizeAll(prefs[key].orEmpty()).toSet() }

        private suspend fun setLanguageSet(
            key: Preferences.Key<Set<String>>,
            languages: Set<String>,
        ) {
            val normalized = LanguageTags.normalizeAll(languages).toSet()
            dataStore.edit { prefs -> prefs[key] = normalized }
        }

        private object Keys {
            val AUTO_TRANSLATE_ENABLED = booleanPreferencesKey("auto_translate_enabled")
            val AUTO_TRANSLATE_LANGUAGES = stringSetPreferencesKey("auto_translate_languages")
            val NEVER_TRANSLATE_LANGUAGES = stringSetPreferencesKey("never_translate_languages")
        }
    }
