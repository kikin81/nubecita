package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.common.session.SessionClearable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [DefaultAuthRepository] focused on the sign-out
 * lifecycle hook into [SessionClearable] consumers.
 */
internal class DefaultAuthRepositoryTest {
    @Test
    fun `signOut calls clearSession on every session-clearable before atOAuth logout`() =
        runTest {
            val clearableA = CountingClearable()
            val clearableB = CountingClearable()
            val atOAuth =
                mockk<AtOAuth>(relaxed = true) {
                    // Snapshot the clearable call counts at the moment logout() executes
                    // so we can assert clearSession() was called BEFORE atOAuth.logout().
                    coEvery { logout() } answers {
                        clearableA.logoutInvokedAtCount = clearableA.callCount.get()
                        clearableB.logoutInvokedAtCount = clearableB.callCount.get()
                    }
                }
            val sessionStateProvider = mockk<SessionStateProvider>(relaxed = true)
            val repo =
                DefaultAuthRepository(
                    atOAuth = atOAuth,
                    sessionStateProvider = sessionStateProvider,
                    sessionClearables = setOf(clearableA, clearableB),
                )

            val result = repo.signOut()

            assertTrue(result.isSuccess, "signOut MUST surface success when atOAuth.logout succeeds")
            assertEquals(
                1,
                clearableA.callCount.get(),
                "Each SessionClearable MUST be cleared exactly once",
            )
            assertEquals(1, clearableB.callCount.get())
            // logoutInvokedAtCount captures callCount at the moment logout() ran.
            // A value of 1 means clearSession() had already been called before logout.
            assertEquals(
                1,
                clearableA.logoutInvokedAtCount,
                "clearSession MUST be called BEFORE atOAuth.logout",
            )
            assertEquals(1, clearableB.logoutInvokedAtCount)
            coVerify(exactly = 1) { atOAuth.logout() }
        }

    @Test
    fun `signOut surfaces failure from atOAuth logout but still clears sessions`() =
        runTest {
            val clearable = CountingClearable()
            val failure = IllegalStateException("revocation rejected")
            val atOAuth =
                mockk<AtOAuth> {
                    coEvery { logout() } throws failure
                }
            val sessionStateProvider = mockk<SessionStateProvider>(relaxed = true)
            val repo =
                DefaultAuthRepository(
                    atOAuth = atOAuth,
                    sessionStateProvider = sessionStateProvider,
                    sessionClearables = setOf(clearable),
                )

            val result = repo.signOut()

            assertTrue(result.isFailure, "atOAuth.logout failure MUST surface as Result.failure")
            assertEquals(failure, result.exceptionOrNull())
            assertEquals(
                1,
                clearable.callCount.get(),
                "SessionClearable MUST still be cleared even when logout fails — " +
                    "the user signaled intent to sign out",
            )
        }

    private class CountingClearable : SessionClearable {
        val callCount = AtomicInteger(0)

        @Volatile
        var logoutInvokedAtCount: Int = 0

        override fun clearSession() {
            callCount.incrementAndGet()
        }
    }
}
