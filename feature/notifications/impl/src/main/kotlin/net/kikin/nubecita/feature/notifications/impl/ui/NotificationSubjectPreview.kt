package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.data.models.imageContainer
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCardImageEmbed
import kotlin.time.Instant

/**
 * Mini subject-post preview rendered inside a [NotificationRow] when the
 * notification carries a hydrated [PostUi].
 *
 * Shows:
 * - up to 2 lines of post text (ellipsized with `…`),
 * - the existing `PostCardImageEmbed` carousel when the embed is
 *   [EmbedUi.Images].
 *
 * Other embed variants (video, external link, quoted record) are
 * intentionally omitted from the mini preview — Bluesky's own client
 * collapses the preview to text-only for those embeds. We can add
 * per-variant treatments in a future slice once telemetry confirms the
 * surface is heavily used.
 *
 * Surface: `surfaceContainerLow` recessed inset (per CLAUDE.md surface
 * roles table — "recessed inset" matches the row-within-a-row depth)
 * with 12.dp rounded corners and 12.dp interior padding.
 */
@Composable
internal fun NotificationSubjectPreview(
    post: PostUi,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (post.text.isNotEmpty()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = MAX_TEXT_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val embed = post.embed
            val mediaEmbed = embed.imageContainer
            if (mediaEmbed != null) {
                PostCardImageEmbed(items = mediaEmbed.items)
            }
        }
    }
}

private const val MAX_TEXT_LINES = 2

// ---------- Previews -------------------------------------------------------

private val PREVIEW_AUTHOR =
    AuthorUi(
        did = "did:plc:preview-subject",
        handle = "you.bsky.social",
        displayName = "You",
        avatarUrl = null,
    )

private fun previewPost(
    text: String = "A quick subject post for the notification mini-preview.",
    embed: EmbedUi = EmbedUi.Empty,
): PostUi =
    PostUi(
        id = "at://did:plc:preview-subject/app.bsky.feed.post/preview",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = PREVIEW_AUTHOR,
        createdAt = Instant.parse("2026-04-26T10:00:00Z"),
        text = text,
        facets = persistentListOf(),
        embed = embed,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

@Preview(name = "Subject — text only (light)", showBackground = true)
@Composable
private fun NotificationSubjectPreviewTextOnlyPreview() {
    NubecitaTheme {
        NotificationSubjectPreview(post = previewPost())
    }
}

@Preview(name = "Subject — text only (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationSubjectPreviewTextOnlyDarkPreview() {
    NubecitaTheme {
        NotificationSubjectPreview(post = previewPost())
    }
}

@Preview(name = "Subject — long text ellipsizes", showBackground = true)
@Composable
private fun NotificationSubjectPreviewLongTextPreview() {
    NubecitaTheme {
        NotificationSubjectPreview(
            post =
                previewPost(
                    text =
                        "A very long subject post that exceeds the two-line cap so we can verify the " +
                            "ellipsization behaviour kicks in cleanly. The mini-preview should never " +
                            "balloon vertically when a thread starter writes a wall of text.",
                ),
        )
    }
}

@Preview(name = "Subject — with images", showBackground = true)
@Composable
private fun NotificationSubjectPreviewImagesPreview() {
    NubecitaTheme {
        NotificationSubjectPreview(
            post =
                previewPost(
                    embed =
                        EmbedUi.Images(
                            items =
                                persistentListOf(
                                    ImageUi(
                                        fullsizeUrl = "https://placeholder.example/full",
                                        thumbUrl = "https://placeholder.example/thumb",
                                        altText = "Preview image",
                                        aspectRatio = 1.5f,
                                    ),
                                ),
                        ),
                ),
        )
    }
}
