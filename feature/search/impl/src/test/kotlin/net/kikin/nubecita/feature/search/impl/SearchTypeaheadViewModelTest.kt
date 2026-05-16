package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.posting.ActorTypeaheadRepository
import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchTypeaheadViewModel]. Same `UnconfinedTestDispatcher
 * + runTest` shape as [SearchActorsViewModelTest]. No snapshotFlow involved
 * (the parent VM owns the textFieldState), no debounce inside this VM, so
 * no `Snapshot.sendApplyNotifications()` / `advanceTimeBy` needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchTypeaheadViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = ControllableTypeaheadRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle_repoNotCalled() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            runCurrent()

            assertEquals(SearchTypeaheadStatus.Idle, vm.uiState.value.status)
            assertEquals(0, repo.callLog.size, "VM init must not hit the repo")
        }

    @Test
    fun setQuery_blank_stateStaysIdle_repoNotCalled() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            runCurrent()

            // Empty + whitespace are both blank-equivalent per the VM contract.
            vm.setQuery("")
            runCurrent()
            assertEquals(SearchTypeaheadStatus.Idle, vm.uiState.value.status)

            vm.setQuery("   ")
            runCurrent()
            assertEquals(SearchTypeaheadStatus.Idle, vm.uiState.value.status)

            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_singleActor_emitsSuggestions_withEmptyPeople() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            val alice = actor("did:plc:alice", "alice.bsky.social", "Alice")
            repo.respond("ali", listOf(alice))

            vm.setQuery("ali")
            runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is SearchTypeaheadStatus.Suggestions, "expected Suggestions, was $status")
            status as SearchTypeaheadStatus.Suggestions
            assertEquals(alice, status.topMatch)
            assertTrue(status.people.isEmpty(), "single-actor response → empty people list")
            assertEquals("ali", status.query)
        }

    @Test
    fun setQuery_nonBlank_multipleActors_emitsSuggestions_topMatchPlusPeople() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            val alice = actor("did:plc:alice", "alice.bsky.social", "Alice Chen")
            val alex = actor("did:plc:alex", "alex.bsky.social", "Alex Park")
            val albert = actor("did:plc:albert", "albert.bsky.social", null)
            repo.respond("al", listOf(alice, alex, albert))

            vm.setQuery("al")
            runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is SearchTypeaheadStatus.Suggestions, "expected Suggestions, was $status")
            status as SearchTypeaheadStatus.Suggestions
            assertEquals(alice, status.topMatch)
            assertEquals(listOf(alex, albert), status.people.toList())
        }

    @Test
    fun setQuery_emptyResponse_emitsNoResults() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            repo.respond("zxyqq", emptyList())

            vm.setQuery("zxyqq")
            runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is SearchTypeaheadStatus.NoResults, "expected NoResults, was $status")
            assertEquals("zxyqq", (status as SearchTypeaheadStatus.NoResults).query)
        }

    @Test
    fun setQuery_failure_collapsesToIdle_doesNotEmitErrorEffect() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            repo.fail("ali", java.io.IOException("flap"))

            vm.setQuery("ali")
            runCurrent()

            assertEquals(SearchTypeaheadStatus.Idle, vm.uiState.value.status)
        }

    @Test
    fun setQuery_rapidChange_cancelsPriorViaMapLatest() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            // Gate "ali" so it stays in-flight while we set a new query.
            val aliGate = repo.gate("ali")
            val alice = actor("did:plc:alice", "alice.bsky.social", "Alice")
            repo.respond("alic", listOf(alice))

            vm.setQuery("ali")
            runCurrent()
            assertTrue(vm.uiState.value.status is SearchTypeaheadStatus.Loading)

            vm.setQuery("alic")
            runCurrent()

            // The "ali" deferred completes late — mapLatest should have
            // cancelled the previous block before its onSuccess fires.
            aliGate.complete(
                Result.success(
                    listOf(actor("did:plc:stale", "stale.bsky.social", "Stale")),
                ),
            )
            runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is SearchTypeaheadStatus.Suggestions, "expected alic Suggestions, was $status")
            assertEquals(
                "did:plc:alice",
                (status as SearchTypeaheadStatus.Suggestions).topMatch.did,
                "stale 'ali' completion must not clobber 'alic' results",
            )
        }

    @Test
    fun setQuery_becomesBlankWhileFetchInFlight_resetsToIdle() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            val aliGate = repo.gate("ali")

            vm.setQuery("ali")
            runCurrent()
            assertTrue(vm.uiState.value.status is SearchTypeaheadStatus.Loading)

            vm.setQuery("")
            runCurrent()
            assertEquals(SearchTypeaheadStatus.Idle, vm.uiState.value.status)

            // Stale completion arrives after the reset. mapLatest must have
            // cancelled the in-flight block; the onSuccess must not fire.
            aliGate.complete(
                Result.success(
                    listOf(actor("did:plc:stale", "stale.bsky.social", "Stale")),
                ),
            )
            runCurrent()

            assertEquals(
                SearchTypeaheadStatus.Idle,
                vm.uiState.value.status,
                "stale ali completion must not clobber Idle after the field was cleared",
            )
        }

    @Test
    fun actorTapped_emitsNavigateToProfileEffect() =
        runTest {
            val vm = SearchTypeaheadViewModel(repo)
            vm.effects.test {
                vm.handleEvent(SearchTypeaheadEvent.ActorTapped("alice.bsky.social"))
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchTypeaheadEffect.NavigateToProfile)
                assertEquals(
                    "alice.bsky.social",
                    (effect as SearchTypeaheadEffect.NavigateToProfile).handle,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun actor(
        did: String,
        handle: String,
        displayName: String?,
    ): ActorUi =
        ActorUi(
            did = did,
            handle = handle,
            displayName = displayName,
            avatarUrl = null,
        )
}

/**
 * Test fake for [ActorTypeaheadRepository]. Same shape as the composer's
 * [net.kikin.nubecita.feature.composer.impl.ComposerViewModelTypeaheadTest]
 * fake (kept inline because the composer fake is `private` to that test).
 *
 *  - [respond] / [fail] resolve a query immediately on the next
 *    `searchTypeahead(query)` call.
 *  - [gate] registers an explicitly-completable deferred (suspends until
 *    the test calls `.complete(...)`).
 *  - [callLog] is the chronological list of queries the VM passed in.
 */
private class ControllableTypeaheadRepository : ActorTypeaheadRepository {
    private val deferreds = mutableMapOf<String, CompletableDeferred<Result<List<ActorUi>>>>()
    val callLog: MutableList<String> = mutableListOf()

    fun respond(
        query: String,
        actors: List<ActorUi>,
    ) {
        gate(query).complete(Result.success(actors))
    }

    fun fail(
        query: String,
        throwable: Throwable,
    ) {
        gate(query).complete(Result.failure(throwable))
    }

    fun gate(query: String): CompletableDeferred<Result<List<ActorUi>>> = deferreds.getOrPut(query) { CompletableDeferred() }

    override suspend fun searchTypeahead(query: String): Result<List<ActorUi>> {
        callLog += query
        return gate(query).await()
    }
}
