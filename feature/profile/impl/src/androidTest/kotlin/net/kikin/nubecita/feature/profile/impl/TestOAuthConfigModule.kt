package net.kikin.nubecita.feature.profile.impl

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthRedirectUri
import net.kikin.nubecita.core.auth.di.OAuthScope
import javax.inject.Singleton

/**
 * Test-only OAuth config for this module's `@HiltAndroidTest` graph.
 *
 * `:core:auth`'s `AtOAuthModule` injects these three qualified strings, which in
 * production come from `:app`'s `OAuthConfigModule` (backed by `:app` `BuildConfig`
 * fields). A feature module's isolated instrumented-test Hilt graph has no `:app`,
 * so without this the whole test `SingletonComponent` fails to compile with a
 * `MissingBinding` for `@OAuthClientMetadataUrl` / `@OAuthRedirectUri` / `@OAuthScope`
 * — even though these tests construct their ViewModel manually. Values are inert
 * dummies; no OAuth flow runs in these tests (the repository is faked/mocked).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestOAuthConfigModule {
    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun clientMetadataUrl(): String = "https://example.test/client-metadata.json"

    @Provides
    @Singleton
    @OAuthRedirectUri
    fun redirectUri(): String = "https://example.test/oauth-redirect"

    @Provides
    @Singleton
    @OAuthScope
    fun scope(): String = "atproto transition:generic"
}
