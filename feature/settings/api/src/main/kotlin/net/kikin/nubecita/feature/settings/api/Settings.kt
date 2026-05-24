package net.kikin.nubecita.feature.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Settings screen.
 *
 * Pushed onto MainShell's inner back stack from the You-tab Profile
 * via `navState.add(Settings)` (see Profile's nav module). Sub-routes
 * spawned by individual Settings sections (e.g. a future "Push
 * notifications" deep-dive) declare their own NavKey types alongside
 * this one once they exist.
 */
@Serializable
data object Settings : NavKey
