package net.kikin.nubecita.core.wearsync.internal

import net.kikin.nubecita.core.common.text.GraphemeCounter
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearCreatePost
import net.kikin.nubecita.core.wearsync.WearActionHandler
import javax.inject.Inject

/**
 * Posts the text the user wrote on the watch (nubecita-rxsh.3) through
 * [PostingRepository], so mentions, links and tags are detected, `langs` is
 * picked and `create_post` is logged exactly as for the composer. Returns the
 * new post's URI.
 *
 * The watch already enforces [WearCreatePost.MAX_GRAPHEMES]; the limit is
 * checked again here because the repository doesn't, and Bluesky's refusal
 * would only read as a generic error. Expires after
 * [WearCreatePost.MAX_AGE_MS].
 */
internal class CreatePostActionHandler
    @Inject
    constructor(
        private val postingRepository: PostingRepository,
    ) : WearActionHandler {
        override fun canHandle(action: WearAction): Boolean = action is WearAction.CreatePost

        override fun maxAgeMs(action: WearAction): Long = WearCreatePost.MAX_AGE_MS

        override suspend fun execute(action: WearAction): Result<String?> {
            if (action !is WearAction.CreatePost) return Result.failure(IllegalArgumentException("not a post: $action"))
            val text = action.text.trim()
            if (text.isEmpty()) return Result.failure(IllegalArgumentException("empty watch post"))
            val graphemes = GraphemeCounter.count(text)
            if (graphemes > WearCreatePost.MAX_GRAPHEMES) {
                return Result.failure(IllegalArgumentException("watch post is $graphemes graphemes, over ${WearCreatePost.MAX_GRAPHEMES}"))
            }
            return postingRepository.createPost(text = text, attachments = emptyList(), replyTo = null).map { it.raw }
        }
    }
