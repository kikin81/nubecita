package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.TokenMediationConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object TokenMediationModule {
    @Provides
    @Singleton
    fun provideTokenMediationConfig(
        @OAuthMediatorUrl url: String,
    ): TokenMediationConfig = TokenMediationConfig.from(url)
}
