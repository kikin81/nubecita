package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Renders a Bluesky `app.bsky.embed.recordWithMedia#view` as a
 * composition: media on top, quoted card below. Matches the
 * official Bluesky Android client's layout (per the bd issue's UX
 * note + the `2026-04-25-postcard-embed-scope-v1.md` design doc).
 *
 * Internally a single `Column` with two `when` dispatches:
 *
 * - The media slot ([EmbedUi.MediaEmbed]) routes to
 *   [PostCardImageEmbed] / the host-supplied `videoEmbedSlot` /
 *   [PostCardExternalEmbed]. NO surrounding `Surface` — adjacency
 *   to the quoted card below + the parent-post-body context provide
 *   the visual grouping. Each leaf renders at its native treatment.
 * - An 8 dp `Spacer` separates media from the quoted card.
 * - The record slot ([EmbedUi.RecordOrUnavailable]) routes to
 *   [PostCardQuotedPost] (resolved) or [PostCardRecordUnavailable]
 *   (unavailable chip). The quoted card carries its own
 *   `surfaceContainerLow` Surface — that's the visual anchor.
 *
 * **Tap target.** The composable does NOT have its own
 * `Modifier.clickable`. Tap-to-open-PostDetail is deferred to the
 * follow-up bd issue paired with the post-detail destination, same
 * as 6vq's quoted card.
 *
 * **Inner taps.** When `media is External`, [onExternalMediaTap]
 * is forwarded to [PostCardExternalEmbed.onTap] — the link card IS
 * a real top-level affordance on the parent post, so it SHOULD
 * open in Custom Tabs the same way a top-level `EmbedUi.External`
 * does. Default null preserves `:designsystem`'s clickable-free
 * preview / screenshot path. The host (`PostCard.EmbedSlot`)
 * passes `callbacks.onExternalEmbedTap`.
 *
 * **Compile-time bounds.** Both inner `when` dispatches are
 * exhaustive over their respective marker sealed interfaces — no
 * `else` branch. Any future addition to [EmbedUi.MediaEmbed] or
 * [EmbedUi.RecordOrUnavailable] surfaces as a compile error here.
 */
@Composable
public fun PostCardRecordWithMediaEmbed(
    record: EmbedUi.RecordOrUnavailable,
    media: EmbedUi.MediaEmbed,
    modifier: Modifier = Modifier,
    onExternalMediaTap: ((uri: String) -> Unit)? = null,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Track whether the media branch actually rendered something. If
        // `media is Video` and `videoEmbedSlot` is null, the branch
        // produces nothing — same convention as PostCard.EmbedSlot's
        // top-level Video handling. Without this guard, the 8 dp Spacer
        // below would leave a phantom gap above the quoted card.
        val mediaRendered: Boolean =
            when (media) {
                is EmbedUi.Images -> {
                    PostCardImageEmbed(items = media.items)
                    true
                }
                is EmbedUi.External -> {
                    PostCardExternalEmbed(
                        uri = media.uri,
                        domain = media.domain,
                        title = media.title,
                        description = media.description,
                        thumbUrl = media.thumbUrl,
                        onTap = onExternalMediaTap,
                    )
                    true
                }
                is EmbedUi.Video ->
                    if (videoEmbedSlot != null) {
                        videoEmbedSlot(media)
                        true
                    } else {
                        false
                    }
            }
        if (mediaRendered) Spacer(Modifier.height(8.dp))
        when (record) {
            is EmbedUi.Record ->
                PostCardQuotedPost(
                    quotedPost = record.quotedPost,
                    quotedVideoEmbedSlot = quotedVideoEmbedSlot,
                )
            is EmbedUi.RecordUnavailable -> PostCardRecordUnavailable(reason = record.reason)
        }
    }
}

// ---------- Previews ----------

private val PREVIEW_CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

private val PREVIEW_AUTHOR: AuthorUi =
    AuthorUi(
        did = "did:plc:preview",
        handle = "preview.bsky.social",
        displayName = "Preview Author",
        avatarUrl = null,
    )

private fun previewQuotedPost(embed: QuotedEmbedUi = QuotedEmbedUi.Empty): QuotedPostUi =
    QuotedPostUi(
        uri = "at://did:plc:preview/app.bsky.feed.post/q",
        cid = "bafyreifakecid000000000000000000000000000000000",
        author = PREVIEW_AUTHOR,
        createdAt = PREVIEW_CREATED_AT,
        text =
            "Quoted post inside a recordWithMedia composition — the parent " +
                "post's media renders above this card.",
        facets = persistentListOf(),
        embed = embed,
    )

@Preview(name = "RecordWithMedia — resolved + Images", showBackground = true)
@Preview(
    name = "RecordWithMedia — resolved + Images — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardRecordWithMediaResolvedImagesPreview() {
    NubecitaTheme {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.Record(quotedPost = previewQuotedPost()),
            media =
                EmbedUi.Images(
                    items =
                        persistentListOf(
                            ImageUi(
                                url = "https://example.com/preview.jpg",
                                altText = null,
                                aspectRatio = 16f / 9f,
                            ),
                        ),
                ),
        )
    }
}

@Preview(name = "RecordWithMedia — resolved + External", showBackground = true)
@Composable
private fun PostCardRecordWithMediaResolvedExternalPreview() {
    NubecitaTheme {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.Record(quotedPost = previewQuotedPost()),
            media =
                EmbedUi.External(
                    uri = "https://www.theverge.com/article",
                    domain = "theverge.com",
                    title = "Headline goes here",
                    description = "Short description copy for the link card.",
                    thumbUrl = null,
                ),
            onExternalMediaTap = {},
        )
    }
}

@Preview(name = "RecordWithMedia — unavailable + Images", showBackground = true)
@Composable
private fun PostCardRecordWithMediaUnavailableImagesPreview() {
    NubecitaTheme {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
            media =
                EmbedUi.Images(
                    items =
                        persistentListOf(
                            ImageUi(
                                url = "https://example.com/preview.jpg",
                                altText = null,
                                aspectRatio = 16f / 9f,
                            ),
                        ),
                ),
        )
    }
}

@Preview(name = "RecordWithMedia — unavailable + External", showBackground = true)
@Composable
private fun PostCardRecordWithMediaUnavailableExternalPreview() {
    NubecitaTheme {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked),
            media =
                EmbedUi.External(
                    uri = "https://www.example.com/article",
                    domain = "example.com",
                    title = "Article title",
                    description = "Description.",
                    thumbUrl = null,
                ),
            onExternalMediaTap = {},
        )
    }
}
