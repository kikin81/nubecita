package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedProfileDao
import net.kikin.nubecita.core.database.model.CachedProfileEntity
import net.kikin.nubecita.core.profile.internal.RoomProfileCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

internal class RoomProfileCacheTest {
    private val me = "did:plc:me"
    private val now = Instant.fromEpochMilliseconds(1_000)
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "me.test", did = me))
    private val sessionStateProvider = mockk<SessionStateProvider> { every { state } returns session }
    private val dao = mockk<CachedProfileDao>(relaxed = true)
    private val clock =
        object : Clock {
            override fun now(): Instant = now
        }

    private fun TestScope.cache() =
        RoomProfileCache(
            dao = dao,
            sessionStateProvider = sessionStateProvider,
            clock = clock,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

    private val profile =
        ProfileViewDetailed(
            did = Did("did:plc:alice"),
            handle = Handle("alice.test"),
            displayName = "Alice",
            pronouns = "she/her",
        )

    @Test
    fun `write upserts the blob and trims keeping the account's own profile`() =
        runTest {
            val row = slot<CachedProfileEntity>()
            coEvery { dao.upsert(capture(row)) } returns Unit

            cache().write(profile)

            assertEquals(me, row.captured.accountDid)
            assertEquals("did:plc:alice", row.captured.did)
            assertEquals("alice.test", row.captured.handle)
            assertEquals(now, row.captured.lastOpenedAt)
            coVerify { dao.trimToCap(me, 200, me) }
        }

    @Test
    fun `read decodes the stored blob and touches last opened`() =
        runTest {
            val written = slot<CachedProfileEntity>()
            coEvery { dao.upsert(capture(written)) } returns Unit
            cache().write(profile)
            coEvery { dao.byActor(me, "alice.test") } returns written.captured

            val read = cache().read("alice.test")

            assertEquals("she/her", read?.pronouns)
            assertEquals("Alice", read?.displayName)
            coVerify { dao.touch(me, "did:plc:alice", now) }
        }

    @Test
    fun `read when signed out returns null without touching the database`() =
        runTest {
            session.value = SessionState.SignedOut

            assertNull(cache().read("alice.test"))
            coVerify(exactly = 0) { dao.byActor(any(), any()) }
        }

    @Test
    fun `read of a corrupt blob returns null and drops the row`() =
        runTest {
            coEvery { dao.byActor(me, "alice.test") } returns
                CachedProfileEntity(
                    accountDid = me,
                    did = "did:plc:alice",
                    handle = "alice.test",
                    profileBlob = "{not json",
                    fetchedAt = now,
                    lastOpenedAt = now,
                )

            assertNull(cache().read("alice.test"))
            coVerify { dao.deleteByActor(me, "did:plc:alice") }
        }

    @Test
    fun `remove deletes by actor under the signed-in account`() =
        runTest {
            cache().remove("alice.test")

            coVerify { dao.deleteByActor(me, "alice.test") }
        }
}
