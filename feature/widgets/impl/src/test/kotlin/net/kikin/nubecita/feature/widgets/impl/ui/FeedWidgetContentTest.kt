package net.kikin.nubecita.feature.widgets.impl.ui

import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import net.kikin.nubecita.feature.widgets.impl.MAX_WIDGET_POSTS
import net.kikin.nubecita.feature.widgets.impl.model.WidgetPostItem
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Wraps Glance widget unit tests with a generous timeout. The default
 * `runGlanceAppWidgetUnitTest` timeout is short; under CI's contended scheduler
 * the harness's Recomposer spin-up can exceed it, surfacing as a flaky
 * `UncompletedCoroutinesError` (the widget composables themselves launch no
 * coroutines — verified). Virtual time skips delays, so the happy path stays
 * fast; the timeout only adds headroom on a slow runner (nubecita-o2oi).
 */
private fun runWidgetTest(block: GlanceAppWidgetUnitTest.() -> Unit) = runGlanceAppWidgetUnitTest(timeout = 30.seconds) { block() }

internal class FeedWidgetContentTest {
    @Test
    fun loadingStateComposes() =
        runWidgetTest {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loading, STRINGS) }

            onNode(hasTestTag(WidgetTestTags.LOADING)).assertExists()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
        }

    @Test
    fun signedOutStateComposes() =
        runWidgetTest {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.SignedOut, STRINGS) }

            onNode(hasTestTag(WidgetTestTags.SIGNED_OUT)).assertExists()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
        }

    @Test
    fun emptyLoadedStateShowsTheEmptyState() =
        runWidgetTest {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = emptyList()), STRINGS) }

            onNode(hasTestTag(WidgetTestTags.EMPTY)).assertExists()
            onNode(hasTestTag(WidgetTestTags.POST_ROW)).assertDoesNotExist()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
        }

    @Test
    fun populatedStateComposesARowPerPostShowsTitleAndRefresh() =
        runWidgetTest {
            provideComposable {
                FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = listOf(row("at://1", "Alice"), row("at://2", "Bob"), row("at://3", "Cara"))), STRINGS)
            }

            onNode(hasText(TITLE)).assertExists()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
            onAllNodes(hasTestTag(WidgetTestTags.POST_ROW)).assertCountEquals(3)
        }

    @Test
    fun everyRowRendersUpToTheWidgetCap() =
        // Guards the LazyColumn -> Column switch (nubecita-ew77): a Column must
        // still render every row, not just the ones that fit. Uses MAX_WIDGET_POSTS
        // rows so a regression to a bounded/lazy container that drops overflow rows
        // would fail here.
        runWidgetTest {
            val rows = (1..MAX_WIDGET_POSTS).map { row("at://$it", "Author$it") }
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = rows), STRINGS) }

            onAllNodes(hasTestTag(WidgetTestTags.POST_ROW)).assertCountEquals(MAX_WIDGET_POSTS)
        }

    private companion object {
        const val TITLE = "Following"
        val STRINGS = WidgetStrings(loading = "Loading…", signedOut = "Sign in to see your feed", empty = "No posts yet", refresh = "Refresh")

        fun row(
            uri: String,
            author: String,
        ): WidgetRow =
            WidgetRow(
                item =
                    WidgetPostItem(
                        postUri = uri,
                        authorDisplay = author,
                        handle = "$author.bsky.social",
                        text = "hello from $author",
                        relativeTime = "2h",
                        hasMedia = false,
                        extraImageCount = 0,
                        mediaContentDescription = null,
                    ),
                thumbnail = null,
            )
    }
}
