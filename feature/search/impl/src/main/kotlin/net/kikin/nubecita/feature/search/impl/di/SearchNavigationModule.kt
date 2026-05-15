package net.kikin.nubecita.feature.search.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.search.impl.SearchScreen

/**
 * Registers the Search tab-home entry on the `@MainShell`-qualified
 * `EntryProviderInstaller` multibinding so `MainShell` can resolve the
 * `Search` NavKey to a real Composable. Replaces the `:app`-side
 * placeholder provider in `MainShellPlaceholderModule` (deleted in
 * the same commit).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SearchNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideSearchEntries(): EntryProviderInstaller =
        {
            entry<Search> {
                SearchScreen()
            }
        }
}
