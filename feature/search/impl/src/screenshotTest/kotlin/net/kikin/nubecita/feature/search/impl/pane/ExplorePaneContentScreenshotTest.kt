package net.kikin.nubecita.feature.search.impl.pane

import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.DiscoverState
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_ACCOUNT_FOLLOWING
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_ACCOUNT_NO_MUTUALS
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_ACCOUNT_WITH_MUTUALS
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_FEED_LOADED
import net.kikin.nubecita.feature.search.impl.ui.SAMPLE_FEED_LOADING

private val FEEDS =
    listOf(
        SAMPLE_FEED_LOADED,
        SAMPLE_FEED_LOADING,
        feedCopy(SAMPLE_FEED_LOADED, "gaming", "Gaming"),
        feedCopy(SAMPLE_FEED_LOADED, "books", "Books"),
        feedCopy(SAMPLE_FEED_LOADED, "music", "Music"),
    )

private val ACCOUNTS =
    listOf(
        SAMPLE_ACCOUNT_NO_MUTUALS,
        SAMPLE_ACCOUNT_WITH_MUTUALS,
        SAMPLE_ACCOUNT_FOLLOWING,
        SAMPLE_ACCOUNT_NO_MUTUALS.copy(did = "did:plc:test4", handle = "dana.bsky.social", displayName = "Dana Ortiz"),
        SAMPLE_ACCOUNT_WITH_MUTUALS.copy(did = "did:plc:test5", handle = "maps.bsky.social", displayName = "Map Nerds"),
    )

private fun feedCopy(
    base: DiscoverFeedUi,
    id: String,
    name: String,
) = base.copy(feed = base.feed.copy(uri = "at://did:plc:test9/app.bsky.feed.generator/$id", displayName = name))

private val PACKS =
    listOf(
        StarterPackUiFixtures.packWithAvatars(slug = "android", name = "Android developers", memberCount = 42),
        StarterPackUiFixtures.pack(slug = "science", name = "Science writers", memberCount = 18),
        StarterPackUiFixtures.packWithAvatars(count = 2, slug = "indie", name = "Indie developers and a name long enough to wrap", memberCount = null),
        StarterPackUiFixtures.pack(slug = "coffee", name = "Coffee people", memberCount = 1),
        StarterPackUiFixtures.packWithAvatars(slug = "trail", name = "Trail runners", memberCount = 64),
    )

// The sections below that predate starter packs keep their packs Empty, so they still draw exactly what they did.
private val LOADED =
    DiscoverState(
        feeds = persistentListOf(*FEEDS.toTypedArray()),
        feedsStatus = DiscoverSectionStatus.Loaded,
        accounts = persistentListOf(*ACCOUNTS.toTypedArray()),
        accountsStatus = DiscoverSectionStatus.Loaded,
        starterPacksStatus = DiscoverSectionStatus.Empty,
    )

private val LOADED_WITH_PACKS =
    LOADED.copy(
        starterPacks = persistentListOf(*PACKS.toTypedArray()),
        starterPacksStatus = DiscoverSectionStatus.Loaded,
    )

/** Feeds and people failed; the packs section alone has content. */
private val ONLY_PACKS =
    DiscoverState(
        feedsStatus = DiscoverSectionStatus.Error,
        accountsStatus = DiscoverSectionStatus.Error,
        starterPacks = persistentListOf(*PACKS.take(3).toTypedArray()),
        starterPacksStatus = DiscoverSectionStatus.Loaded,
    )

private val LOADING =
    DiscoverState(
        feedsStatus = DiscoverSectionStatus.Loading,
        accountsStatus = DiscoverSectionStatus.Loading,
    )

private val BOTH_FAILED =
    DiscoverState(
        feedsStatus = DiscoverSectionStatus.Error,
        accountsStatus = DiscoverSectionStatus.Error,
        starterPacksStatus = DiscoverSectionStatus.Error,
    )

private val ALL_EMPTY =
    DiscoverState(
        feedsStatus = DiscoverSectionStatus.Empty,
        accountsStatus = DiscoverSectionStatus.Empty,
        starterPacksStatus = DiscoverSectionStatus.Empty,
    )

/** Three feeds and three accounts: the last row of each grid has one cell and an empty spacer. */
private val ODD =
    DiscoverState(
        feeds = persistentListOf(*FEEDS.take(3).toTypedArray()),
        feedsStatus = DiscoverSectionStatus.Loaded,
        accounts = persistentListOf(*ACCOUNTS.take(3).toTypedArray()),
        accountsStatus = DiscoverSectionStatus.Loaded,
        starterPacksStatus = DiscoverSectionStatus.Empty,
    )

@Composable
private fun PaneFixture(
    state: DiscoverState,
    feedsExpanded: Boolean = false,
    accountsExpanded: Boolean = false,
    packsExpanded: Boolean = false,
) {
    NubecitaCanvasPreviewTheme {
        // Like the real pane: the measured width decides the feed columns.
        BoxWithConstraints {
            ExplorePaneContent(
                state = state,
                feedColumns = feedColumnsFor(maxWidth),
                feedsExpanded = feedsExpanded,
                accountsExpanded = accountsExpanded,
                packsExpanded = packsExpanded,
                onDiscoverEvent = {},
                onToggleFeeds = {},
                onToggleAccounts = {},
                onTogglePacks = {},
                listState = remember { LazyListState() },
                modifier = Modifier,
            )
        }
    }
}

// Starter packs: the third grid, after feeds and people.
@PreviewTest
@Preview(name = "explore-pane-with-packs-480-light", widthDp = 480, heightDp = 1500, showBackground = true)
@Preview(name = "explore-pane-with-packs-480-dark", widthDp = 480, heightDp = 1500, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "explore-pane-with-packs-480-es419", widthDp = 480, heightDp = 1500, showBackground = true, locale = "b+es+419")
@Preview(name = "explore-pane-with-packs-700-light", widthDp = 700, heightDp = 1500, showBackground = true)
@Composable
private fun ExplorePaneWithPacksScreenshot() = PaneFixture(LOADED_WITH_PACKS)

@PreviewTest
@Preview(name = "explore-pane-packs-expanded-480-light", widthDp = 480, heightDp = 1700, showBackground = true)
@Composable
private fun ExplorePanePacksExpandedScreenshot() = PaneFixture(LOADED_WITH_PACKS, packsExpanded = true)

@PreviewTest
@Preview(name = "explore-pane-only-packs-480-light", widthDp = 480, heightDp = 600, showBackground = true)
@Composable
private fun ExplorePaneOnlyPacksScreenshot() = PaneFixture(ONLY_PACKS)

// 480dp: the narrowest pane a Medium window gives the second pane.
@PreviewTest
@Preview(name = "explore-pane-loaded-480-light", widthDp = 480, heightDp = 1000, showBackground = true)
@Preview(name = "explore-pane-loaded-480-dark", widthDp = 480, heightDp = 1000, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "explore-pane-loaded-480-es419", widthDp = 480, heightDp = 1000, showBackground = true, locale = "b+es+419")
@Composable
private fun ExplorePaneLoaded480Screenshot() = PaneFixture(LOADED)

@PreviewTest
@Preview(name = "explore-pane-loaded-700-light", widthDp = 700, heightDp = 1000, showBackground = true)
@Composable
private fun ExplorePaneLoaded700Screenshot() = PaneFixture(LOADED)

@PreviewTest
@Preview(name = "explore-pane-expanded-700-light", widthDp = 700, heightDp = 1400, showBackground = true)
@Composable
private fun ExplorePaneExpanded700Screenshot() = PaneFixture(LOADED, feedsExpanded = true, accountsExpanded = true)

@PreviewTest
@Preview(name = "explore-pane-loading-480-light", widthDp = 480, heightDp = 700, showBackground = true)
@Preview(name = "explore-pane-loading-700-light", widthDp = 700, heightDp = 700, showBackground = true)
@Composable
private fun ExplorePaneLoadingScreenshot() = PaneFixture(LOADING)

@PreviewTest
@Preview(name = "explore-pane-retry-480-light", widthDp = 480, heightDp = 400, showBackground = true)
@Preview(name = "explore-pane-retry-700-light", widthDp = 700, heightDp = 400, showBackground = true)
@Composable
private fun ExplorePaneRetryScreenshot() = PaneFixture(BOTH_FAILED)

@PreviewTest
@Preview(name = "explore-pane-odd-480-light", widthDp = 480, heightDp = 1000, showBackground = true)
@Preview(name = "explore-pane-odd-700-light", widthDp = 700, heightDp = 1000, showBackground = true)
@Composable
private fun ExplorePaneOddScreenshot() = PaneFixture(ODD)

@PreviewTest
@Preview(name = "explore-pane-empty-480-light", widthDp = 480, heightDp = 300, showBackground = true)
@Preview(name = "explore-pane-empty-700-light", widthDp = 700, heightDp = 300, showBackground = true)
@Composable
private fun ExplorePaneEmptyScreenshot() = PaneFixture(ALL_EMPTY)

// The feed-column threshold: one column at 599dp, two at 600dp.
@PreviewTest
@Preview(name = "explore-pane-loaded-599-light", widthDp = 599, heightDp = 1000, showBackground = true)
@Preview(name = "explore-pane-loaded-600-light", widthDp = 600, heightDp = 1000, showBackground = true)
@Composable
private fun ExplorePaneThresholdScreenshot() = PaneFixture(LOADED)
