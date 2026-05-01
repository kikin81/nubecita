package net.kikin.nubecita.data.models

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
}
