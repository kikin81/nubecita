package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage

/**
 * Single row in the NewChat recipient picker. Tapping anywhere on the
 * row invokes [onClick] with no args — the caller supplies the actor's
 * DID via a closure.
 *
 * Avatar: 40dp circle with [NubecitaAsyncImage] when [ActorUi.avatarUrl]
 * is non-null; otherwise a hue-derived background + initial letter fallback,
 * mirroring the pattern in [ConvoListItem] and [ChatScreenContent]'s
 * `ChatTopBarAvatar`.
 *
 * Title: [ActorUi.displayName] when available, else [ActorUi.handle].
 * Subtitle: "@" + [ActorUi.handle] in `onSurfaceVariant`.
 */
@Composable
internal fun RecipientRow(
    actor: ActorUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipientAvatar(actor = actor, modifier = Modifier.size(40.dp))
        Column(
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = actor.displayName ?: actor.handle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${actor.handle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 40dp circle avatar: async image if [ActorUi.avatarUrl] is set, otherwise
 * a hue-derived background with an initial-letter fallback. The hue is
 * derived from the actor's DID hash so it's stable across recompositions
 * and consistent with how [ConvoListItem] colours its own avatar.
 */
@Composable
private fun RecipientAvatar(
    actor: ActorUi,
    modifier: Modifier = Modifier,
) {
    val hue = (actor.did.hashCode() and 0x7fffffff) % 360
    val hueColor = Color.hsv(hue.toFloat(), saturation = 0.5f, value = 0.55f)
    val initial =
        (actor.displayName ?: actor.handle)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercase()
            ?: "?"

    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(hueColor),
        contentAlignment = Alignment.Center,
    ) {
        if (actor.avatarUrl != null) {
            NubecitaAsyncImage(
                model = actor.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipientRowWithDisplayNamePreview() {
    NubecitaTheme(dynamicColor = false) {
        RecipientRow(
            actor =
                ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Liddell",
                    avatarUrl = null,
                ),
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecipientRowHandleOnlyPreview() {
    NubecitaTheme(dynamicColor = false) {
        RecipientRow(
            actor =
                ActorUi(
                    did = "did:plc:bob",
                    handle = "bob.bsky.social",
                    displayName = null,
                    avatarUrl = null,
                ),
            onClick = {},
        )
    }
}
