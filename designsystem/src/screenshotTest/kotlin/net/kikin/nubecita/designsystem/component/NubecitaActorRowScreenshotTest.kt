package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

private fun actor(
    handle: String,
    displayName: String? = null,
    badge: VerifiedBadge = VerifiedBadge.None,
) = ActorUi(
    did = "did:plc:$handle",
    handle = handle,
    displayName = displayName,
    avatarUrl = null,
    verifiedBadge = badge,
)

/**
 * Every shape the row has to hold: a display name plus handle, a handle-only
 * actor (whose handle must NOT render twice), a whitespace-only name, a verified badge competing with a
 * long name for the same line, an active query highlight, and the supporting
 * slot the login typeahead uses for its network line.
 */
@Composable
private fun NubecitaActorRowFixtures() {
    Column {
        NubecitaActorRow(actor = actor("alice.bsky.social", "Alice Chen"), onClick = {})
        NubecitaActorRow(actor = actor("handleonly.bsky.social"), onClick = {})
        // A whitespace-only display name must behave exactly like a missing one:
        // handle as the title, no empty line above it and no handle repeated below.
        NubecitaActorRow(actor = actor("blankname.bsky.social", "   "), onClick = {})
        NubecitaActorRow(
            actor =
                actor(
                    "verylongname.bsky.social",
                    "A Very Long Display Name That Must Ellipsize",
                    VerifiedBadge.Verified,
                ),
            onClick = {},
        )
        // Highlight spans both lines — the match appears in the name and the handle.
        NubecitaActorRow(actor = actor("francisco.bsky.social", "Francisco Velazquez"), query = "fran", onClick = {})
        NubecitaActorRow(
            actor = actor("selfhosted.dev", "Self Hosted"),
            showAvatarFallback = true,
            onClick = {},
        ) {
            Text(
                text = "pds.selfhosted.dev",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-light", showBackground = true)
@Preview(name = "actor-row-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaActorRowPreview() {
    NubecitaActorRowFixtures()
}
