package net.kikin.nubecita.feature.login.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.push.di.PushAppConfig
import net.kikin.nubecita.core.push.di.PushSmallIconRes
import javax.inject.Singleton

/**
 * Graph-completion shims for `:feature:login:impl`'s `@HiltAndroidTest` graph.
 *
 * `TestAuthBindingsModule` replaces the production `AuthBindingsModule` but only
 * rebinds the two interfaces `LoginViewModel` historically injected. As the login
 * graph grew (session observation + FCM registration on the sign-in path), the
 * test component began requiring `SessionStateProvider`, `XrpcClientProvider`, and
 * `:core:push`'s `PushAppConfig` / `@PushSmallIconRes` — all supplied by `:app`
 * at runtime and therefore absent here. These stand-ins complete the graph so
 * validation passes; the login-screen tests never exercise any of them.
 *
 * `SessionStateProvider` gets a real fake (a `SignedOut` `StateFlow`) so any
 * incidental collection at VM init is well-defined; `XrpcClientProvider.authenticated()`
 * throws, since no login-screen test path calls it (and mockk-android isn't on this
 * module's androidTest classpath).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestLoginGraphCompletionModule {
    @Provides
    @Singleton
    fun provideSessionStateProvider(): SessionStateProvider =
        object : SessionStateProvider {
            override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedOut)
            override val isSelfHosted: Flow<Boolean> = flowOf(false)

            override suspend fun refresh() = Unit
        }

    @Provides
    @Singleton
    fun provideXrpcClientProvider(): XrpcClientProvider =
        object : XrpcClientProvider {
            override suspend fun authenticated() = error("XrpcClientProvider.authenticated() must not be called in login-screen tests")
        }

    @Provides
    @Singleton
    fun providePushAppConfig(): PushAppConfig = PushAppConfig(applicationId = "net.kikin.nubecita")

    @Provides
    @Singleton
    @PushSmallIconRes
    fun providePushSmallIconRes(): Int = 0
}
