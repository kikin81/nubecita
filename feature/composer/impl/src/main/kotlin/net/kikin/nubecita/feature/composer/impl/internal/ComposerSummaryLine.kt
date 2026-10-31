package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.feature.composer.impl.R

/**
 * A quiet line under the text showing the post's language and, for top-level
 * posts, its audience — e.g. "English · Visible to all". Keeps that state
 * visible at a glance once the language and audience controls are icon-only.
 * Not interactive: the controls own the actions.
 *
 * @param audience null for replies (threadgate/postgate belong to the thread
 *   root), which shows the language alone.
 */
@Composable
internal fun ComposerSummaryLine(
    selectedLangs: List<String>?,
    deviceLocaleTag: String,
    audience: PostAudience?,
    modifier: Modifier = Modifier,
) {
    val noLanguage = stringResource(R.string.composer_language_chip_none)
    val visibleToAll = stringResource(R.string.composer_audience_chip_default)
    val limited = stringResource(R.string.composer_audience_chip_limited)
    val text =
        remember(selectedLangs, deviceLocaleTag, audience, noLanguage, visibleToAll, limited) {
            summaryLineFor(
                languageLabel = chipLabelFor(selectedLangs, deviceLocaleTag, noLanguage),
                audienceLabel =
                    audience?.let { if (it == PostAudience.DEFAULT) visibleToAll else limited },
            )
        }
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Pure join rule for [ComposerSummaryLine], pinned by its unit test. */
internal fun summaryLineFor(
    languageLabel: String,
    audienceLabel: String?,
): String = if (audienceLabel == null) languageLabel else "$languageLabel · $audienceLabel"
