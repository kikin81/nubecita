package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchFeedsError
import net.kikin.nubecita.feature.search.impl.SearchFeedsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchFeedsState
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi

@PreviewTest
@Preview(name = "feeds-tab-initial-loading-light", showBackground = true)
@Preview(name = "feeds-tab-initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedsTabContentInitialLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        loadStatus = SearchFeedsLoadStatus.InitialLoading,
                        currentQuery = "art",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "feeds-tab-empty-light", showBackground = true)
@Preview(name = "feeds-tab-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedsTabContentEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        loadStatus = SearchFeedsLoadStatus.Empty,
                        currentQuery = "xyzqq",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "feeds-tab-loaded-light", showBackground = true)
@Preview(name = "feeds-tab-loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedsTabContentLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        currentQuery = "art",
                        loadStatus =
                            SearchFeedsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f1/app.bsky.feed.generator/discover",
                                            displayName = "Discover",
                                            creatorHandle = "skyfeed.bsky.social",
                                            creatorDisplayName = "skyfeed",
                                            description = "A curated feed of trending posts on Bluesky.",
                                            avatarUrl = null,
                                            likeCount = 14_237L,
                                        ),
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f2/app.bsky.feed.generator/art",
                                            displayName = "Art",
                                            creatorHandle = "art-feed.bsky.social",
                                            creatorDisplayName = null,
                                            description = null,
                                            avatarUrl = null,
                                            likeCount = 1_234L,
                                        ),
                                    ),
                                nextCursor = "c2",
                                endReached = false,
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "feeds-tab-loaded-appending-light", showBackground = true)
@Preview(name = "feeds-tab-loaded-appending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedsTabContentLoadedAppendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        currentQuery = "art",
                        loadStatus =
                            SearchFeedsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f1/app.bsky.feed.generator/discover",
                                            displayName = "Discover",
                                            creatorHandle = "skyfeed.bsky.social",
                                            creatorDisplayName = "skyfeed",
                                            description = "A curated feed of trending posts on Bluesky.",
                                            avatarUrl = null,
                                            likeCount = 14_237L,
                                        ),
                                    ),
                                nextCursor = "c2",
                                endReached = false,
                                isAppending = true,
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "feeds-tab-initial-error-network-light", showBackground = true)
@Preview(name = "feeds-tab-initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedsTabContentInitialErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        loadStatus = SearchFeedsLoadStatus.InitialError(error = SearchFeedsError.Network),
                        currentQuery = "art",
                    ),
                onEvent = {},
            )
        }
    }
}
