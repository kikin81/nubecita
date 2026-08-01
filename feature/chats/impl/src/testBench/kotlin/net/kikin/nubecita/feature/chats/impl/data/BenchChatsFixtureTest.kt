package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins the shape of the bench chats fixture.
 *
 * Bench source lives in the `bench` flavor, so this test has to live in the
 * flavor-specific `src/testBench` set — `src/test` is shared with `production`,
 * where `BenchConvoDto` does not exist and the test would not compile.
 *
 * The fixture is read from disk rather than from Android assets: JVM unit tests
 * have no `AssetManager`, and the point here is that the packaged JSON itself
 * parses and carries the request fixtures, not that the loader works.
 */
class BenchChatsFixtureTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(): BenchConvoListDto {
        // Probe module-relative and repo-root-relative: the working directory
        // differs between a Gradle run and an IDE run. Same shape as
        // BenchTimelineFixtureTest.
        val file =
            listOf(
                File("src/bench/assets/chats.json"),
                File("feature/chats/impl/src/bench/assets/chats.json"),
            ).firstOrNull { it.exists() }
                ?: error("bench chats.json not found from ${File("").absolutePath}")
        return json.decodeFromString(BenchConvoListDto.serializer(), file.readText())
    }

    @Test
    fun `the bench fixture parses`() {
        val convos = fixture().convos
        assertTrue(convos.isNotEmpty(), "bench fixture must seed conversations")
    }

    @Test
    fun `conversations default to accepted so existing fixtures are untouched`() {
        // The status field is additive: every pre-existing entry omits it and
        // must keep landing in the Chats segment, not the Requests segment.
        val accepted = fixture().convos.filterNot { it.isRequest }
        assertTrue(
            accepted.any { it.convoId == "convo_alice" },
            "an entry with no status field must parse as accepted",
        )
    }

    @Test
    fun `the fixture seeds both a direct and a group request`() {
        // Both are required: the group case is the only thing that exercises the
        // "Accept and join" label variant, which differs from the direct label.
        val requests = fixture().convos.filter { it.isRequest }

        assertEquals(
            2,
            requests.size,
            "expected exactly one direct and one group request, got ${requests.map { it.convoId }}",
        )
        assertTrue(
            requests.any { it.kind == "direct" },
            "a direct request is needed for the plain Accept label",
        )
        assertTrue(
            requests.any { it.kind == "group" },
            "a group request is needed for the Accept-and-join label",
        )
    }

    @Test
    fun `request ids are exactly the request-status fixtures`() {
        // This is the rule the fake's loader routes on. Pinning it against the
        // real fixture catches a typo'd status value — which would silently send
        // a request into the Chats segment and quietly delete the coverage the
        // request fixtures exist to provide.
        assertEquals(
            setOf("convo_request_priya", "convo_request_kotlin_mx"),
            fixture().convos.requestConvoIds(),
        )
    }

    @Test
    fun `accepted conversations are not routed to requests`() {
        val requestIds = fixture().convos.requestConvoIds()
        val acceptedIds = fixture().convos.map { it.convoId }.toSet() - requestIds

        assertTrue(acceptedIds.contains("convo_alice"), "existing direct convo stays accepted")
        assertTrue(acceptedIds.contains("convo_group_design"), "existing group convo stays accepted")
        assertTrue(
            requestIds.none { it in acceptedIds },
            "a conversation must not appear in both segments",
        )
    }

    @Test
    fun `request fixtures carry a last message so the row renders like a real request`() {
        // A request row shows the message that is being requested; a request with
        // no snippet would screenshot as an empty row and prove nothing.
        fixture().convos.filter { it.isRequest }.forEach { convo ->
            assertTrue(
                !convo.lastMessageSnippet.isNullOrBlank(),
                "${convo.convoId} must carry a last-message snippet",
            )
            assertTrue(
                !convo.sentAt.isNullOrBlank(),
                "${convo.convoId} must carry a timestamp",
            )
        }
    }
}
