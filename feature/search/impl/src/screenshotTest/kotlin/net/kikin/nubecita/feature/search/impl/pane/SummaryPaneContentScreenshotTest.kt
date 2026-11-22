package net.kikin.nubecita.feature.search.impl.pane

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import kotlin.time.Instant

private const val LONG_QUERY =
    "how do I keep a long running foreground service alive on Android when the user swipes the app away and the battery saver is on"

private fun post(
    text: String =
        "Compose now ships a media query API, so a screen can ask what the window is instead of guessing from its width. " +
            "Pair it with scene strategies and the same route becomes a full screen on a phone and a dialog on a tablet.",
    name: String = "Alice Chen",
) = FeedItemUi.Single(
    post =
        PostUi(
            id = "at://did:plc:alice/app.bsky.feed.post/1",
            cid = "bafyreifakecid000000000000000000000000000000000",
            author = AuthorUi(did = "did:plc:alice", handle = "alice.bsky.social", displayName = name, avatarUrl = null),
            createdAt = Instant.parse("2026-11-20T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(replyCount = 14, repostCount = 1240, likeCount = 15300),
            viewer = ViewerStateUi(),
            repostedBy = null,
        ),
)

private fun actor(
    n: Int,
    name: String = "Person $n",
    following: Boolean = false,
) = ActorUi(
    did = "did:plc:p$n",
    handle = "person$n.bsky.social",
    displayName = name,
    avatarUrl = null,
    bio = "Writes about Android, Compose and the things in between.",
    isFollowing = following,
)

private fun feed(
    n: Int,
    pinned: Boolean = false,
) = FeedGeneratorUi(
    uri = "at://did:plc:f$n/app.bsky.feed.generator/f$n",
    displayName = "Feed number $n",
    creatorHandle = "curator$n.bsky.social",
    creatorDisplayName = "Curator $n",
    description = "A hand-picked feed of the best posts about the topic.",
    avatarUrl = null,
    likeCount = 1200L * n,
    isPinned = pinned,
)

private val TWO_FILTERS = PostSearchFilters(followingOnly = true, media = MediaFilter.HAS_MEDIA)

private fun search(
    query: String = "compose",
    filters: PostSearchFilters = PostSearchFilters(),
) = SubmittedSearch(query = query, fromRecent = false, filters = filters)

private val FULL =
    SearchPaneMode.Summary(
        search = search(filters = TWO_FILTERS),
        hitsTotal = 12_400,
        topPost = post(),
        people = persistentListOf(actor(1), actor(2, following = true), actor(3)),
        feeds = persistentListOf(feed(1), feed(2, pinned = true), feed(3)),
        postsStatus = SummarySectionStatus.Loaded,
        peopleStatus = SummarySectionStatus.Loaded,
        feedsStatus = SummarySectionStatus.Loaded,
        // Loaded and empty: hidden, so the sections that predate starter packs draw as they did.
        packsStatus = SummarySectionStatus.Loaded,
    )

private val FULL_WITH_PACKS =
    FULL.copy(
        packs =
            persistentListOf(
                StarterPackUiFixtures.packWithAvatars(slug = "android", name = "Android developers", memberCount = 42),
                StarterPackUiFixtures.pack(slug = "science", name = "Science writers and a name long enough to wrap onto a second line", memberCount = null),
            ),
    )

private val LOADED = SummarySectionStatus.Loaded

@Composable
private fun SummaryFixture(summary: SearchPaneMode.Summary) {
    NubecitaCanvasPreviewTheme {
        SummaryPaneContent(
            summary = summary,
            viewerDid = "did:plc:p3",
            onEvent = {},
            listState = remember { LazyListState() },
            modifier = Modifier,
        )
    }
}

@PreviewTest
@Preview(name = "summary-pane-full-480-light", widthDp = 480, heightDp = 1000, showBackground = true)
@Preview(name = "summary-pane-full-480-dark", widthDp = 480, heightDp = 1000, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "summary-pane-full-700-light", widthDp = 700, heightDp = 1000, showBackground = true)
@Composable
private fun SummaryPaneFullScreenshot() = SummaryFixture(FULL)

@PreviewTest
@Preview(name = "summary-pane-with-packs-480-light", widthDp = 480, heightDp = 1250, showBackground = true)
@Preview(name = "summary-pane-with-packs-480-dark", widthDp = 480, heightDp = 1250, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "summary-pane-with-packs-480-es419", widthDp = 480, heightDp = 1250, showBackground = true, locale = "b+es+419")
@Composable
private fun SummaryPaneWithPacksScreenshot() = SummaryFixture(FULL_WITH_PACKS)

@PreviewTest
@Preview(name = "summary-pane-packs-error-480-light", widthDp = 480, heightDp = 1000, showBackground = true)
@Composable
private fun SummaryPanePacksErrorScreenshot() = SummaryFixture(FULL.copy(packsStatus = SummarySectionStatus.Error))

@PreviewTest
@Preview(name = "summary-pane-only-packs-480-light", widthDp = 480, heightDp = 500, showBackground = true)
@Composable
private fun SummaryPaneOnlyPacksScreenshot() = SummaryFixture(FULL_WITH_PACKS.copy(topPost = null, people = persistentListOf(), feeds = persistentListOf()))

@PreviewTest
@Preview(name = "summary-pane-no-count-480-light", widthDp = 480, heightDp = 1000, showBackground = true)
@Composable
private fun SummaryPaneNoCountScreenshot() = SummaryFixture(FULL.copy(hitsTotal = null, search = search(filters = PostSearchFilters())))

@PreviewTest
@Preview(name = "summary-pane-only-posts-480-light", widthDp = 480, heightDp = 700, showBackground = true)
@Composable
private fun SummaryPaneOnlyPostsScreenshot() = SummaryFixture(FULL.copy(people = persistentListOf(), feeds = persistentListOf()))

@PreviewTest
@Preview(name = "summary-pane-no-results-480-light", widthDp = 480, heightDp = 500, showBackground = true)
@Composable
private fun SummaryPaneNoResultsScreenshot() =
    SummaryFixture(
        SearchPaneMode.Summary(
            search = search(query = "xyzzyplugh"),
            postsStatus = LOADED,
            peopleStatus = LOADED,
            feedsStatus = LOADED,
            packsStatus = LOADED,
        ),
    )

@PreviewTest
@Preview(name = "summary-pane-loading-480-light", widthDp = 480, heightDp = 800, showBackground = true)
@Composable
private fun SummaryPaneLoadingScreenshot() = SummaryFixture(SearchPaneMode.Summary(search = search()))

@PreviewTest
@Preview(name = "summary-pane-one-section-error-480-light", widthDp = 480, heightDp = 900, showBackground = true)
@Composable
private fun SummaryPaneOneSectionErrorScreenshot() = SummaryFixture(FULL.copy(people = persistentListOf(), peopleStatus = SummarySectionStatus.Error))

@PreviewTest
@Preview(name = "summary-pane-es419-480-light", widthDp = 480, heightDp = 1000, showBackground = true, locale = "b+es+419")
@Composable
private fun SummaryPaneEs419Screenshot() = SummaryFixture(FULL)

@PreviewTest
@Preview(name = "summary-pane-pt-br-480-light", widthDp = 480, heightDp = 1000, showBackground = true, locale = "pt-rBR")
@Composable
private fun SummaryPanePtBrScreenshot() = SummaryFixture(FULL)

@PreviewTest
@Preview(name = "summary-pane-long-text-480-light", widthDp = 480, heightDp = 1000, showBackground = true)
@Composable
private fun SummaryPaneLongTextScreenshot() =
    SummaryFixture(
        FULL.copy(
            search =
                search(
                    query = LONG_QUERY,
                    filters = TWO_FILTERS.copy(date = DateFilter.PastWeek, language = "pt", sort = SearchPostsSort.LATEST),
                ),
            topPost = post(name = "A display name that is much too long to fit on one line of the card without being cut"),
            people = persistentListOf(actor(1, name = "A display name that is much too long to fit on one line of a row"), actor(2), actor(3)),
        ),
    )
