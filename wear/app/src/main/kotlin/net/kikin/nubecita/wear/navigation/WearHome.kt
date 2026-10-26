package net.kikin.nubecita.wear.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Start destination: the selected synced feed. */
@Serializable
data object WearHome : NavKey

/** Choose which synced feed [WearHome] shows. */
@Serializable
data object WearFeedPicker : NavKey

/** One post of the selected feed, by AT-URI. */
@Serializable
data class WearPost(
    val uri: String,
) : NavKey
