package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backs the Appearance screen. The rendered selection is a pure projection of
 * [UserPreferencesRepository.themePreference] — never local optimistic state —
 * so the radio list and the app's actual theme cannot disagree.
 *
 * The initial state is [ThemePreference.DYNAMIC] because the repository exposes
 * a cold `Flow` with no cached `.value` to seed from. In practice DataStore has
 * already served this key during startup (the composition root reads it to pick
 * the theme), so the real value arrives from the in-memory cache on the first
 * emission and no wrong row is perceptibly selected.
 */
@HiltViewModel
internal class AppearanceViewModel
    @Inject
    constructor(
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : MviViewModel<AppearanceState, AppearanceEvent, AppearanceEffect>(
            AppearanceState(selected = ThemePreference.DYNAMIC),
        ) {
        init {
            userPreferencesRepository.themePreference
                .onEach { preference -> setState { copy(selected = preference) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: AppearanceEvent) {
            when (event) {
                is AppearanceEvent.ThemeSelected -> select(event.theme)
            }
        }

        private fun select(theme: ThemePreference) {
            // `NubecitaListItem`'s `onSelect` fires on every tap, including the
            // already-selected row, and its KDoc puts this guard in the ViewModel
            // rather than the UI so it survives a swap of the control. Without it
            // every re-tap would be a redundant DataStore write.
            if (theme == uiState.value.selected) return
            viewModelScope.launch {
                // Broad catch, deliberately. `IOException` does cover DataStore's
                // realistic failure surface, and narrowing to it would drop the
                // CancellationException rethrow — but the consequence is
                // asymmetric: anything unexpected escaping here crashes the app
                // on a tap, and failing to save a cosmetic preference should
                // never do that. Matches the shape in `FeedPreferencesViewModel`
                // and the one-shot-command idiom in CLAUDE.md.
                try {
                    userPreferencesRepository.setThemePreference(theme)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    sendEffect(AppearanceEffect.ShowSaveError)
                }
            }
        }
    }
