package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.search.impl.ui.DISCOVER_ACCOUNTS_ONLY_STATE
import net.kikin.nubecita.feature.search.impl.ui.DISCOVER_EMPTY_STATE
import net.kikin.nubecita.feature.search.impl.ui.DISCOVER_FEEDS_ONLY_STATE
import net.kikin.nubecita.feature.search.impl.ui.DISCOVER_POPULATED_STATE
import net.kikin.nubecita.feature.search.impl.ui.DiscoverSections
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_ACCOUNT_NO_MUTUALS
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_ACCOUNT_WITH_MUTUALS
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_FEED_LOADED
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_FEED_LOADING
import net.kikin.nubecita.feature.search.impl.ui.SuggestedAccountCard
import net.kikin.nubecita.feature.search.impl.ui.SuggestedFeedCard

// ---------------------------------------------------------------------------
// SuggestedAccountCard component tests
// ---------------------------------------------------------------------------

@PreviewTest
@Preview(name = "discover-account-card-no-mutuals-light", showBackground = true)
@Preview(name = "discover-account-card-no-mutuals-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverAccountCardNoMutualsScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SuggestedAccountCard(
                account = SAMPLE_ACCOUNT_NO_MUTUALS,
                onAccountClick = {},
                onFollowClick = {},
                onDismissClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewTest
@Preview(name = "discover-account-card-with-mutuals-light", showBackground = true)
@Preview(name = "discover-account-card-with-mutuals-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverAccountCardWithMutualsScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SuggestedAccountCard(
                account = SAMPLE_ACCOUNT_WITH_MUTUALS,
                onAccountClick = {},
                onFollowClick = {},
                onDismissClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// SuggestedFeedCard component tests
// ---------------------------------------------------------------------------

@PreviewTest
@Preview(name = "discover-feed-card-loaded-light", showBackground = true)
@Preview(name = "discover-feed-card-loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverFeedCardLoadedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SuggestedFeedCard(
                discoverFeed = SAMPLE_FEED_LOADED,
                onFeedClick = {},
                onPinClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewTest
@Preview(name = "discover-feed-card-loading-shimmer-light", showBackground = true)
@Preview(name = "discover-feed-card-loading-shimmer-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverFeedCardLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SuggestedFeedCard(
                discoverFeed = SAMPLE_FEED_LOADING,
                onFeedClick = {},
                onPinClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewTest
@Preview(name = "discover-feed-card-pinned-light", showBackground = true)
@Composable
private fun DiscoverFeedCardPinnedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SuggestedFeedCard(
                discoverFeed = SAMPLE_FEED_LOADED.copy(feed = SAMPLE_FEED_LOADED.feed.copy(isPinned = true)),
                onFeedClick = {},
                onPinClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DiscoverSections integration screenshots
// ---------------------------------------------------------------------------

@PreviewTest
@Preview(name = "discover-sections-populated-light", showBackground = true, heightDp = 600)
@Preview(name = "discover-sections-populated-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverSectionsPopulatedScreenshot() {
    NubecitaCanvasPreviewTheme {
        DiscoverSections(
            state = DISCOVER_POPULATED_STATE,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "discover-sections-accounts-only-light", showBackground = true, heightDp = 320)
@Preview(name = "discover-sections-accounts-only-dark", showBackground = true, heightDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverSectionsAccountsOnlyScreenshot() {
    NubecitaCanvasPreviewTheme {
        DiscoverSections(
            state = DISCOVER_ACCOUNTS_ONLY_STATE,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "discover-sections-feeds-only-light", showBackground = true, heightDp = 380)
@Preview(name = "discover-sections-feeds-only-dark", showBackground = true, heightDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverSectionsFeedsOnlyScreenshot() {
    NubecitaCanvasPreviewTheme {
        DiscoverSections(
            state = DISCOVER_FEEDS_ONLY_STATE,
            onEvent = {},
        )
    }
}

// ---------------------------------------------------------------------------
// SearchScreenContent integration — Discover phase with fixture state
// ---------------------------------------------------------------------------

// NOTE: The integrated Discover variant of SearchScreenContent is driven with
// plain DiscoverState fixture — no DiscoverViewModel / Hilt graph needed. The
// PR box indicator doesn't render in layoutlib (no pull gesture), so these
// capture the resting carousel layout.

@PreviewTest
@Preview(name = "search-screen-discover-populated-light", showBackground = true, heightDp = 720)
@Preview(name = "search-screen-discover-populated-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenDiscoverPopulatedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState =
                    androidx.compose.foundation.text.input
                        .TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf("kotlin", "compose"),
                onEvent = {},
                onClearQueryRequest = {},
                discoverState = DISCOVER_POPULATED_STATE,
                onDiscoverEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-screen-discover-partial-light", showBackground = true, heightDp = 720)
@Composable
private fun SearchScreenDiscoverPartialScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState =
                    androidx.compose.foundation.text.input
                        .TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf(),
                onEvent = {},
                onClearQueryRequest = {},
                discoverState = DISCOVER_ACCOUNTS_ONLY_STATE,
                onDiscoverEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-screen-discover-empty-hint-light", showBackground = true, heightDp = 600)
@Preview(name = "search-screen-discover-empty-hint-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenDiscoverEmptyHintScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState =
                    androidx.compose.foundation.text.input
                        .TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf(),
                onEvent = {},
                onClearQueryRequest = {},
                discoverState = DISCOVER_EMPTY_STATE,
                onDiscoverEvent = {},
            )
        }
    }
}
