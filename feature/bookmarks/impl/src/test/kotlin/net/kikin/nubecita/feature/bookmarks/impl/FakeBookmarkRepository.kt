package net.kikin.nubecita.feature.bookmarks.impl

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.bookmarks.BookmarksPage

/**
 * Hand-written test double for [BookmarkRepository]. [onGetBookmarks] maps
 * each requested cursor (`null` for the first page) to a canned result, so
 * a test can script a multi-page or failing sequence; every requested
 * cursor is recorded in [requestedCursors] for assertions.
 */
internal class FakeBookmarkRepository(
    var onGetBookmarks: (cursor: String?) -> Result<BookmarksPage> = { Result.success(BookmarksPage(kotlinx.collections.immutable.persistentListOf(), null)) },
) : BookmarkRepository {
    val requestedCursors: MutableList<String?> = mutableListOf()

    override suspend fun getBookmarks(cursor: String?): Result<BookmarksPage> {
        requestedCursors += cursor
        return onGetBookmarks(cursor)
    }

    override suspend fun bookmark(post: StrongRef): Result<Unit> = Result.success(Unit)

    override suspend fun unbookmark(post: StrongRef): Result<Unit> = Result.success(Unit)
}
