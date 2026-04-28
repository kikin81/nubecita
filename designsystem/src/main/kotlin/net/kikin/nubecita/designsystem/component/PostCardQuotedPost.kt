package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.kikin81.atproto.compose.material3.rememberBlueskyAnnotatedString
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.rememberRelativeTimeText
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import kotlin.time.Instant

/**
 * Renders a Bluesky `app.bsky.embed.record#viewRecord` as a nested
 * "quote post" card. Density matches the official Bluesky Android
 * client: parent layout minus the action row, smaller (32 dp) avatar,
 * single-line author treatment, full body text (no truncation), and
 * an embed slot that dispatches the quoted post's own embed.
 *
 * **Not clickable in v1.** The card has no `Modifier.clickable` —
 * tap-to-open-PostDetail will land in a follow-up bd issue paired
 * with the post-detail destination so wiring lands once instead of
 * being introduced and rewired. Inner embed leaf composables manage
 * their own tap surfaces (e.g. external link card, video controls).
 *
 * **Recursion bound.** [QuotedPostUi.embed] is typed as
 * [QuotedEmbedUi] which deliberately excludes a `Record` variant —
 * the one-level quote-of-quote bound is enforced at the type system,
 * not by runtime guards. A nested record on the wire produces
 * [QuotedEmbedUi.QuotedThreadChip] at the mapper boundary which
 * renders as a "View thread" placeholder here.
 *
 * **Video render.** Quoted-post videos route through
 * [quotedVideoEmbedSlot] — the host-supplied slot lambda that lives
 * in `:feature:feed:impl` and binds the FeedScreen-scoped
 * `FeedVideoPlayerCoordinator`. Default `null` keeps `:designsystem`
 * Media3-free for previews / screenshot tests.
 */
@Composable
public fun PostCardQuotedPost(
    quotedPost: QuotedPostUi,
    modifier: Modifier = Modifier,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(QUOTED_CARD_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        QuotedAuthorLine(quotedPost = quotedPost)
        Spacer(Modifier.height(4.dp))
        QuotedBodyText(quotedPost = quotedPost)
        QuotedEmbedSlot(
            embed = quotedPost.embed,
            quotedVideoEmbedSlot = quotedVideoEmbedSlot,
        )
    }
}

/**
 * Single non-wrapping row: 32 dp avatar, displayName + handle +
 * relative-time on one visual line. Same right-pinned-timestamp
 * behavior as parent [PostCard.AuthorLine] — handle takes
 * `weight(1f)` so a long handle ellipsizes only when it would
 * otherwise overflow.
 */
@Composable
private fun QuotedAuthorLine(quotedPost: QuotedPostUi) {
    val timestamp by rememberRelativeTimeText(then = quotedPost.createdAt)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NubecitaAvatar(
            model = quotedPost.author.avatarUrl,
            contentDescription = quotedPost.author.displayName,
            size = 32.dp,
        )
        Text(
            text = quotedPost.author.displayName,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.postcard_handle, quotedPost.author.handle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.postcard_relative_time, timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Body text with facet-styled annotations (mentions, links, tags) —
 * same `rememberBlueskyAnnotatedString` helper the parent post body
 * uses, just at the smaller `bodyMedium` style. NO `maxLines` cap;
 * the official client renders quoted posts in full.
 */
@Composable
private fun QuotedBodyText(quotedPost: QuotedPostUi) {
    val annotated = rememberBlueskyAnnotatedString(text = quotedPost.text, facets = quotedPost.facets)
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
}

/**
 * Exhaustive dispatch over [QuotedEmbedUi]. Reuses parent leaf
 * composables for [QuotedEmbedUi.Images] and [QuotedEmbedUi.External]
 * (their payloads are structurally identical to the parent variants).
 * Video routes through [quotedVideoEmbedSlot] — a default-null lambda
 * keeps `:designsystem` Media3-free.
 *
 * No `else` branch — `QuotedEmbedUi` is sealed, and adding a future
 * variant becomes a compile error here.
 */
@Composable
private fun QuotedEmbedSlot(
    embed: QuotedEmbedUi,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)?,
) {
    when (embed) {
        QuotedEmbedUi.Empty -> Unit
        is QuotedEmbedUi.Images -> {
            Spacer(Modifier.height(8.dp))
            // Wrapper-type duplication, payload reuse — the inner
            // ImmutableList<ImageUi> is the same shape parent's
            // EmbedUi.Images carries.
            PostCardImageEmbed(items = embed.items)
        }
        is QuotedEmbedUi.External -> {
            Spacer(Modifier.height(8.dp))
            PostCardExternalEmbed(
                uri = embed.uri,
                domain = embed.domain,
                title = embed.title,
                description = embed.description,
                thumbUrl = embed.thumbUrl,
                onTap = {},
            )
        }
        is QuotedEmbedUi.Video -> {
            if (quotedVideoEmbedSlot != null) {
                Spacer(Modifier.height(8.dp))
                quotedVideoEmbedSlot(embed)
            }
        }
        QuotedEmbedUi.QuotedThreadChip -> {
            Spacer(Modifier.height(8.dp))
            QuotedThreadChip()
        }
        is QuotedEmbedUi.Unsupported -> {
            Spacer(Modifier.height(8.dp))
            PostCardUnsupportedEmbed(typeUri = embed.typeUri)
        }
    }
}

/**
 * Recursion-bounded placeholder rendered when a quoted post itself
 * quotes another post (one-level bound). Mirrors
 * [PostCardRecordUnavailable]'s surface treatment with different
 * copy ("View thread") to signal "there's more behind this — open
 * the parent quoted post to see the chain."
 */
@Composable
private fun QuotedThreadChip(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.postcard_quoted_post_view_thread),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clip(CHIP_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private val QUOTED_CARD_SHAPE = RoundedCornerShape(12.dp)
private val CHIP_SHAPE = RoundedCornerShape(8.dp)

// ---------- Previews ----------

private val PREVIEW_CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

private val PREVIEW_AUTHOR: AuthorUi =
    AuthorUi(
        did = "did:plc:preview",
        handle = "preview.bsky.social",
        displayName = "Preview Author",
        avatarUrl = null,
    )

private val PREVIEW_TEXT: String =
    "This is a preview of a quoted post. It carries non-trivial body " +
        "copy so the layout's full-text rendering (no maxLines cap) " +
        "exercises wrap behavior at the typical post width."

private fun previewQuoted(embed: QuotedEmbedUi): QuotedPostUi =
    QuotedPostUi(
        uri = "at://did:plc:preview/app.bsky.feed.post/q",
        cid = "bafyreifakecid000000000000000000000000000000000",
        author = PREVIEW_AUTHOR,
        createdAt = PREVIEW_CREATED_AT,
        text = PREVIEW_TEXT,
        facets = persistentListOf(),
        embed = embed,
    )

@Preview(name = "QuotedPost — text only", showBackground = true)
@Preview(
    name = "QuotedPost — text only — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardQuotedPostTextOnlyPreview() {
    NubecitaTheme {
        PostCardQuotedPost(quotedPost = previewQuoted(QuotedEmbedUi.Empty))
    }
}

@Preview(name = "QuotedPost — with image", showBackground = true)
@Composable
private fun PostCardQuotedPostWithImagePreview() {
    NubecitaTheme {
        PostCardQuotedPost(
            quotedPost =
                previewQuoted(
                    QuotedEmbedUi.Images(
                        items =
                            persistentListOf(
                                ImageUi(
                                    url = "https://example.com/preview.jpg",
                                    altText = null,
                                    aspectRatio = 16f / 9f,
                                ),
                            ),
                    ),
                ),
        )
    }
}

@Preview(name = "QuotedPost — with external", showBackground = true)
@Composable
private fun PostCardQuotedPostWithExternalPreview() {
    NubecitaTheme {
        PostCardQuotedPost(
            quotedPost =
                previewQuoted(
                    QuotedEmbedUi.External(
                        uri = "https://www.theverge.com/article",
                        domain = "theverge.com",
                        title = "Headline goes here",
                        description = "Short description.",
                        thumbUrl = null,
                    ),
                ),
        )
    }
}

@Preview(name = "QuotedPost — view thread (recursion-bound)", showBackground = true)
@Composable
private fun PostCardQuotedPostViewThreadPreview() {
    NubecitaTheme {
        PostCardQuotedPost(quotedPost = previewQuoted(QuotedEmbedUi.QuotedThreadChip))
    }
}

@Preview(name = "QuotedPost — unsupported inner embed", showBackground = true)
@Composable
private fun PostCardQuotedPostUnsupportedPreview() {
    NubecitaTheme {
        PostCardQuotedPost(
            quotedPost = previewQuoted(QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")),
        )
    }
}
