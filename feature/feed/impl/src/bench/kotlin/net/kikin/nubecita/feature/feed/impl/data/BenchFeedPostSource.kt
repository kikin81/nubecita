package net.kikin.nubecita.feature.feed.impl.data

import net.kikin.nubecita.core.posts.BenchPostSource
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject

/**
 * Answers the bench `PostRepository.getPost` from the feed's own fixtures, so a
 * media viewer opened from a feed image shows that post's images instead of the
 * bench video fallback. Reuses [BenchFakeFeedRepository]'s parsed-asset cache.
 */
internal class BenchFeedPostSource
    @Inject
    constructor(
        private val feedRepository: BenchFakeFeedRepository,
    ) : BenchPostSource {
        override suspend fun findPost(uri: String): PostUi? = feedRepository.findPost(uri)
    }
