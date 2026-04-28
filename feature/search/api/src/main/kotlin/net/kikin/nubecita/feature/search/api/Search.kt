package net.kikin.nubecita.feature.search.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Search top-level tab.
 *
 * Lives in `:feature:search:api` so cross-feature modules that need to push
 * `Search` onto the back stack can depend on `:feature:search:api` alone —
 * never on `:feature:search:impl` (which does not exist yet; until the
 * search feature epic lands, `:app` registers a placeholder Composable for
 * this key). The key carries no arguments today; if it grows arguments
 * later, the consumer-side `hiltViewModel()` call site will need to switch
 * to assisted injection.
 */
@Serializable
data object Search : NavKey
