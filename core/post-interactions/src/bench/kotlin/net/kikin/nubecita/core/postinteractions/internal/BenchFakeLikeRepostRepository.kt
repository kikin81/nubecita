package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [LikeRepostRepository]: the offline bench build issues no
 * network, so like/repost/unlike/unrepost are success no-ops that return a
 * synthetic [AtUri]. The non-blank uri matters: [PostInteractionsCache] stores
 * the like/repost record uri as the "viewer has interacted" sentinel — a blank
 * or null uri would make the optimistic toggle immediately roll back.
 */
@Singleton
internal class BenchFakeLikeRepostRepository
    @Inject
    constructor() : LikeRepostRepository {
        override suspend fun like(post: StrongRef): Result<AtUri> = Result.success(AtUri("at://bench/app.bsky.feed.like/${post.uri.hashCode()}"))

        override suspend fun unlike(likeUri: AtUri): Result<Unit> = Result.success(Unit)

        override suspend fun repost(post: StrongRef): Result<AtUri> = Result.success(AtUri("at://bench/app.bsky.feed.repost/${post.uri.hashCode()}"))

        override suspend fun unrepost(repostUri: AtUri): Result<Unit> = Result.success(Unit)
    }
