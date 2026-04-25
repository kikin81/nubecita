package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Deliberate-degradation chip rendered for embed types outside the v1
 * support scope (per the embed-scope decision in
 * `docs/superpowers/specs/2026-04-25-postcard-embed-scope-v1.md`).
 *
 * Visual treatment is intentionally NOT error-styled — this is "we know
 * what this is, we just don't render it yet," not "something went wrong."
 * `surfaceContainerHighest` background, `onSurfaceVariant` text. The label
 * names the embed kind in friendly form (`"video"`, `"link card"`, etc.)
 * mapped from the lexicon URI so users see a readable hint rather than a
 * developer string. Localized via `R.string.postcard_embed_label_*`.
 */
@Composable
fun PostCardUnsupportedEmbed(
    typeUri: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.postcard_unsupported_embed_format, unsupportedEmbedLabel(typeUri)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clip(CHIP_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * Map a Bluesky lexicon embed URI to a user-readable, localized label.
 * Unknown URIs fall through to the generic `embed` label so future
 * lexicon additions we haven't seen yet still render something readable.
 */
@Composable
private fun unsupportedEmbedLabel(typeUri: String): String =
    when (typeUri) {
        "app.bsky.embed.external" -> stringResource(R.string.postcard_embed_label_external)
        "app.bsky.embed.record" -> stringResource(R.string.postcard_embed_label_record)
        "app.bsky.embed.video" -> stringResource(R.string.postcard_embed_label_video)
        "app.bsky.embed.recordWithMedia" -> stringResource(R.string.postcard_embed_label_record_with_media)
        else -> stringResource(R.string.postcard_embed_label_unknown)
    }

private val CHIP_SHAPE = RoundedCornerShape(8.dp)

@Preview(name = "Unsupported — video", showBackground = true)
@Composable
private fun PostCardUnsupportedEmbedVideoPreview() {
    NubecitaTheme {
        PostCardUnsupportedEmbed(typeUri = "app.bsky.embed.video")
    }
}

@Preview(name = "Unsupported — quoted post", showBackground = true)
@Composable
private fun PostCardUnsupportedEmbedQuotedPostPreview() {
    NubecitaTheme {
        PostCardUnsupportedEmbed(typeUri = "app.bsky.embed.record")
    }
}

@Preview(name = "Unsupported — unknown URI", showBackground = true)
@Composable
private fun PostCardUnsupportedEmbedUnknownPreview() {
    NubecitaTheme {
        PostCardUnsupportedEmbed(typeUri = "app.bsky.embed.somethingNew")
    }
}
