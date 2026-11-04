package net.kikin.nubecita.feature.feed.impl

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.OutgoingPostStatusUi
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Instant

/**
 * Two pure mappings, tested without a Compose host or a `Pager`:
 *
 * - Paging's load states → the feed's own [FeedLoadStatus] vocabulary.
 * - That status plus an item count → the screen's render branch, total over
 *   both so a later change cannot introduce a silent unhandled state.
 */
internal class FeedScreenViewStateTest {
    // --- load states -> status -------------------------------------------

    @Test
    fun `refreshing with nothing on screen is the first load`() {
        assertEquals(FeedLoadStatus.InitialLoading, loadStates(refresh = LoadState.Loading).toFeedLoadStatus(itemCount = 0))
    }

    @Test
    fun `refreshing with items on screen is a refresh`() {
        // Same Paging state, different reader experience: the item count is
        // what separates the shimmer from the pull indicator.
        assertEquals(FeedLoadStatus.Refreshing, loadStates(refresh = LoadState.Loading).toFeedLoadStatus(itemCount = 12))
    }

    @Test
    fun `a failed first load carries the mapped error`() {
        val status = loadStates(refresh = LoadState.Error(IOException("down"))).toFeedLoadStatus(itemCount = 0)
        assertEquals(FeedLoadStatus.InitialError(FeedError.Network), status)
    }

    @Test
    fun `a failed refresh with items on screen is not an initial error`() {
        // The reader keeps their timeline; a refresh failure is a snackbar, not
        // a full-screen retry.
        assertEquals(FeedLoadStatus.Idle, loadStates(refresh = LoadState.Error(IOException("down"))).toFeedLoadStatus(itemCount = 5))
    }

    @Test
    fun `appending wins over refreshing`() {
        // The tail shimmer and the pull indicator are different affordances and
        // only one may show.
        val status =
            loadStates(refresh = LoadState.Loading, append = LoadState.Loading).toFeedLoadStatus(itemCount = 5)
        assertEquals(FeedLoadStatus.Appending, status)
    }

    @Test
    fun `settled states are Idle`() {
        assertEquals(FeedLoadStatus.Idle, loadStates().toFeedLoadStatus(itemCount = 5))
        assertEquals(FeedLoadStatus.Idle, loadStates().toFeedLoadStatus(itemCount = 0))
    }

    // --- status x item count -> render branch -----------------------------

    @Test
    fun `nothing loaded while loading shows the shimmer, not the empty state`() {
        // Cold-start regression guard (lq9t.3.6): a never-loaded feed must
        // render the shimmer, NOT the terminal "No posts" screen.
        assertEquals(
            FeedScreenViewState.InitialLoading,
            FeedState().toViewState(FeedLoadStatus.InitialLoading, itemCount = 0),
        )
    }

    @Test
    fun `nothing loaded and settled is the empty state`() {
        assertEquals(FeedScreenViewState.Empty, FeedState().toViewState(FeedLoadStatus.Idle, itemCount = 0))
    }

    @Test
    fun `nothing loaded after a failure shows the error, carrying its variant`() {
        assertEquals(
            FeedScreenViewState.InitialError(FeedError.Unauthenticated),
            FeedState().toViewState(FeedLoadStatus.InitialError(FeedError.Unauthenticated), itemCount = 0),
        )
        assertEquals(
            FeedScreenViewState.InitialError(FeedError.FeedNotFound),
            FeedState().toViewState(FeedLoadStatus.InitialError(FeedError.FeedNotFound), itemCount = 0),
        )
    }

    @Test
    fun `an empty feed mid-refresh falls back to the empty state`() {
        // Not reachable from toFeedLoadStatus, which gates both loading
        // branches on an empty list — handled so the matrix stays total.
        assertEquals(FeedScreenViewState.Empty, FeedState().toViewState(FeedLoadStatus.Refreshing, itemCount = 0))
        assertEquals(FeedScreenViewState.Empty, FeedState().toViewState(FeedLoadStatus.Appending, itemCount = 0))
    }

    @Test
    fun `items loaded and settled is Loaded with neither indicator`() {
        val viewState = FeedState().toViewState(FeedLoadStatus.Idle, itemCount = 3)
        assertTrue(viewState is FeedScreenViewState.Loaded)
        viewState as FeedScreenViewState.Loaded
        assertEquals(false, viewState.isAppending)
        assertEquals(false, viewState.isRefreshing)
    }

    @Test
    fun `appending and refreshing drive one indicator each`() {
        val appending = FeedState().toViewState(FeedLoadStatus.Appending, itemCount = 3) as FeedScreenViewState.Loaded
        assertEquals(true, appending.isAppending)
        assertEquals(false, appending.isRefreshing)

        val refreshing = FeedState().toViewState(FeedLoadStatus.Refreshing, itemCount = 3) as FeedScreenViewState.Loaded
        assertEquals(false, refreshing.isAppending)
        assertEquals(true, refreshing.isRefreshing)
    }

    @Test
    fun `Loaded carries the state Paging knows nothing about`() {
        val state =
            FeedState(
                lastLikeTapPostUri = "at://liked",
                lastRepostTapPostUri = "at://reposted",
                videoAutoplayEnabled = false,
                outgoingPosts = persistentListOf(outgoingPost()),
            )

        val loaded = state.toViewState(FeedLoadStatus.Idle, itemCount = 1) as FeedScreenViewState.Loaded

        assertEquals("at://liked", loaded.lastLikeTapPostUri)
        assertEquals("at://reposted", loaded.lastRepostTapPostUri)
        assertEquals(false, loaded.videoAutoplayEnabled)
        assertEquals(listOf("local-1"), loaded.outgoingPosts.map { it.localId })
    }

    @Test
    fun `an initial error with items on screen still renders the items`() {
        // Not reachable either, and the safe fallback is the one that keeps the
        // reader's timeline rather than replacing it with a retry screen.
        val viewState = FeedState().toViewState(FeedLoadStatus.InitialError(FeedError.Network), itemCount = 2)
        assertTrue(viewState is FeedScreenViewState.Loaded)
    }
}

private fun loadStates(
    refresh: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
    append: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
    prepend: LoadState = LoadState.NotLoading(endOfPaginationReached = false),
): CombinedLoadStates {
    val states = LoadStates(refresh = refresh, prepend = prepend, append = append)
    return CombinedLoadStates(
        refresh = refresh,
        prepend = prepend,
        append = append,
        source = states,
        mediator = states,
    )
}

private fun outgoingPost(): OutgoingPostUi =
    OutgoingPostUi(
        localId = "local-1",
        post =
            PostUi(
                id = "at://sent",
                cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
                author = AuthorUi(did = "did:plc:viewer", handle = "viewer.bsky.social", displayName = "Viewer", avatarUrl = null),
                createdAt = Instant.parse("2026-04-25T12:00:00Z"),
                text = "sent",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            ),
        status = OutgoingPostStatusUi.Sending,
    )
