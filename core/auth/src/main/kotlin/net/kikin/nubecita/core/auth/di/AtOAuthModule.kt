package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.ktor.client.HttpClient
import javax.inject.Singleton

/**
 * Hilt provider for the OAuth flow orchestrator. The `clientMetadataUrl`
 * is sourced from `:app`'s [OAuthClientMetadataUrl]-qualified `String`
 * binding (BuildConfig) so the dev → prod URL swap is a build-variant
 * change, not a code edit inside `:core:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AtOAuthModule {
    @Provides
    @Singleton
    fun provideAtOAuth(
        @OAuthClientMetadataUrl clientMetadataUrl: String,
        sessionStore: OAuthSessionStore,
        httpClient: HttpClient,
    ): AtOAuth =
        AtOAuth(
            clientMetadataUrl = clientMetadataUrl,
            sessionStore = sessionStore,
            httpClient = httpClient,
        )
}
