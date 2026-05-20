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
            // Persist first, navigate second. If persistence throws, the
            // `hasSeenOnboarding` flow never emits `true`, so `MainActivity`'s
            // combine collector wouldn't re-route on its own — the user
            // would be stranded on Onboarding for the rest of the session.
            // We emit `NavigateToLogin` regardless of the persist outcome so
            // the screen-side `LaunchedEffect` calls `replaceTo(Login)` as a
            // failsafe; worst case the user re-sees onboarding once on the
            // next cold start.
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
