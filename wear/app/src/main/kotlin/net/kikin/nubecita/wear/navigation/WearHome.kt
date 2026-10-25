package net.kikin.nubecita.wear.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Start destination. Later epics replace its body with the feed (W2). */
@Serializable
data object WearHome : NavKey
