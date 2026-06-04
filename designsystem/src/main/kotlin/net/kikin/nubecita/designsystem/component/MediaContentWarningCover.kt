package net.kikin.nubecita.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ContentWarningCategory
import net.kikin.nubecita.data.models.MediaContentWarning
import net.kikin.nubecita.designsystem.R

/**
 * Render-time pairing of a precomputed [MediaContentWarning] with its reveal
 * callback. `PostCard` builds this from a media embed's `contentWarning` and the
 * per-post reveal state and threads it into each media composable; a `null`
 * `MediaCover` means "render the media normally" (the embed isn't covered, or
 * the viewer already revealed it).
 *
 * The decision is precomputed off the render path (`:core:moderation` →
 * `:core:feed-mapping`), so the cover does zero moderation work — it only draws.
 */
@Immutable
public data class MediaCover(
    val warning: MediaContentWarning,
    val onReveal: () -> Unit,
)

/**
 * The scrim that replaces sensitive media until the viewer reveals it. A flat
 * fill (NO runtime blur — that would cost a render pass per frame and defeat the
 * 120 Hz target) with the category and, when [MediaContentWarning.overridable],
 * a "Show anyway" button. When not overridable (the forced adult-gate-off hide),
 * no reveal affordance is shown.
 *
 * Callers overlay this at the media's natural size (e.g. `Modifier.matchParentSize()`
 * inside the media `Box`) and pass `model = null` to Coil so nothing is fetched
 * or decoded while covered.
 */
@Composable
public fun MediaContentWarningCover(
    cover: MediaCover,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.media_warning_sensitive),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(cover.warning.category.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (cover.warning.overridable) {
            FilledTonalButton(onClick = cover.onReveal) {
                Text(stringResource(R.string.media_warning_show))
            }
        }
    }
}

@StringRes
private fun ContentWarningCategory.labelRes(): Int =
    when (this) {
        ContentWarningCategory.ADULT_CONTENT -> R.string.media_warning_cat_adult
        ContentWarningCategory.SEXUALLY_SUGGESTIVE -> R.string.media_warning_cat_suggestive
        ContentWarningCategory.GRAPHIC_MEDIA -> R.string.media_warning_cat_graphic
        ContentWarningCategory.NON_SEXUAL_NUDITY -> R.string.media_warning_cat_nudity
    }
