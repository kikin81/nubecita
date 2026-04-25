package net.kikin.nubecita.navigation

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller

/**
 * Hilt entry point that exposes the `Set<EntryProviderInstaller>`
 * multibinding to `MainNavigation` — which is a Composable and can't use
 * constructor injection. Each `:feature:*:impl` module contributes its
 * own `@Provides @IntoSet` entry provider; `:app` collects the set and
 * invokes every member inside `NavDisplay`'s `entryProvider { }` block.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigationEntryPoint {
    fun entryProviderInstallers(): Set<@JvmSuppressWildcards EntryProviderInstaller>
}
