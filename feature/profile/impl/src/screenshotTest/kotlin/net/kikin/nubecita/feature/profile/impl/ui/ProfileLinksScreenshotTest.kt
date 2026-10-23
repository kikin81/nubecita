package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.ProfileLinkUi

/**
 * Screenshot baselines for the profile-links row in [ProfileHero] and the
 * "+N" overflow [ProfileLinksSheetContent]. Icons are null so the globe
 * fallback renders deterministically (no network in the screenshot host).
 *
 * Fixtures pin: a short list (all pills, no overflow), the 4 + "+N" split
 * with a support (emphasized) link, long titles truncated and wrapping at
 * phone width, and the sheet listing every link with full titles + URLs.
 */
private fun link(
    n: Int,
    label: String,
    url: String,
    isSupportLink: Boolean = false,
): ProfileLinkUi =
    ProfileLinkUi(
        uri = "at://did:plc:alice/app.bsky.actor.link/l$n",
        url = url,
        label = label,
        host = url.removePrefix("https://").removePrefix("www.").substringBefore('/'),
        isSupportLink = isSupportLink,
    )

private val SIX_LINKS: ImmutableList<ProfileLinkUi> =
    listOf(
        link(1, "Support our work!", "https://www.patreon.com/alice", isSupportLink = true),
        link(2, "Science Merch!", "https://shop.example.com/alice"),
        link(3, "Skype a Scientist", "https://skypeascientist.com"),
        link(4, "Art Shop", "https://ko-fi.com/alice/shop", isSupportLink = true),
        link(5, "alice.example.com", "https://alice.example.com"),
        link(6, "Podcast", "https://podcast.example.com"),
    ).toImmutableList()

private val LONG_TITLE_LINKS: ImmutableList<ProfileLinkUi> =
    listOf(
        link(1, "My extremely long newsletter title here", "https://news.example.com"),
        link(2, "Another very long link title that goes on", "https://blog.example.com"),
        link(3, "Portfolio and case studies", "https://portfolio.example.com"),
        link(4, "Commissions open — read the terms", "https://ko-fi.com/alice", isSupportLink = true),
        link(5, "extra.example.com", "https://extra.example.com"),
    ).toImmutableList()

private val HEADER =
    ProfileHeaderUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = "Alice",
        avatarUrl = null,
        bannerUrl = null,
        bio = "Squid biologist · science communicator",
        location = null,
        website = null,
        joinedDisplay = "Joined April 2023",
        postsCount = 412,
        followersCount = 2_142,
        followsCount = 342,
    )

@Composable
private fun HeroFixture(links: ImmutableList<ProfileLinkUi>) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Box(modifier = Modifier.width(360.dp)) {
                ProfileHero(
                    header = HEADER.copy(links = links),
                    headerError = null,
                    showSupporterBadge = false,
                    onRetryHeader = {},
                    onVerificationBadgeClick = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "hero-links-three-light", showBackground = true)
@Preview(name = "hero-links-three-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroThreeLinksScreenshot() {
    HeroFixture(SIX_LINKS.take(3).toImmutableList())
}

@PreviewTest
@Preview(name = "hero-links-overflow-light", showBackground = true)
@Preview(name = "hero-links-overflow-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroOverflowLinksScreenshot() {
    HeroFixture(SIX_LINKS)
}

@PreviewTest
@Preview(name = "hero-links-long-titles-light", showBackground = true)
@Preview(name = "hero-links-long-titles-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileHeroLongTitleLinksScreenshot() {
    HeroFixture(LONG_TITLE_LINKS)
}

@PreviewTest
@Preview(name = "links-sheet-light", showBackground = true)
@Preview(name = "links-sheet-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileLinksSheetScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Box(modifier = Modifier.width(360.dp)) {
                ProfileLinksSheetContent(links = SIX_LINKS)
            }
        }
    }
}
