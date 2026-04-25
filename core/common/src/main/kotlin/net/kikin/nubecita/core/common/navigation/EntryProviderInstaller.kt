package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * One-line typealias for a function that registers `entry<...>` calls on
 * a Nav 3 [EntryProviderScope]. Feature modules `@Provides @IntoSet` an
 * instance of this from their `:impl` Hilt module; `:app` collects the set
 * via a Hilt `EntryPoint` and invokes every member inside `NavDisplay`'s
 * `entryProvider { }` block.
 *
 * Lives in `:core:common` (rather than `:app`) so feature modules can
 * import it without taking an illegal back-dep on `:app`. Earmark for
 * relocation to a future `:core:navigation` module if it grows neighbours
 * (deep-link router, scene strategy registry, typed NavKey sealed root).
 *
 * Reference: Now in Android `nav3-recipes` modular Hilt sample.
 */
typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit
