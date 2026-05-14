package net.kikin.nubecita.feature.chats.impl.testing

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.feature.chats.api.Chats
import javax.inject.Singleton

/**
 * Test-only provider for the [StartDestination]-qualified [NavKey] that
 * `:core:common`'s `DefaultNavigator` requires. The production binding
 * lives in `:app/src/main/.../StartDestinationModule.kt` and isn't on
 * the classpath when running `:feature:chats:impl/src/androidTest/`,
 * so Hilt fails to construct `Navigator` without this stand-in. Same
 * pattern as `:feature:composer:impl/src/androidTest/.../TestStartDestinationModule.kt`.
 *
 * Returns the [Chats] tab key (the feature under test) since
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
    fun provideStartDestination(): NavKey = Chats
}
