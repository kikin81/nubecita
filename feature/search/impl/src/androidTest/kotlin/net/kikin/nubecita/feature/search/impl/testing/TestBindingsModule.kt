package net.kikin.nubecita.feature.search.impl.testing

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthScope
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.feature.search.api.Search
import javax.inject.Singleton

/**
 * Graph-completion shims for `:feature:search:impl`'s instrumentation
 * tests. Every binding below would normally be supplied by `:app` at
 * runtime, but `:app`'s modules aren't on the classpath when running
 * `:feature:search:impl/src/androidTest/`, so Hilt fails the test
 * component validation before any test method runs.
 *
 * What's stubbed and why:
 *
 *  - **`@StartDestination NavKey`**: required by `DefaultNavigator` in
 *    `:core:common`. `Navigator` is reachable from the Hilt
 *    `ViewModelMap` transitively through any feature ViewModel that
 *    might inject it; even if our tests don't use it, the test
 *    component validates the full ViewModelMap chain. Returns [Search]
 *    (the feature under test) as the seed since tests never read the
 *    seeded back stack.
 *  - **`@OAuthClientMetadataUrl` / `@OAuthScope` `String`**: required
 *    by `:core:auth`'s `AtOAuthModule.provideAtOAuth`. Transitively
 *    reachable through `SearchTypeaheadViewModel` →
 *    `ActorTypeaheadRepository` → `XrpcClientProvider` → `AtOAuth`. We
 *    don't run the typeahead VM in vrba.9's tap-through tests, but
 *    Hilt's ViewModelMap pulls in every `@HiltViewModel` in the module.
 *    Production values live in `:app`'s `OAuthConfigModule`; here we
 *    return empty strings because no test path constructs the real
 *    `AtOAuth` (the typeahead VM is never instantiated).
 *
 * Uses `@InstallIn(SingletonComponent::class)` rather than
 * `@TestInstallIn` because there's no production binding visible from
 * this module to replace — these are graph-completion shims, not swaps.
 * Mirrors the shape of `:feature:feed:impl`'s
 * `TestStartDestinationModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestBindingsModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = Search

    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun provideOAuthClientMetadataUrl(): String = ""

    @Provides
    @Singleton
    @OAuthScope
    fun provideOAuthScope(): String = ""
}
