package net.kikin.nubecita.feature.search.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Hashtag screen: Top / Latest posts
 * tagged [tag].
 *
 * - [tag] is the facet value with no leading `#` (a cashtag keeps its `$`,
 *   e.g. `$BTC`).
 * - [author] (handle or DID) is the account whose post the tag was tapped in.
 *   When set, the screen offers a "From @author" filter; it does NOT filter by
 *   default, matching the official client's tap behavior.
 *
 * Pushed onto the active tab's stack from any post-text host. Lives in
 * `:feature:search:api` so hosts depend on the key alone, never on
 * `:feature:search:impl`.
 */
@Serializable
data class Hashtag(
    val tag: String,
    val author: String? = null,
) : NavKey
