package net.kikin.nubecita.core.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.DefaultAuthRepository
import net.kikin.nubecita.core.auth.DefaultOAuthRedirectBroker
import net.kikin.nubecita.core.auth.EncryptedOAuthSessionStore
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindOAuthSessionStore(impl: EncryptedOAuthSessionStore): OAuthSessionStore

    @Binds
    @Singleton
    internal abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    internal abstract fun bindOAuthRedirectBroker(impl: DefaultOAuthRedirectBroker): OAuthRedirectBroker
}
