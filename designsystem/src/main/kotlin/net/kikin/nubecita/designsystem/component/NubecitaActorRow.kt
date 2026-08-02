package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.R

/**
 * One account in a list of accounts: avatar, name line with verification
 * badge, `@handle`, and an optional supporting line.
 *
 * Promoted here from the People tab's `ActorRow`, whose KDoc had said
 * "promotion happens when a third consumer surfaces". Login's account
 * typeahead is that consumer, and the rendering is search's exactly — the
 * People tab keeps byte-identical screenshot baselines across the move, which
 * is what makes this a promotion rather than a redesign.
 *
 * **Two rows deliberately did NOT move.** The composer's suggestion row wraps
 * itself in an `OutlinedCard` and skips highlighting; the chat pickers'
 * `RecipientRow` carries a disabled state and a "can't be messaged" line, on
 * different padding and type. Reconciling either would change how a surface
 * looks for the benefit of a feature it has no stake in, so they stay put —
 * see nubecita-qt71.3.
 *
 * @param query the text to highlight in the name and handle, when the row is
 *   showing search or typeahead results. Blank or null highlights nothing.
 * @param supportingContent an optional third line under the handle — the login
 *   typeahead uses it for the network hosting the account. Rendered inside the
 *   text column, so it wraps and ellipsizes with the rest.
 * @param showAvatarFallback draws the deterministic hue-and-initial placeholder
 *   when the actor has no avatar, instead of an empty circle. Defaults to off
 *   because the People tab ships the empty circle today and turning it on there
 *   would be a visual change this promotion has no business making.
 */
@Composable
fun NubecitaActorRow(
    actor: ActorUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    query: String? = null,
    showAvatarFallback: Boolean = false,
    supportingContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A whitespace-only display name would otherwise render an empty title
        // with the handle stranded below it — and announce an empty string to a
        // screen reader. avatarFallbackFor already treats blank as absent; match
        // that everywhere the name is read.
        val displayName = actor.displayName?.takeIf { it.isNotBlank() }
        NubecitaAvatar(
            model = actor.avatarUrl,
            contentDescription = displayName ?: actor.handle,
            fallback =
                if (showAvatarFallback) {
                    avatarFallbackFor(did = actor.did, handle = actor.handle, displayName = displayName)
                } else {
                    null
                },
        )
        Column(modifier = Modifier.weight(1f)) {
            // Name-priority line: the name ellipsizes to fit while the fixed-size
            // badge (nothing for VerifiedBadge.None) stays visible ahead of it —
            // mirrors PostCard's AuthorLine (nubecita-vw45.5's long-name fix).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HighlightedText(
                    modifier = Modifier.weight(1f, fill = false),
                    text = displayName ?: actor.handle,
                    match = query?.takeIf { it.isNotBlank() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VerificationBadge(badge = actor.verifiedBadge)
            }
            // Only when a display name occupies the line above — otherwise the
            // handle is already the title and would render twice.
            if (displayName != null) {
                HighlightedText(
                    text = stringResource(R.string.nubecita_actor_handle, actor.handle),
                    match = query?.takeIf { it.isNotBlank() },
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
            supportingContent?.invoke(this)
        }
    }
}
