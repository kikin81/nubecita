package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaScreenPreviewTheme
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi

/**
 * Screenshot baselines for [ProfileTopBar]. Four α stops × two themes
 * = 8 baselines:
 *
 * - α = 0.0, own-profile (settings gear, no back, transparent)
 * - α = 0.5, own-profile (mid cross-fade)
 * - α = 1.0, own-profile (settings gear, fully visible)
 * - α = 1.0, other-profile (back arrow, no settings, fully visible)
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
    NubecitaScreenPreviewTheme {
        ProfileTopBar(
            header = SAMPLE_HEADER,
            alpha = 0f,
            ownProfile = true,
            onBack = null,
            onSettings = {},
        )
    }
}

@PreviewTest
@Preview(name = "topbar-alpha-half-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha-half-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaHalfScreenshot() {
    NubecitaScreenPreviewTheme {
        ProfileTopBar(
            header = SAMPLE_HEADER,
            alpha = 0.5f,
            ownProfile = true,
            onBack = null,
            onSettings = {},
        )
    }
}

@PreviewTest
@Preview(name = "topbar-alpha1-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha1-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaOneScreenshot() {
    NubecitaScreenPreviewTheme {
        ProfileTopBar(
            header = SAMPLE_HEADER,
            alpha = 1f,
            ownProfile = true,
            onBack = null,
            onSettings = {},
        )
    }
}

@PreviewTest
@Preview(name = "topbar-alpha1-back-light", showBackground = true, heightDp = 80)
@Preview(name = "topbar-alpha1-back-dark", showBackground = true, heightDp = 80, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileTopBarAlphaOneWithBackScreenshot() {
    NubecitaScreenPreviewTheme {
        ProfileTopBar(
            header = SAMPLE_HEADER,
            alpha = 1f,
            ownProfile = false,
            onBack = {},
            onSettings = null,
        )
    }
}
