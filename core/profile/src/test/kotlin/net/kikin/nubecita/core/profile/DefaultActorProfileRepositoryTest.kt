package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Splits the projection logic (verified directly via [toActorProfile])
 * from the repository's coroutine + error-handling shell. Booting a
 * real [io.github.kikin81.atproto.runtime.XrpcClient] in a unit test
 * is heavy and brittle (forging HTTP responses), so the success path
 * is exercised at the mapper layer and the repo test is scoped to the
 * `runCatching → Result.failure` shell.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultActorProfileRepositoryTest {
    @Test
    fun `toActorProfile unwraps wire raws into the slim projection`() {
        val response =
            ProfileViewDetailed(
                did = Did("did:plc:alice"),
                handle = Handle("alice.bsky.social"),
                displayName = "Alice Anderson",
                avatar = Uri("https://cdn.example/alice.jpg"),
            )

        val projected = response.toActorProfile()

        assertEquals("did:plc:alice", projected.did)
        assertEquals("alice.bsky.social", projected.handle)
        assertEquals("Alice Anderson", projected.displayName)
        assertEquals("https://cdn.example/alice.jpg", projected.avatarUrl)
    }

    @Test
    fun `toActorProfile collapses blank displayName to null`() {
        // takeUnless { isBlank } unwraps whitespace-only values so the
        // UI layer can treat null uniformly as "no name" without a
        // second isBlank() check.
        val response =
            ProfileViewDetailed(
                did = Did("did:plc:bob"),
                handle = Handle("bob.bsky.social"),
                displayName = "   ",
                avatar = null,
            )

        val projected = response.toActorProfile()

        assertNull(projected.displayName)
        assertNull(projected.avatarUrl)
    }

    @Test
    fun `toActorProfile preserves a null displayName as null`() {
        val response =
            ProfileViewDetailed(
                did = Did("did:plc:carol"),
                handle = Handle("carol.bsky.social"),
                displayName = null,
                avatar = null,
            )

        val projected = response.toActorProfile()

        assertNull(projected.displayName)
        assertNull(projected.avatarUrl)
    }

    @Test
    fun `fetchProfile wraps thrown auth exceptions in Result_failure`() =
        runTest {
            // When XrpcClientProvider.authenticated() throws (no session,
            // refresh-failure, etc.), the repo's runCatching captures it
            // and surfaces it through Result.failure. The .onFailure side
            // effect logs only throwable.javaClass.name (PII redaction);
            // that's not directly observable here, but the Result outcome
            // is.
            val provider =
                mockk<XrpcClientProvider> {
                    coEvery { authenticated() } throws IOException("net down")
                }
            val repo =
                DefaultActorProfileRepository(
                    xrpcClientProvider = provider,
                    dispatcher = UnconfinedTestDispatcher(),
                )

            val result = repo.fetchProfile("did:plc:bob")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }
}
