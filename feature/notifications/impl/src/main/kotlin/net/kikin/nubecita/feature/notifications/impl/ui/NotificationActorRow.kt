package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAvatar

/**
 * Single actor row used inside [ActorListSheet]. Renders the actor's
 * avatar + displayName + handle, clickable for profile navigation.
 *
 * Lives in the impl module (not `:designsystem`) for now — until a
 * second consumer surfaces. Today the only call site is the actor-list
 * bottom sheet, and the search-tab's `ActorRow` carries query-highlight
 * concerns that this row doesn't need.
 */
@Composable
internal fun NotificationActorRow(
    actor: AuthorUi,
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
            contentDescription = actor.displayName.ifEmpty { actor.handle },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = actor.displayName.ifEmpty { actor.handle },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (actor.displayName.isNotEmpty()) {
                Text(
                    text = "@${actor.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "ActorRow — with displayName", showBackground = true)
@Composable
private fun NotificationActorRowWithDisplayNamePreview() {
    NubecitaTheme {
        NotificationActorRow(
            actor =
                AuthorUi(
                    did = "did:plc:preview-alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — handle only", showBackground = true)
@Composable
private fun NotificationActorRowHandleOnlyPreview() {
    NubecitaTheme {
        NotificationActorRow(
            actor =
                AuthorUi(
                    did = "did:plc:preview-handle",
                    handle = "anon.bsky.social",
                    displayName = "",
                    avatarUrl = null,
                ),
            onClick = {},
        )
    }
}

@Preview(
    name = "ActorRow — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NotificationActorRowDarkPreview() {
    NubecitaTheme {
        NotificationActorRow(
            actor =
                AuthorUi(
                    did = "did:plc:preview-bob",
                    handle = "bob.bsky.social",
                    displayName = "Bob Diaz",
                    avatarUrl = null,
                ),
            onClick = {},
        )
    }
}
