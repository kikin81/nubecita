package net.kikin.nubecita.feature.chats.impl

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins the shared pipeline's timing directly.
 *
 * The three pickers' own tests drive time with `advanceUntilIdle`, so they pass
 * for any debounce value at all — the duration used to sit in three per-screen
 * constants and was equally uncovered there. Now that one default serves all
 * three, a bad edit reaches every picker at once, so the window is asserted.
 *
 * Collected into a list by a launched collector rather than via Turbine:
 * `awaitItem()` lets the test scheduler skip ahead to whatever delay is
 * pending, so it would happily pass for a debounce of five seconds. Advancing
 * virtual time by hand and reading what has arrived so far catches a window
 * that is too LONG as well as one that is too short.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecipientSearchResultTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repo = mockk<ActorRepository>(relaxed = true)

    private fun actor(did: String) = ActorUi(did, "$did.bsky", null, null)

    @Test
    fun `search fires only once the debounce window has fully elapsed`() =
        runTest(mainDispatcher.dispatcher) {
            every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
            coEvery { repo.searchTypeahead(any(), any()) } returns Result.success(listOf(actor("did:found")))
            val queries = MutableSharedFlow<String>(replay = 1)
            val seen = mutableListOf<RecipientSearchResult>()

            val collector =
                launch {
                    recipientSearchResults(
                        queries = queries,
                        actorRepository = repo,
                        selfDid = "did:self",
                    ).toList(seen)
                }

            queries.emit("alice")
            runCurrent()
            // The spinner must not wait on the debounce.
            assertEquals(listOf(RecipientSearchResult.Searching), seen)

            // One millisecond short of the window — a shorter debounce fails here.
            advanceTimeBy(249.milliseconds)
            assertEquals(listOf(RecipientSearchResult.Searching), seen)

            // Crossing the window — a longer debounce fails here, because time is
            // advanced by hand and never skips ahead to a pending delay.
            advanceTimeBy(2.milliseconds)
            assertEquals(2, seen.size)
            assertEquals(listOf("did:found"), (seen[1] as RecipientSearchResult.Results).actors.map { it.did })

            collector.cancel()
        }
}
