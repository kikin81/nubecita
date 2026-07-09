package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.HighlightedText
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.VerificationBadge
import net.kikin.nubecita.feature.search.impl.R

/**
 * Single-actor row for the People tab.
 *
 * Renders: leading avatar, primary line (displayName or fallback to
 * handle), secondary line (`@handle`) only when displayName is non-null.
 * Both lines support case-insensitive query-substring highlighting via
 * the `:designsystem`'s [HighlightedText].
 *
 * Stateless. Click dispatch is via [onClick]; the parent
 * [PeopleTabContent] wires it to a `SearchActorsEvent.ActorTapped`.
 *
 * Not in `:designsystem` because composer's typeahead uses a similar
 * but distinct row (`OutlinedCard`-wrapped, no highlight); promotion
 * happens when a third consumer surfaces — see spec A2.
 */
@Composable
internal fun ActorRow(
    actor: ActorUi,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        NubecitaAvatar(
            model = actor.avatarUrl,
            contentDescription = actor.displayName ?: actor.handle,
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
                    text = actor.displayName ?: actor.handle,
                    match = query.takeIf { it.isNotBlank() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VerificationBadge(badge = actor.verifiedBadge)
            }
            if (actor.displayName != null) {
                HighlightedText(
                    text = stringResource(R.string.search_people_actor_handle, actor.handle),
                    match = query.takeIf { it.isNotBlank() },
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
            }
        }
    }
}

@Preview(name = "ActorRow — with displayName, no match", showBackground = true)
@Composable
private fun ActorRowWithDisplayNameNoMatchPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — with displayName + match", showBackground = true)
@Composable
private fun ActorRowWithMatchPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
            query = "ali",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — no displayName, match on handle", showBackground = true)
@Composable
private fun ActorRowNoDisplayNamePreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:nodisplay",
                    handle = "anon42.bsky.social",
                    displayName = null,
                    avatarUrl = null,
                ),
            query = "anon",
            onClick = {},
        )
    }
}

@Preview(
    name = "ActorRow — dark, with avatar URL",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ActorRowDarkPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:withavatar",
                    handle = "avatar.bsky.social",
                    displayName = "With Avatar",
                    avatarUrl = "https://example.com/avatar.jpg",
                ),
            query = "avatar",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — verified badge", showBackground = true)
@Composable
private fun ActorRowVerifiedPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:verified",
                    handle = "verified.bsky.social",
                    displayName = "Verified Vera",
                    avatarUrl = null,
                    verifiedBadge = VerifiedBadge.Verified,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — trusted verifier badge", showBackground = true)
@Composable
private fun ActorRowTrustedVerifierPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:trusted",
                    handle = "trusted.bsky.social",
                    displayName = "Trusted Tomas",
                    avatarUrl = null,
                    verifiedBadge = VerifiedBadge.TrustedVerifier,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — verified, long name keeps badge visible", showBackground = true)
@Composable
private fun ActorRowVerifiedLongNamePreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:longname",
                    handle = "longname.bsky.social",
                    displayName = "A Very Long Display Name That Should Ellipsize Before The Badge",
                    avatarUrl = null,
                    verifiedBadge = VerifiedBadge.Verified,
                ),
            query = "",
            onClick = {},
        )
    }
}
