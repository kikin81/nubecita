package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Fixture factories for [KlipyMediaUi] / [KlipyMediaPage], for previews and
 * screenshot tests in the picker + composer feature modules. Lives in `src/main`
 * (not `src/test`) so those downstream `@Preview`/screenshot fixtures can reach
 * it. Every parameter has a default so callers vary only what they assert on.
 */
public object KlipyMediaUiFixtures {
    public fun media(
        slug: String = "happy-cat",
        title: String? = "Happy Cat",
        blurPreview: String? = null,
        type: KlipyMediaType = KlipyMediaType.GIF,
        previewUrl: String = "https://static.klipy.com/ii/preview/$slug.webp",
        previewWidth: Int = 240,
        previewHeight: Int = 180,
        embedUrl: String = "https://static.klipy.com/ii/embed/$slug.gif",
        embedWidth: Int = 480,
        embedHeight: Int = 360,
        mp4Url: String? = "https://static.klipy.com/ii/embed/$slug.mp4",
        webpUrl: String? = "https://static.klipy.com/ii/embed/$slug.webp",
    ): KlipyMediaUi =
        KlipyMediaUi(
            slug = slug,
            title = title,
            blurPreview = blurPreview,
            type = type,
            previewUrl = previewUrl,
            previewWidth = previewWidth,
            previewHeight = previewHeight,
            embedUrl = embedUrl,
            embedWidth = embedWidth,
            embedHeight = embedHeight,
            mp4Url = mp4Url,
            webpUrl = webpUrl,
        )

    public fun page(
        items: ImmutableList<KlipyMediaUi> = defaultItems(),
        hasNext: Boolean = true,
    ): KlipyMediaPage = KlipyMediaPage(items = items, hasNext = hasNext)

    private fun defaultItems(): ImmutableList<KlipyMediaUi> =
        List(9) { index ->
            media(
                slug = "gif-$index",
                title = "GIF $index",
                previewWidth = 200 + (index % 3) * 40,
                previewHeight = 150 + (index % 4) * 30,
            )
        }.toImmutableList()

    public val stickers: ImmutableList<KlipyMediaUi> =
        persistentListOf(
            media(slug = "star", title = "Star", type = KlipyMediaType.STICKER),
            media(slug = "heart", title = "Heart", type = KlipyMediaType.STICKER),
        )
}
