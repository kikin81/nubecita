package net.kikin.nubecita.core.bookmarks.internal

import io.github.kikin81.atproto.app.bsky.bookmark.BookmarkService
import io.github.kikin81.atproto.app.bsky.bookmark.CreateBookmarkRequest
import io.github.kikin81.atproto.app.bsky.bookmark.DeleteBookmarkRequest
import io.github.kikin81.atproto.app.bsky.bookmark.GetBookmarksRequest
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.bookmarks.BookmarksPage
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import timber.log.Timber
import javax.inject.Inject

/**
 * Network-backed [BookmarkRepository]. Builds a [BookmarkService] from the
 * per-session [XrpcClientProvider.authenticated] client on each call (the same
 * per-call construction the like/repost + posts repositories use), and runs on
 * the IO dispatcher. Failures are logged and surfaced as [Result.failure] so the
 * optimistic toggle can roll back.
 */
internal class DefaultBookmarkRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : BookmarkRepository {
        override suspend fun bookmark(post: StrongRef): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    BookmarkService(xrpcClientProvider.authenticated())
                        .createBookmark(CreateBookmarkRequest(cid = post.cid, uri = post.uri))
                }.onFailure { Timber.tag(TAG).w(it, "createBookmark failed: %s", it.javaClass.name) }
            }

        override suspend fun unbookmark(post: StrongRef): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    BookmarkService(xrpcClientProvider.authenticated())
                        .deleteBookmark(DeleteBookmarkRequest(uri = post.uri))
                }.onFailure { Timber.tag(TAG).w(it, "deleteBookmark failed: %s", it.javaClass.name) }
            }

        override suspend fun getBookmarks(cursor: String?): Result<BookmarksPage> =
            withContext(dispatcher) {
                runCatching {
                    val response =
                        BookmarkService(xrpcClientProvider.authenticated())
                            .getBookmarks(GetBookmarksRequest(cursor = cursor))
                    BookmarksPage(
                        // Only bookmarked items that resolve to a viewable post are
                        // surfaced; not-found / blocked union members are dropped.
                        posts =
                            response.bookmarks
                                .mapNotNull { (it.item as? PostView)?.toPostUiCore() }
                                .toImmutableList(),
                        cursor = response.cursor,
                    )
                }.onFailure { Timber.tag(TAG).w(it, "getBookmarks failed: %s", it.javaClass.name) }
            }

        private companion object {
            private const val TAG = "BookmarkRepository"
        }
    }
