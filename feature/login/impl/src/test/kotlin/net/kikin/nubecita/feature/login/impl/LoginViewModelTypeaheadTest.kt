package net.kikin.nubecita.feature.login.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pin-down tests for the login handle typeahead.
 *
 * The debounce lives INSIDE `mapLatest`, so these drive virtual time with
 * `advanceTimeBy` on the same scheduler `Dispatchers.Main` runs on — a
 * `runTest` with its own scheduler would never advance `viewModelScope`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LoginViewModelTypeaheadTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private fun actor(
        did: String,
        handle: String,
        displayName: String? = null,
    ) = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = null)

    @Test
    fun `a query shorter than the minimum never reaches the network`() =
        runTest(mainDispatcher.dispatcher) {
            val search = FakePublicActorSearch()
            val vm = newViewModel(publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("a"))
            advanceUntilIdle()

            assertEquals(emptyList<String>(), search.queries, "one character must not query")
            assertTrue(
                vm.uiState.value.suggestions
                    .isEmpty(),
            )
        }

    @Test
    fun `typing past the minimum queries after the debounce and populates suggestions`() =
        runTest(mainDispatcher.dispatcher) {
            val search =
                FakePublicActorSearch().apply {
                    result = Result.success(listOf(actor("did:plc:1", "alice.bsky.social", "Alice")))
                }
            val vm = newViewModel(publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("alice"))
            // Before the debounce elapses nothing has been asked of the network.
            advanceTimeBy(100.milliseconds)
            assertEquals(emptyList<String>(), search.queries, "must not query before the debounce")

            advanceUntilIdle()
            assertEquals(listOf("alice"), search.queries)
            assertEquals(1, vm.uiState.value.suggestions.size)
            assertEquals(
                "alice.bsky.social",
                vm.uiState.value.suggestions
                    .first()
                    .handle,
            )
        }

    @Test
    fun `a burst of keystrokes only queries for the final one`() =
        runTest(mainDispatcher.dispatcher) {
            val search = FakePublicActorSearch()
            val vm = newViewModel(publicActorSearch = search)

            // Each keystroke lands well inside the debounce window.
            listOf("al", "ali", "alic", "alice").forEach {
                vm.handleEvent(LoginEvent.HandleChanged(it))
                advanceTimeBy(50.milliseconds)
            }
            advanceUntilIdle()

            assertEquals(listOf("alice"), search.queries, "intermediate keystrokes must be superseded")
        }

    @Test
    fun `a newer query supersedes an in-flight one, and the stale result never lands`() =
        runTest(mainDispatcher.dispatcher) {
            val inFlight = CompletableDeferred<Unit>()
            val search =
                FakePublicActorSearch().apply {
                    result = Result.success(listOf(actor("did:plc:stale", "stale.bsky.social")))
                    gate = { inFlight.await() }
                }
            val vm = newViewModel(publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("alice"))
            advanceUntilIdle()
            assertEquals(listOf("alice"), search.queries, "first query is in flight")

            // A newer keystroke arrives while the first request is still hanging.
            search.gate = null
            search.result = Result.success(listOf(actor("did:plc:fresh", "fresh.bsky.social")))
            vm.handleEvent(LoginEvent.HandleChanged("bob"))
            advanceUntilIdle()

            // Releasing the superseded request must not overwrite the newer result.
            inFlight.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf("fresh.bsky.social"),
                vm.uiState.value.suggestions
                    .map { it.handle },
                "the stale response must not clobber the newer one",
            )
        }

    @Test
    fun `a leading at-sign is stripped before querying`() =
        runTest(mainDispatcher.dispatcher) {
            val search = FakePublicActorSearch()
            val vm = newViewModel(publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("@alice"))
            advanceUntilIdle()

            assertEquals(listOf("alice"), search.queries)
        }

    @Test
    fun `hosts fill in per row after the suggestions render`() =
        runTest(mainDispatcher.dispatcher) {
            val search =
                FakePublicActorSearch().apply {
                    result =
                        Result.success(
                            listOf(actor("did:plc:1", "alice.bsky.social"), actor("did:plc:2", "bob.example.com")),
                        )
                    // Only one resolves — the other must simply stay blank.
                    hosts = mapOf("did:plc:1" to "morel.us-east.host.bsky.network")
                }
            val vm = newViewModel(publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("friend"))
            advanceUntilIdle()

            val suggestions = vm.uiState.value.suggestions
            assertEquals("morel.us-east.host.bsky.network", suggestions.first { it.did == "did:plc:1" }.pdsHost)
            assertEquals(null, suggestions.first { it.did == "did:plc:2" }.pdsHost, "an unresolved host stays blank")
        }

    @Test
    fun `a failed search clears suggestions instead of surfacing an error`() =
        runTest(mainDispatcher.dispatcher) {
            val search =
                FakePublicActorSearch().apply {
                    result = Result.success(listOf(actor("did:plc:1", "alice.bsky.social")))
                }
            val vm = newViewModel(publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("alice"))
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.suggestions.size)

            search.result = Result.failure(java.io.IOException("offline"))
            vm.handleEvent(LoginEvent.HandleChanged("alicia"))
            advanceUntilIdle()

            assertTrue(
                vm.uiState.value.suggestions
                    .isEmpty(),
                "a failure empties the list",
            )
            assertEquals(null, vm.uiState.value.errorMessage, "typeahead failure must not surface a login error")
        }

    @Test
    fun `selecting a suggestion fills the field, closes the list, and begins login`() =
        runTest(mainDispatcher.dispatcher) {
            val search =
                FakePublicActorSearch().apply {
                    result = Result.success(listOf(actor("did:plc:1", "alice.bsky.social", "Alice")))
                }
            val auth = FakeAuthRepository()
            val vm = newViewModel(authRepository = auth, publicActorSearch = search)

            vm.handleEvent(LoginEvent.HandleChanged("alice"))
            advanceUntilIdle()

            vm.handleEvent(LoginEvent.SuggestionSelected("alice.bsky.social"))
            advanceUntilIdle()

            assertEquals("alice.bsky.social", vm.uiState.value.handle)
            assertTrue(
                vm.uiState.value.suggestions
                    .isEmpty(),
                "the list must close on selection",
            )
            // Picking your own account means "go" — the tap starts OAuth rather
            // than making the user reach for the button. This is the part that
            // actually launches a browser, so it is asserted, not assumed.
            assertEquals(1, auth.beginLoginInvocations)
            assertEquals("alice.bsky.social", auth.lastBeginLoginHandle)
        }

    @Test
    fun `a selected handle is normalized before it reaches beginLogin`() =
        runTest(mainDispatcher.dispatcher) {
            val auth = FakeAuthRepository()
            val vm = newViewModel(authRepository = auth, publicActorSearch = FakePublicActorSearch())

            // Handles come back from the AppView clean, but selection shares
            // submitLogin with the typed path, so the normalization must hold here too.
            vm.handleEvent(LoginEvent.SuggestionSelected("@Alice.BSKY.social"))
            advanceUntilIdle()

            assertEquals("alice.bsky.social", auth.lastBeginLoginHandle)
        }
}
