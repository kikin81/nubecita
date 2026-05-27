package net.kikin.nubecita.designsystem.icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.designsystem.semanticColors

/**
 * Renders the Material Symbols glyph + tint pair that identifies the
 * source of a notification row by its [NotificationReason]. Used at the
 * left edge of every `NotificationRow` in `:feature:notifications:impl`.
 *
 * The `when` over [NotificationReason] is **exhaustive**: adding a new
 * enum value in `:data:models` SHALL fail to compile here until the
 * mapping is updated. This is the spec's compile-time safety net —
 * spec/design-system §3 — and the reason the mapping lives in
 * `:designsystem` rather than inlined at every call site.
 *
 * The like/repost reasons use the brand's dedicated `likeAccent` /
 * `repostAccent` semantic tokens (see [net.kikin.nubecita.designsystem
 * .NubecitaSemanticColors]). Other reasons map to standard M3 tokens —
 * `primary` for affirming actions (follow, verified), `onSurfaceVariant`
 * for low-emphasis content reasons (reply / mention / quote / etc.).
 *
 * The composable forwards `contentDescription = null` to [NubecitaIcon]
 * — the row's accessibility label is provided by the row composable's
 * own semantics (which describe the full "alice liked your post" phrase
 * rather than naming the icon in isolation). Callers that need a
 * standalone-labeled icon SHOULD use [NubecitaIcon] directly.
 */
@Composable
fun NotificationReasonIcon(
    reason: NotificationReason,
    modifier: Modifier = Modifier,
) {
    val mapping = reasonIconMapping(reason)
    NubecitaIcon(
        name = mapping.name,
        contentDescription = null,
        modifier = modifier,
        filled = mapping.filled,
        tint = mapping.tint,
    )
}

private data class ReasonIconMapping(
    val name: NubecitaIconName,
    val filled: Boolean,
    val tint: Color,
)

@Composable
private fun reasonIconMapping(reason: NotificationReason): ReasonIconMapping =
    when (reason) {
        NotificationReason.Like, NotificationReason.LikeViaRepost ->
            ReasonIconMapping(
                name = NubecitaIconName.Favorite,
                filled = true,
                tint = MaterialTheme.semanticColors.likeAccent,
            )
        NotificationReason.Repost, NotificationReason.RepostViaRepost ->
            ReasonIconMapping(
                name = NubecitaIconName.Repeat,
                filled = false,
                tint = MaterialTheme.semanticColors.repostAccent,
            )
        NotificationReason.Follow,
        NotificationReason.ContactMatch,
        NotificationReason.StarterpackJoined,
        ->
            ReasonIconMapping(
                name = NubecitaIconName.PersonAdd,
                filled = false,
                tint = MaterialTheme.colorScheme.primary,
            )
        NotificationReason.Reply ->
            ReasonIconMapping(
                name = NubecitaIconName.Reply,
                filled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationReason.Mention ->
            ReasonIconMapping(
                name = NubecitaIconName.AlternateEmail,
                filled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationReason.Quote ->
            ReasonIconMapping(
                name = NubecitaIconName.FormatQuote,
                filled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationReason.Verified ->
            ReasonIconMapping(
                name = NubecitaIconName.Verified,
                filled = true,
                tint = MaterialTheme.colorScheme.primary,
            )
        NotificationReason.Unverified ->
            ReasonIconMapping(
                name = NubecitaIconName.Verified,
                filled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationReason.SubscribedPost ->
            ReasonIconMapping(
                name = NubecitaIconName.Article,
                filled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationReason.Unknown ->
            ReasonIconMapping(
                name = NubecitaIconName.Notifications,
                filled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
