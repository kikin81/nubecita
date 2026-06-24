package net.kikin.nubecita.feature.widgets.impl.ui

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import net.kikin.nubecita.feature.widgets.impl.model.WidgetPostItem
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

internal class FeedWidgetContentTest {
    @Test
    fun loadingStateComposes() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loading, STRINGS) }

            onNode(hasTestTag(WidgetTestTags.LOADING)).assertExists()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
        }

    @Test
    fun signedOutStateComposes() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.SignedOut, STRINGS) }

            onNode(hasTestTag(WidgetTestTags.SIGNED_OUT)).assertExists()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
        }

    @Test
    fun emptyLoadedStateShowsTheEmptyState() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = emptyList()), STRINGS) }

            onNode(hasTestTag(WidgetTestTags.EMPTY)).assertExists()
            onNode(hasTestTag(WidgetTestTags.POST_ROW)).assertDoesNotExist()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
        }

    @Test
    fun populatedStateComposesARowPerPostShowsTitleAndRefresh() =
        runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
            provideComposable {
                FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = listOf(row("at://1", "Alice"), row("at://2", "Bob"), row("at://3", "Cara"))), STRINGS)
            }

            onNode(hasText(TITLE)).assertExists()
            onNode(hasTestTag(WidgetTestTags.REFRESH)).assertExists()
            onAllNodes(hasTestTag(WidgetTestTags.POST_ROW)).assertCountEquals(3)
        }

    private companion object {
        // The default runGlanceAppWidgetUnitTest timeout is short; under CI's
        // contended scheduler the harness's recomposer coroutine can take longer
        // to settle, surfacing as a flaky UncompletedCoroutinesError. A generous
        // timeout absorbs that without affecting the fast happy path (nubecita-o2oi).
        val TEST_TIMEOUT = 30.seconds

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
