package net.kikin.nubecita.feature.onboarding.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val userPreferences: UserPreferencesRepository,
    ) : MviViewModel<OnboardingState, OnboardingEvent, OnboardingEffect>(OnboardingState) {
        override fun handleEvent(event: OnboardingEvent) {
            when (event) {
                OnboardingEvent.Skip,
                OnboardingEvent.CompleteOnboarding,
                -> finishOnboarding()
            }
        }

        private fun finishOnboarding() {
            // Persist first, navigate second — if persistence throws, the
            // collector in MainActivity would otherwise route the user back to
            // Onboarding on the next state emission. We log the write failure
            // but still emit the navigation effect so the user isn't stranded;
            // worst case they re-see onboarding once on the next cold start.
            viewModelScope.launch {
                try {
                    userPreferences.markOnboardingSeen()
                } catch (cancellation: CancellationException) {
                    // Don't log or swallow — structured concurrency requires re-throwing
                    // CancellationException so the parent scope's teardown propagates
                    // correctly. The catch-Exception block below would otherwise hide
                    // legitimate ViewModel-clear cancellation.
                    throw cancellation
                } catch (error: Exception) {
                    Timber.w(error, "Failed to persist hasSeenOnboarding=true on onboarding completion")
                }
                sendEffect(OnboardingEffect.NavigateToLogin)
            }
        }
    }
