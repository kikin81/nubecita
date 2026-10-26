package net.kikin.nubecita.core.wearsync.internal

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.core.wearsync.WearActionHandler
import javax.inject.Inject

/**
 * Runs the watch's post actions (nubecita-hohv.2): like, repost and bookmark,
 * and their undos.
 *
 * Calls the repositories directly rather than the phone's
 * `PostInteractionsCache`: the cache *toggles* from its own state, so an
 * explicit "unlike" from the watch could turn into a like on a post the phone
 * hasn't loaded. The watch always says exactly what it wants. The phone's own
 * screens pick the change up on their next refresh.
 */
internal class PostActionHandler
    @Inject
    constructor(
        private val likeRepostRepository: LikeRepostRepository,
        private val bookmarkRepository: BookmarkRepository,
    ) : WearActionHandler {
        override fun canHandle(action: WearAction): Boolean = action is WearPostAction

        override suspend fun execute(action: WearAction): Result<String?> {
            if (action !is WearPostAction) return Result.failure(IllegalArgumentException("not a post action: $action"))
            return when (action) {
                is WearAction.Like -> likeRepostRepository.like(ref(action.postUri, action.postCid)).map { it.raw }
                is WearAction.Unlike -> likeRepostRepository.unlike(AtUri(action.likeUri)).map { null }
                is WearAction.Repost -> likeRepostRepository.repost(ref(action.postUri, action.postCid)).map { it.raw }
                is WearAction.Unrepost -> likeRepostRepository.unrepost(AtUri(action.repostUri)).map { null }
                is WearAction.Bookmark -> bookmarkRepository.bookmark(ref(action.postUri, action.postCid)).map { null }
                is WearAction.Unbookmark -> bookmarkRepository.unbookmark(ref(action.postUri, action.postCid)).map { null }
            }
        }

        private fun ref(
            uri: String,
            cid: String,
        ) = StrongRef(uri = AtUri(uri), cid = Cid(cid))
    }
