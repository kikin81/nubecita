package net.kikin.nubecita.feature.starterpack.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the starter-pack screen: a pack's header,
 * its members (each with a Follow button) and its feeds.
 *
 * [uri] is the pack's `at://{did}/app.bsky.graph.starterpack/{rkey}` URI. The
 * screen loads everything from it, so a key restored after process death needs
 * nothing else. Pushed onto the active tab's stack from any surface that lists
 * packs; lives in `:feature:starterpack:api` so those hosts depend on the key
 * alone, never on `:feature:starterpack:impl`.
 */
@Serializable
data class StarterPack(
    val uri: String,
) : NavKey
