// ============================================================
// Nubecita — Reusable post card + small bits
// ============================================================
package app.nubecita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nubecita.data.Post

@Composable
fun PostCard(
    post: Post,
    onOpen: () -> Unit = {},
    onLike: (Boolean) -> Unit = {},
    onRepost: (Boolean) -> Unit = {},
    onReply: () -> Unit = {},
) {
    var liked    by remember(post.id) { mutableStateOf(post.liked) }
    var reposted by remember(post.id) { mutableStateOf(post.reposted) }
    val likes    = post.likes + (if (liked && !post.liked) 1 else if (!liked && post.liked) -1 else 0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        post.repostedBy?.let {
            Row(
                modifier = Modifier.padding(start = 56.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Repeat, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("$it reposted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NubecitaAvatar(name = post.name, hue = post.hue, following = post.following)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.name, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                    Text("@${post.handle} · ${post.timeAgo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(post.body, style = MaterialTheme.typography.bodyLarge)

                post.imageGradient?.let { stops ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(stops.map { Color(it) }))
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    PostStat(Icons.Outlined.ChatBubbleOutline, "${post.replies}", onReply)
                    PostStat(Icons.Outlined.Repeat, "${post.reposts + (if (reposted) 1 else 0)}",
                        active = reposted,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { reposted = !reposted; onRepost(reposted) })
                    PostStat(
                        if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "$likes",
                        active = liked,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        onClick = { liked = !liked; onLike(liked) },
                    )
                    PostStat(Icons.Outlined.IosShare, "")
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PostStat(
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        if (count.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(count, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
