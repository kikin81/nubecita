package net.kikin.nubecita.core.videofeed

import javax.inject.Inject
import javax.inject.Provider

/**
 * Selects the [VideoFeedSource] for a vertical-feed entry point. Takes the raw
 * `authorDid` (NOT the `VideoFeed` route) so `:core:video-feed` stays free of
 * feature dependencies; the ViewModel passes `route.authorDid` through.
 */
interface VideoFeedSourceFactory {
    /** @param authorDid null → trending feed; non-null → that author's videos. */
    fun create(authorDid: String?): VideoFeedSource
}

internal class DefaultVideoFeedSourceFactory
    @Inject
    constructor(
        private val trending: Provider<DefaultTrendingVideoSource>,
        private val authorSourceFactory: AuthorVideoSource.Factory,
    ) : VideoFeedSourceFactory {
        override fun create(authorDid: String?): VideoFeedSource =
            authorDid
                ?.let { authorSourceFactory.create(it) }
                ?: trending.get()
    }
