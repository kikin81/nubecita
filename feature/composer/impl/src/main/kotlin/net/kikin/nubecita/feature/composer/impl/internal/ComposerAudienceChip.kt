package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Tap target for the per-post audience picker (who can reply / quote). Mirrors
 * [ComposerLanguageChip]: an [AssistChip] with a leading globe icon and a label
 * that reads "Visible to all" for the wide-open [PostAudience.DEFAULT] and
 * "Interaction limited" for anything else.
 *
 * Rendered only on TOP-LEVEL posts — the caller gates it on
 * `state.replyToUri == null`, since threadgate/postgate belong to the thread
 * root, not a reply.
 */
@Composable
internal fun ComposerAudienceChip(
    audience: PostAudience,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve both labels in composable scope (so locale / dark-mode changes
    // recompose) and memoize the selection on `audience` — mirrors
    // ComposerLanguageChip's remember(...) pattern.
    val defaultLabel = stringResource(R.string.composer_audience_chip_default)
    val limitedLabel = stringResource(R.string.composer_audience_chip_limited)
    val label =
        remember(audience, defaultLabel, limitedLabel) {
            if (audience == PostAudience.DEFAULT) defaultLabel else limitedLabel
        }
    AssistChip(
        onClick = onClick,
        modifier = modifier.widthIn(max = 200.dp),
        leadingIcon = {
            NubecitaIcon(
                name = NubecitaIconName.Globe,
                contentDescription = null,
                filled = true,
                opticalSize = AssistChipDefaults.IconSize,
            )
        },
        label = {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}
