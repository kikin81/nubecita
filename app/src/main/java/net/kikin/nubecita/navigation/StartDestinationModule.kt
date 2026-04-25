package net.kikin.nubecita.navigation

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.Splash
import net.kikin.nubecita.core.common.navigation.StartDestination
import javax.inject.Singleton

/**
 * `:app`-side provider for the [StartDestination]-qualified [NavKey] that
 * `:core:common`'s `DefaultNavigator` seeds its back stack with.
 *
 * The seed is [Splash] — an empty composable destination that sits under
 * the system [androidx.core.splashscreen.SplashScreen] until
 * `SessionStateProvider` resolves. `MainActivity`'s reactive collector
 * then calls `navigator.replaceTo(...)` with the appropriate post-bootstrap
 * destination (`Main` if signed in, `Login` if signed out).
 *
 * This is the single place that decides "where does the app open" — when
 * onboarding lands as a feature module, it can either short-circuit at
 * the `Splash` predicate inside MainActivity's collector or replace this
 * provider's return value (e.g. `Onboarding` if first-install).
 */
@Module
@InstallIn(SingletonComponent::class)
object StartDestinationModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = Splash
}
