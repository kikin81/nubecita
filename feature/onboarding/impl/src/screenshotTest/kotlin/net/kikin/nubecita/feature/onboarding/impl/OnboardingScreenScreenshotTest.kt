package net.kikin.nubecita.feature.onboarding.impl

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews

/**
 * Multi-breakpoint baselines for [OnboardingScreen]. Each preview renders
 * the stateless `OnboardingScreen(onEvent, initialPage)` overload across
 * the project's Phone / Foldable / Tablet × Light / Dark matrix
 * (`@PreviewNubecitaScreenPreviews`) — 6 fixtures per preview × 2
 * fixtures (first page + last page) = 12 baselines.
 *
 * Width-cap at 600dp inside [OnboardingScreen] keeps the pager + bottom
 * bar centered on Medium / Expanded widths instead of stretching
 * edge-to-edge; this test pins that behavior.
 */

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun OnboardingScreenFirstPagePreviews() {
    NubecitaTheme(dynamicColor = false) {
        OnboardingScreen(onEvent = {}, initialPage = 0)
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun OnboardingScreenLastPagePreviews() {
    NubecitaTheme(dynamicColor = false) {
        OnboardingScreen(onEvent = {}, initialPage = OnboardingPage.entries.lastIndex)
    }
}
