package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi

/**
 * Screenshot baselines for [ProfileHero] focused on the Pro "Supporter"
 * badge: the hero WITH the badge (Pro, own profile) and WITHOUT it (the
 * non-Pro / other-profile rendering), across light + dark. The badge's
 * presence is driven purely by `showSupporterBadge` here — the four-way
 * `isPro × own/other` gate logic is asserted in `ProfileViewModelTest`,
 * not pinned visually. Regenerate with
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
        avatarHue = 217,
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
            )
        }
    }
}
