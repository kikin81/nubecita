package net.kikin.nubecita.feature.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Which list [ProfileConnections] opens on. */
@Serializable
enum class ProfileConnectionsTab {
    Followers,
    Following,
}

/**
 * Who follows [actor], and who [actor] follows.
 *
 * [title] and the counts travel with the route because the list endpoints
 * return `ProfileView`, which has no counts, so they are a snapshot of the
 * profile the viewer came from.
 *
 * @param actor a handle or DID, passed opaquely to the list endpoints.
 */
@Serializable
data class ProfileConnections(
    val actor: String,
    val initialTab: ProfileConnectionsTab,
    val title: String,
    val followersCount: Long,
    val followsCount: Long,
) : NavKey
