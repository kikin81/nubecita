package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.QuoteLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.QuotePostUi

/**
 * Quote-mode section rendered BELOW the composer's text field (mirroring the
 * reader's stacked layout: reply context on top → text → quote at the bottom).
 * Shows one of three states based on [status]:
 *
 * - [QuoteLoadStatus.Loading] → a flat skeleton block so the layout doesn't
 *   reflow when the fetch resolves.
 * - [QuoteLoadStatus.Loaded] → a read-only quoted-post card rendered as a
 *   full-post preview (avatar + `displayName @handle` + body + optional media
 *   thumbnail) via [ComposerContextPostBody], with a dismiss (✕) affordance that
 *   detaches the quote.
 * - [QuoteLoadStatus.Failed] → an inline retry tile (tap-to-retry) plus a dismiss
 *   affordance so the user can give up on a quote that won't load.
 *
 * Renders nothing when [status] is `null` (not quoting).
 *
 * Sibling of [ComposerReplyParentSection]; same [OutlinedCard] styling so the two
 * context cards read as a matched pair around the text field.
 */
@Composable
internal fun ComposerQuoteSection(
    status: QuoteLoadStatus?,
    onRetryClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == null) return

    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
    ) {
        when (status) {
            QuoteLoadStatus.Loading -> ComposerQuoteSkeleton()
            is QuoteLoadStatus.Loaded -> ComposerQuoteCard(post = status.post, onRemoveClick = onRemoveClick)
            is QuoteLoadStatus.Failed ->
                ComposerQuoteRetryTile(onRetryClick = onRetryClick, onRemoveClick = onRemoveClick)
        }
    }
}

@Composable
private fun ComposerQuoteSkeleton() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp),
    ) {}
}

@Composable
private fun ComposerQuoteCard(
    post: QuotePostUi,
    onRemoveClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Full-post presentation (nubecita-8g28.7): avatar + displayName @handle
        // + body + optional media thumbnail. Quote context is conveyed by position
        // (below the field) so no "Quoting" caption is needed.
        ComposerContextPostBody(
            avatarUrl = post.avatarUrl,
            displayName = post.authorDisplayName ?: post.authorHandle,
            handle = post.authorHandle,
            text = post.text,
            thumbnailUrl = post.thumbnailUrl,
            modifier = Modifier.weight(1f),
        )
        QuoteDismissButton(onRemoveClick = onRemoveClick)
    }
}

@Composable
private fun ComposerQuoteRetryTile(
    onRetryClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val retryLabel = stringResource(R.string.composer_reply_parent_retry_action_label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = retryLabel,
                        onClick = onRetryClick,
                    ).padding(start = 12.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.composer_quote_load_failed_retry),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        QuoteDismissButton(onRemoveClick = onRemoveClick)
    }
}

@Composable
private fun QuoteDismissButton(onRemoveClick: () -> Unit) {
    IconButton(onClick = onRemoveClick) {
        NubecitaIcon(
            name = NubecitaIconName.Close,
            contentDescription = stringResource(R.string.composer_quote_remove_action_label),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
