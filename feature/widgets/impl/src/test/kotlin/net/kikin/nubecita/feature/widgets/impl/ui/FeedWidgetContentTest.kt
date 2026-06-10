package net.kikin.nubecita.feature.widgets.impl.ui

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import net.kikin.nubecita.feature.widgets.impl.model.WidgetPostItem
import org.junit.jupiter.api.Test

internal class FeedWidgetContentTest {
    @Test
    fun loadingStateComposes() =
        runGlanceAppWidgetUnitTest {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loading) }

            onNode(hasTestTag(WidgetTestTags.LOADING)).assertExists()
        }

    @Test
    fun signedOutStateComposes() =
        runGlanceAppWidgetUnitTest {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.SignedOut) }

            onNode(hasTestTag(WidgetTestTags.SIGNED_OUT)).assertExists()
        }

    @Test
    fun emptyLoadedStateShowsTheEmptyState() =
        runGlanceAppWidgetUnitTest {
            provideComposable { FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = emptyList())) }

            onNode(hasTestTag(WidgetTestTags.EMPTY)).assertExists()
            onNode(hasTestTag(WidgetTestTags.POST_ROW)).assertDoesNotExist()
        }

    @Test
    fun populatedStateComposesARowPerPostAndShowsTheTitle() =
        runGlanceAppWidgetUnitTest {
            provideComposable {
                FeedWidgetContent(TITLE, FeedWidgetUiState.Loaded(rows = listOf(row("at://1", "Alice"), row("at://2", "Bob"), row("at://3", "Cara"))))
            }

            onNode(hasText(TITLE)).assertExists()
            onAllNodes(hasTestTag(WidgetTestTags.POST_ROW)).assertCountEquals(3)
        }

    private companion object {
        const val TITLE = "Following"

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
