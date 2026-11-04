package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.posts.BenchPostSource
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject

/**
 * Answers the bench `PostRepository.getPost` from the feed's own fixtures, so a
 * media viewer opened from a feed image shows that post's images instead of the
 * bench video fallback. Reuses [FakeFeedRepository]'s parsed-asset cache.
 */
internal class BenchFeedPostSource
    @Inject
    constructor(
        private val feedRepository: FakeFeedRepository,
    ) : BenchPostSource {
        override suspend fun findPost(uri: String): PostUi? = feedRepository.findPost(uri)
    }
