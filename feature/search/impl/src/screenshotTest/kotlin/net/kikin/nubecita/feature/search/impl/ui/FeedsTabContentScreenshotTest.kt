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
                                            isPinned = true,
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

@PreviewTest
@Preview(name = "feeds-tab-loaded-pinned-and-large-count-light", showBackground = true)
@Preview(
    name = "feeds-tab-loaded-pinned-and-large-count-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedsTabContentPinnedAndLargeCountScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        currentQuery = "news",
                        loadStatus =
                            SearchFeedsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f3/app.bsky.feed.generator/news",
                                            displayName = "The Long Running Daily News Roundup From Everywhere",
                                            creatorHandle = "newsroom.bsky.social",
                                            creatorDisplayName = "Newsroom",
                                            description = "Everything that happened today, ranked by how many people you follow shared it.",
                                            avatarUrl = null,
                                            likeCount = 2_480_000L,
                                            isPinned = true,
                                        ),
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f4/app.bsky.feed.generator/one",
                                            displayName = "One Like",
                                            creatorHandle = "tiny.bsky.social",
                                            creatorDisplayName = null,
                                            description = null,
                                            avatarUrl = null,
                                            likeCount = 1L,
                                        ),
                                    ),
                                nextCursor = null,
                                endReached = true,
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "feeds-tab-loaded-es419", showBackground = true, locale = "b+es+419")
@Composable
private fun FeedsTabContentLoadedEs419Screenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            FeedsTabContent(
                state =
                    SearchFeedsState(
                        currentQuery = "arte",
                        loadStatus =
                            SearchFeedsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f1/app.bsky.feed.generator/arte",
                                            displayName = "Arte",
                                            creatorHandle = "arte.bsky.social",
                                            creatorDisplayName = "Arte Diario",
                                            description = "Ilustración y fotografía.",
                                            avatarUrl = null,
                                            likeCount = 14_237L,
                                            isPinned = true,
                                        ),
                                        FeedGeneratorUi(
                                            uri = "at://did:plc:f2/app.bsky.feed.generator/foto",
                                            displayName = "Foto",
                                            creatorHandle = "foto.bsky.social",
                                            creatorDisplayName = null,
                                            description = null,
                                            avatarUrl = null,
                                            likeCount = 87L,
                                        ),
                                    ),
                                nextCursor = null,
                                endReached = true,
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}
