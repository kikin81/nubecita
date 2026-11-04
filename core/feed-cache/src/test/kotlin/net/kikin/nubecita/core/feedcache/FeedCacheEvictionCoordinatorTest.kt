package net.kikin.nubecita.core.feedcache

import androidx.paging.PagingData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedCacheEvictionCoordinatorTest {
    private val signedIn = SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:viewer")

    @Test
    fun `clears the previous account on sign-out`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = coordinator(session)
            f.coordinator.start()
            runCurrent()
            assertEquals(emptyList<String>(), f.repo.cleared)

            session.value = SessionState.SignedOut
            runCurrent()

            assertEquals(listOf("did:plc:viewer"), f.repo.cleared)
        }

    @Test
    fun `clears the leaving account on account switch`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = coordinator(session)
            f.coordinator.start()
            runCurrent()

            session.value = SessionState.SignedIn(handle = "other.bsky.social", did = "did:plc:other")
            runCurrent()

            assertEquals(listOf("did:plc:viewer"), f.repo.cleared)
        }

    @Test
    fun `does not clear when starting from signed-out`() =
        runTest {
            val session = MutableStateFlow<SessionState>(SessionState.Loading)
            val f = coordinator(session)
            f.coordinator.start()
            runCurrent()

            session.value = SessionState.SignedOut
            runCurrent()

            assertEquals(emptyList<String>(), f.repo.cleared)
        }

    @Test
    fun `start is idempotent`() =
        runTest {
            val session = MutableStateFlow<SessionState>(signedIn)
            val f = coordinator(session)
            f.coordinator.start()
            f.coordinator.start()
            runCurrent()

            session.value = SessionState.SignedOut
            runCurrent()

            // Only one collector -> exactly one clear, not two.
            assertEquals(listOf("did:plc:viewer"), f.repo.cleared)
        }

    private class Fixture(
        val coordinator: FeedCacheEvictionCoordinator,
        val repo: RecordingFeedRepository,
    )

    private fun TestScope.coordinator(session: MutableStateFlow<SessionState>): Fixture {
        val repo = RecordingFeedRepository()
        return Fixture(
            coordinator =
                FeedCacheEvictionCoordinator(
                    scope = backgroundScope,
                    sessionStateProvider = FakeSessionStateProvider(session),
                    feedRepository = repo,
                ),
            repo = repo,
        )
    }

    private class RecordingFeedRepository : FeedRepository {
        val cleared = mutableListOf<String>()

        override fun pagedFeed(
            feedKey: FeedKey,
            initialPosition: Int?,
        ): Flow<PagingData<FeedItemUi>> = emptyFlow()

        override fun head(
            feedKey: FeedKey,
            n: Int,
        ): Flow<List<PostUi>> = emptyFlow()

        override suspend fun refresh(feedKey: FeedKey): Result<Boolean> = Result.success(true)

        override suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> = Result.success(CatchUpOutcome.NOT_POSSIBLE)

        override suspend fun anchor(feedKey: FeedKey): FeedAnchor? = null

        override suspend fun saveAnchor(
            feedKey: FeedKey,
            itemKey: String,
            offset: Int,
        ) = Unit

        override suspend fun clearAnchor(feedKey: FeedKey) = Unit

        override fun newPostAuthors(feedKey: FeedKey): Flow<ImmutableList<AuthorUi>> = flowOf(persistentListOf())

        override suspend fun dismissNewPosts(feedKey: FeedKey) = Unit

        override suspend fun trimToCap(
            feedKey: FeedKey,
            cap: Int,
            anchorItemPosition: Int?,
            ceiling: Int,
        ) = Unit

        override suspend fun clearAccount(accountDid: String) {
            cleared += accountDid
        }
    }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() = Unit
    }
}
