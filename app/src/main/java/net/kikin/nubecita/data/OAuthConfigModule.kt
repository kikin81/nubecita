package net.kikin.nubecita.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthRedirectUri
import net.kikin.nubecita.core.auth.di.OAuthScope
import javax.inject.Singleton

/**
 * `:app`-side providers for the OAuth configuration values that
 * `:core:auth`'s `AtOAuthModule` injects. Read from `BuildConfig` fields
 * set in `app/build.gradle.kts` so dev / prod / future flavors can supply
 * different values without touching `:core:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
object OAuthConfigModule {
    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun provideOAuthClientMetadataUrl(): String = BuildConfig.OAUTH_CLIENT_METADATA_URL

    @Provides
    @Singleton
    @OAuthRedirectUri
    fun provideOAuthRedirectUri(): String = BuildConfig.OAUTH_REDIRECT_URI

    @Provides
    @Singleton
    @OAuthScope
    fun provideOAuthScope(): String = BuildConfig.OAUTH_SCOPE
}
