package net.kikin.nubecita.core.common.navigation

import javax.inject.Qualifier

/**
 * Hilt qualifier marking an [EntryProviderInstaller] as belonging to the
 * outer `NavDisplay` — the one in `:app/Navigation.kt` that owns the pre-
 * `MainShell` lifecycle (`Splash → Login → Main`).
 *
 * `:feature:*:impl` modules whose entries live above the adaptive shell
 * (login, future onboarding, etc.) annotate their `@Provides @IntoSet`
 * declarations with `@OuterShell`. `:app`'s `NavigationEntryPoint`
 * exposes a parallel `@OuterShell` accessor that returns the qualified set.
 *
 * Pairs with [MainShell]; one or the other must be present on every
 * `EntryProviderInstaller` provider (an unqualified provider will be
 * dropped by both accessors).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OuterShell

/**
 * Hilt qualifier marking an [EntryProviderInstaller] as belonging to the
 * inner `NavDisplay` hosted by `MainShell` — the four top-level tabs
 * (`Feed | Search | Chats | You`) and any sub-routes that push onto a
 * tab's stack (e.g. `Profile(handle)`).
 *
 * `:feature:*:impl` modules whose entries live inside the adaptive shell
 * annotate their `@Provides @IntoSet` declarations with `@MainShell`.
 * `:app`'s `NavigationEntryPoint` exposes a parallel `@MainShell` accessor
 * that returns the qualified set; `MainShell` invokes those installers
 * inside its inner `NavDisplay`'s `entryProvider { }` block.
 *
 * Pairs with [OuterShell]; one or the other must be present on every
 * `EntryProviderInstaller` provider (an unqualified provider will be
 * dropped by both accessors).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainShell
