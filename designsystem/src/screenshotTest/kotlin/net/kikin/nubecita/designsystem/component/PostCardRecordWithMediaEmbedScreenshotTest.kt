package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for [PostCardRecordWithMediaEmbed]'s
 * non-video shapes — 4 cases (resolved/unavailable × Images/External)
 * × {light, dark} = 8 baselines. The with-video case lives in
 * `:feature:feed:impl`'s screenshot-test source set because the
 * `videoEmbedSlot` body imports `PostCardVideoEmbed` which is
 * internal to that module.
 */

private val FIXED_CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

private val FIXED_AUTHOR: AuthorUi =
    AuthorUi(
        did = "did:plc:fixed",
        handle = "acyn.bsky.social",
        displayName = "Acyn",
        avatarUrl = null,
    )

private const val FIXED_TEXT: String =
    "Quoted post inside a recordWithMedia composition — the " +
        "parent's media renders above this card per the official " +
        "Bluesky Android client's layout."

private fun fixedQuotedPost(): QuotedPostUi =
    QuotedPostUi(
        uri = "at://did:plc:fixed/app.bsky.feed.post/q",
        cid = "bafyreifixedquotedcid000000000000000000000000000",
        author = FIXED_AUTHOR,
        createdAt = FIXED_CREATED_AT,
        text = FIXED_TEXT,
        facets = persistentListOf(),
        embed = QuotedEmbedUi.Empty,
    )

private fun fixedImagesMedia(): EmbedUi.Images =
    EmbedUi.Images(
        items =
            persistentListOf(
                ImageUi(
                    fullsizeUrl = "https://example.com/preview.jpg",
                    thumbUrl = "https://example.com/preview.jpg",
                    altText = null,
                    aspectRatio = 16f / 9f,
                ),
            ),
    )

private fun fixedExternalMedia(): EmbedUi.External =
    EmbedUi.External(
        uri = "https://www.theverge.com/article",
        domain = "theverge.com",
        title = "An article headline goes here",
        description = "Short description copy for the link card.",
        thumbUrl = null,
    )

@PreviewTest
@Preview(name = "resolved-images-light", showBackground = true)
@Preview(name = "resolved-images-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRecordWithMediaResolvedImagesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.Record(quotedPost = fixedQuotedPost()),
            media = fixedImagesMedia(),
        )
    }
}

@PreviewTest
@Preview(name = "resolved-external-light", showBackground = true)
@Preview(name = "resolved-external-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRecordWithMediaResolvedExternalScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.Record(quotedPost = fixedQuotedPost()),
            media = fixedExternalMedia(),
            onExternalMediaTap = {},
        )
    }
}

@PreviewTest
@Preview(name = "unavailable-images-light", showBackground = true)
@Preview(name = "unavailable-images-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRecordWithMediaUnavailableImagesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
            media = fixedImagesMedia(),
        )
    }
}

@PreviewTest
@Preview(name = "unavailable-external-light", showBackground = true)
@Preview(name = "unavailable-external-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRecordWithMediaUnavailableExternalScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked),
            media = fixedExternalMedia(),
            onExternalMediaTap = {},
        )
    }
}
