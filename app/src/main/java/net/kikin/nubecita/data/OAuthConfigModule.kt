package net.kikin.nubecita.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import javax.inject.Singleton

/**
 * `:app`-side provider for the OAuth client metadata URL. Reads the
 * BuildConfig field set in `app/build.gradle.kts`, so dev / prod / future
 * flavors can supply different URLs without touching `:core:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
object OAuthConfigModule {
    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun provideOAuthClientMetadataUrl(): String = BuildConfig.OAUTH_CLIENT_METADATA_URL
}
