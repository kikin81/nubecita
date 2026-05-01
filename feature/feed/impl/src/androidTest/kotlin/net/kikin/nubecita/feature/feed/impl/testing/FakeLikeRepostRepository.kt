package net.kikin.nubecita.feature.feed.impl.testing

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.feature.feed.impl.data.LikeRepostRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op [LikeRepostRepository] for instrumentation tests. Hilt-injected
 * via `TestLikeRepostRepositoryModule`'s
 * `@TestInstallIn(replaces = [LikeRepostRepositoryModule::class])`.
 *
 * The first reference instrumentation test (`FeedScreenInstrumentationTest`)
 * doesn't tap like or repost — it asserts initial render only. The fake
 * exists solely so the Hilt graph stays satisfiable when
 * `FeedRepositoryModule` is replaced; like/repost tests should extend
 * this with a stateful fake when they land.
 */
@Singleton
internal class FakeLikeRepostRepository
    @Inject
    constructor() : LikeRepostRepository {
        override suspend fun like(post: StrongRef): Result<AtUri> = Result.success(AtUri("at://test/like/0"))

        override suspend fun unlike(likeUri: AtUri): Result<Unit> = Result.success(Unit)

        override suspend fun repost(post: StrongRef): Result<AtUri> = Result.success(AtUri("at://test/repost/0"))

        override suspend fun unrepost(repostUri: AtUri): Result<Unit> = Result.success(Unit)
    }
