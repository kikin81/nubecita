package net.kikin.nubecita.feature.onboarding.impl.testing

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import javax.inject.Singleton

/**
 * Graph-completion shim for `:core:common`'s `DefaultNavigator`. The
 * production `@StartDestination NavKey` lives in `:app` and isn't on
 * the classpath when running `:feature:onboarding:impl/src/androidTest/`,
 * so Hilt fails to construct the Singleton-scoped `Navigator` without
 * this stand-in. Tests use a `RecordingNavigator` exposed via
 * `LocalAppNavigator` so they never observe the Hilt-bound navigator's
 * back stack — the shim only exists to complete the DI graph.
 *
 * Returns [Onboarding] (the feature under test) for parity with the
 * pattern in `:feature:login:impl/.../TestStartDestinationModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestStartDestinationModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = Onboarding
}
