package net.kikin.nubecita.feature.composer.impl.testing

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import javax.inject.Singleton

/**
 * Test-only provider for the [StartDestination]-qualified [NavKey] that
 * `:core:common`'s `DefaultNavigator` requires. The production binding
 * lives in `:app/src/main/.../StartDestinationModule.kt` and isn't on
 * the classpath when running `:feature:composer:impl/src/androidTest/`,
 * so Hilt fails to construct `Navigator` without this stand-in. Same
 * pattern as `:feature:feed:impl/src/androidTest/.../TestStartDestinationModule.kt`.
 *
 * Returns [ComposerRoute] (the feature under test) since
 * `DefaultNavigator` uses this only to seed its back stack and the
 * test never observes the back stack — any non-null `NavKey` would
 * do.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestStartDestinationModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = ComposerRoute(replyToUri = null)
}
