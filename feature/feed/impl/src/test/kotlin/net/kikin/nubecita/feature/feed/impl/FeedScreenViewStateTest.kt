package net.kikin.nubecita.feature.feed.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Total over the (loadStatus × feedItems.isEmpty()) matrix — 5 statuses × 2
 * emptiness = 10 cells. Each cell asserts the screen-private projection
 * picks the correct render branch, including the four cells the VM never
 * produces (kept here as defensive fallbacks per
 * `feature-feed`'s "render matrix is total over `FeedState`" requirement).
 */
internal class FeedScreenViewStateTest {
    // --- empty feedItems --------------------------------------------------

    @Test
    fun `empty + Idle maps to Empty`() {
        val viewState = FeedState(loadStatus = FeedLoadStatus.Idle).toViewState()
        assertEquals(FeedScreenViewState.Empty, viewState)
    }

    @Test
    fun `empty + InitialLoading maps to InitialLoading`() {
        val viewState = FeedState(loadStatus = FeedLoadStatus.InitialLoading).toViewState()
        assertEquals(FeedScreenViewState.InitialLoading, viewState)
    }

    @Test
    fun `empty + InitialError carries the error variant`() {
        val viewState =
            FeedState(loadStatus = FeedLoadStatus.InitialError(FeedError.Network)).toViewState()
        assertEquals(FeedScreenViewState.InitialError(FeedError.Network), viewState)
    }

    @Test
    fun `empty + InitialError(Unauthenticated) carries the variant`() {
        val viewState =
            FeedState(loadStatus = FeedLoadStatus.InitialError(FeedError.Unauthenticated)).toViewState()
        assertEquals(FeedScreenViewState.InitialError(FeedError.Unauthenticated), viewState)
    }

    @Test
    fun `empty + Refreshing falls back to Empty (VM-impossible)`() {
        val viewState = FeedState(loadStatus = FeedLoadStatus.Refreshing).toViewState()
        assertEquals(FeedScreenViewState.Empty, viewState)
    }

    @Test
    fun `empty + Appending falls back to Empty (VM-impossible)`() {
        val viewState = FeedState(loadStatus = FeedLoadStatus.Appending).toViewState()
        assertEquals(FeedScreenViewState.Empty, viewState)
    }

    // --- non-empty feedItems ----------------------------------------------

    @Test
    fun `non-empty + Idle maps to Loaded(false, false)`() {
        val viewState =
            FeedState(feedItems = feedItems("p1", "p2"), loadStatus = FeedLoadStatus.Idle).toViewState()
        assertTrue(viewState is FeedScreenViewState.Loaded)
        viewState as FeedScreenViewState.Loaded
        assertEquals(2, viewState.feedItems.size)
        assertEquals(false, viewState.isAppending)
        assertEquals(false, viewState.isRefreshing)
    }

    @Test
    fun `non-empty + Refreshing maps to Loaded(isRefreshing = true)`() {
        val viewState =
            FeedState(feedItems = feedItems("p1"), loadStatus = FeedLoadStatus.Refreshing).toViewState()
        assertTrue(viewState is FeedScreenViewState.Loaded)
        viewState as FeedScreenViewState.Loaded
        assertEquals(false, viewState.isAppending)
        assertEquals(true, viewState.isRefreshing)
    }

    @Test
    fun `non-empty + Appending maps to Loaded(isAppending = true)`() {
        val viewState =
            FeedState(feedItems = feedItems("p1"), loadStatus = FeedLoadStatus.Appending).toViewState()
        assertTrue(viewState is FeedScreenViewState.Loaded)
        viewState as FeedScreenViewState.Loaded
        assertEquals(true, viewState.isAppending)
        assertEquals(false, viewState.isRefreshing)
    }

    @Test
    fun `non-empty + InitialLoading maps to Loaded(false, false) (VM-impossible)`() {
        val viewState =
            FeedState(feedItems = feedItems("p1"), loadStatus = FeedLoadStatus.InitialLoading).toViewState()
        assertTrue(viewState is FeedScreenViewState.Loaded)
        viewState as FeedScreenViewState.Loaded
        assertEquals(false, viewState.isAppending)
        assertEquals(false, viewState.isRefreshing)
    }

    @Test
    fun `non-empty + InitialError maps to Loaded(false, false) (VM-impossible)`() {
        val viewState =
            FeedState(
                feedItems = feedItems("p1"),
                loadStatus = FeedLoadStatus.InitialError(FeedError.Network),
            ).toViewState()
        assertTrue(viewState is FeedScreenViewState.Loaded)
        viewState as FeedScreenViewState.Loaded
        assertEquals(false, viewState.isAppending)
        assertEquals(false, viewState.isRefreshing)
    }
}

private fun feedItems(vararg ids: String): ImmutableList<FeedItemUi> = ids.map { FeedItemUi.Single(samplePost(it)) }.toImmutableList()

private fun samplePost(id: String): PostUi =
    PostUi(
        id = id,
        author =
            AuthorUi(
                did = "did:plc:fake",
                handle = "fake.bsky.social",
                displayName = "Fake",
                avatarUrl = null,
            ),
        createdAt = Instant.parse("2026-04-25T12:00:00Z"),
        text = "fake text $id",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )
