@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strip a video URL down to its cache key by dropping the query string. Bluesky
 * HLS URLs may carry signed/query params that vary per request, so keying on the
 * bare path keeps cache hits stable. Pure (string-only) so it is unit-testable
 * without `android.net.Uri`.
 */
internal fun videoCacheKey(url: String): String = url.substringBefore('?')

/**
 * Process-singleton owner of the Media3 [SimpleCache] for video playback. Only
 * ONE `SimpleCache` may exist per cache directory per process, so this wrapper
 * memoizes it. [get] builds it **off the main thread** via the injected
 * [IoDispatcher] — `SimpleCache`'s constructor touches disk and would risk an
 * ANR on the main thread. Cache lives in `cacheDir` (internal storage; external
 * dir is absent on some OEMs) with an explicit LRU evictor.
 *
 * Shared building block for the video playback engine (epic nubecita-zdv8): the
 * `VerticalVideoPlaylistPlayer` pool consumes it; `SharedVideoPlayer` may adopt
 * it later (bd nubecita-zdv8.8).
 */
@Singleton
public class VideoCache
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var cache: SimpleCache? = null

        /** The shared [SimpleCache], constructed once off-main on first access. */
        public suspend fun get(): SimpleCache =
            cache ?: mutex.withLock {
                cache ?: withContext(ioDispatcher) {
                    SimpleCache(
                        File(context.cacheDir, CACHE_DIR),
                        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                        StandaloneDatabaseProvider(context),
                    ).also { cache = it }
                }
            }

        private companion object {
            const val CACHE_DIR = "nubecita-video-cache"
            const val MAX_CACHE_BYTES = 256L * 1024 * 1024 // 256 MB
        }
    }
