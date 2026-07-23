package net.kikin.nubecita.core.videofeed

import javax.inject.Inject
import javax.inject.Provider

/**
 * Bench-flavor factory: every entry point (trending or author) resolves to the
 * bundled [FakeVideoFeedSource], so the vertical feed and the profile-videos entry
 * both play fully offline.
 */
internal class BenchVideoFeedSourceFactory
    @Inject
    constructor(
        private val fake: Provider<FakeVideoFeedSource>,
    ) : VideoFeedSourceFactory {
        override fun create(authorDid: String?): VideoFeedSource = fake.get()
    }
