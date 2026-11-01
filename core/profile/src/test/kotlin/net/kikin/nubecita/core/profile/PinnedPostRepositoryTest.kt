package net.kikin.nubecita.core.profile

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pin / unpin on the profile record (nubecita-iwjz.2). Unpin is
 * compare-and-clear: it removes `pinnedPost` only when it still points at the
 * post being unpinned, so a stale "Unpin" can't clear a different pin.
 */
class PinnedPostRepositoryTest {
    private val postA = "at://did:plc:me/app.bsky.feed.post/a"
    private val postB = "at://did:plc:me/app.bsky.feed.post/b"

    @Test
    fun pinSetsStrongRef() =
        runTest {
            val writer = InMemoryWriter(JsonObject(mapOf("displayName" to JsonPrimitive("Me"))))

            val result = DefaultPinnedPostRepository(writer).pin(uri = postA, cid = "bafa")

            assertTrue(result.isSuccess)
            val pinned = writer.record.getValue("pinnedPost").jsonObject
            assertEquals(postA, pinned.getValue("uri").jsonPrimitive.content)
            assertEquals("bafa", pinned.getValue("cid").jsonPrimitive.content)
            assertEquals(
                "Me",
                writer.record
                    .getValue("displayName")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun unpinClearsWhenPointingAtPost() =
        runTest {
            val writer = InMemoryWriter(recordPinnedTo(postA))

            val result = DefaultPinnedPostRepository(writer).unpin(postA)

            assertTrue(result.isSuccess)
            assertFalse(writer.record.containsKey("pinnedPost"))
            assertEquals(1, writer.writes)
        }

    @Test
    fun unpinElsewhereWritesNothing() =
        runTest {
            val writer = InMemoryWriter(recordPinnedTo(postB))

            val result = DefaultPinnedPostRepository(writer).unpin(postA)

            assertTrue(result.isSuccess)
            assertEquals(0, writer.writes)
            assertEquals(
                postB,
                writer.record
                    .getValue("pinnedPost")
                    .jsonObject
                    .getValue("uri")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun unpinWithNoPinnedPostWritesNothing() =
        runTest {
            val writer = InMemoryWriter(JsonObject(mapOf("displayName" to JsonPrimitive("Me"))))

            val result = DefaultPinnedPostRepository(writer).unpin(postA)

            assertTrue(result.isSuccess)
            assertEquals(0, writer.writes)
        }

    private fun recordPinnedTo(uri: String): JsonObject =
        JsonObject(
            mapOf(
                "displayName" to JsonPrimitive("Me"),
                "pinnedPost" to JsonObject(mapOf("uri" to JsonPrimitive(uri), "cid" to JsonPrimitive("bafx"))),
            ),
        )

    private class InMemoryWriter(
        var record: JsonObject,
    ) : ProfileRecordWriter {
        var writes = 0

        override suspend fun update(transform: suspend (existing: JsonObject?) -> JsonObject?): Result<Boolean> {
            val next = transform(record) ?: return Result.success(false)
            record = next
            writes++
            return Result.success(true)
        }
    }
}
