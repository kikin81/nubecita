package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
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
import net.kikin.nubecita.feature.search.impl.HashtagState
import net.kikin.nubecita.feature.search.impl.HashtagTabState
import net.kikin.nubecita.feature.search.impl.SearchPostsError
import net.kikin.nubecita.feature.search.impl.SearchPostsLoadStatus
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import kotlin.time.Instant

/**
 * Baselines for the stateless [HashtagContent]: loaded Top results, the
 * author-filter chip (off and on, on the Latest tab), the empty tab, and
 * the initial-error tab. Phone-sized so the top bar, tabs and chip lay out
 * as on device.
 */
@PreviewTest
@Preview(name = "hashtag-loaded-light", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "hashtag-loaded-dark", widthDp = 411, heightDp = 891, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HashtagLoadedScreenshot() {
    HashtagFixture(
        HashtagState(
            tag = "Kotlin",
            top = HashtagTabState(status = loaded("Shipping a #Kotlin library today", "Coroutines tip for #kotlin folks")),
        ),
    )
}

@PreviewTest
@Preview(name = "hashtag-author-filter-off-light", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "hashtag-author-filter-off-dark", widthDp = 411, heightDp = 891, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HashtagAuthorFilterOffScreenshot() {
    HashtagFixture(
        HashtagState(
            tag = "Kotlin",
            author = "alice.bsky.social",
            selectedSort = SearchPostsSort.LATEST,
            latest = HashtagTabState(status = loaded("Latest #kotlin post")),
        ),
    )
}

@PreviewTest
@Preview(name = "hashtag-author-filter-on-light", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "hashtag-author-filter-on-dark", widthDp = 411, heightDp = 891, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HashtagAuthorFilterOnScreenshot() {
    HashtagFixture(
        HashtagState(
            tag = "Kotlin",
            author = "alice.bsky.social",
            authorFilterOn = true,
            top = HashtagTabState(status = loaded("Alice on #kotlin")),
        ),
    )
}

@PreviewTest
@Preview(name = "hashtag-empty-light", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "hashtag-empty-dark", widthDp = 411, heightDp = 891, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HashtagEmptyScreenshot() {
    HashtagFixture(HashtagState(tag = "\$BTC", top = HashtagTabState(status = SearchPostsLoadStatus.Empty)))
}

@PreviewTest
@Preview(name = "hashtag-error-light", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "hashtag-error-dark", widthDp = 411, heightDp = 891, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HashtagErrorScreenshot() {
    HashtagFixture(
        HashtagState(
            tag = "Kotlin",
            top = HashtagTabState(status = SearchPostsLoadStatus.InitialError(SearchPostsError.Network)),
        ),
    )
}

@Composable
private fun HashtagFixture(state: HashtagState) {
    NubecitaTheme(dynamicColor = false) {
        HashtagContent(state = state, onEvent = {}, onBack = {})
    }
}

private fun loaded(vararg texts: String): SearchPostsLoadStatus.Loaded =
    SearchPostsLoadStatus.Loaded(
        items = persistentListOf(*texts.mapIndexed { i, text -> hit("at://p$i", text) }.toTypedArray()),
        nextCursor = null,
        endReached = true,
    )

private fun hit(
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
