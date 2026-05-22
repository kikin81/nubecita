package net.kikin.nubecita.navigation

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.DeepLinkRouter
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
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

    /**
     * Hilt-multibound deep-link matchers, contributed by `:feature:*:impl`
     * modules via `@Provides @IntoSet`. `MainActivity.handleIntent`
     * iterates this set on each incoming `Intent`; the first non-null
     * match is published to the [DeepLinkRouter] for `MainShell` to
     * push onto the inner back stack. Matchers MUST be registered in
     * declaration order from most-specific to least-specific so the
     * `pathSegments.size` gate in `UriDeepLinkMatcher.matchUri` cleanly
     * short-circuits the wrong matcher before regex evaluation
     * (decision: nubecita-kf6k.4).
     */
    fun deepLinkMatchers(): Set<@JvmSuppressWildcards NavKeyDeepLinkMatcher>

    fun deepLinkRouter(): DeepLinkRouter
}
