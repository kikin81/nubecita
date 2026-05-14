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
 * and `scope` values are sourced from `:app`'s
 * [OAuthClientMetadataUrl]- / [OAuthScope]-qualified `String` bindings
 * (both reading from `BuildConfig`) so dev → prod swaps and per-flavor
 * capability changes are build-variant edits rather than code edits
 * inside `:core:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AtOAuthModule {
    @Provides
    @Singleton
    fun provideAtOAuth(
        @OAuthClientMetadataUrl clientMetadataUrl: String,
        @OAuthScope scope: String,
        sessionStore: OAuthSessionStore,
        httpClient: HttpClient,
    ): AtOAuth =
        AtOAuth(
            clientMetadataUrl = clientMetadataUrl,
            sessionStore = sessionStore,
            httpClient = httpClient,
            scope = scope,
        )
}
