package net.kikin.nubecita.navigation

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.common.navigation.DeepLinkRouter
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
import net.kikin.nubecita.core.common.navigation.Navigator
import net.kikin.nubecita.core.common.navigation.OuterShell
import net.kikin.nubecita.feature.notifications.impl.store.NotificationsUnreadCountStore

/**
 * Hilt entry point that exposes the `EntryProviderInstaller` multibindings
 * to the two `NavDisplay` composables. Each set is partitioned by
 * qualifier:
 *
 * - `@OuterShell` — entries for the outer `NavDisplay` in
 *   `MainNavigation` (Splash → Login → Main wrapper).
 * - `@MainShell` — entries for the inner `NavDisplay` hosted inside
 *   `MainShell`, covering the five top-level tabs and any sub-routes
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
     * sorts this set by `NavKeyDeepLinkMatcher.patternSpecificity`
     * descending (path-segment count, derived automatically by the
     * `uriDeepLinkMatcher(...)` factory) and scans for the first
     * non-null match. Registration order is therefore irrelevant —
     * specificity ordering is data on each matcher, not on the
     * `@Provides` declaration. See decision nubecita-kf6k.4 for the
     * source-level rationale.
     */
    fun deepLinkMatchers(): Set<@JvmSuppressWildcards NavKeyDeepLinkMatcher>

    fun deepLinkRouter(): DeepLinkRouter

    /**
     * The process-singleton unread-notifications store, populated by
     * `:feature:notifications:impl`'s `NotificationsPollingObserver` while
     * the app is foregrounded. `MainShell` collects the `StateFlow<Int>`
     * and threads it into `NavigationSuiteItem`'s `badge` slot on the
     * Notifications tab. Reaches across the `:feature:notifications:impl`
     * boundary via this entry point — Composables can't constructor-inject
     * the singleton, and `:app` already depends on the impl module for
     * the qualifier-tagged `EntryProviderInstaller` set.
     */
    fun notificationsUnreadCountStore(): NotificationsUnreadCountStore

    /**
     * The process-singleton analytics sink (`@Singleton`, bound to
     * `FirebaseAnalyticsClient` in production / `NoOpAnalyticsClient` in
     * bench). Both `NavDisplay` hosts (`MainNavigation`'s outer display and
     * `MainShell`'s inner display) reach it through this entry point so the
     * host-layer `TrackScreenViews` composable can emit a manual
     * `screen_view` for each top `NavKey`. Composables can't constructor-
     * inject, and analytics must never be observed from a ViewModel (the
     * screen-tracking concern lives at the host layer, mirroring
     * `LocalMainShellNavState` / `LocalTabReTapSignal`). See nubecita-049f.2.
     */
    fun analyticsClient(): AnalyticsClient
}
