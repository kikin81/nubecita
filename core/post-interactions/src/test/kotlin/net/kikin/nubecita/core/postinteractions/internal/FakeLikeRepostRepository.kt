package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [LikeRepostRepository] for [DefaultPostInteractionsCache]
 * tests. Tracks call counts and per-method captured args; lets the
 * test class set the next return value before each call.
 */
internal class FakeLikeRepostRepository : LikeRepostRepository {
    val likeCalls = AtomicInteger(0)
    val unlikeCalls = AtomicInteger(0)
    val repostCalls = AtomicInteger(0)
    val unrepostCalls = AtomicInteger(0)

    var lastLikedSubject: StrongRef? = null
    var lastUnlikedUri: AtUri? = null
    var lastRepostedSubject: StrongRef? = null
    var lastUnrepostedUri: AtUri? = null

    var nextLikeResult: Result<AtUri> = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.like/auto"))
    var nextUnlikeResult: Result<Unit> = Result.success(Unit)
    var nextRepostResult: Result<AtUri> = Result.success(AtUri("at://did:plc:viewer/app.bsky.feed.repost/auto"))
    var nextUnrepostResult: Result<Unit> = Result.success(Unit)

    /** Optional latch; set non-zero to make calls suspend for N ms before returning. */
    var nextDelayMs: Long = 0

    override suspend fun like(post: StrongRef): Result<AtUri> {
        likeCalls.incrementAndGet()
        lastLikedSubject = post
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextLikeResult
    }

    override suspend fun unlike(likeUri: AtUri): Result<Unit> {
        unlikeCalls.incrementAndGet()
        lastUnlikedUri = likeUri
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextUnlikeResult
    }

    override suspend fun repost(post: StrongRef): Result<AtUri> {
        repostCalls.incrementAndGet()
        lastRepostedSubject = post
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextRepostResult
    }

    override suspend fun unrepost(repostUri: AtUri): Result<Unit> {
        unrepostCalls.incrementAndGet()
        lastUnrepostedUri = repostUri
        if (nextDelayMs > 0) kotlinx.coroutines.delay(nextDelayMs)
        return nextUnrepostResult
    }
}
