package net.kikin.nubecita.feature.chats.impl

import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.core.postinteractions.FollowRepository

/**
 * In-memory [FollowRepository] for `GroupDetailsViewModel` unit tests. Settable
 * results + call capture; an optional [gate] lets a test observe the optimistic
 * `InFlight` projection before the call reconciles.
 */
internal class FakeFollowRepository(
    var followResult: Result<String> = Result.success("at://follow/new"),
    var unfollowResult: Result<Unit> = Result.success(Unit),
) : FollowRepository {
    val followCalls = mutableListOf<String>()
    val unfollowCalls = mutableListOf<String>()

    /** When set, both follow/unfollow suspend on it before returning. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun follow(subjectDid: String): Result<String> {
        followCalls += subjectDid
        gate?.await()
        return followResult
    }

    override suspend fun unfollow(followUri: String): Result<Unit> {
        unfollowCalls += followUri
        gate?.await()
        return unfollowResult
    }
}
