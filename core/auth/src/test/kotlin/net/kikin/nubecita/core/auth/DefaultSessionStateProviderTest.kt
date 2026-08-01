package net.kikin.nubecita.core.auth

import app.cash.turbine.test
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException

class DefaultSessionStateProviderTest {
    private val telemetry = mockk<SessionTelemetry>(relaxed = true)

    /** Mirrors `DefaultSessionStateProvider.RETRY_DELAYS_MILLIS.size`. */
    private val boundedRetryCount = 3

    /**
     * The store stream defaults to [emptyFlow] so the pull-path tests below
     * exercise `refresh()` in isolation; the reactive tests pass an explicit
     * stream. [scope] is the test's `backgroundScope` so the init collector is
     * torn down with the test rather than leaking across cases.
     */
    private fun TestScope.provider(
        stream: Flow<SessionLoadResult> = emptyFlow(),
        reader: SessionReader,
    ) = DefaultSessionStateProvider(reader, telemetry, { stream }, backgroundScope)

    @Test
    fun `initial state is Loading before any refresh`() =
        runTest {
            val provider = provider { SessionLoadResult.Absent }
            assertEquals(SessionState.Loading, provider.state.value)
        }

    @Test
    fun `a session cleared by the SDK flips the state to SignedOut without any refresh call`() =
        runTest {
            // Regression for nubecita-kzsd. DpopAuthProvider.failRefresh clears
            // the store on invalid_grant; no app code initiates that write, so
            // with a pull-only provider the app kept reporting SignedIn and the
            // user went on tapping like/follow against a revoked token until
            // something happened to call refresh() (next cold start / a worker).
            val store = MutableStateFlow<SessionLoadResult>(SessionLoadResult.Loaded(sampleSession()))
            val provider =
                provider(stream = store) { error("refresh() must not be needed to observe a clear") }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                assertTrue(awaitItem() is SessionState.SignedIn, "the stored session must surface as SignedIn")

                store.value = SessionLoadResult.Absent // the SDK's clear()

                assertEquals(SessionState.SignedOut, awaitItem(), "a clear must route to Login immediately")
            }
        }

    @Test
    fun `a transient stream read error is retried and never surfaces as SignedOut`() =
        runTest {
            var attempts = 0
            val flaky =
                flow {
                    if (attempts++ < 2) throw IOException("disk contention")
                    emit(SessionLoadResult.Loaded(sampleSession()))
                }
            val provider = provider(stream = flaky) { error("unused") }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                assertTrue(awaitItem() is SessionState.SignedIn, "the retry must recover, not sign the user out")
            }
            verify(exactly = 0) { telemetry.onSessionReadErrorTerminal(any()) }
        }

    @Test
    fun `a stream read error surviving every retry records terminal telemetry and signs out`() =
        runTest {
            val alwaysFails = flow<SessionLoadResult> { throw GeneralSecurityException("keystore gone") }
            val provider = provider(stream = alwaysFails) { error("unused") }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                assertEquals(SessionState.SignedOut, awaitItem())
            }
            verify(exactly = 1) { telemetry.onSessionReadErrorTerminal(any()) }
        }

    @Test
    fun `a successful refresh revives an observer killed by a terminal read error`() =
        runTest {
            // A terminal storage failure completes the collector rather than
            // looping — an unbounded retry would spin forever against a
            // permanently invalidated Keystore. refresh() is what revives it, so
            // the app doesn't spend the rest of the process blind to SDK clears.
            var streamAttempts = 0
            val session = sampleSession()
            val store = MutableStateFlow<SessionLoadResult>(SessionLoadResult.Loaded(session))
            val stream =
                flow {
                    // Fail the initial collection AND all three bounded retries so
                    // the collector goes terminal; succeed once refresh() revives it.
                    if (streamAttempts++ <= boundedRetryCount) throw IOException("disk unreadable")
                    emitAll(store)
                }
            val provider = provider(stream = stream) { SessionLoadResult.Loaded(session) }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                assertEquals(SessionState.SignedOut, awaitItem(), "terminal read error routes to Login")

                // The store is readable again — refresh() must restart the observer.
                provider.refresh()
                assertTrue(awaitItem() is SessionState.SignedIn)

                store.value = SessionLoadResult.Absent // a later SDK clear()

                assertEquals(
                    SessionState.SignedOut,
                    awaitItem(),
                    "the revived observer must still see clears it did not initiate",
                )
            }
        }

    @Test
    fun `a refresh that still fails does not re-arm a doomed observer`() =
        runTest {
            var streamAttempts = 0
            val stream =
                flow<SessionLoadResult> {
                    streamAttempts++
                    throw IOException("disk unreadable")
                }
            val provider = provider(stream = stream) { SessionLoadResult.ReadError(IOException("still broken")) }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                assertEquals(SessionState.SignedOut, awaitItem())
                val afterFirstCollection = streamAttempts

                provider.refresh() // store is still unreadable

                assertEquals(
                    afterFirstCollection,
                    streamAttempts,
                    "a failing refresh must not restart the collector — that is the hot-loop we avoid",
                )
            }
        }

    @Test
    fun `a clear observed mid-refresh is not overwritten by the stale read`() =
        runTest {
            // The dangerous ordering: refresh() reads SignedIn, the SDK clears the
            // session while that read is in flight, the observer publishes
            // SignedOut — and then refresh() writes its stale SignedIn back on top.
            // DataStore only re-emits on the next write, so nothing would correct
            // it: the app is signed-in against a cleared store, which is precisely
            // the zombie session this class exists to prevent.
            val store = MutableStateFlow<SessionLoadResult>(SessionLoadResult.Loaded(sampleSession()))
            val readStarted = CompletableDeferred<Unit>()
            val provider =
                provider(stream = store) {
                    readStarted.complete(Unit)
                    // Suspend inside the read so the observer can publish a clear
                    // before refresh() gets to its own write.
                    delay(50)
                    SessionLoadResult.Loaded(sampleSession()) // stale by the time it lands
                }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                assertTrue(awaitItem() is SessionState.SignedIn)

                val refreshing = launch { provider.refresh() }
                readStarted.await()
                store.value = SessionLoadResult.Absent // the SDK's clear(), mid-read
                assertEquals(SessionState.SignedOut, awaitItem(), "the observer must publish the clear")

                refreshing.join()

                expectNoEvents()
                assertEquals(
                    SessionState.SignedOut,
                    provider.state.value,
                    "refresh() must not resurrect a session the observer already saw cleared",
                )
            }
        }

    @Test
    fun `refresh with a loaded session emits SignedIn carrying handle and did`() =
        runTest {
            val session = sampleSession(handle = "alice.bsky.social", did = "did:plc:alice")
            val provider = provider { SessionLoadResult.Loaded(session) }

            provider.refresh()

            assertEquals(
                SessionState.SignedIn(
                    handle = "alice.bsky.social",
                    did = "did:plc:alice",
                    pdsUrl = "https://pds.example",
                ),
                provider.state.value,
            )
        }

    @Test
    fun `refresh with an absent session emits SignedOut immediately without retry delay`() =
        runTest {
            val provider = provider { SessionLoadResult.Absent }

            provider.refresh()

            assertEquals(SessionState.SignedOut, provider.state.value)
            assertEquals(0, currentTime, "Absent must short-circuit — no retry/backoff on the splash path")
        }

    @Test
    fun `refresh with session whose handle is null stays in Loading`() =
        runTest {
            val session = sampleSession(handle = null, did = "did:plc:alice")
            val provider = provider { SessionLoadResult.Loaded(session) }

            provider.refresh()

            assertEquals(SessionState.Loading, provider.state.value)
        }

    @Test
    fun `refresh with session whose did is null stays in Loading`() =
        runTest {
            val session = sampleSession(handle = "alice.bsky.social", did = null)
            val provider = provider { SessionLoadResult.Loaded(session) }

            provider.refresh()

            assertEquals(SessionState.Loading, provider.state.value)
        }

    @Test
    fun `transient read error recovers on retry and never surfaces SignedOut`() =
        runTest {
            // First read fails (e.g. Keystore not ready just after boot), the
            // retry succeeds. The user must never be routed to Login: the state
            // must go Loading → SignedIn with no SignedOut in between.
            var attempts = 0
            val session = sampleSession(handle = "alice.bsky.social", did = "did:plc:alice")
            val provider =
                provider {
                    attempts++
                    if (attempts == 1) {
                        SessionLoadResult.ReadError(GeneralSecurityException("keystore not ready"))
                    } else {
                        SessionLoadResult.Loaded(session)
                    }
                }

            provider.state.test {
                assertEquals(SessionState.Loading, awaitItem())
                val job = launch { provider.refresh() }
                assertEquals(
                    SessionState.SignedIn(
                        handle = "alice.bsky.social",
                        did = "did:plc:alice",
                        pdsUrl = "https://pds.example",
                    ),
                    awaitItem(),
                    "the only emission after a recovered read error must be SignedIn",
                )
                job.join()
            }
            assertEquals(2, attempts)
        }

    @Test
    fun `persistent read error exhausts bounded retries then signs out with terminal telemetry`() =
        runTest {
            var attempts = 0
            val cause = IOException("disk unreadable")
            val provider =
                provider {
                    attempts++
                    SessionLoadResult.ReadError(cause)
                }

            provider.refresh()

            assertEquals(SessionState.SignedOut, provider.state.value)
            assertEquals(4, attempts, "1 initial read + 3 bounded retries")
            assertTrue(currentTime in 4_000..6_000, "retries must be bounded to ~5s, took ${currentTime}ms")
            verify(exactly = 1) { telemetry.onSessionReadErrorTerminal(cause) }
        }

    @Test
    fun `recovered read error records no terminal telemetry`() =
        runTest {
            var attempts = 0
            val provider =
                provider {
                    attempts++
                    if (attempts < 3) {
                        SessionLoadResult.ReadError(IOException("still flaky"))
                    } else {
                        SessionLoadResult.Absent
                    }
                }

            provider.refresh()

            assertEquals(SessionState.SignedOut, provider.state.value)
            verify(exactly = 0) { telemetry.onSessionReadErrorTerminal(any()) }
        }

    @Test
    fun `subsequent refresh after sign-in transitions to SignedOut when store clears`() =
        runTest {
            var current: SessionLoadResult =
                SessionLoadResult.Loaded(sampleSession(handle = "alice.bsky.social"))
            val provider = provider { current }

            provider.refresh()
            assertEquals(
                SessionState.SignedIn(
                    handle = "alice.bsky.social",
                    did = "did:plc:samplesubject",
                    pdsUrl = "https://pds.example",
                ),
                provider.state.value,
            )

            current = SessionLoadResult.Absent
            provider.refresh()
            assertEquals(SessionState.SignedOut, provider.state.value)
        }
}
