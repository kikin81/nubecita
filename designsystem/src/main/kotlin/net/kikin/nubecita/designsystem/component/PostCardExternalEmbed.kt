package net.kikin.nubecita.designsystem.component

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Renders a Bluesky `app.bsky.embed.external` link card natively in Compose.
 *
 * The whole `Surface` is a single tap target — taps anywhere open the URI
 * via the host-supplied [onTap] callback (typically a `CustomTabsIntent`
 * launcher provided by the screen).
 *
 * Layout:
 * - Optional thumbnail at 1.91:1 (OG-image standard) using [ContentScale.Crop],
 *   clipped to the card's top corners. When [thumbUrl] is null the section
 *   is omitted entirely (text-only card; no placeholder, no gradient).
 * - Title (titleMedium), description (bodyMedium) — each capped at 2 lines
 *   with ellipsis. Empty strings are skipped (no empty `Text` row).
 * - Domain footer: globe icon + host (`Uri.parse(uri).host?.removePrefix("www.")`,
 *   falling back to the full URI when the host is null for opaque inputs).
 */
@Composable
fun PostCardExternalEmbed(
    uri: String,
    title: String,
    description: String,
    thumbUrl: String?,
    onTap: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().clickable { onTap(uri) },
    ) {
        Column {
            if (thumbUrl != null) {
                NubecitaAsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(THUMB_ASPECT)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                ),
                            ),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = displayHost(uri),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val THUMB_ASPECT: Float = 1.91f

/**
 * Extracts a user-readable host from [uri]. Falls back to the full URI
 * when `Uri.parse(uri).host` is null (opaque or malformed inputs).
 *
 * `Uri` here is `android.net.Uri`, not the atproto-kotlin runtime `Uri`
 * — the composable takes a `String` to keep `:designsystem` free of
 * lib-type leakage.
 */
private fun displayHost(uri: String): String = Uri.parse(uri).host?.removePrefix("www.") ?: uri

@Preview(name = "External embed — with thumb", showBackground = true)
@Composable
private fun PostCardExternalEmbedWithThumbPreview() {
    NubecitaTheme {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = PREVIEW_THUMB,
            onTap = {},
        )
    }
}

@Preview(name = "External embed — no thumb", showBackground = true)
@Composable
private fun PostCardExternalEmbedNoThumbPreview() {
    NubecitaTheme {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = null,
            onTap = {},
        )
    }
}

private const val PREVIEW_URI: String = "https://www.theverge.com/tech/elon-altman-court-battle"
private const val PREVIEW_TITLE: String = "Elon Musk and Sam Altman's court battle over the future of OpenAI"
private const val PREVIEW_DESCRIPTION: String = "The billionaire battle goes to court."
private const val PREVIEW_THUMB: String = "https://example.com/preview-external-thumb.jpg"
