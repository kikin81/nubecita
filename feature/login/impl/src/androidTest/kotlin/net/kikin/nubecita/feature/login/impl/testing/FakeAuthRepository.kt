package net.kikin.nubecita.feature.login.impl.testing

import net.kikin.nubecita.core.auth.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [AuthRepository] for instrumentation tests. Injected via
 * [TestAuthBindingsModule]'s `@TestInstallIn(replaces = [AuthBindingsModule::class])`.
 *
 * `beginLogin` returns a fixed canned authorization URL so the
 * LoginViewModel emits `LoginEffect.LaunchCustomTab(url)` and the screen
 * fires the Custom Tab intent the test asserts on. `completeLogin` is
 * not exercised by the login intent test — the post-callback path
 * (redirect → token exchange → SignedIn) is covered separately in
 * `:core:auth/src/androidTest/` (nubecita-z9d).
 */
@Singleton
internal class FakeAuthRepository
    @Inject
    constructor() : AuthRepository {
        @Volatile
        var beginLoginUrl: String = DEFAULT_AUTHORIZATION_URL

        @Volatile
        var beginLoginFailure: Throwable? = null

        override suspend fun beginLogin(handle: String): Result<String> {
            beginLoginFailure?.let { return Result.failure(it) }
            return Result.success(beginLoginUrl)
        }

        override suspend fun completeLogin(redirectUri: String): Result<Unit> = Result.success(Unit)

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        companion object {
            const val DEFAULT_AUTHORIZATION_URL: String =
                "https://bsky.social/oauth/authorize?client_id=test&state=abc123"
        }
    }
