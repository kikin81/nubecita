package net.kikin.nubecita.navigation

import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.Main
import net.kikin.nubecita.core.common.navigation.StartDestination
import javax.inject.Singleton

/**
 * `:app`-side provider for the [StartDestination]-qualified [NavKey] that
 * `:core:common`'s `DefaultNavigator` seeds its back stack with. Today the
 * app starts at [Main]; when `nubecita-30c` adds auth-gated routing, this
 * provider becomes a small computation over session state (Login if no
 * session, Main if signed in).
 */
@Module
@InstallIn(SingletonComponent::class)
object StartDestinationModule {
    @Provides
    @Singleton
    @StartDestination
    fun provideStartDestination(): NavKey = Main
}
