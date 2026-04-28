package net.kikin.nubecita.navigation

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.Navigator
import net.kikin.nubecita.core.common.navigation.OuterShell

/**
 * Hilt entry point that exposes the `EntryProviderInstaller` multibindings
 * to the two `NavDisplay` composables. Each set is partitioned by
 * qualifier:
 *
 * - `@OuterShell` — entries for the outer `NavDisplay` in
 *   `MainNavigation` (Splash → Login → Main wrapper).
 * - `@MainShell` — entries for the inner `NavDisplay` hosted inside
 *   `MainShell`, covering the four top-level tabs and any sub-routes
 *   pushed onto a tab's stack.
 *
 * `:feature:*:impl` modules contribute via `@Provides @IntoSet` with the
 * appropriate qualifier annotation; `:app` collects each set and invokes
 * every member inside the matching `NavDisplay`'s `entryProvider { }`
 * block. Composables can't use constructor injection — this entry point
 * is the bridge.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigationEntryPoint {
    fun navigator(): Navigator

    @OuterShell
    fun outerEntryProviderInstallers(): Set<@JvmSuppressWildcards EntryProviderInstaller>

    @MainShell
    fun mainShellEntryProviderInstallers(): Set<@JvmSuppressWildcards EntryProviderInstaller>
}
