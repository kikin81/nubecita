package net.kikin.nubecita.feature.profile.impl.data

import net.kikin.nubecita.core.posts.BenchPostSource
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject

/**
 * Lets the bench `getPost` resolve the profile fixture's posts (e.g. a post the
 * viewer pins from their profile), mirroring the feed's `BenchFeedPostSource`.
 */
internal class BenchProfilePostSource
    @Inject
    constructor(
        private val profileRepository: BenchFakeProfileRepository,
    ) : BenchPostSource {
        override suspend fun findPost(uri: String): PostUi? = profileRepository.findPost(uri)
    }
