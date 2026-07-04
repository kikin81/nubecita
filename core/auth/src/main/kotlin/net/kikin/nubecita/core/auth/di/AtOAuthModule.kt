package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.ktor.client.HttpClient
import net.kikin.nubecita.core.auth.SessionPersistFailureReporter
import javax.inject.Singleton

/**
 * Hilt provider for the OAuth flow orchestrator. The `clientMetadataUrl`,
 * `redirectUri`, and `scope` values are sourced from `:app`'s
 * [OAuthClientMetadataUrl]- / [OAuthRedirectUri]- / [OAuthScope]-qualified
 * `String` bindings (all reading from `BuildConfig`) so dev → prod swaps
 * and per-flavor capability changes are build-variant edits rather than
 * code edits inside `:core:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AtOAuthModule {
    @Provides
    @Singleton
    fun provideAtOAuth(
        @OAuthClientMetadataUrl clientMetadataUrl: String,
        @OAuthRedirectUri redirectUri: String,
        @OAuthScope scope: String,
        sessionStore: OAuthSessionStore,
        httpClient: HttpClient,
        persistFailureReporter: SessionPersistFailureReporter,
    ): AtOAuth =
        AtOAuth(
            clientMetadataUrl = clientMetadataUrl,
            redirectUri = redirectUri,
            sessionStore = sessionStore,
            httpClient = httpClient,
            scope = scope,
            // Fires when the SDK rotated the refresh token but couldn't persist
            // the new session even after its retry — the precursor of a
            // cold-start invalid_grant logout (epic nubecita-09xt).
            onSessionPersistFailure = persistFailureReporter::report,
        )
}
