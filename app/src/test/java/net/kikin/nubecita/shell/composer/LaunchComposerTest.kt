package net.kikin.nubecita.shell.composer

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.window.core.layout.WindowWidthSizeClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for the pure [launchComposer] branching helper.
 *
 * The Composable wrapper [rememberComposerLauncher] plumbs the live
 * width-class and live state holders into this function; the
 * branching itself is unit-testable in pure JVM with no Compose
 * harness.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class LaunchComposerTest {
    @Test
    fun compactWidth_pushesRoute_onlyOnPushRouteFires() {
        val pushedUris = mutableListOf<String?>()
        val overlayUris = mutableListOf<String?>()

        launchComposer(
            widthSizeClass = WindowWidthSizeClass.COMPACT,
            replyToUri = "at://did:plc:alice/app.bsky.feed.post/abc",
            onPushRoute = { pushedUris += it },
            onShowOverlay = { overlayUris += it },
        )

        assertEquals(listOf("at://did:plc:alice/app.bsky.feed.post/abc"), pushedUris)
        assertEquals(emptyList<String?>(), overlayUris)
    }

    @Test
    fun mediumWidth_showsOverlay_onlyOnShowOverlayFires() {
        val pushedUris = mutableListOf<String?>()
        val overlayUris = mutableListOf<String?>()

        launchComposer(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            replyToUri = null,
            onPushRoute = { pushedUris += it },
            onShowOverlay = { overlayUris += it },
        )

        assertEquals(emptyList<String?>(), pushedUris)
        assertEquals(listOf<String?>(null), overlayUris)
    }

    @Test
    fun expandedWidth_showsOverlay_onlyOnShowOverlayFires() {
        val pushedUris = mutableListOf<String?>()
        val overlayUris = mutableListOf<String?>()

        launchComposer(
            widthSizeClass = WindowWidthSizeClass.EXPANDED,
            replyToUri = "at://did:plc:bob/app.bsky.feed.post/xyz",
            onPushRoute = { pushedUris += it },
            onShowOverlay = { overlayUris += it },
        )

        assertEquals(emptyList<String?>(), pushedUris)
        assertEquals(listOf("at://did:plc:bob/app.bsky.feed.post/xyz"), overlayUris)
    }

    @Test
    fun nullReplyToUri_isPropagated_onBothPaths() {
        // Both new-post (null) and reply (non-null) URIs travel
        // through the helper unchanged.
        var pushedAtCompact: String? = "sentinel"
        launchComposer(
            widthSizeClass = WindowWidthSizeClass.COMPACT,
            replyToUri = null,
            onPushRoute = { pushedAtCompact = it },
            onShowOverlay = { error("overlay should not fire at Compact") },
        )
        assertNull(pushedAtCompact)

        var overlayAtMedium: String? = "sentinel"
        launchComposer(
            widthSizeClass = WindowWidthSizeClass.MEDIUM,
            replyToUri = null,
            onPushRoute = { error("push should not fire at Medium") },
            onShowOverlay = { overlayAtMedium = it },
        )
        assertNull(overlayAtMedium)
    }
}
