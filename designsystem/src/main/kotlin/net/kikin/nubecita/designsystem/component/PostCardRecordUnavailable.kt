package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
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
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Renders the single-stub chip for an unavailable quoted post — the
 * lexicon's `app.bsky.embed.record#view{NotFound,Blocked,Detached}`
 * variants, plus the open-union `Unknown` fallback (and `viewRecord`
 * forms whose record `value` failed to decode at the mapper).
 *
 * Per the design's YAGNI decision, the rendered copy is identical for
 * every [reason] — users rarely need to know whether a quoted post is
 * gone because it was deleted vs blocked vs detached; they need to know
 * it's gone. The [reason] parameter is carried for forward
 * compatibility (a future per-variant copy upgrade is non-breaking)
 * and for telemetry / debug consumers.
 *
 * Visual treatment mirrors [PostCardUnsupportedEmbed] —
 * `surfaceContainerHighest` background, `onSurfaceVariant` text — so
 * the user reads "this region is intentionally a small note, not the
 * primary post content" through the same visual cue as the
 * unsupported-embed chip. NOT error-styled.
 */
@Composable
public fun PostCardRecordUnavailable(
    @Suppress("UNUSED_PARAMETER") reason: EmbedUi.RecordUnavailable.Reason,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.postcard_quoted_post_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .clip(CHIP_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private val CHIP_SHAPE = RoundedCornerShape(8.dp)

@Preview(name = "RecordUnavailable — light", showBackground = true)
@Preview(
    name = "RecordUnavailable — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardRecordUnavailablePreview() {
    NubecitaTheme {
        PostCardRecordUnavailable(reason = EmbedUi.RecordUnavailable.Reason.NotFound)
    }
}
