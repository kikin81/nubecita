package net.kikin.nubecita.data.models

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/**
 * Test + preview fixtures for [PostUi] and friends.
 *
 * Lives in `src/test` so production code never accidentally instantiates
 * fake data. Downstream `:designsystem` previews can either depend on
 * `testFixtures` or duplicate small fixtures inline — the choice is per
 * the consuming module's preview-vs-test boundary.
 */
internal object PostUiFixtures {
    fun fakeAuthor(
        did: String = "did:plc:fakedid000000000000000",
        handle: String = "alice.bsky.social",
        displayName: String = "Alice Chen",
        avatarUrl: String? = null,
    ): AuthorUi = AuthorUi(did, handle, displayName, avatarUrl)

    fun fakePost(
        id: String = "p1",
        cid: String = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author: AuthorUi = fakeAuthor(),
        createdAt: Instant = Instant.parse("2026-04-25T12:00:00Z"),
        text: String = "Hello from the test fixture.",
        facets: ImmutableList<Facet> = persistentListOf(),
        embed: EmbedUi = EmbedUi.Empty,
        stats: PostStatsUi = PostStatsUi(),
        viewer: ViewerStateUi = ViewerStateUi(),
        repostedBy: String? = null,
    ): PostUi = PostUi(id, cid, author, createdAt, text, facets, embed, stats, viewer, repostedBy)

    /** A single [ImageUi] with both CDN variants and an aspect ratio. */
    fun fakeImageUi(
        index: Int = 0,
        aspectRatio: Float? = 1.5f,
        altText: String? = "Test image $index",
    ): ImageUi =
        ImageUi(
            fullsizeUrl = "https://cdn.example/img/feed_fullsize/$index@jpeg",
            thumbUrl = "https://cdn.example/img/feed_thumbnail/$index@jpeg",
            altText = altText,
            aspectRatio = aspectRatio,
        )

    /** [count] images as an immutable list — shared payload for both embeds. */
    fun fakeImageList(count: Int): ImmutableList<ImageUi> = (0 until count).map { fakeImageUi(index = it) }.toImmutableList()

    /** `app.bsky.embed.images` fixture (clamp [count] to the 1–4 lexicon range yourself). */
    fun fakeImagesEmbed(count: Int = 4): EmbedUi.Images = EmbedUi.Images(items = fakeImageList(count))

    /** `app.bsky.embed.gallery#view` fixture (defaults to a typical 5–10 image gallery). */
    fun fakeGalleryEmbed(count: Int = 7): EmbedUi.Gallery = EmbedUi.Gallery(items = fakeImageList(count))
}
