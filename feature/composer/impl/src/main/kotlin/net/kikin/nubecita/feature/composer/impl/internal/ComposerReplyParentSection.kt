package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi

/**
 * Reply-mode header rendered above the composer's text field. Shows
 * one of three states based on [status]:
 *
 * - [ParentLoadStatus.Loading] → a flat skeleton block (placeholder
 *   color, ~64dp tall) so the layout doesn't reflow when the fetch
 *   resolves.
 * - [ParentLoadStatus.Loaded] → a read-only parent-post card rendered as a
 *   full-post preview (avatar + `displayName @handle` + body + optional media
 *   thumbnail) via [ComposerContextPostBody]. No interaction affordances —
 *   this is a context preview, not an interactive post tile.
 * - [ParentLoadStatus.Failed] → an inline retry tile: error icon +
 *   localized message; the whole tile is tap-to-retry, dispatching
 *   [onRetryClick]. The retry tile is NOT a button-shaped Surface
 *   — keeping it card-shaped (consistent with the Loaded state)
 *   means the layout doesn't shift when transitioning Failed →
 *   Loading → Loaded.
 *
 * Renders nothing when [status] is `null` (new-post mode — the route
 * had no `replyToUri` so there's no parent to load).
 *
 * Styling: M3 [OutlinedCard] container so the boundary is visible
 * against the composer's surface but the visual weight stays under
 * that of the active text field.
 */
@Composable
internal fun ComposerReplyParentSection(
    status: ParentLoadStatus?,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == null) return

    OutlinedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
    ) {
        when (status) {
            ParentLoadStatus.Loading -> ComposerReplyParentSkeleton()
            is ParentLoadStatus.Loaded -> ComposerReplyParentCard(post = status.post)
            is ParentLoadStatus.Failed -> ComposerReplyParentRetryTile(onRetryClick = onRetryClick)
        }
    }
}

@Composable
private fun ComposerReplyParentSkeleton() {
    // Plain colored block at the same height the Loaded card lands
    // at — keeps the composer layout stable across the Loading →
    // Loaded transition. No shimmer animation in V1; the static
    // placeholder reads as "we're fetching" without burning frames.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp),
    ) {}
}

@Composable
private fun ComposerReplyParentCard(post: ParentPostUi) {
    // Full-post presentation: avatar + displayName @handle + body + optional
    // media thumbnail (nubecita-8g28.7). The "Replying to" caption is dropped
    // visually (context is clear from position + toolbar) but preserved for
    // screen readers via a merged contentDescription so TalkBack still announces
    // what the card represents.
    val displayName = post.authorDisplayName ?: post.authorHandle
    val label = stringResource(R.string.composer_reply_header, displayName)
    val description = if (post.text.isNotBlank()) "$label: ${post.text}" else label
    ComposerContextPostBody(
        avatarUrl = post.avatarUrl,
        displayName = displayName,
        handle = post.authorHandle,
        text = post.text,
        thumbnailUrl = post.thumbnailUrl,
        modifier =
            Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
    )
}

@Composable
private fun ComposerReplyParentRetryTile(onRetryClick: () -> Unit) {
    val retryLabel = stringResource(R.string.composer_reply_parent_retry_action_label)
    Row(
        // role + onClickLabel make TalkBack announce the tile as
        // "Retry, Button — double tap to activate" instead of the
        // default "double tap to activate" with no semantic. Matches
        // the :designsystem PostStat pattern.
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = retryLabel,
                    onClick = onRetryClick,
                ).padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.composer_reply_parent_load_failed_retry),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
