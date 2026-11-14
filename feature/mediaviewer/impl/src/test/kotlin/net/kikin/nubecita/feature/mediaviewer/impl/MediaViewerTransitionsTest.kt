package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation3.ui.NavDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * On a predictive back the viewer's page fades out in place; only the shared
 * image moves (nubecita-vxrn).
 */
internal class MediaViewerTransitionsTest {
    @Test
    fun `the page leaves with a fade and nothing else`() {
        // Equal to a bare fadeOut means no scale and no slide rode along:
        // NavDisplay's default scaleOut is what shrank the black page.
        assertEquals(
            fadeOut(animationSpec = PageFadeOutSpec),
            mediaViewerPredictivePopTransform().initialContentExit,
        )
    }

    @Test
    fun `the screen underneath fades in as it does by default`() {
        assertEquals(
            fadeIn(animationSpec = UnderneathFadeInSpec),
            mediaViewerPredictivePopTransform().targetContentEnter,
        )
    }

    @Test
    fun `only the predictive back is overridden`() {
        // A plain pop reads a different key and already fades in place.
        assertEquals(
            NavDisplay.predictivePopTransitionSpec { null }.keys,
            mediaViewerTransitionMetadata().keys,
        )
    }
}
