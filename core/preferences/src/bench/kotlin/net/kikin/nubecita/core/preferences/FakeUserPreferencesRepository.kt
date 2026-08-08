package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [UserPreferencesRepository]. Always reports
 * `hasSeenOnboarding = true` so MainActivity's routing gate doesn't
 * steer the bench journey into the Onboarding flow, and
 * `markOnboardingSeen` is a no-op.
 *
 * Pairs with `:core:auth`'s bench `FakeSessionStateProvider` (which
 * reports `SignedIn` at boot). Together they collapse Splash → Main in
 * a single frame.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeUserPreferencesRepository
    @Inject
    constructor() : UserPreferencesRepository {
        override val hasSeenOnboarding: Flow<Boolean> = flowOf(true)

        override suspend fun markOnboardingSeen() = Unit

        override val lastSelectedFeedUri: Flow<String?> = flowOf(null)

        override suspend fun setLastSelectedFeedUri(uri: String) = Unit

        // Stateful, unlike the other fakes here, because the Appearance picker
        // (nubecita-wqb8) is a *write* surface: with a constant flow and a no-op
        // setter, tapping a theme in a bench build would change nothing, so the
        // bench smoke test for it could only ever be a false pass. Starts at
        // DYNAMIC, matching the production default, so benchmark journeys that
        // never touch the picker are unaffected. In-memory only — nothing here
        // persists across process death, which is what bench wants.
        private val theme = MutableStateFlow(ThemePreference.DYNAMIC)

        override val themePreference: Flow<ThemePreference> = theme.asStateFlow()

        override suspend fun setThemePreference(preference: ThemePreference) {
            theme.value = preference
        }
    }
