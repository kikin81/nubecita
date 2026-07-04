package net.kikin.nubecita.feature.notifications.impl.testing

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import javax.inject.Singleton

/**
 * Test-only provider for the [StartDestination]-qualified [NavKey] that
 * `:core:common`'s `DefaultNavigator` requires. The production binding lives in
 * `:app/src/main/.../StartDestinationModule.kt` and isn't on the classpath when
 * running `:feature:notifications:impl/src/androidTest/`, so the `@HiltAndroidTest`
 * graph fails to construct `Navigator` without this stand-in.
 *
 * Returns [NotificationsTab] (the feature under test); `DefaultNavigator` only uses
 * it to seed its back stack, which these tests never observe — any non-null
 * `NavKey` would do. `@InstallIn` (not `@TestInstallIn`) because there's no
 * production binding visible from this module to replace — it's a graph-completion
 * shim, not a swap. Mirrors `:feature:feed:impl`'s shim of the same name.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestStartDestinationModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = NotificationsTab
}
