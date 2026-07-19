@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.TrackSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles cache-backed, short-video-tuned [ExoPlayer] instances — the shared
 * playback building block the vertical-video pool
 * (`VerticalVideoPlaylistPlayer`, epic nubecita-zdv8 Slice 2b) consumes. Wires:
 * the process-shared [VideoCache] via a [CacheDataSource] over the default HTTP
 * upstream, the short-video [shortVideoLoadControl], and a renderers factory
 * with software-decoder fallback enabled.
 *
 * `MediaSource`es are produced fresh per playback by [DefaultMediaSourceFactory]
 * (which delegates to the HLS module for `.m3u8`); they are never cached or
 * shared across players — byte-level reuse lives in [VideoCache].
 */
@Singleton
public class VideoPlayerFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val videoCache: VideoCache,
    ) {
        /**
         * Build a fresh cache-backed ExoPlayer using [trackSelector]. Suspends to
         * construct the shared cache off-main on first use.
         */
        public suspend fun create(trackSelector: TrackSelector): ExoPlayer {
            val cacheDataSourceFactory =
                CacheDataSource
                    .Factory()
                    .setCache(videoCache.get())
                    .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
                    // Strip query params so signed Bluesky HLS URLs still hit cache (see videoCacheKey).
                    .setCacheKeyFactory { dataSpec -> dataSpec.key ?: videoCacheKey(dataSpec.uri.toString()) }
                    // On a cache read error, fall through to upstream rather than failing playback.
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

            return ExoPlayer
                .Builder(context, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
                .setTrackSelector(trackSelector)
                .setLoadControl(shortVideoLoadControl())
                .build()
        }
    }
