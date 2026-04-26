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
