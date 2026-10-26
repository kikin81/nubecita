package net.kikin.nubecita.wear.di

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.StartDestination
import net.kikin.nubecita.wear.navigation.WearHome

/**
 * `:core:common` (MVI base, coroutine scope, clock) also contributes the shared
 * `Navigator`, which needs the app's start destination. On the watch that is
 * [WearHome]; the watch's own back stack lives in `NubecitaWearApp`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WearNavigationModule {
    @Provides
    @StartDestination
    fun provideStartDestination(): NavKey = WearHome
}
