package net.kikin.nubecita.core.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.DefaultAuthRepository
import net.kikin.nubecita.core.auth.DefaultOAuthRedirectBroker
import net.kikin.nubecita.core.auth.DefaultSessionStateProvider
import net.kikin.nubecita.core.auth.DefaultXrpcClientProvider
import net.kikin.nubecita.core.auth.EncryptedOAuthSessionStore
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import javax.inject.Singleton

/**
 * Hilt bindings for `:core:auth`'s repository / state-provider interfaces.
 *
 * The class itself is publicly addressable (rather than `internal`) so
 * downstream feature modules' instrumentation tests can swap individual
 * bindings via `@TestInstallIn(replaces = [AuthBindingsModule::class])`.
 * Kotlin's `internal` modifier is per-Gradle-module, so an internal
 * binding module would be invisible to `:feature:*:impl/src/androidTest/`
 * and the swap target wouldn't compile. The bound implementations remain
 * `internal` — only the module class itself is addressable.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindOAuthSessionStore(impl: EncryptedOAuthSessionStore): OAuthSessionStore

    @Binds
    @Singleton
    internal abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    internal abstract fun bindOAuthRedirectBroker(impl: DefaultOAuthRedirectBroker): OAuthRedirectBroker

    @Binds
    @Singleton
    internal abstract fun bindSessionStateProvider(impl: DefaultSessionStateProvider): SessionStateProvider

    @Binds
    @Singleton
    internal abstract fun bindXrpcClientProvider(impl: DefaultXrpcClientProvider): XrpcClientProvider
}
