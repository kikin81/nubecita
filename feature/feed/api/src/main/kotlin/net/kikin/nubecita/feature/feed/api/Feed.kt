package net.kikin.nubecita.feature.feed.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Following timeline screen.
 *
 * Lives in `:feature:feed:api` so cross-feature modules that need to push
 * `Feed` onto the back stack can depend on `:feature:feed:api` alone —
 * never on `:feature:feed:impl`. The key carries no arguments today; if
 * it grows arguments later (e.g., a saved scroll position), the
 * consumer-side `hiltViewModel()` call site will need to switch to
 * assisted injection.
 */
@Serializable
data object Feed : NavKey

/**
 * Navigation 3 destination key for a single custom/generator feed screen.
 *
 * Pushed when the user opens a specific feed from the tab bar or the
 * Explore/Discover surface. [feedUri] is the AT-URI of the generator or
 * list feed (`at://…`). [displayName] is the feed's display name as of the
 * moment of navigation; the screen refreshes the canonical name from the
 * network and should not persist the nav-time value.
 *
 * The URI is intentionally opaque to `:feature:feed:api` — it is never
 * inspected or parsed here. Consumer screens in `:feature:feed:impl` pass it
 * through to the repository layer verbatim.
 */
@Serializable
data class FeedView(
    val feedUri: String,
    val displayName: String? = null,
) : NavKey
