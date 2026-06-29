package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filter
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.search.impl.DiscoverEvent
import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.DiscoverState
import net.kikin.nubecita.feature.search.impl.FeedPreviewStatus
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.SuggestedAccountUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedFeedUi

/**
 * Renders the two horizontal [LazyRow] carousels for the Discover tab:
 *  1. **Suggested accounts** (hidden when [DiscoverState.accountsStatus] is
 *     not [DiscoverSectionStatus.Loaded] or the list is empty).
 *  2. **Discover feeds** (hidden when [DiscoverState.feedsStatus] is not
 *     [DiscoverSectionStatus.Loaded] or the list is empty).
 *
 * Each section has a bold section-header label above the [LazyRow].
 *
 * **Scroll-settled lazy preview trigger:** The feeds [LazyRow] tracks its own
 * [androidx.compose.foundation.lazy.LazyListState]. A [LaunchedEffect] collects
 * a [snapshotFlow] on [LazyListState.isScrollInProgress]: when scrolling
 * settles (`isScrollInProgress` transitions to `false`), all currently visible
 * feed URIs are emitted via [DiscoverEvent.OnFeedCardVisible]. The
 * [DiscoverViewModel][net.kikin.nubecita.feature.search.impl.DiscoverViewModel]
 * caches the fetch once started so repeated emissions are no-ops.
 *
 * This composable is stateless with respect to loading — callers pass the
 * full [DiscoverState] and let the VM drive the fetch lifecycle.
 *
 * Used by [net.kikin.nubecita.feature.search.impl.SearchScreen] under
 * [net.kikin.nubecita.feature.search.impl.SearchPhase.Discover] and by
 * [DiscoverScreenshotTest][net.kikin.nubecita.feature.search.impl.DiscoverScreenshotTest]
 * (called with plain fixture state — no VM/Hilt dependency).
 */
@Composable
internal fun DiscoverSections(
    state: DiscoverState,
    onEvent: (DiscoverEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (state.accountsStatus == DiscoverSectionStatus.Loaded && state.accounts.isNotEmpty()) {
            AccountsSection(
                accounts = state.accounts,
                onEvent = onEvent,
            )
        }
        if (state.feedsStatus == DiscoverSectionStatus.Loaded && state.feeds.isNotEmpty()) {
            FeedsSection(
                feeds = state.feeds,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun AccountsSection(
    accounts: ImmutableList<SuggestedAccountUi>,
    onEvent: (DiscoverEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        DiscoverSectionHeader(
            title = stringResource(R.string.discover_accounts_section_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.wrapContentHeight(),
        ) {
            items(
                items = accounts,
                key = { it.did },
            ) { account ->
                SuggestedAccountCard(
                    account = account,
                    onAccountClick = {
                        onEvent(DiscoverEvent.OnAccountTapped(did = account.did, handle = account.handle))
                    },
                    onFollowClick = {
                        onEvent(DiscoverEvent.OnFollowTapped(did = account.did))
                    },
                    onDismissClick = {
                        onEvent(DiscoverEvent.OnAccountDismissed(did = account.did))
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedsSection(
    feeds: ImmutableList<DiscoverFeedUi>,
    onEvent: (DiscoverEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentFeeds by rememberUpdatedState(feeds)
    val currentOnEvent by rememberUpdatedState(onEvent)

    // Scroll-settled lazy preview: when scrolling stops, fire OnFeedCardVisible
    // for every currently visible feed so the VM can initiate a one-shot preview
    // fetch. Also fires on initial composition (isScrollInProgress = false at
    // first snapshot), which loads previews for the initially visible cards.
    // The VM guards IdempotentId on FeedPreviewStatus, so repeat calls are no-ops.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it } // settled: not scrolling
            .collect {
                listState.layoutInfo.visibleItemsInfo.forEach { itemInfo ->
                    val uri = itemInfo.key as? String ?: return@forEach
                    currentOnEvent(DiscoverEvent.OnFeedCardVisible(uri))
                }
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        DiscoverSectionHeader(
            title = stringResource(R.string.discover_feeds_section_title),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.wrapContentHeight(),
        ) {
            items(
                items = feeds,
                key = { it.feed.uri },
            ) { discoverFeed ->
                SuggestedFeedCard(
                    discoverFeed = discoverFeed,
                    onFeedClick = {
                        currentOnEvent(
                            DiscoverEvent.OnFeedTapped(
                                uri = discoverFeed.feed.uri,
                                displayName = discoverFeed.feed.displayName,
                            ),
                        )
                    },
                    onPinClick = {
                        currentOnEvent(DiscoverEvent.OnPinTapped(uri = discoverFeed.feed.uri))
                    },
                )
            }
        }
    }
}

@Composable
private fun DiscoverSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(
    name = "DiscoverSections — populated, light",
    showBackground = true,
    heightDp = 500,
)
@Composable
private fun DiscoverSectionsPopulatedLightPreview() {
    NubecitaCanvasPreviewTheme {
        DiscoverSections(
            state = DISCOVER_POPULATED_STATE,
            onEvent = {},
        )
    }
}

@Preview(
    name = "DiscoverSections — populated, dark",
    showBackground = true,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DiscoverSectionsPopulatedDarkPreview() {
    NubecitaCanvasPreviewTheme {
        DiscoverSections(
            state = DISCOVER_POPULATED_STATE,
            onEvent = {},
        )
    }
}

@Preview(
    name = "DiscoverSections — accounts only, light",
    showBackground = true,
    heightDp = 300,
)
@Composable
private fun DiscoverSectionsAccountsOnlyPreview() {
    NubecitaCanvasPreviewTheme {
        DiscoverSections(
            state = DISCOVER_ACCOUNTS_ONLY_STATE,
            onEvent = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Shared fixture state for previews + DiscoverScreenshotTest
// ---------------------------------------------------------------------------

internal val DISCOVER_POPULATED_STATE =
    DiscoverState(
        accounts =
            persistentListOf(
                SAMPLE_ACCOUNT_NO_MUTUALS,
                SAMPLE_ACCOUNT_WITH_MUTUALS,
                SAMPLE_ACCOUNT_FOLLOWING,
            ),
        accountsStatus = DiscoverSectionStatus.Loaded,
        feeds =
            persistentListOf(
                SAMPLE_FEED_LOADED,
                SAMPLE_FEED_LOADING,
                DiscoverFeedUi(
                    feed =
                        SuggestedFeedUi(
                            uri = "at://did:plc:bench3/app.bsky.feed.generator/dev",
                            displayName = "Indie Dev",
                            creatorHandle = "shipit.bsky.social",
                            avatarUrl = null,
                            description = null,
                            isPinned = true,
                        ),
                    preview = SAMPLE_FEED_LOADED.preview,
                    previewStatus = FeedPreviewStatus.Loaded,
                ),
            ),
        feedsStatus = DiscoverSectionStatus.Loaded,
    )

internal val DISCOVER_ACCOUNTS_ONLY_STATE =
    DiscoverState(
        accounts =
            persistentListOf(
                SAMPLE_ACCOUNT_NO_MUTUALS,
                SAMPLE_ACCOUNT_WITH_MUTUALS,
            ),
        accountsStatus = DiscoverSectionStatus.Loaded,
        feeds = persistentListOf(),
        feedsStatus = DiscoverSectionStatus.Empty,
    )

internal val DISCOVER_FEEDS_ONLY_STATE =
    DiscoverState(
        accounts = persistentListOf(),
        accountsStatus = DiscoverSectionStatus.Empty,
        feeds =
            persistentListOf(
                SAMPLE_FEED_LOADED,
                SAMPLE_FEED_LOADING,
            ),
        feedsStatus = DiscoverSectionStatus.Loaded,
    )

internal val DISCOVER_EMPTY_STATE =
    DiscoverState(
        accounts = persistentListOf(),
        accountsStatus = DiscoverSectionStatus.Empty,
        feeds = persistentListOf(),
        feedsStatus = DiscoverSectionStatus.Empty,
    )
