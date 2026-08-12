package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.preferences.AutoplayPreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backs the Media and animations screen. Both rendered controls are pure
 * projections of the stored preferences — never local optimistic state — so the
 * UI and what playback actually honours cannot disagree.
 *
 * Initial state is the production defaults (ALWAYS / GIFs on), matching
 * [AppearanceViewModel]'s reasoning: the repository exposes a cold `Flow` with
 * no cached `.value` to seed from, and seeding with anything else would flash a
 * wrong row before the first emission.
 */
@HiltViewModel
internal class MediaAndAnimationsViewModel
    @Inject
    constructor(
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : MviViewModel<MediaAndAnimationsState, MediaAndAnimationsEvent, MediaAndAnimationsEffect>(
            MediaAndAnimationsState(autoplay = AutoplayPreference.ALWAYS, autoplayGifs = true),
        ) {
        init {
            userPreferencesRepository.autoplayPreference
                .onEach { preference -> setState { copy(autoplay = preference) } }
                .launchIn(viewModelScope)

            userPreferencesRepository.autoplayGifs
                .onEach { enabled -> setState { copy(autoplayGifs = enabled) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: MediaAndAnimationsEvent) {
            when (event) {
                is MediaAndAnimationsEvent.AutoplaySelected -> selectAutoplay(event.preference)
                is MediaAndAnimationsEvent.AutoplayGifsToggled -> setGifs(event.enabled)
            }
        }

        private fun selectAutoplay(preference: AutoplayPreference) {
            // `NubecitaListItem.onSelect` fires on every tap, including the
            // already-selected row; the guard lives here rather than in the UI so
            // it survives a swap of the control. Without it, re-tapping the
            // current option is a redundant DataStore write.
            if (preference == uiState.value.autoplay) return
            persist("the video autoplay preference") {
                userPreferencesRepository.setAutoplayPreference(preference)
            }
        }

        private fun setGifs(enabled: Boolean) {
            if (enabled == uiState.value.autoplayGifs) return
            persist("the GIF autoplay preference") {
                userPreferencesRepository.setAutoplayGifs(enabled)
            }
        }

        private fun persist(
            what: String,
            write: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                // Broad catch, matching AppearanceViewModel and the one-shot
                // command idiom in CLAUDE.md. IOException does cover DataStore's
                // realistic failure surface, but the consequence is asymmetric:
                // anything unexpected escaping here crashes the app on a tap, and
                // failing to save a preference should never do that.
                try {
                    write()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // The snackbar tells the user; this tells us. A preference
                    // that silently refuses to stick is otherwise invisible in
                    // a bug report.
                    Timber.w(error, "Failed to persist %s", what)
                    sendEffect(MediaAndAnimationsEffect.ShowSaveError)
                }
            }
        }
    }
