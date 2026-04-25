// ============================================================
// Nubecita — Self-thread support
// Vertical connector line through the avatar gutter + a "View
// full thread" fold for non-adjacent siblings (Bluesky-style).
// ============================================================
package app.nubecita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nubecita.data.Post
import app.nubecita.data.QuoteCardData

/**
 * A post in a self-thread cluster. Draws a 2dp vertical connector
 * up and/or down through the avatar gutter (centered at x = 32dp)
 * to join sibling posts visually.
 *
 * Ladder:  ┃         (connectAbove = true)
 *          ●━━ post body
 *          ┃         (connectBelow = true)
 */
@Composable
fun ThreadPost(
    post: Post,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
    onOpen: () -> Unit = {},
    onLike: (Boolean) -> Unit = {},
    onRepost: (Boolean) -> Unit = {},
) {
    var liked    by remember(post.id) { mutableStateOf(post.liked) }
    var reposted by remember(post.id) { mutableStateOf(post.reposted) }
    val likes = post.likes + (if (liked && !post.liked) 1 else if (!liked && post.liked) -1 else 0)

    val connectorColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .drawWithContent {
                drawContent()
                // x = 20.dp (gutter) + 22.dp (avatar half) - 1.dp (line half) = 41.dp
                val x = 41.dp.toPx()
                val avatarTop    = 12.dp.toPx()
                val avatarBottom = (12 + 44).dp.toPx()
                if (connectAbove) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(x, 0f),
                        end   = Offset(x, avatarTop),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
                if (connectBelow) {
                    drawLine(
                        color = connectorColor,
                        start = Offset(x, avatarBottom),
                        end   = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NubecitaAvatar(name = post.name, hue = post.hue, following = post.following)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text("@${post.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(post.timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(post.body, style = MaterialTheme.typography.bodyLarge)

            if (post.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.tags.forEach { tag ->
                        Text(
                            tag,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                        )
                    }
                }
            }

            post.quoteCard?.let { Spacer(Modifier.height(10.dp)); QuoteCard(it) }

            post.imageGradient?.let { stops ->
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(stops.map { Color(it) }))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp))
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThreadStat(Icons.Outlined.ChatBubbleOutline, "${post.replies}")
                ThreadStat(
                    Icons.Outlined.Repeat,
                    "${post.reposts + (if (reposted) 1 else 0)}",
                    active = reposted,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { reposted = !reposted; onRepost(reposted) },
                )
                ThreadStat(
                    if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "$likes",
                    active = liked,
                    activeColor = MaterialTheme.colorScheme.secondary,
                    onClick = { liked = !liked; onLike(liked) },
                )
                ThreadStat(Icons.Outlined.BookmarkBorder, "")
                ThreadStat(Icons.Outlined.IosShare, "")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreHoriz, "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * The "View full thread" fold — sits between non-adjacent posts in a thread.
 * The connector line continues unbroken; three small dots mark the elision.
 */
@Composable
fun ThreadFold(
    count: Int = 0,
    onClick: () -> Unit = {},
) {
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .drawWithContent {
                drawContent()
                val x = 41.dp.toPx()
                drawLine(
                    color = connectorColor,
                    start = Offset(x, 0f),
                    end   = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Three dots stacked along the connector
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
        }
        Row(
            modifier = Modifier.heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "View full thread",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "· $count more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Embedded post-like preview (the card-in-card pattern). */
@Composable
fun QuoteCard(card: QuoteCardData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        )
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(card.author, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(6.dp))
            Text("· ${card.timeAgo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(card.title, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            card.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
        )
    }
}

@Composable
private fun ThreadStat(
    icon: ImageVector,
    count: String,
    onClick: () -> Unit = {},
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        if (count.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(count, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
