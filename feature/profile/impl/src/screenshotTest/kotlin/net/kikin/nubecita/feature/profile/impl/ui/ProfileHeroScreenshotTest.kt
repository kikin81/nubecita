package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.ViewerModerationState

/**
 * Screenshot baselines for [ProfileHero] covering two concerns:
 *
 * 1. **Pro "Supporter" badge** — the hero WITH the badge (Pro, own profile)
 *    and WITHOUT it (the non-Pro / other-profile rendering), across light +
 *    dark. The badge's presence is driven purely by `showSupporterBadge`
 *    here — the four-way `isPro × own/other` gate logic is asserted in
 *    `ProfileViewModelTest`, not pinned visually.
 *
 * 2. **Muted-user notice** — the hero rendered with
 *    `viewerModeration.isMutedByViewer = true`, showing the inline muted
 *    notice that the [ProfileHeroMutedScreenshot] fixtures capture. The
 *    optimistic-flip + rollback behaviour for the mute/unmute actions is
 *    asserted in `ProfileViewModelTest`.
 *
 * Regenerate with
 * `./gradlew :feature:profile:impl:updateDebugScreenshotTest` after
 * intentional visual changes.
 */
private val SAMPLE_HEADER =
    ProfileHeaderUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = "Alice",
        avatarUrl = null,
        bannerUrl = null,
        bio = "Designer · lima → barcelona · she/her",
        location = "Lima, Peru",
        website = "alice.example.com",
        joinedDisplay = "Joined April 2023",
        postsCount = 412,
        followersCount = 2_142,
        followsCount = 342,
    )

@PreviewTest
@Preview(name = "hero-with-supporter-badge-light", showBackground = true)
@Preview(name = "hero-with-supporter-badge-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroWithSupporterBadgeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfileHero(
                header = SAMPLE_HEADER,
                headerError = null,
                showSupporterBadge = true,
                onRetryHeader = {},
                onVerificationBadgeClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "hero-without-supporter-badge-light", showBackground = true)
@Preview(name = "hero-without-supporter-badge-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroWithoutSupporterBadgeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfileHero(
                header = SAMPLE_HEADER,
                headerError = null,
                showSupporterBadge = false,
                onRetryHeader = {},
                onVerificationBadgeClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "hero-muted-light", showBackground = true)
@Preview(name = "hero-muted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroMutedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfileHero(
                header =
                    SAMPLE_HEADER.copy(
                        viewerModeration = ViewerModerationState(isMutedByViewer = true),
                    ),
                headerError = null,
                showSupporterBadge = false,
                onRetryHeader = {},
                onVerificationBadgeClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "hero-with-bio-link-light", showBackground = true)
@Preview(name = "hero-with-bio-link-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroWithBioLinkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfileHero(
                header = SAMPLE_HEADER.copy(bio = "Designer · portfolio at https://alice.design ✦ she/her"),
                headerError = null,
                showSupporterBadge = false,
                onRetryHeader = {},
                onVerificationBadgeClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "hero-verified-light", showBackground = true)
@Preview(name = "hero-verified-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroVerifiedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfileHero(
                header = SAMPLE_HEADER.copy(verifiedBadge = VerifiedBadge.Verified),
                headerError = null,
                showSupporterBadge = false,
                onRetryHeader = {},
                onVerificationBadgeClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "hero-trusted-verifier-light", showBackground = true)
@Preview(name = "hero-trusted-verifier-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroTrustedVerifierScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfileHero(
                header = SAMPLE_HEADER.copy(verifiedBadge = VerifiedBadge.TrustedVerifier),
                headerError = null,
                showSupporterBadge = false,
                onRetryHeader = {},
                onVerificationBadgeClick = {},
            )
        }
    }
}
