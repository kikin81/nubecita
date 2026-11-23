package net.kikin.nubecita.core.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [AuthRepository]. Returns deterministic happy-path
 * results so any code path that constructs the repository compiles and
 * the Hilt graph builds cleanly under `assembleBenchDebug`.
 *
 * Not expected to be called during the bench journey — the bench starts
 * with [FakeSessionStateProvider] reporting [SessionState.SignedIn] at
 * boot, so the Login screen never composes until `signOut` flips the
 * session to [SessionState.SignedOut]; `completeLogin` flips it back.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeAuthRepository
    @Inject
    constructor(
        private val sessionStateProvider: FakeSessionStateProvider,
    ) : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> = Result.success("https://bench.invalid/oauth/authorize?bench=true")

        override suspend fun completeLogin(redirectUri: String): Result<Unit> {
            sessionStateProvider.setSignedIn(true)
            return Result.success(Unit)
        }

        override suspend fun signOut(): Result<Unit> {
            sessionStateProvider.setSignedIn(false)
            return Result.success(Unit)
        }
    }
