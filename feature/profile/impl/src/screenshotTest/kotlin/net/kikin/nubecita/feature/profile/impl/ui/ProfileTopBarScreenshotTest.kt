package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi

/**
 * Screenshot baselines for [ProfileTopBar]. Four α stops × two themes
 * = 8 baselines:
 *
 * - α = 0.0, no nav icon (transparent inset reservation)
 * - α = 0.5, no nav icon (mid cross-fade)
 * - α = 1.0, no nav icon (own-profile root, fully visible)
 * - α = 1.0, with nav icon (other-user pushed, fully visible)
 *
 * Uses the alpha-driven internal overload so Layoutlib doesn't need
 * to run a LazyColumn host to drive the scroll-derived alpha.
 */
private val SAMPLE_HEADER =
    ProfileHeaderUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = "Alice",
        avatarUrl = null,
        bannerUrl = null,
        avatarHue = 217,
        bio = null,
        location = null,
        website = null,
        joinedDisplay = null,
        postsCount = 0L,
        followersCount = 0L,
        followsCount = 0L,
    )

@PreviewTest
@Preview(name = "topbar-alpha0-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha0-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaZeroScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 0f, onBack = null)
    }
}

@PreviewTest
@Preview(name = "topbar-alpha-half-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha-half-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaHalfScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 0.5f, onBack = null)
    }
}

@PreviewTest
@Preview(name = "topbar-alpha1-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha1-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaOneScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 1f, onBack = null)
    }
}

@PreviewTest
@Preview(name = "topbar-alpha1-back-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha1-back-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaOneWithBackScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileTopBar(header = SAMPLE_HEADER, alpha = 1f, onBack = {})
    }
}
