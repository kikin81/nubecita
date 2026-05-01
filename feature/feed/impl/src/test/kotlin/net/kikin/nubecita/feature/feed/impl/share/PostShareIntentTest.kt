package net.kikin.nubecita.feature.feed.impl.share

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class PostShareIntentTest {
    @Test
    fun `permalink uses author handle and rkey extracted from AT-URI`() {
        val post =
            samplePost(
                id = "at://did:plc:fakedid000000000000000/app.bsky.feed.post/3kabc123def",
                handle = "alice.bsky.social",
            )

        val intent = post.toShareIntent()

        assertEquals(
            "https://bsky.app/profile/alice.bsky.social/post/3kabc123def",
            intent.permalink,
        )
    }

    @Test
    fun `share text equals the permalink (link-only — bsky links carry rich previews)`() {
        val post =
            samplePost(
                id = "at://did:plc:fakedid000000000000000/app.bsky.feed.post/3kabc123def",
                handle = "alice.bsky.social",
            )

        val intent = post.toShareIntent()

        assertEquals(intent.permalink, intent.text)
    }

    @Test
    fun `non-ascii post body does not affect the permalink`() {
        val post =
            samplePost(
                id = "at://did:plc:fakedid000000000000000/app.bsky.feed.post/3krkey0000",
                handle = "user.bsky.social",
                text = "café — ñoño 🚀",
            )

        val intent = post.toShareIntent()

        assertEquals(
            "https://bsky.app/profile/user.bsky.social/post/3krkey0000",
            intent.permalink,
        )
    }

    @Test
    fun `malformed id without a slash falls back to using the whole id as rkey`() {
        // PostUi.id should always be an AT-URI per the mapper contract; this
        // test pins the defensive fallback so a bad id doesn't crash the
        // share path. The resulting URL won't resolve on bsky.app but it
        // doesn't throw either.
        val post = samplePost(id = "rogue-id-no-slashes", handle = "x.bsky.social")

        val intent = post.toShareIntent()

        assertEquals("https://bsky.app/profile/x.bsky.social/post/rogue-id-no-slashes", intent.permalink)
    }

    private fun samplePost(
        id: String,
        handle: String,
        text: String = "hello",
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author =
                AuthorUi(
                    did = "did:plc:fakedid000000000000000",
                    handle = handle,
                    displayName = "Sample",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-25T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
