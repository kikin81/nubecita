package net.kikin.nubecita.core.widgetsync.di

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthRedirectUri
import net.kikin.nubecita.core.auth.di.OAuthScope
import net.kikin.nubecita.core.common.navigation.StartDestination
import javax.inject.Singleton

/**
 * Graph-completion shims for `:core:widget-sync`'s `@HiltAndroidTest` graph.
 *
 * The worker test constructs the full `SingletonComponent`, which validates every
 * binding reachable from it — including `:core:common`'s `DefaultNavigator`
 * (`@StartDestination NavKey`) and `:core:auth`'s `AtOAuthModule` (the three OAuth
 * config strings). Those are supplied by `:app` at runtime, but `:app`'s modules
 * aren't on the classpath here, so the test component fails validation before any
 * test runs even though `WidgetRefreshWorker` never touches nav or OAuth.
 *
 * `@InstallIn` (not `@TestInstallIn`) because there's no production binding visible
 * from this module to replace — these are graph-completion shims, not swaps.
 * Mirrors `:feature:*:impl`'s `TestOAuthConfigModule` / `TestStartDestinationModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestGraphCompletionModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = TestStartRoute

    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun provideOAuthClientMetadataUrl(): String = ""

    @Provides
    @Singleton
    @OAuthRedirectUri
    fun provideOAuthRedirectUri(): String = ""

    @Provides
    @Singleton
    @OAuthScope
    fun provideOAuthScope(): String = ""
}

/** A test-only [NavKey] to seed [StartDestination]; never read by the worker test. */
private data object TestStartRoute : NavKey
