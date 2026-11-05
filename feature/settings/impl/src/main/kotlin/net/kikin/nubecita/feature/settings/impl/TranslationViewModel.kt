package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.translation.TranslationModelRepository
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backs the Translation screen.
 *
 * Every rendered control is a projection of stored state, never local
 * optimistic state, so the screen and what translation actually honours cannot
 * disagree — the same rule [MediaAndAnimationsViewModel] follows.
 */
@HiltViewModel
internal class TranslationViewModel
    @Inject
    constructor(
        private val preferences: TranslationPreferences,
        private val models: TranslationModelRepository,
    ) : MviViewModel<TranslationState, TranslationEvent, TranslationEffect>(TranslationState()) {
        init {
            preferences.autoTranslateEnabled
                .onEach { enabled -> setState { copy(autoTranslateEnabled = enabled) } }
                .launchIn(viewModelScope)

            preferences.autoTranslateLanguages
                .onEach { languages -> setState { copy(autoTranslate = languages.sorted().toImmutableList()) } }
                .launchIn(viewModelScope)

            preferences.neverTranslateLanguages
                .onEach { languages -> setState { copy(neverTranslate = languages.sorted().toImmutableList()) } }
                .launchIn(viewModelScope)

            models.downloaded
                .onEach { packs -> setState { copy(downloaded = packs.toImmutableList()) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: TranslationEvent) {
            when (event) {
                is TranslationEvent.AutoTranslateToggled -> toggleAutoTranslate(event.enabled)
                is TranslationEvent.AutoTranslateLanguagesChanged -> setAutoLanguages(event.languages)
                is TranslationEvent.NeverTranslateLanguagesChanged -> setNeverLanguages(event.languages)
                is TranslationEvent.LanguageDeleted -> deleteLanguage(event.languageTag)
            }
        }

        private fun toggleAutoTranslate(enabled: Boolean) {
            if (enabled == uiState.value.autoTranslateEnabled) return
            persist("the automatic translation switch") { preferences.setAutoTranslateEnabled(enabled) }
        }

        private fun setAutoLanguages(languages: Set<String>) {
            if (languages == uiState.value.autoTranslate.toSet()) return
            // A language cannot be in both lists: "translate this automatically"
            // and "I read this" are contradictory, and leaving both set would
            // make the feed's behaviour depend on which check ran first.
            val never = uiState.value.neverTranslate.toSet() - languages
            persist("the automatic translation languages") {
                preferences.setAutoTranslateLanguages(languages)
                if (never != uiState.value.neverTranslate.toSet()) {
                    preferences.setNeverTranslateLanguages(never)
                }
            }
        }

        private fun setNeverLanguages(languages: Set<String>) {
            if (languages == uiState.value.neverTranslate.toSet()) return
            val auto = uiState.value.autoTranslate.toSet() - languages
            persist("the never-translate languages") {
                preferences.setNeverTranslateLanguages(languages)
                if (auto != uiState.value.autoTranslate.toSet()) {
                    preferences.setAutoTranslateLanguages(auto)
                }
            }
        }

        private fun deleteLanguage(languageTag: String) {
            persist("the language pack deletion") {
                models.delete(languageTag)
                // Dropping it from the auto list is part of the delete, not a
                // follow-up: an automatic translation whose model is gone is a
                // shimmer that never resolves.
                val remaining = uiState.value.autoTranslate.toSet() - languageTag
                if (remaining != uiState.value.autoTranslate.toSet()) {
                    preferences.setAutoTranslateLanguages(remaining)
                }
            }
        }

        private fun persist(
            what: String,
            write: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                // Broad catch, matching MediaAndAnimationsViewModel: DataStore's
                // realistic failures are IO, but the consequence is asymmetric —
                // anything unexpected escaping here crashes the app on a tap, and
                // failing to save a preference should never do that.
                try {
                    write()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.w(error, "Failed to persist %s", what)
                    sendEffect(TranslationEffect.ShowSaveError)
                }
            }
        }
    }
