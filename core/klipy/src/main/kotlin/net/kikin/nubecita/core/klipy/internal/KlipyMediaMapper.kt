package net.kikin.nubecita.core.klipy.internal

import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUi

/**
 * Maps a KLIPY wire item to the UI model, or `null` if it can't back a usable
 * embed. Two renditions are picked from the hd/md/sm/xs ladder:
 *
 * - **Embed** = highest available `gif` (`hd → md → sm`). Its URL is the
 *   `static.klipy.com/ii/…` GIF that gets posted; its width/height become the
 *   `ww`/`hh` embed params. No gif rendition ⇒ unusable ⇒ `null`.
 * - **Preview** = lightest available `webp` (`sm → xs → md`), falling back to
 *   that rendition's gif, then to the embed gif — small enough to animate every
 *   visible grid cell cheaply.
 *
 * Ads and clips are dropped (return `null`): v1 posts only GIFs and stickers,
 * and we send no ad-targeting params so ad slots shouldn't appear anyway.
 */
internal fun KlipyMediaItemDto.toUiOrNull(): KlipyMediaUi? {
    val item = this as? KlipyMediaItemDto.General ?: return null
    val slug = item.slug ?: return null
    val file = item.file ?: return null

    val embedTypes = file.hd ?: file.md ?: file.sm ?: return null
    val embedGif = embedTypes.gif ?: return null
    val embedUrl = embedGif.url ?: return null
    val embedWidth = embedGif.width ?: return null
    val embedHeight = embedGif.height ?: return null

    // Only take a preview rendition that actually has a URL — otherwise we'd keep
    // its dimensions while sourcing the image from embedGif, distorting the grid cell.
    val previewTypes = file.sm ?: file.xs ?: file.md ?: embedTypes
    val preview =
        previewTypes.webp?.takeIf { !it.url.isNullOrEmpty() }
            ?: previewTypes.gif?.takeIf { !it.url.isNullOrEmpty() }
            ?: embedGif

    return KlipyMediaUi(
        slug = slug,
        title = item.title,
        blurPreview = item.blurPreview,
        type = if (item.type == "sticker") KlipyMediaType.STICKER else KlipyMediaType.GIF,
        previewUrl = preview.url ?: embedUrl,
        previewWidth = preview.width ?: embedWidth,
        previewHeight = preview.height ?: embedHeight,
        embedUrl = embedUrl,
        embedWidth = embedWidth,
        embedHeight = embedHeight,
        mp4Url = embedTypes.mp4?.url,
        webpUrl = embedTypes.webp?.url,
    )
}
