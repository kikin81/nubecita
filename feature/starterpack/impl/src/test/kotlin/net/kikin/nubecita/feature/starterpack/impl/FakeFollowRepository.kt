package net.kikin.nubecita.feature.starterpack.impl

import net.kikin.nubecita.core.postinteractions.FollowRepository

/** Hand-written [FollowRepository] fake: canned results, recorded calls. */
class FakeFollowRepository(
    var followResult: Result<String> = Result.success("at://did:plc:me/app.bsky.graph.follow/new"),
    var unfollowResult: Result<Unit> = Result.success(Unit),
) : FollowRepository {
    val followCallDids = mutableListOf<String>()
    val unfollowCallUris = mutableListOf<String>()

    override suspend fun follow(subjectDid: String): Result<String> {
        followCallDids += subjectDid
        return followResult
    }

    override suspend fun unfollow(followUri: String): Result<Unit> {
        unfollowCallUris += followUri
        return unfollowResult
    }
}
