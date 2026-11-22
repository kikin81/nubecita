package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.data.models.ThreadPositionUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import java.time.ZoneOffset
import kotlin.time.Instant

/**
 * Screenshot baselines for the thread pill ("3/5"): the pill alone, then in
 * each card that draws it — [PostCard], [FocusPostCard] and
 * [PostCardQuotedPost].
 *
 * The cases pin the placements that can go wrong: the pill following the last
 * word, wrapping with a last line that is already full, sitting after a link
 * that ends the text, and standing on its own line above the media of a post
 * with no text. Posts without a position are covered by the cards' own
 * baselines, which must not move.
 */

private val CREATED_AT: Instant = Instant.parse("2025-10-15T12:00:00Z")

private val AUTHOR =
    AuthorUi(
        did = "did:plc:fakedid000000000000000",
        handle = "alice.bsky.social",
        displayName = "Alice Chen",
        avatarUrl = null,
    )

private const val LINK_TEXT = "Notes from shipping the feed cache, written up at example.com/notes"

private fun numberedPost(
    text: String = "Here is what I learned shipping the feed cache this month.",
    position: ThreadPositionUi = ThreadPositionUi(index = 1, count = 5),
    facets: ImmutableList<Facet> = persistentListOf(),
    embed: EmbedUi = EmbedUi.Empty,
): PostUi =
    PostUi(
        id = "at://did:plc:fakedid000000000000000/app.bsky.feed.post/thread",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = AUTHOR,
        createdAt = CREATED_AT,
        text = text,
        facets = facets,
        embed = embed,
        stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
        viewer = ViewerStateUi(),
        repostedBy = null,
        threadPosition = position,
    )

@PreviewTest
@Preview(name = "thread-badge-light", showBackground = true)
@Preview(name = "thread-badge-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadPositionBadgeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThreadPositionBadge(position = ThreadPositionUi(index = 1, count = 5))
                ThreadPositionBadge(position = ThreadPositionUi(index = 12, count = 240))
            }
        }
    }
}

@PreviewTest
@Preview(name = "thread-postcard-text-light", showBackground = true)
@Preview(name = "thread-postcard-text-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardThreadPositionScreenshot() {
    PostCard(post = numberedPost())
}

// The last line is already full, so the pill and the word it follows wrap together.
@PreviewTest
@Preview(name = "thread-postcard-full-last-line-light", showBackground = true)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardThreadPositionFullLastLineScreenshot() {
    PostCard(
        post =
            numberedPost(
                text = "The thing about writing a thread is that every post has to stand alone and also lead on to the next.",
                position = ThreadPositionUi(index = 12, count = 240),
            ),
    )
}

@PreviewTest
@Preview(name = "thread-postcard-trailing-link-light", showBackground = true)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardThreadPositionTrailingLinkScreenshot() {
    val linkStart = LINK_TEXT.length - "example.com/notes".length
    PostCard(
        post =
            numberedPost(
                text = LINK_TEXT,
                position = ThreadPositionUi(index = 2, count = 5),
                facets =
                    persistentListOf(
                        Facet(
                            features = listOf(FacetLink(uri = Uri("https://example.com/notes"))),
                            index = FacetByteSlice(byteStart = linkStart.toLong(), byteEnd = LINK_TEXT.length.toLong()),
                        ),
                    ),
            ),
    )
}

// No text: the pill stands on its own line, above the image.
@PreviewTest
@Preview(name = "thread-postcard-textless-image-light", showBackground = true)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardThreadPositionTextlessScreenshot() {
    PostCard(
        post =
            numberedPost(
                text = "",
                position = ThreadPositionUi(index = 3, count = 5),
                embed =
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
                    ),
            ),
    )
}

@PreviewTest
@Preview(name = "thread-postcard-font-scale-2-light", showBackground = true, fontScale = 2f)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardThreadPositionLargeFontScreenshot() {
    PostCard(post = numberedPost(position = ThreadPositionUi(index = 12, count = 240)))
}

@PreviewTest
@Preview(name = "thread-focus-light", showBackground = true)
@Preview(name = "thread-focus-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusPostCardThreadPositionScreenshot() {
    NubecitaCanvasPreviewTheme {
        FocusPostCard(post = numberedPost(position = ThreadPositionUi(index = 3, count = 5)), zoneId = ZoneOffset.UTC)
    }
}

@PreviewTest
@Preview(name = "thread-quoted-light", showBackground = true)
@Preview(name = "thread-quoted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostThreadPositionScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(
            quotedPost =
                QuotedPostUi(
                    uri = "at://did:plc:fakedid000000000000000/app.bsky.feed.post/quoted",
                    cid = "bafyreifixedquotedcid000000000000000000000000000",
                    author = AUTHOR,
                    createdAt = CREATED_AT,
                    text = "Second post of the thread, quoted somewhere else.",
                    facets = persistentListOf(),
                    embed = QuotedEmbedUi.Empty,
                    threadPosition = ThreadPositionUi(index = 2, count = 4),
                ),
        )
    }
}
