package net.kikin.nubecita.feature.composer.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComposerRouteTest {
    @Test
    fun newPostMode_defaultsReplyToUriToNull() {
        val route = ComposerRoute()

        assertNull(route.replyToUri)
    }

    @Test
    fun replyMode_carriesReplyToUri() {
        val parentUri = "at://did:plc:abc/app.bsky.feed.post/3kxyz"

        val route = ComposerRoute(replyToUri = parentUri)

        assertEquals(parentUri, route.replyToUri)
    }

    @Test
    fun composerRoute_isANavKey() {
        val route: NavKey = ComposerRoute()

        // Compile-time + runtime conformance — the assignment proves the
        // type relationship; the assertion documents intent.
        assertEquals(ComposerRoute::class, route::class)
    }

    @Test
    fun newPostRoute_serializesAndRoundTrips() {
        val route = ComposerRoute()

        val encoded = Json.encodeToString(ComposerRoute.serializer(), route)
        val decoded = Json.decodeFromString(ComposerRoute.serializer(), encoded)

        assertEquals(route, decoded)
        assertNull(decoded.replyToUri)
    }

    @Test
    fun replyRoute_serializesAndRoundTrips() {
        val route = ComposerRoute(replyToUri = "at://did:plc:abc/app.bsky.feed.post/3kxyz")

        val encoded = Json.encodeToString(ComposerRoute.serializer(), route)
        val decoded = Json.decodeFromString(ComposerRoute.serializer(), encoded)

        assertEquals(route, decoded)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/3kxyz", decoded.replyToUri)
    }
}
