package net.kikin.nubecita.core.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import net.kikin.nubecita.core.auth.EncryptedOAuthSessionStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindOAuthSessionStore(impl: EncryptedOAuthSessionStore): OAuthSessionStore
}
