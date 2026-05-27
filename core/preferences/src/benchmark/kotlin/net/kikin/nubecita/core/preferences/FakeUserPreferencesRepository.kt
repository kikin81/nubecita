package net.kikin.nubecita.core.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benchmark-flavor [UserPreferencesRepository]. Always reports
 * `hasSeenOnboarding = true` so MainActivity's routing gate doesn't
 * steer the bench journey into the Onboarding flow, and
 * `markOnboardingSeen` is a no-op.
 *
 * Pairs with `:core:auth`'s benchmark `FakeSessionStateProvider` (which
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
    }
