package net.kikin.nubecita.feature.starterpack.impl.data

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.ActorUi

/**
 * One feed a starter pack recommends, drawn as a row that opens the feed.
 */
@Immutable
internal data class PackFeedUi(
    val uri: String,
    val displayName: String,
    val creatorHandle: String,
    val avatarUrl: String?,
)

/**
 * A starter pack's header data: everything on the screen except its members.
 *
 * [listUri] is the pack's member list; null when the response carried none, in
 * which case the pack has no members to page. [memberCount] is what the server
 * said the list holds, when it said.
 */
@Immutable
internal data class StarterPackDetail(
    val uri: String,
    val name: String,
    val creatorHandle: String,
    val description: String?,
    val memberCount: Int?,
    val listUri: String?,
    val feeds: ImmutableList<PackFeedUi>,
)

/** One page of a pack's members, as people rows. */
internal data class PackMembersPage(
    val members: ImmutableList<ActorUi>,
    val nextCursor: String?,
)

/**
 * The starter-pack read surface: the pack itself (`app.bsky.graph.getStarterPack`)
 * and its members, paged from the pack's list (`app.bsky.graph.getList`).
 *
 * Stateless — the caller owns the cursor. Both calls return a [Result]; a
 * malformed URI or a failed request is a failure, never a throw.
 */
internal interface StarterPackRepository {
    suspend fun getStarterPack(uri: String): Result<StarterPackDetail>

    suspend fun getMembers(
        listUri: String,
        cursor: String?,
        limit: Int = MEMBERS_PAGE_LIMIT,
    ): Result<PackMembersPage>
}

/** Members per `getList` page. The lexicon allows 1–100. */
internal const val MEMBERS_PAGE_LIMIT: Int = 50
