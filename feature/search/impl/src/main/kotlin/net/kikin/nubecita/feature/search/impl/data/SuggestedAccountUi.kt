package net.kikin.nubecita.feature.search.impl.data

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * UI-ready projection of an `app.bsky.actor.defs#profileView` from the
 * `app.bsky.actor.getSuggestions` surface. Drives the suggested-people
 * section in the Discover tab.
 *
 * - [isFollowing] / [followUri]: derived from `viewer.following` — the
 *   follow-record AtUri is present iff the viewer follows this account.
 * - [mutualsCount]: from `viewer.knownFollowers.count` (the total count
 *   of viewer-following accounts who also follow this actor; may be higher
 *   than [mutualAvatarUrls].size when only a few are surfaced).
 * - [mutualAvatarUrls]: the first few mutual followers' avatar URLs, for
 *   the overlap-avatar stack; may be shorter than [mutualsCount].
 *
 * `displayName` is nullable — blank upstream display names collapse to null
 * at the mapping layer (same contract as [net.kikin.nubecita.data.models.ActorUi]).
 */
@Immutable
internal data class SuggestedAccountUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val isFollowing: Boolean,
    val followUri: String?,
    val mutualsCount: Int,
    val mutualAvatarUrls: ImmutableList<String>,
)
