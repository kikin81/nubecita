package net.kikin.nubecita.feature.feeds.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the deferred Feeds-management screen.
 *
 * Distinct from `:feature:feed:api`'s `Feed` key — `Feed` is the Following
 * timeline tab, whereas `Feeds` is the (not-yet-built) screen for managing
 * which feeds are pinned. Lives in `:feature:feeds:api` so the Feed chip
 * row's trailing button can push it via
 * `LocalMainShellNavState.current.add(Feeds)` without depending on any
 * `:impl` module.
 *
 * Per the `:api`-first stub convention, `:app` currently registers a
 * placeholder Composable for this key under a `@MainShell`
 * `EntryProviderInstaller`. The full `:feature:feeds:impl` lands later in
 * its own epic, at which point the `:app`-side placeholder provider is
 * deleted in favour of the impl module's `@MainShell` provider — no
 * bridging artifacts.
 */
@Serializable
data object Feeds : NavKey
