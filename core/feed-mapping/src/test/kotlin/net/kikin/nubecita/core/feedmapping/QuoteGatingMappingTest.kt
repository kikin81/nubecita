package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.ViewerState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Postgate quote-permission mapping (nubecita-8g28.2). The appview computes
 * quote (embedding) eligibility server-side and returns it as
 * `app.bsky.feed.defs#viewerState.embeddingDisabled`; we project the inverse onto
 * [net.kikin.nubecita.data.models.ViewerStateUi.canViewerQuote] so the UI never
 * reimplements the postgate rules. The mapping must fail OPEN (quote allowed)
 * when the flag is absent — most posts carry no postgate.
 */
internal class QuoteGatingMappingTest {
    @Test
    fun `embeddingDisabled true maps to canViewerQuote false`() {
        val ui = ViewerState(embeddingDisabled = true).toViewerStateUi()
        assertFalse(ui.canViewerQuote, "a quote-gated post must report the viewer cannot quote")
    }

    @Test
    fun `embeddingDisabled false maps to canViewerQuote true`() {
        val ui = ViewerState(embeddingDisabled = false).toViewerStateUi()
        assertTrue(ui.canViewerQuote)
    }

    @Test
    fun `absent embeddingDisabled fails open to canViewerQuote true`() {
        // The common case: no postgate → null embeddingDisabled → quote allowed.
        val ui = ViewerState().toViewerStateUi()
        assertTrue(ui.canViewerQuote, "an absent postgate flag must not disable quoting")
    }

    @Test
    fun `a null post-viewer fails open to canViewerQuote true`() {
        val ui = (null as ViewerState?).toViewerStateUi()
        assertTrue(ui.canViewerQuote)
    }
}
