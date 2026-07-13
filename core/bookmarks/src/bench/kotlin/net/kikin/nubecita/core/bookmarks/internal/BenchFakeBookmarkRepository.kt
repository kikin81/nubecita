package net.kikin.nubecita.core.bookmarks.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.bookmarks.BookmarksPage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [BookmarkRepository]: the offline bench build issues no network,
 * so bookmark / unbookmark are success no-ops and [getBookmarks] returns an
 * empty page. The optimistic toggle in the interaction handler treats
 * [Result.success] as "committed", so bookmark state sticks on the bench build
 * without a network call.
 */
@Singleton
internal class BenchFakeBookmarkRepository
    @Inject
    constructor() : BookmarkRepository {
        override suspend fun bookmark(post: StrongRef): Result<Unit> = Result.success(Unit)

        override suspend fun unbookmark(post: StrongRef): Result<Unit> = Result.success(Unit)

        override suspend fun getBookmarks(cursor: String?): Result<BookmarksPage> = Result.success(BookmarksPage(posts = persistentListOf(), cursor = null))
    }
