package net.kikin.nubecita.feature.profile.impl.data

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.ActorUi

/**
 * One account in a followers or following list.
 *
 * [followUri] is the viewer's own follow record for this account, which is what
 * an unfollow deletes. [canFollow] is false for rows that offer no follow
 * action at all: the viewer themselves, either side of a block, and a deleted
 * account.
 */
@Immutable
internal data class ConnectionRowUi(
    val actor: ActorUi,
    val followUri: String?,
    val canFollow: Boolean,
)

internal data class ConnectionPage(
    val rows: ImmutableList<ConnectionRowUi>,
    val cursor: String?,
)

/** Reads who follows an account and who it follows, a page at a time. */
internal interface ProfileConnectionsRepository {
    suspend fun getFollowers(
        actor: String,
        cursor: String?,
    ): Result<ConnectionPage>

    suspend fun getFollows(
        actor: String,
        cursor: String?,
    ): Result<ConnectionPage>
}
