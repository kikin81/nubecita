package net.kikin.nubecita.shell.composer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pin-down tests for the pure [launchComposer] branching helper.
 *
 * The Composable wrapper [rememberComposerLauncher] derives `isCompact`
 * from `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(
 * WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` and plumbs it into this
 * function; the branching itself is unit-testable in pure JVM with no
 * Compose harness.
 */
class LaunchComposerTest {
    @Test
    fun compact_pushesRoute_onlyOnPushRouteFires() {
        val pushedUris = mutableListOf<String?>()
        val overlayUris = mutableListOf<String?>()

        launchComposer(
            isCompact = true,
            replyToUri = "at://did:plc:alice/app.bsky.feed.post/abc",
            onPushRoute = { pushedUris += it },
            onShowOverlay = { overlayUris += it },
        )

        assertEquals(listOf("at://did:plc:alice/app.bsky.feed.post/abc"), pushedUris)
        assertEquals(emptyList<String?>(), overlayUris)
    }

    @Test
    fun nonCompact_showsOverlay_onlyOnShowOverlayFires() {
        val pushedUris = mutableListOf<String?>()
        val overlayUris = mutableListOf<String?>()

        launchComposer(
            isCompact = false,
            replyToUri = null,
            onPushRoute = { pushedUris += it },
            onShowOverlay = { overlayUris += it },
        )

        assertEquals(emptyList<String?>(), pushedUris)
        assertEquals(listOf<String?>(null), overlayUris)
    }

    @Test
    fun nonCompact_showsOverlay_propagatesReplyUri() {
        val pushedUris = mutableListOf<String?>()
        val overlayUris = mutableListOf<String?>()

        launchComposer(
            isCompact = false,
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
            isCompact = true,
            replyToUri = null,
            onPushRoute = { pushedAtCompact = it },
            onShowOverlay = { error("overlay should not fire when isCompact=true") },
        )
        assertNull(pushedAtCompact)

        var overlayAtNonCompact: String? = "sentinel"
        launchComposer(
            isCompact = false,
            replyToUri = null,
            onPushRoute = { error("push should not fire when isCompact=false") },
            onShowOverlay = { overlayAtNonCompact = it },
        )
        assertNull(overlayAtNonCompact)
    }
}
