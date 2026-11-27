package net.kikin.nubecita.core.auth.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.AttestationTokenProvider
import net.kikin.nubecita.core.auth.DefaultSessionLifetimeProvider
import net.kikin.nubecita.core.auth.SessionLifetimeProvider
import net.kikin.nubecita.core.auth.TokenMediationConfig
import java.util.Optional
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/**
 * Token mediation is optional by construction: a graph that provides neither
 * a mediator URL nor an attestation source (the instrumented-test graphs of
 * other modules, for example) gets the direct path and needs no extra
 * bindings. `:app` supplies both.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TokenMediationModule {
    @BindsOptionalOf
    @OAuthMediatorUrl
    abstract fun optionalMediatorUrl(): String

    @BindsOptionalOf
    abstract fun optionalAttestationTokenProvider(): AttestationTokenProvider

    @Binds
    @Singleton
    abstract fun bindSessionLifetimeProvider(impl: DefaultSessionLifetimeProvider): SessionLifetimeProvider

    companion object {
        @Provides
        @Singleton
        fun provideTokenMediationConfig(
            @OAuthMediatorUrl url: Optional<String>,
        ): TokenMediationConfig = TokenMediationConfig.from(url.getOrNull().orEmpty())
    }
}
