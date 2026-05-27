package net.kikin.nubecita.core.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benchmark-flavor [AuthRepository]. Returns deterministic happy-path
 * results so any code path that constructs the repository compiles and
 * the Hilt graph builds cleanly under `assembleBenchmarkDebug`.
 *
 * Not expected to be called during the benchmark journey — the bench
 * starts with [FakeSessionStateProvider] reporting [SessionState.SignedIn]
 * at boot, so the Login screen never composes and `beginLogin` /
 * `completeLogin` are unreachable. `signOut` is no-op for parity.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeAuthRepository
    @Inject
    constructor() : AuthRepository {
        override suspend fun beginLogin(handle: String): Result<String> = Result.success("https://benchmark.invalid/oauth/authorize?bench=true")

        override suspend fun completeLogin(redirectUri: String): Result<Unit> = Result.success(Unit)

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }
