package net.kikin.nubecita.feature.onboarding.impl.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [UserPreferencesRepository] for instrumentation tests.
 * Injected via [TestUserPreferencesBindingsModule]'s
 * `@TestInstallIn(replaces = [UserPreferencesBindingsModule::class])`.
 *
 * [markFailure] (when non-null) causes [markOnboardingSeen] to throw —
 * lets tests pin the screen-side failsafe path (`NavigateToLogin` still
 * emits, screen still calls `replaceTo(Login)`, user not stranded).
 * [markCalls] records the number of persist attempts so tests can
 * assert the VM actually tried to write the flag.
 */
@Singleton
internal class FakeUserPreferencesRepository
    @Inject
    constructor() : UserPreferencesRepository {
        private val seen = MutableStateFlow(false)
        override val hasSeenOnboarding: Flow<Boolean> = seen.asStateFlow()

        @Volatile
        var markFailure: Throwable? = null

        @Volatile
        var markCalls: Int = 0

        override suspend fun markOnboardingSeen() {
            markCalls += 1
            markFailure?.let { throw it }
            seen.value = true
        }

        override val lastSelectedFeedUri: Flow<String?> = MutableStateFlow<String?>(null).asStateFlow()

        override suspend fun setLastSelectedFeedUri(uri: String) = Unit
    }
