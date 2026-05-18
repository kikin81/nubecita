package net.kikin.nubecita.feature.feed.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthRedirectUri
import net.kikin.nubecita.core.auth.di.OAuthScope
import javax.inject.Singleton

/**
 * Test-only providers for the OAuth configuration strings that
 * `:core:auth`'s `AtOAuthModule.provideAtOAuth` requires. The production
 * bindings live in `:app/.../OAuthConfigModule.kt` and read from
 * `BuildConfig.OAUTH_*`, none of which is on the classpath when running
 * `:feature:feed:impl/src/androidTest/`.
 *
 * Returns placeholder values — the instrumentation tests in this module
 * never open an OAuth flow, but the singleton graph still needs to
 * construct `AtOAuth` because `XrpcClientProvider` is transitively
 * needed by `FeedViewModel`'s `PostInteractionsCache → LikeRepostRepository`
 * dependency chain.
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
