package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.bookmarks.BookmarksPage
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [BookmarkRepository] for [DefaultPostInteractionsCache] tests.
 * Tracks call counts + captured [StrongRef] args; the test sets the next
 * return value before each call. Mirrors [FakeLikeRepostRepository].
 */
internal class FakeBookmarkRepository : BookmarkRepository {
    val bookmarkCalls = AtomicInteger(0)
    val unbookmarkCalls = AtomicInteger(0)

    var lastBookmarkedRef: StrongRef? = null
    var lastUnbookmarkedRef: StrongRef? = null

    var nextBookmarkResult: Result<Unit> = Result.success(Unit)
    var nextUnbookmarkResult: Result<Unit> = Result.success(Unit)

    /** Optional latch; set non-zero to make calls suspend for N ms before returning. */
    var nextDelayMs: Long = 0

    override suspend fun bookmark(post: StrongRef): Result<Unit> {
        bookmarkCalls.incrementAndGet()
        lastBookmarkedRef = post
        if (nextDelayMs > 0) delay(nextDelayMs)
        return nextBookmarkResult
    }

    override suspend fun unbookmark(post: StrongRef): Result<Unit> {
        unbookmarkCalls.incrementAndGet()
        lastUnbookmarkedRef = post
        if (nextDelayMs > 0) delay(nextDelayMs)
        return nextUnbookmarkResult
    }

    override suspend fun getBookmarks(cursor: String?): Result<BookmarksPage> = Result.success(BookmarksPage(posts = persistentListOf(), cursor = null))
}
