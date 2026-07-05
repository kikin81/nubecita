package net.kikin.nubecita.feature.widgets.impl.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.EmbedUi
import javax.inject.Inject

/**
 * Render-time thumbnail resolution for the feed widgets (nubecita-iqpc).
 *
 * The widget renders the LIVE feed cache, but thumbnails are prefetched only by
 * the background worker — and the cache is also written by the foreground app
 * (the worker no-ops while foregrounded, D-B4) while Glance re-composes on its
 * own triggers. So the cache is routinely fresher than the thumbnail store, and
 * every such post rendered a permanent empty media box until the next worker
 * run (hours away under Doze).
 *
 * A missing file therefore falls back to decoding from **Coil's local caches
 * only** (`allowNetwork = false` — the render path must issue no network, both
 * for the Glance session budget and for battery): the app almost always has
 * these exact CDN images cached from the user's own scrolling. The result is
 * written through [WidgetThumbnailStore], so the heal is permanent and later
 * renders take the fast path. A post whose image was never seen in-app stays
 * text-only until the worker's next prefetch — the pre-existing behavior.
 *
 * Constructed with an injectable file→[Bitmap] decode so the orchestration is
 * JVM-unit-testable ([BitmapFactory] needs an Android runtime); Hilt uses the
 * secondary constructor (mirrors [WidgetThumbnailStore]).
 */
internal class WidgetThumbnailLoader(
    private val store: WidgetThumbnailStore,
    private val decoder: ThumbnailDecoder,
    private val dispatcher: CoroutineDispatcher,
    private val decodeBitmapFile: (String) -> Bitmap?,
) {
    @Inject
    constructor(
        store: WidgetThumbnailStore,
        decoder: ThumbnailDecoder,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : this(store, decoder, dispatcher, { path -> BitmapFactory.decodeFile(path) })

    // Main-safe by contract: all file I/O + bitmap decoding runs on [dispatcher],
    // so callers may invoke from any context.
    suspend fun load(
        accountDid: String,
        postId: String,
        embed: EmbedUi,
    ): Bitmap? =
        withContext(dispatcher) {
            val file = store.thumbnailFile(accountDid, postId)
            if (file.exists()) {
                val cached = decodeBitmapFile(file.path)
                if (cached != null) return@withContext cached
                // An existing-but-undecodable file (truncated write predating the
                // decoder's tmp+rename, bit rot) would otherwise short-circuit the
                // self-heal on every render, forever. Delete it and fall through
                // so the heal — or the worker's next prefetch — can rewrite it.
                file.delete()
            }

            val url = widgetThumbnailUrl(embed) ?: return@withContext null
            val healed = decoder.decodeToFile(url, file, allowNetwork = false)
            if (healed) decodeBitmapFile(file.path) else null
        }
}
