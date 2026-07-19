@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.TrackSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
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
         * Build a fresh cache-backed ExoPlayer using [trackSelector]. The player
         * is constructed on the **main thread** — ExoPlayer binds to the thread
         * that builds it, and this `suspend fun` could otherwise resume off-main
         * after [VideoCache.get] (which itself hops to IO), leaving the player
         * accessible only from a background thread. If the shared cache fails to
         * initialize, playback falls back to an **uncached** data source rather
         * than failing entirely — the cache is an optimization, not a hard
         * dependency.
         */
        public suspend fun create(trackSelector: TrackSelector): ExoPlayer =
            withContext(Dispatchers.Main.immediate) {
                val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
                ExoPlayer
                    .Builder(context, renderersFactory)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory()))
                    .setTrackSelector(trackSelector)
                    .setLoadControl(shortVideoLoadControl())
                    .build()
            }

        /**
         * The cache-backed [DataSource.Factory], or a plain uncached factory when
         * the shared cache can't be constructed (e.g. SQLite corruption / disk I/O).
         */
        private suspend fun dataSourceFactory(): DataSource.Factory {
            val cache =
                try {
                    videoCache.get()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "video cache init failed; falling back to uncached playback")
                    null
                } ?: return DefaultDataSource.Factory(context)

            return CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
                // Strip query params so signed Bluesky HLS URLs still hit cache (see videoCacheKey).
                .setCacheKeyFactory { dataSpec -> dataSpec.key ?: videoCacheKey(dataSpec.uri.toString()) }
                // On a cache read error, fall through to upstream rather than failing playback.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }

        private companion object {
            const val TAG = "VideoPlayerFactory"
        }
    }
