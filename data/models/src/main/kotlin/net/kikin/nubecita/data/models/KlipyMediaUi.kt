package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList

/**
 * The two KLIPY media kinds Nubecita supports in the composer. Clips (native
 * video) are deferred to their own epic; ads are filtered out at the mapper.
 */
public enum class KlipyMediaType { GIF, STICKER }

/**
 * One KLIPY GIF or sticker, as surfaced to the picker and — once selected —
 * to the post composer.
 *
 * A KLIPY item carries multiple renditions (hd/md/sm/xs × gif/webp/mp4). This
 * model keeps exactly two of them, for two different jobs:
 *
 * - **Grid preview** ([previewUrl] + [previewWidth]/[previewHeight]) — a light
 *   animated webp (falling back to gif) for the staggered picker grid. Kept
 *   small so the grid can animate every visible cell through one shared Coil
 *   `ImageLoader` without threatening 120hz.
 * - **Embed source** ([embedUrl] + [embedWidth]/[embedHeight] + [mp4Url]/
 *   [webpUrl]) — the high-quality `static.klipy.com/ii/…` gif URL plus its
 *   pixel dimensions and the mp4/webp variant URLs. At post time the composer
 *   assembles these into an `app.bsky.embed.external` whose URI is
 *   `…/<file>.gif?ww=<embedWidth>&hh=<embedHeight>&mp4=<mp4 slug>`, the exact
 *   shape GIF-aware clients (Nubecita's own feed render path and the official
 *   Bluesky app) detect and play inline-animated. [embedWidth]/[embedHeight]
 *   become the `ww`/`hh` query params our `gifAspectRatioOrNull` reads back.
 *
 * [blurPreview] is KLIPY's base64-encoded placeholder shown while the preview
 * loads. [slug] is KLIPY's stable id, used for view/share/report tracking and
 * as the LazyStaggeredGrid item key.
 */
@Stable
public data class KlipyMediaUi(
    val slug: String,
    val title: String?,
    val blurPreview: String?,
    val type: KlipyMediaType,
    val previewUrl: String,
    val previewWidth: Int,
    val previewHeight: Int,
    val embedUrl: String,
    val embedWidth: Int,
    val embedHeight: Int,
    val mp4Url: String?,
    val webpUrl: String?,
)

/** The preview rendition's aspect ratio (width / height) for staggered-grid layout. */
public val KlipyMediaUi.previewAspectRatio: Float
    get() = if (previewHeight > 0) previewWidth.toFloat() / previewHeight else 1f

/**
 * One page of KLIPY results plus whether another page exists — the paging
 * signal that drives `KlipyPagingSource` (KLIPY paginates by page/per_page and
 * reports `has_next` rather than a cursor).
 */
@Immutable
public data class KlipyMediaPage(
    val items: ImmutableList<KlipyMediaUi>,
    val hasNext: Boolean,
)
