package net.kikin.nubecita.feature.profile.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.mockk.mockk
import kotlinx.coroutines.delay
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.auth.di.AuthBindingsModule
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton

/**
 * Replaces the production `AuthBindingsModule` for instrumentation
 * tests in `:feature:profile:impl`. Provides:
 *
 * - `AuthRepository` as a [FakeAuthRepository] that counts signOut()
 *   invocations and can be configured to return success or a specified
 *   failure.
 * - All other interfaces `AuthBindingsModule` binds as `mockk(relaxed
 *   = true)` so the Hilt graph stays valid for any consumer that
 *   transitively needs them.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthBindingsModule::class],
)
internal object TestAuthRepositoryModule {
    @Singleton
    @Provides
    fun provideAuthRepository(): AuthRepository = FakeAuthRepository.shared

    @Singleton
    @Provides
    fun provideOAuthSessionStore(): OAuthSessionStore = mockk(relaxed = true)

    @Singleton
    @Provides
    fun provideOAuthRedirectBroker(): OAuthRedirectBroker = mockk(relaxed = true)

    @Singleton
    @Provides
    fun provideSessionStateProvider(): SessionStateProvider = mockk(relaxed = true)

    @Singleton
    @Provides
    fun provideXrpcClientProvider(): XrpcClientProvider = mockk(relaxed = true)
}

/**
 * In-memory [AuthRepository] with controllable signOut() behavior.
 * Singleton-scoped via the [TestAuthRepositoryModule] above so the
 * test class can read the call count and adjust the next-return value.
 */
internal class FakeAuthRepository : AuthRepository {
    val signOutCalls = AtomicInteger(0)

    @Volatile var nextSignOutResult: Result<Unit> = Result.success(Unit)

    @Volatile var signOutDelayMs: Long = 0

    override suspend fun beginLogin(handle: String): Result<String> = Result.failure(UnsupportedOperationException("Login not exercised in this test"))

    override suspend fun completeLogin(redirectUri: String): Result<Unit> = Result.failure(UnsupportedOperationException("Login not exercised in this test"))

    override suspend fun signOut(): Result<Unit> {
        signOutCalls.incrementAndGet()
        if (signOutDelayMs > 0) delay(signOutDelayMs)
        return nextSignOutResult
    }

    fun reset() {
        signOutCalls.set(0)
        nextSignOutResult = Result.success(Unit)
        signOutDelayMs = 0
    }

    companion object {
        // Singleton so the test class can read it.
        val shared = FakeAuthRepository()
    }
}
