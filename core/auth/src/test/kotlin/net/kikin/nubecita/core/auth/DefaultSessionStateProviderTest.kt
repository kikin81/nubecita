package net.kikin.nubecita.core.auth

import app.cash.turbine.test
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException

class DefaultSessionStateProviderTest {
    private val telemetry = mockk<SessionTelemetry>(relaxed = true)

    private fun provider(reader: SessionReader) = DefaultSessionStateProvider(reader, telemetry)

    @Test
    fun `initial state is Loading before any refresh`() {
        val provider = provider { SessionLoadResult.Absent }
        assertEquals(SessionState.Loading, provider.state.value)
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
