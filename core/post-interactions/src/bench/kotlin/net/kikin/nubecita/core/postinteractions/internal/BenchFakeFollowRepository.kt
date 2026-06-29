package net.kikin.nubecita.core.postinteractions.internal

import net.kikin.nubecita.core.postinteractions.FollowRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [FollowRepository]: the offline bench build issues no network,
 * so follow/unfollow are success no-ops. The returned at-uri for [follow] is
 * a synthetic non-blank string so any caller that stores the follow-record uri
 * gets a valid sentinel (rather than an empty string that might be treated as
 * "not following").
 */
@Singleton
internal class BenchFakeFollowRepository
    @Inject
    constructor() : FollowRepository {
        override suspend fun follow(subjectDid: String): Result<String> = Result.success("at://bench/app.bsky.graph.follow/${subjectDid.hashCode()}")

        override suspend fun unfollow(followUri: String): Result<Unit> = Result.success(Unit)
    }
