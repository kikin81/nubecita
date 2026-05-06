package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.posting.ActorTypeaheadRepository
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.TypeaheadStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pin-down tests for [ComposerViewModel]'s `@`-mention typeahead
 * pipeline. Covers:
 *
 *  - State transitions Idle → Querying → Suggestions after debounce.
 *  - Idle → Querying → NoResults for an empty actor list.
 *  - Idle (no effect) on repo failure (the "hide on error" decision).
 *  - `mapLatest` cancels an in-flight query when a newer token
 *    arrives.
 *  - `distinctUntilChanged` drops duplicate consecutive tokens.
 *  - `TypeaheadResultClicked` atomically replaces the active token
 *    with `@<handle> ` and places the cursor after the insertion.
 *  - `TypeaheadResultClicked` no-ops when the active `@`-position
 *    can't be re-located.
 *  - The next snapshot after a replacement transitions back to Idle.
 *
 * Setup notes:
 *  - The unit tests for the rest of the VM live in
 *    [ComposerViewModelTest]; here we install a [ControllableTypeaheadRepository]
 *    that suspends until explicitly completed, so we can observe
 *    intermediate states (Querying) and orchestrate cancellations.
 *  - Each text mutation is followed by `Snapshot.sendApplyNotifications()`
 *    plus `testScheduler.runCurrent()` because the production
 *    Compose recomposer drives those on every frame; in unit tests
 *    there's no frame loop, so the VM's snapshotFlow collector
 *    silently never fires unless we drive it.
 *  - `advanceTimeBy(150.milliseconds + 1ms)` drives the test scheduler
 *    past the debounce window. The 1ms slop avoids an off-by-one
 *    where `debounce` requires *strictly* more than 150ms of
 *    silence before emitting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTypeaheadTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val postingRepository = mockk<PostingRepository>(relaxed = true)
    private val parentFetchSource = mockk<ParentFetchSource>(relaxed = true)
    private val typeaheadRepo = ControllableTypeaheadRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pipeline_transitionsIdleToQueryingToSuggestions_afterDebounce() =
        runTest {
            val vm = newVm()
            val actors =
                listOf(
                    actor(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
                    actor(did = "did:plc:alex", handle = "alex.bsky.social", displayName = "Alex"),
                )

            // Pre-arrange the repo's eventual response for "ali".
            typeaheadRepo.respond("ali", actors)

            setComposerText(vm, "@ali")
            assertEquals(TypeaheadStatus.Idle, vm.uiState.value.typeahead, "before debounce, still Idle")

            advanceDebounce()
            // After the debounce fires, the pipeline emits Querying
            // synchronously and then awaits the repo. Our fake
            // already has the result ready, so `await()` completes
            // immediately and we land in Suggestions.
            val current = vm.uiState.value.typeahead
            assertTrue(current is TypeaheadStatus.Suggestions, "expected Suggestions, was $current")
            assertEquals("ali", (current as TypeaheadStatus.Suggestions).query)
            assertEquals(actors, current.results.toList())
        }

    @Test
    fun pipeline_emptyActors_landInNoResults() =
        runTest {
            val vm = newVm()
            typeaheadRepo.respond("zzz", emptyList())

            setComposerText(vm, "@zzz")
            advanceDebounce()

            assertEquals(TypeaheadStatus.NoResults("zzz"), vm.uiState.value.typeahead)
        }

    @Test
    fun pipeline_repoFailure_collapsesToIdle_withoutEmittingShowError() =
        runTest {
            val vm = newVm()
            typeaheadRepo.fail("ali", RuntimeException("offline"))

            vm.effects.test {
                setComposerText(vm, "@ali")
                advanceDebounce()

                assertEquals(TypeaheadStatus.Idle, vm.uiState.value.typeahead)
                expectNoEvents()
            }
        }

    @Test
    fun pipeline_mapLatest_cancelsInFlightQueryWhenNewerTokenArrives() =
        runTest {
            val vm = newVm()
            // Leave "ali" pending forever via a CompletableDeferred so
            // we can observe it being cancelled mid-flight.
            val aliDeferred = typeaheadRepo.gate("ali")
            // Pre-arrange "alic" to land instantly once mapLatest
            // restarts on the new token.
            typeaheadRepo.respond("alic", listOf(actor("did:plc:alice", "alice.bsky.social", "Alice")))

            setComposerText(vm, "@ali")
            advanceDebounce()
            assertEquals(TypeaheadStatus.Querying("ali"), vm.uiState.value.typeahead)

            // User types one more char — token becomes "alic". snapshot
            // fires, queryFlow emits "alic", mapLatest cancels the
            // suspended "ali" call, restarts for "alic".
            setComposerText(vm, "@alic")
            advanceDebounce()

            // The cancelled "ali" deferred is still pending — completing
            // it now must NOT clobber the Suggestions("alic", ...) that
            // mapLatest wrote. The cancellation throws inside the
            // suspending `await()`, the cancelled branch's setState
            // never runs.
            aliDeferred.complete(
                Result.success(listOf(actor("did:plc:fakealice", "fake.bsky.social", "Fake"))),
            )
            advanceDebounce()

            val current = vm.uiState.value.typeahead
            assertTrue(current is TypeaheadStatus.Suggestions, "expected Suggestions, was $current")
            assertEquals("alic", (current as TypeaheadStatus.Suggestions).query)
        }

    @Test
    fun pipeline_distinctUntilChanged_dropsDuplicateConsecutiveTokens() =
        runTest {
            val vm = newVm()
            typeaheadRepo.respond("a", listOf(actor("did:plc:alice", "alice.bsky.social", "Alice")))

            setComposerText(vm, "@a")
            advanceDebounce()
            assertEquals(1, typeaheadRepo.callLog.size)

            // Delete and retype the same char — token is "a" both
            // times. distinctUntilChanged sees consecutive identical
            // values after the snapshot collector emits "" then "a"
            // again. The "" sentinel goes to Idle (different from "a"),
            // then "a" comes back. So we expect TWO calls in this
            // case — distinctUntilChanged dedupes only when the SAME
            // token appears back-to-back without an intervening
            // sentinel. Verify by flooding the same token.
            setComposerText(vm, "@a")
            setComposerText(vm, "@a")
            advanceDebounce()
            // Repeated "@a" without an intervening clear should not
            // re-fire the repo — distinctUntilChanged drops the
            // duplicates.
            assertEquals(1, typeaheadRepo.callLog.size)
        }

    @Test
    fun typeaheadResultClicked_insertsCanonicalHandleWithTrailingSpaceAndCursor() =
        runTest {
            val vm = newVm()
            // Set up a partial token in the field.
            vm.textFieldState.setTextAndPlaceCursorAtEnd("hi @al")
            Snapshot.sendApplyNotifications()
            testScheduler.runCurrent()

            val pickedActor = actor(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")
            vm.handleEvent(ComposerEvent.TypeaheadResultClicked(pickedActor))

            assertEquals("hi @alice.bsky.social ", vm.textFieldState.text.toString())
            assertEquals(
                "hi @alice.bsky.social ".length,
                vm.textFieldState.selection.end,
                "cursor should land right after the inserted segment",
            )
        }

    @Test
    fun typeaheadResultClicked_isNoOpWhenAtPositionCannotBeReLocated() =
        runTest {
            val vm = newVm()
            vm.textFieldState.setTextAndPlaceCursorAtEnd("hello there")
            // Place cursor far from any '@' — `findActiveMentionStart`
            // returns null and the click should be a no-op.
            vm.textFieldState.edit { placeCursorAtEnd() }
            Snapshot.sendApplyNotifications()
            testScheduler.runCurrent()

            val originalText = vm.textFieldState.text.toString()
            vm.handleEvent(
                ComposerEvent.TypeaheadResultClicked(
                    actor(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice"),
                ),
            )

            assertEquals(originalText, vm.textFieldState.text.toString(), "text must not change")
        }

    @Test
    fun typeaheadResultClicked_returnsTypeaheadStateToIdleAfterReplacement() =
        runTest {
            val vm = newVm()
            // Get into a Suggestions state so we can observe the
            // post-replacement transition back to Idle.
            typeaheadRepo.respond("al", listOf(actor("did:plc:alice", "alice.bsky.social", "Alice")))
            setComposerText(vm, "@al")
            advanceDebounce()
            assertTrue(vm.uiState.value.typeahead is TypeaheadStatus.Suggestions)

            vm.handleEvent(
                ComposerEvent.TypeaheadResultClicked(
                    actor("did:plc:alice", "alice.bsky.social", "Alice"),
                ),
            )
            // The replacement edits textFieldState; the snapshotFlow
            // collector picks up the trailing-space boundary and
            // synchronously sets typeahead = Idle.
            Snapshot.sendApplyNotifications()
            testScheduler.runCurrent()

            assertEquals(TypeaheadStatus.Idle, vm.uiState.value.typeahead)
        }

    // ---------- harness ----------

    private fun newVm(): ComposerViewModel =
        ComposerViewModel(
            route = ComposerRoute(replyToUri = null),
            postingRepository = postingRepository,
            parentFetchSource = parentFetchSource,
            actorTypeaheadRepository = typeaheadRepo,
        )

    private fun TestScope.setComposerText(
        vm: ComposerViewModel,
        text: String,
    ) {
        vm.textFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }

    /**
     * Drives the test scheduler past the debounce window plus a
     * 1ms slop so the `debounce` operator's strict `>` condition
     * fires deterministically.
     */
    private fun TestScope.advanceDebounce() {
        testScheduler.advanceTimeBy(151.milliseconds)
        testScheduler.runCurrent()
    }

    private fun actor(
        did: String,
        handle: String,
        displayName: String? = null,
    ): ActorTypeaheadUi =
        ActorTypeaheadUi(
            did = did,
            handle = handle,
            displayName = displayName,
            avatarUrl = null,
        )
}

/**
 * Test fake for [ActorTypeaheadRepository] that records every query
 * and either resolves to a pre-registered response or suspends on a
 * gateable [CompletableDeferred].
 *
 * - [respond] / [fail] register a result for a query (resolves
 *   immediately on the next `searchTypeahead(query)` call).
 * - [gate] registers an explicitly-completable deferred for a query
 *   (suspends until the test calls `.complete(...)`).
 *
 * [callLog] is the chronological list of every query the VM passed
 * to the repo; tests use it to assert that mapLatest cancellation
 * and distinctUntilChanged correctly dedupe / cancel.
 */
private class ControllableTypeaheadRepository : ActorTypeaheadRepository {
    private val deferreds = mutableMapOf<String, CompletableDeferred<Result<List<ActorTypeaheadUi>>>>()
    val callLog: MutableList<String> = mutableListOf()

    fun respond(
        query: String,
        actors: List<ActorTypeaheadUi>,
    ) {
        gate(query).complete(Result.success(actors))
    }

    fun fail(
        query: String,
        throwable: Throwable,
    ) {
        gate(query).complete(Result.failure(throwable))
    }

    fun gate(query: String): CompletableDeferred<Result<List<ActorTypeaheadUi>>> = deferreds.getOrPut(query) { CompletableDeferred() }

    override suspend fun searchTypeahead(query: String): Result<List<ActorTypeaheadUi>> {
        callLog += query
        return gate(query).await()
    }
}
