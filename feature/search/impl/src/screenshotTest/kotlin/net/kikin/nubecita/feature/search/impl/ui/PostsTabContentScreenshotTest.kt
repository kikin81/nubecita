package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchPostsError
import net.kikin.nubecita.feature.search.impl.SearchPostsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchPostsState
import kotlin.time.Instant

@PreviewTest
@Preview(name = "posts-tab-initial-loading-light", showBackground = true)
@Preview(name = "posts-tab-initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsTabContentInitialLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsTabContent(
                state =
                    SearchPostsState(
                        loadStatus = SearchPostsLoadStatus.InitialLoading,
                        currentQuery = "kotlin",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "posts-tab-empty-light", showBackground = true)
@Preview(name = "posts-tab-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsTabContentEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsTabContent(
                state =
                    SearchPostsState(
                        loadStatus = SearchPostsLoadStatus.Empty,
                        currentQuery = "xyzqq",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "posts-tab-loaded-highlight-light", showBackground = true)
@Preview(name = "posts-tab-loaded-highlight-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsTabContentLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsTabContent(
                state =
                    SearchPostsState(
                        currentQuery = "kotlin",
                        loadStatus =
                            SearchPostsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        screenshotHit("at://p1", "Kotlin coroutines are great"),
                                        screenshotHit("at://p2", "I love writing Kotlin every day"),
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
@Preview(name = "posts-tab-loaded-appending-light", showBackground = true)
@Preview(name = "posts-tab-loaded-appending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsTabContentLoadedAppendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsTabContent(
                state =
                    SearchPostsState(
                        currentQuery = "kotlin",
                        loadStatus =
                            SearchPostsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        screenshotHit("at://p1", "Kotlin coroutines are great"),
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
@Preview(name = "posts-tab-initial-error-network-light", showBackground = true)
@Preview(name = "posts-tab-initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsTabContentInitialErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsTabContent(
                state =
                    SearchPostsState(
                        loadStatus =
                            SearchPostsLoadStatus.InitialError(error = SearchPostsError.Network),
                        currentQuery = "kotlin",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "posts-tab-initial-error-rate-limited-light", showBackground = true)
@Preview(name = "posts-tab-initial-error-rate-limited-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsTabContentInitialErrorRateLimitedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsTabContent(
                state =
                    SearchPostsState(
                        loadStatus =
                            SearchPostsLoadStatus.InitialError(error = SearchPostsError.RateLimited),
                        currentQuery = "kotlin",
                    ),
                onEvent = {},
            )
        }
    }
}

private fun screenshotHit(
    uri: String,
    text: String,
): FeedItemUi.Single =
    FeedItemUi.Single(
        post =
            PostUi(
                id = uri,
                cid = "bafyreifakecid000000000000000000000000000000000",
                author =
                    AuthorUi(
                        did = "did:plc:fake",
                        handle = "fake.bsky.social",
                        displayName = "Fake User",
                        avatarUrl = null,
                    ),
                createdAt = Instant.parse("2026-04-25T12:00:00Z"),
                text = text,
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            ),
    )
