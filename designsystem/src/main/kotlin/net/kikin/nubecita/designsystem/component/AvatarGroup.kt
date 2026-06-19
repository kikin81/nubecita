package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi

/** Result of [avatarGroupSplit]: how many avatars to draw and the "+N" remainder. */
@Immutable
data class AvatarGroupSplit(
    val visibleAvatars: Int,
    val overflowCount: Int,
)

/**
 * When `total > maxVisible`, show `maxVisible` avatars and append a
 * `+(total-maxVisible)` pill (the pill is an extra bubble; it does NOT replace
 * an avatar). Otherwise show all avatars and no pill.
 */
fun avatarGroupSplit(
    total: Int,
    maxVisible: Int,
): AvatarGroupSplit =
    if (total > maxVisible) {
        AvatarGroupSplit(visibleAvatars = maxVisible, overflowCount = total - maxVisible)
    } else {
        AvatarGroupSplit(visibleAvatars = total, overflowCount = 0)
    }

/**
 * Overlapping facepile of [members] — up to [maxVisible] avatars, then a "+N"
 * pill. Each bubble is photo-or-(hue+initial) via [NubecitaAvatar], ringed in
 * `colorScheme.surface` so circles stay distinct, leftmost on top.
 *
 * [contentDescription] is caller-supplied (the design system doesn't own the
 * domain plural strings) and applied to the whole row via merged semantics.
 */
@Composable
fun AvatarGroup(
    members: ImmutableList<AuthorUi>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    avatarSize: Dp = DEFAULT_AVATAR_SIZE,
) {
    val split = avatarGroupSplit(total = members.size, maxVisible = maxVisible)
    val ringColor = MaterialTheme.colorScheme.surface
    val overlap = avatarSize * OVERLAP_FRACTION
    val rowModifier =
        if (contentDescription != null) {
            modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        } else {
            modifier
        }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(-overlap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        members.take(split.visibleAvatars).forEachIndexed { index, member ->
            NubecitaAvatar(
                model = member.avatarUrl,
                contentDescription = null,
                size = avatarSize,
                fallback = avatarFallbackFor(member),
                // NubecitaAvatar self-clips to a circle; the border here paints the
                // outer separation ring without re-clipping the avatar content.
                modifier =
                    Modifier
                        .zIndex((split.visibleAvatars - index).toFloat())
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape),
            )
        }
        if (split.overflowCount > 0) {
            Box(
                modifier =
                    Modifier
                        .zIndex(0f)
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${split.overflowCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fraction of [AvatarGroup]'s avatar diameter that each bubble overlaps the previous one. */
private const val OVERLAP_FRACTION = 0.30f
