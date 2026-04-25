package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * One interactive icon + count cell in a [PostCard]'s action row.
 *
 * `active` flips the icon and count to [activeColor] (typically
 * `MaterialTheme.colorScheme.secondary` for like, `tertiary` for repost).
 * Inactive uses `onSurfaceVariant`. The cell is a `Row` clipped to a
 * circle so the ripple matches the icon's affordance.
 *
 * Pass an empty `count` string for cells that don't show a number (e.g.
 * the share button).
 */
@Composable
internal fun PostStat(
    icon: ImageVector,
    count: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(STAT_ICON_SIZE),
        )
        if (count.isNotEmpty()) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

private val STAT_ICON_SIZE = 18.dp

@Preview(name = "PostStat — inactive", showBackground = true)
@Composable
private fun PostStatInactivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(icon = Icons.Outlined.ChatBubbleOutline, count = "12")
            PostStat(icon = Icons.Outlined.Repeat, count = "4")
            PostStat(icon = Icons.Outlined.FavoriteBorder, count = "86")
            PostStat(icon = Icons.Outlined.IosShare, count = "")
        }
    }
}

@Preview(name = "PostStat — active like + repost", showBackground = true)
@Composable
private fun PostStatActivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(icon = Icons.Outlined.ChatBubbleOutline, count = "12")
            PostStat(
                icon = Icons.Outlined.Repeat,
                count = "5",
                active = true,
                activeColor = MaterialTheme.colorScheme.tertiary,
            )
            PostStat(
                icon = Icons.Filled.Favorite,
                count = "87",
                active = true,
                activeColor = MaterialTheme.colorScheme.secondary,
            )
            PostStat(icon = Icons.Outlined.IosShare, count = "")
        }
    }
}
