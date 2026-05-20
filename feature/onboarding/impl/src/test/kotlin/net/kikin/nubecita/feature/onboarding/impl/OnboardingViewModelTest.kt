package net.kikin.nubecita.feature.onboarding.impl

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnboardingViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `Skip persists flag then emits NavigateToLogin`() =
        runTest(mainDispatcher.dispatcher) {
            val prefs = RecordingPreferences()
            val vm = OnboardingViewModel(prefs)

            vm.effects.test {
                vm.handleEvent(OnboardingEvent.Skip)

                assertEquals(OnboardingEffect.NavigateToLogin, awaitItem())
            }

            assertEquals(1, prefs.markCalls)
        }

    @Test
    fun `CompleteOnboarding persists flag then emits NavigateToLogin`() =
        runTest(mainDispatcher.dispatcher) {
            val prefs = RecordingPreferences()
            val vm = OnboardingViewModel(prefs)

            vm.effects.test {
                vm.handleEvent(OnboardingEvent.CompleteOnboarding)

                assertEquals(OnboardingEffect.NavigateToLogin, awaitItem())
            }

            assertEquals(1, prefs.markCalls)
        }

    @Test
    fun `persistence failure still emits NavigateToLogin so the screen failsafe path can fire`() =
        runTest(mainDispatcher.dispatcher) {
            val prefs = FailingPreferences()
            val vm = OnboardingViewModel(prefs)

            // The "isn't stranded" guarantee is split across two layers:
            // (1) the VM emits NavigateToLogin even when the persist throws
            //     — verified here; and
            // (2) the screen Composable's LaunchedEffect translates that
            //     effect into `navigator.replaceTo(Login)` so the user is
            //     actually moved off Onboarding — verified by the screen-
            //     side instrumentation tests in nubecita-lo3f.5.
            // Without step 1 the failsafe in step 2 would never fire, so
            // this test pins the contract.
            vm.effects.test {
                vm.handleEvent(OnboardingEvent.Skip)

                assertEquals(OnboardingEffect.NavigateToLogin, awaitItem())
            }
            assertTrue(prefs.markAttempts > 0)
        }

    private class RecordingPreferences : UserPreferencesRepository {
        private val seen = MutableStateFlow(false)
        override val hasSeenOnboarding: Flow<Boolean> = seen.asStateFlow()
        var markCalls = 0

        override suspend fun markOnboardingSeen() {
            markCalls += 1
            seen.value = true
        }
    }

    private class FailingPreferences : UserPreferencesRepository {
        private val seen = MutableStateFlow(false)
        override val hasSeenOnboarding: Flow<Boolean> = seen.asStateFlow()
        var markAttempts = 0

        override suspend fun markOnboardingSeen() {
            markAttempts += 1
            error("simulated disk failure")
        }
    }
}
