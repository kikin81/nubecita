package net.kikin.nubecita.feature.bookmarks.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the signed-in user's Bookmarks list.
 *
 * Pushed onto MainShell's inner back stack from the own-profile top bar
 * via `navState.add(Bookmarks)` (see Profile's nav module). Bookmarks are
 * private / own-user only, so this route is only ever reached from the
 * signed-in user's own profile — there is no `handle`/`did` argument.
 */
@Serializable
data object Bookmarks : NavKey
