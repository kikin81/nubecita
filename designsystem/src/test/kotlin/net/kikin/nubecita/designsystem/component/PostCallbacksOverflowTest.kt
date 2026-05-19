package net.kikin.nubecita.designsystem.component

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * JVM-only contract tests for [PostCallbacks.onOverflowAction]. The
 * Composable interaction path is exercised by the androidTest
 * `PostCardClickModelTest`; this test pins the lambda's identity and
 * recording behavior at the data-class level so a future refactor
 * (e.g. promoting the lambda to a sealed interface) doesn't silently
 * skip the recording semantics.
 */
class PostCallbacksOverflowTest {
    @Test
    fun overflowCallback_defaults_to_null_in_PostCallbacks_None() {
        // PostCallbacks.None is the shared no-op singleton — the
        // overflow icon's render-gate ("only when non-null") relies on
        // this default. Locks the default.
        assertNull(PostCallbacks.None.onOverflowAction)
    }

    @Test
    fun overflowCallback_records_every_action_variant() {
        // One recording lambda, drive it through every variant. Locks
        // the (post, action) shape — both halves flow.
        val recorded = mutableListOf<Pair<PostUi, PostOverflowAction>>()
        val callbacks =
            PostCallbacks(
                onOverflowAction = { post, action -> recorded += post to action },
            )
        val handler = checkNotNull(callbacks.onOverflowAction)

        val variants =
            listOf(
                PostOverflowAction.ReportPost,
                PostOverflowAction.MuteAuthor,
                PostOverflowAction.UnmuteAuthor,
                PostOverflowAction.BlockAuthor,
                PostOverflowAction.UnblockAuthor,
                PostOverflowAction.MuteThread,
                PostOverflowAction.UnmuteThread,
                PostOverflowAction.CopyPostText,
            )
        for (action in variants) handler(samplePost, action)

        assertEquals(variants.size, recorded.size)
        recorded.forEachIndexed { index, (post, action) ->
            assertEquals(samplePost, post)
            assertEquals(variants[index], action)
        }
    }

    @Test
    fun overflowCallback_preserves_PostCallbacks_data_equality() {
        // Two callback bags with the SAME lambda reference for
        // onOverflowAction (and matching everything else) compare equal.
        // Underwrites the @Stable contract — call sites that
        // `remember { PostCallbacks(...) }` and pass the bag through
        // multiple recompositions still skip cleanly.
        val handler: (PostUi, PostOverflowAction) -> Unit = { _, _ -> }
        val a = PostCallbacks(onOverflowAction = handler)
        val b = PostCallbacks(onOverflowAction = handler)
        assertEquals(a, b)
    }

    private val sampleAuthor: AuthorUi =
        AuthorUi(
            did = "did:plc:sample",
            handle = "sample.bsky.social",
            displayName = "Sample",
            avatarUrl = null,
        )

    private val samplePost: PostUi =
        PostUi(
            id = "at://did:plc:sample/app.bsky.feed.post/x",
            cid = "bafyreisampleciD000000000000000000000000000000000",
            author = sampleAuthor,
            createdAt = Instant.parse("2025-10-15T12:00:00Z"),
            text = "Sample text",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
