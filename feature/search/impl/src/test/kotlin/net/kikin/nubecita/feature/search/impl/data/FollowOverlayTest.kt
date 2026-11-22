package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
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
            overlay.set("did:plc:a", FollowOverride(true, "at://follow/1"))
            assertEquals(1, overlay.overrides.value.size)

            state.value = signedIn("did:plc:other")
            runCurrent()

            assertTrue(overlay.overrides.value.isEmpty())
        }

    @Test
    fun `clear removes only that account`() =
        runTest {
            val overlay = FollowOverlay(TestScope(UnconfinedTestDispatcher(testScheduler)), provider(session("did:plc:me")))
            overlay.set("did:plc:a", FollowOverride(true, "u1"))
            overlay.set("did:plc:b", FollowOverride(false, null))

            overlay.clear("did:plc:a")

            assertEquals(setOf("did:plc:b"), overlay.overrides.value.keys)
        }

    @Test
    fun `an override is applied to the matching actor only`() {
        val a = actorFixture(did = "did:plc:a")
        val b = actorFixture(did = "did:plc:b")
        val overrides = mapOf("did:plc:a" to FollowOverride(true, "u1"))

        assertEquals(a.copy(isFollowing = true, followUri = "u1"), a.withFollowOverride(overrides))
        assertEquals(b, b.withFollowOverride(overrides))
    }
}
