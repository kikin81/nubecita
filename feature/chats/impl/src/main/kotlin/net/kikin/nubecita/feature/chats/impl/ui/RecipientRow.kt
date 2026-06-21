package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.feature.chats.impl.R

/**
 * Single row in the NewChat recipient picker. Tapping anywhere on the
 * row invokes [onClick] with no args — the caller supplies the actor's
 * DID via a closure.
 *
 * Avatar: 40dp [NubecitaAvatar] with a deterministic hue+initial fallback
 * when [ActorUi.avatarUrl] is null — mirroring [ConvoListItem] and
 * [ChatScreenContent]'s `ChatTopBarAvatar`.
 *
 * Title: [ActorUi.displayName] when available, else [ActorUi.handle].
 * Subtitle: "@" + [ActorUi.handle] in `onSurfaceVariant`.
 *
 * Disabled state: when [respectCanMessage] is true (the default — the New-Chat
 * DM-start picker) and [ActorUi.canMessage] is false, the actor can't receive a
 * DM from the viewer: the row is greyed ([DISABLED_CONTENT_ALPHA]), the tap
 * target is disabled, and a "Can't be messaged" line is shown — matching the
 * official Bluesky client, which keeps such actors visible rather than hiding
 * them.
 *
 * The add-group-members picker passes [respectCanMessage] = false: the group-add
 * eligibility rule is "they must follow YOU" (server-enforced `NotFollowedBySender`
 * → [net.kikin.nubecita.feature.chats.impl.ChatError.FollowRequiredToAdd]), NOT
 * the recipient's DM-privacy. Gating that picker on `canMessage` would wrongly
 * grey out valid candidates, so the opt-out skips both the disable and the
 * "Can't be messaged" line.
 */
@Composable
internal fun RecipientRow(
    actor: ActorUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    respectCanMessage: Boolean = true,
) {
    val canMessage = !respectCanMessage || actor.canMessage
    val effectiveEnabled = enabled && canMessage
    val contentAlpha = if (effectiveEnabled) 1f else DISABLED_CONTENT_ALPHA
    Row(
        modifier =
            modifier
                .clickable(enabled = effectiveEnabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipientAvatar(actor = actor, modifier = Modifier.alpha(contentAlpha))
        Column(
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Text(
                text = actor.displayName ?: actor.handle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha),
            )
            Text(
                text = "@${actor.handle}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha),
            )
            if (!canMessage) {
                Text(
                    text = stringResource(R.string.new_chat_cannot_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 40dp circle avatar: [NubecitaAvatar] with a deterministic hue+initial
 * fallback when [ActorUi.avatarUrl] is null. Delegates hue derivation and
 * initial selection to [avatarFallbackFor] so the disc is consistent with
 * [ConvoListItem] and [ChatTopBarAvatar].
 */
@Composable
private fun RecipientAvatar(
    actor: ActorUi,
    modifier: Modifier = Modifier,
) {
    NubecitaAvatar(
        model = actor.avatarUrl,
        contentDescription = null,
        modifier = modifier,
        size = 40.dp,
        fallback = avatarFallbackFor(did = actor.did, handle = actor.handle, displayName = actor.displayName),
    )
}

// M3 disabled-content alpha — greys the avatar/name/handle of a recipient the
// viewer can't DM, while the "Can't be messaged" reason stays at full opacity.
private const val DISABLED_CONTENT_ALPHA = 0.38f

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

@Preview(showBackground = true)
@Composable
private fun RecipientRowCannotMessagePreview() {
    NubecitaTheme(dynamicColor = false) {
        RecipientRow(
            actor =
                ActorUi(
                    did = "did:plc:carol",
                    handle = "carol.bsky.social",
                    displayName = "Carol",
                    avatarUrl = null,
                    canMessage = false,
                ),
            onClick = {},
        )
    }
}
