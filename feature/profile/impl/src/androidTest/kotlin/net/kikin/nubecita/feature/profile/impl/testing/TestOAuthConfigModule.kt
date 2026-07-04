package net.kikin.nubecita.feature.profile.impl.testing

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
 *
 * Mirrors `:feature:feed:impl`'s shim of the same name (package, visibility, and
 * placeholder values) so the test-only DI shims stay consistent across modules.
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
