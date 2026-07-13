package net.kikin.nubecita.core.bookmarks

import androidx.compose.runtime.Immutable
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.PostUi

/**
 * Create / remove bookmarks and read the signed-in user's bookmarked posts,
 * backed by the AT Protocol `app.bsky.bookmark.*` lexicon.
 *
 * - [bookmark] → `app.bsky.bookmark.createBookmark` (needs the post's uri + cid).
 * - [unbookmark] → `app.bsky.bookmark.deleteBookmark` (keys off the post's own
 *   AT URI — there is no separate bookmark-record URI to track, unlike
 *   like/repost).
 * - [getBookmarks] → `app.bsky.bookmark.getBookmarks`, mapped to [PostUi].
 *
 * Both writes take a [StrongRef] so the call site builds one value and passes it
 * to the whole toggle, mirroring `LikeRepostRepository`. Returns [Result] so the
 * caller (an optimistic toggle) can roll back on failure.
 */
interface BookmarkRepository {
    suspend fun bookmark(post: StrongRef): Result<Unit>

    suspend fun unbookmark(post: StrongRef): Result<Unit>

    suspend fun getBookmarks(cursor: String? = null): Result<BookmarksPage>
}

/**
 * One page of the signed-in user's bookmarks: the resolved [posts] (bookmarked
 * items that mapped to a viewable post — not-found / blocked entries are
 * dropped) and the [cursor] for the next page (null when exhausted).
 */
@Immutable
data class BookmarksPage(
    val posts: ImmutableList<PostUi>,
    val cursor: String?,
)
