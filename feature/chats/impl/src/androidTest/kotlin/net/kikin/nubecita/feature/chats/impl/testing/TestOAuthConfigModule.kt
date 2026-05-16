package net.kikin.nubecita.feature.chats.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthRedirectUri
import net.kikin.nubecita.core.auth.di.OAuthScope
import javax.inject.Singleton

/**
 * Test-only providers for the OAuth configuration values that
 * `:core:auth`'s `AtOAuthModule.provideAtOAuth` requires. The production
 * bindings live in `:app/src/main/.../OAuthConfigModule.kt` and read
 * `BuildConfig.OAUTH_CLIENT_METADATA_URL` / `BuildConfig.OAUTH_REDIRECT_URI`
 * / `BuildConfig.OAUTH_SCOPE`, none of which is on the classpath when running
 * `:feature:chats:impl/src/androidTest/`.
 *
 * Returns placeholder values — the chat instrumentation test never
 * opens an OAuth flow, but the singleton graph still needs to be able
 * to construct `AtOAuth` because `XrpcClientProvider` (transitively
 * needed by `DefaultChatRepository`) injects it.
 *
 * Uses `@InstallIn(SingletonComponent::class)` rather than
 * `@TestInstallIn` because the production module isn't visible from
 * this module's androidTest classpath; this is a graph-completion
 * shim, not a swap. Mirrors `:feature:composer:impl`'s
 * `TestOAuthConfigModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestOAuthConfigModule {
    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun provideOAuthClientMetadataUrl(): String = "https://example.invalid/oauth/client-metadata.json"

    @Provides
    @Singleton
    @OAuthRedirectUri
    fun provideOAuthRedirectUri(): String = "app.example:/oauth-redirect"

    @Provides
    @Singleton
    @OAuthScope
    fun provideOAuthScope(): String = "atproto transition:generic"
}
