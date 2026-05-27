package net.kikin.nubecita.feature.notifications.impl

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Screenshot baselines for `NotificationsScreen`'s render-state matrix:
 * - InitialLoading
 * - Empty
 * - InitialError × {Network, Unauthenticated, Unknown}
 * - Loaded × {idle, refreshing, appending} × {mixed reasons, aggregated-heavy}
 *
 * Driven through [NotificationsContent] directly with fixture
 * [NotificationsScreenViewState] inputs so captures are deterministic
 * across machines — no Hilt graph, no live network, no animation.
 * Time-driven previews pin [LocalClock] to a fixed instant so
 * `rememberRelativeTimeText` resolves to deterministic "2h" / "3d"
 * buckets.
 */

@PreviewTest
@Preview(name = "initial-loading-light", showBackground = true)
@Preview(name = "initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenInitialLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(viewState = NotificationsScreenViewState.InitialLoading)
    }
}

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(viewState = NotificationsScreenViewState.Empty)
    }
}

@PreviewTest
@Preview(name = "initial-error-network-light", showBackground = true)
@Preview(name = "initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenInitialErrorNetworkScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState = NotificationsScreenViewState.InitialError(NotificationsError.Network),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unauthenticated-light", showBackground = true)
@Preview(name = "initial-error-unauthenticated-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenInitialErrorUnauthScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState = NotificationsScreenViewState.InitialError(NotificationsError.Unauthenticated),
        )
    }
}

@PreviewTest
@Preview(name = "initial-error-unknown-light", showBackground = true)
@Preview(name = "initial-error-unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenInitialErrorUnknownScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState = NotificationsScreenViewState.InitialError(NotificationsError.Unknown),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-mixed-light", showBackground = true)
@Preview(name = "loaded-mixed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenLoadedMixedScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState =
                NotificationsScreenViewState.Loaded(
                    items = previewMixedItems(),
                    activeFilter = NotificationFilter.All,
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-aggregated-light", showBackground = true)
@Preview(name = "loaded-aggregated-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsScreenLoadedAggregatedScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState =
                NotificationsScreenViewState.Loaded(
                    items =
                        persistentListOf<NotificationItemUi>(
                            NotificationItemUiFixtures.aggregatedLikes(
                                actorCount = 8,
                                itemKey = "agg-likes-1",
                                indexedAt = PREVIEW_INDEXED_AT,
                            ),
                            NotificationItemUiFixtures.aggregatedReposts(
                                actorCount = 4,
                                itemKey = "agg-reposts-1",
                                indexedAt = PREVIEW_INDEXED_AT,
                                isRead = true,
                            ),
                            NotificationItemUiFixtures.aggregatedFollows(
                                actorCount = 5,
                                itemKey = "agg-follows-1",
                                indexedAt = PREVIEW_INDEXED_AT,
                            ),
                        ).toImmutableList(),
                    activeFilter = NotificationFilter.All,
                    isAppending = false,
                    isRefreshing = false,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-refreshing-light", showBackground = true)
@Composable
private fun NotificationsScreenLoadedRefreshingScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState =
                NotificationsScreenViewState.Loaded(
                    items = previewMixedItems(),
                    activeFilter = NotificationFilter.All,
                    isAppending = false,
                    isRefreshing = true,
                ),
        )
    }
}

@PreviewTest
@Preview(name = "loaded-appending-light", showBackground = true)
@Composable
private fun NotificationsScreenLoadedAppendingScreenshot() {
    NubecitaCanvasPreviewTheme {
        NotificationsScreenshotHost(
            viewState =
                NotificationsScreenViewState.Loaded(
                    items = previewMixedItems(),
                    activeFilter = NotificationFilter.Mentions,
                    isAppending = true,
                    isRefreshing = false,
                ),
        )
    }
}

@Composable
private fun NotificationsScreenshotHost(
    viewState: NotificationsScreenViewState,
    activeFilter: NotificationFilter = NotificationFilter.All,
) {
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalClock provides PreviewClock) {
        NotificationsContent(
            viewState = viewState,
            activeFilter = activeFilter,
            listState = listState,
            snackbarHostState = snackbarHostState,
            onEvent = {},
        )
    }
}

// Fixed instants for screenshot determinism. Paired with PreviewClock,
// `rememberRelativeTimeText` resolves to "2h" forever.
private val PREVIEW_NOW = Instant.parse("2026-05-26T12:00:00Z")
private val PREVIEW_INDEXED_AT = Instant.parse("2026-05-26T10:00:00Z")

private object PreviewClock : Clock {
    override fun now(): Instant = PREVIEW_NOW
}

private fun previewMixedItems() =
    persistentListOf<NotificationItemUi>(
        NotificationItemUiFixtures.singleLike(
            itemKey = "mixed-like-single",
            indexedAt = PREVIEW_INDEXED_AT,
        ),
        NotificationItemUiFixtures.aggregatedLikes(
            actorCount = 3,
            itemKey = "mixed-likes-agg",
            indexedAt = PREVIEW_INDEXED_AT,
        ),
        NotificationItemUiFixtures.singleReply(
            itemKey = "mixed-reply",
            isRead = true,
            indexedAt = PREVIEW_INDEXED_AT,
        ),
        NotificationItemUiFixtures.singleFollow(
            itemKey = "mixed-follow",
            indexedAt = PREVIEW_INDEXED_AT,
        ),
        NotificationItemUiFixtures.singleMention(
            itemKey = "mixed-mention",
            indexedAt = PREVIEW_INDEXED_AT,
        ),
        NotificationItemUiFixtures.singleQuote(
            itemKey = "mixed-quote",
            isRead = true,
            indexedAt = PREVIEW_INDEXED_AT,
        ),
    ).toImmutableList()
