package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.TypeaheadStatus

/**
 * Inline suggestion surface for the composer's `@`-mention typeahead.
 *
 * Renders only when [typeahead] is [TypeaheadStatus.Suggestions] or
 * [TypeaheadStatus.NoResults]. The container is a Material 3
 * [OutlinedCard] that sits in the composer's primary `Column`
 * between the text field and the attachment row, so the IME's own
 * inset-pushing keeps the dropdown visible above the keyboard
 * without an explicit popup or anchoring math (see
 * `openspec/changes/add-composer-mention-typeahead/design.md` decision
 * #6 for why we picked the inline layout over a `Popup`).
 *
 * Hidden in [TypeaheadStatus.Idle] and [TypeaheadStatus.Querying] —
 * we deliberately don't render a loading state on every keystroke
 * because the flicker is worse than a brief stale list. Suggestions
 * stay visible until the next response replaces them.
 */
@Composable
internal fun ComposerSuggestionList(
    typeahead: TypeaheadStatus,
    onSuggestionClick: (ActorTypeaheadUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (typeahead) {
        is TypeaheadStatus.Suggestions -> {
            OutlinedCard(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = MAX_LIST_HEIGHT)) {
                    itemsIndexed(
                        items = typeahead.results,
                        // DIDs are stable across queries; using them as
                        // keys lets the LazyColumn survive re-emissions
                        // (same handle re-appearing after a typeahead
                        // refresh) without re-laying-out the row.
                        key = { _, actor -> actor.did },
                    ) { index, actor ->
                        if (index > 0) HorizontalDivider()
                        ComposerSuggestionRow(actor = actor, onClick = onSuggestionClick)
                    }
                }
            }
        }

        is TypeaheadStatus.NoResults -> {
            OutlinedCard(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                ComposerSuggestionEmptyRow(query = typeahead.query)
            }
        }

        TypeaheadStatus.Idle, is TypeaheadStatus.Querying -> Unit
    }
}

/**
 * One actor row in the suggestion list: 40dp circular avatar +
 * display-name line (titleSmall) + `@handle` line (bodySmall,
 * onSurfaceVariant). When `displayName` is null, the top line falls
 * back to the bare handle (no `@`) so the row still has the canonical
 * "title + subtitle" shape.
 *
 * The row is the click target — [NubecitaAsyncImage]'s
 * `contentDescription` is null so the avatar doesn't double-announce
 * the row to a screen reader; the merged Row text already conveys
 * the actor identity.
 */
@Composable
private fun ComposerSuggestionRow(
    actor: ActorTypeaheadUi,
    onClick: (ActorTypeaheadUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick(actor) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NubecitaAsyncImage(
            model = actor.avatarUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = actor.displayName ?: actor.handle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${actor.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Empty-state row inside the suggestion card when the typeahead
 * query returned zero actors. Distinct from the failure path: a
 * server failure collapses to [TypeaheadStatus.Idle] (hidden card),
 * a successful empty result lands here so the user knows the lookup
 * actually ran.
 */
@Composable
private fun ComposerSuggestionEmptyRow(
    query: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.composer_typeahead_no_matches, query),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

/**
 * Caps the suggestion `LazyColumn` so 4 rows are visible cleanly
 * before scrolling. Each row is ~64dp (12dp top pad + 40dp avatar
 * + 12dp bottom pad); 4 rows + dividers ≈ 260dp, plus a few
 * px of slack rounds to 280dp. Picked over a tighter cap because
 * a partial 4th row reads as "broken UI" at tablet density rather
 * than "scroll hint".
 */
private val MAX_LIST_HEIGHT = 280.dp
