package net.kikin.nubecita.feature.widgets.impl.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    private val decodeBitmapFile: (String) -> Bitmap?,
) {
    @Inject
    constructor(
        store: WidgetThumbnailStore,
        decoder: ThumbnailDecoder,
    ) : this(store, decoder, { path -> BitmapFactory.decodeFile(path) })

    suspend fun load(
        accountDid: String,
        postId: String,
        embed: EmbedUi,
    ): Bitmap? {
        val file = store.thumbnailFile(accountDid, postId)
        if (file.exists()) return decodeBitmapFile(file.path)

        val url = widgetThumbnailUrl(embed) ?: return null
        val healed = decoder.decodeToFile(url, file, allowNetwork = false)
        return if (healed) decodeBitmapFile(file.path) else null
    }
}
