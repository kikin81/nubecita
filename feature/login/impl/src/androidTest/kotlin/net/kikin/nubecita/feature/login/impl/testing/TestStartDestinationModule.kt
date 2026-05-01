package net.kikin.nubecita.feature.login.impl.testing

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.feature.login.api.Login
import javax.inject.Singleton

/**
 * Test-only provider for the [StartDestination]-qualified [NavKey] that
 * `:core:common`'s `DefaultNavigator` requires. The production binding
 * lives in `:app/src/main/.../StartDestinationModule.kt` and isn't on
 * the classpath when running `:feature:login:impl/src/androidTest/`,
 * so Hilt fails to construct `Navigator` without this stand-in.
 *
 * Returns [Login] (the feature under test) since [DefaultNavigator]
 * uses this only to seed its back stack, and the test never observes
 * the back stack — any non-null [NavKey] would do.
 *
 * Uses `@InstallIn(SingletonComponent::class)` rather than
 * `@TestInstallIn` because there's no production binding visible from
 * this module to replace; it's a graph-completion shim, not a swap.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestStartDestinationModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = Login
}
