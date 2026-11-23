package net.kikin.nubecita.core.postinteractions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowOverlayTest {
    private fun session(did: String?) = MutableStateFlow<SessionState>(if (did == null) SessionState.SignedOut else signedIn(did))

    private fun provider(state: MutableStateFlow<SessionState>) =
        object : SessionStateProvider {
            override val state = state

            override suspend fun refresh() = Unit
        }

    @Test
    fun `overrides are dropped when the account changes`() =
        runTest {
            val state = session("did:plc:me")
            val overlay = FollowOverlay(TestScope(UnconfinedTestDispatcher(testScheduler)), provider(state))
            overlay.seed("did:plc:a", FollowOverride(true, "at://follow/1"))
            assertEquals(1, overlay.current().size)

            state.value = signedIn("did:plc:other")
            runCurrent()

            assertTrue(overlay.current().isEmpty())
        }

    @Test
    fun `clear removes only that account`() =
        runTest {
            val overlay = FollowOverlay(TestScope(UnconfinedTestDispatcher(testScheduler)), provider(session("did:plc:me")))
            overlay.seed("did:plc:a", FollowOverride(true, "u1"))
            overlay.seed("did:plc:b", FollowOverride(false, null))

            overlay.write("did:plc:a", null, "did:plc:me")

            assertEquals(setOf("did:plc:b"), overlay.current().keys)
        }

    @Test
    fun `an override is applied to the matching actor only`() {
        val a = actorFixture(did = "did:plc:a")
        val b = actorFixture(did = "did:plc:b")
        val overrides = mapOf("did:plc:a" to FollowOverride(true, "u1"))

        assertEquals(a.copy(isFollowing = true, followUri = "u1"), a.withFollowOverride(overrides))
        assertEquals(b, b.withFollowOverride(overrides))
    }

    @Test
    fun `an override written by one account is not applied for another before the clear runs`() =
        runTest {
            val state = session("did:plc:me")
            // A scope nothing advances: the account-change clear never gets to run.
            val overlay = FollowOverlay(TestScope(StandardTestDispatcher(testScheduler)), provider(state))
            overlay.seed("did:plc:a", FollowOverride(true, "at://follow/1"))
            assertEquals(setOf("did:plc:a"), overlay.current().keys)

            state.value = signedIn("did:plc:other")

            assertTrue(overlay.current().isEmpty(), "the other account must not see the first account's override")
            assertNull(overlay["did:plc:a"])
            assertTrue(overlay.overrides.first().isEmpty())

            // The new account's own write is visible; the old one stays hidden.
            overlay.seed("did:plc:b", FollowOverride(false, null), account = "did:plc:other")
            assertEquals(setOf("did:plc:b"), overlay.current().keys)

            // Signing back in as the first account does not bring its entry back either, once cleared.
            state.value = SessionState.SignedOut
            assertTrue(overlay.current().isEmpty())
        }

    @Test
    fun `an entry left by another account does not block a write`() =
        runTest {
            val state = session("did:plc:a")
            // Nothing advances this scope, so the account-change clear never runs.
            val overlay = FollowOverlay(TestScope(StandardTestDispatcher(testScheduler)), provider(state))
            overlay.write("did:plc:x", FollowOverride(true, "at://follow/as-a"), account = "did:plc:a")
            state.value = signedIn("did:plc:b")

            overlay.write("did:plc:x", FollowOverride(true, "at://follow/as-b"), account = "did:plc:b")

            assertEquals(mapOf("did:plc:x" to FollowOverride(true, "at://follow/as-b")), overlay.current())
        }

    @Test
    fun `a write for an account that is no longer signed in is dropped`() =
        runTest {
            val state = session("did:plc:a")
            val overlay = FollowOverlay(TestScope(StandardTestDispatcher(testScheduler)), provider(state))
            state.value = signedIn("did:plc:b")

            overlay.write("did:plc:x", FollowOverride(true, "at://follow/late"), account = "did:plc:a")
            state.value = signedIn("did:plc:a")

            assertTrue(overlay.current().isEmpty())
        }
}
