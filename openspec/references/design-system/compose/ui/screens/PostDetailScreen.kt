// ============================================================
// Nubecita — PostDetailScreen
// Mirrors ui_kits/nubecita-android/Screens.jsx → PostDetailScreen.
// ============================================================
package app.nubecita.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.unit.sp
import app.nubecita.data.FakeData
import app.nubecita.data.Post
import app.nubecita.ui.components.NubecitaAvatar
import app.nubecita.ui.components.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    post: Post,
    onBack: () -> Unit,
    onReply: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, "More") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {

            // ── Focused post ────────────────────────────────
            item("focus") { FocusedPost(post) }

            // ── Reply prompt ───────────────────────────────
            item("compose") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onReply)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NubecitaAvatar(name = "You", hue = 120, size = 36.dp)
                    Text(
                        "Post your reply",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    FilledTonalButton(onClick = onReply) { Text("Reply") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // ── Replies ────────────────────────────────────
            items(FakeData.replies, key = { it.id }) { r -> PostCard(post = r) }
        }
    }
}

@Composable
private fun FocusedPost(post: Post) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NubecitaAvatar(name = post.name, hue = post.hue, size = 48.dp,
                following = post.following)
            Column(modifier = Modifier.weight(1f)) {
                Text(post.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Text("@${post.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!post.following) {
                FilledTonalButton(onClick = {}) { Text("Follow") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            post.body,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 19.sp,
                lineHeight = 30.sp,
            ),
        )

        post.imageGradient?.let { stops ->
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(stops.map { Color(it) }))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(16.dp))
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "3:24 PM · Mar 4 · 2.1k views",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Stat(value = "${post.reposts}", label = "Reposts")
            Stat(value = "${post.likes}",   label = "Likes")
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailAction(Icons.Outlined.ChatBubbleOutline)
            DetailAction(Icons.Outlined.Repeat)
            DetailAction(Icons.Filled.Favorite, tint = MaterialTheme.colorScheme.secondary)
            DetailAction(Icons.Outlined.IosShare)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Stat(value: String, label: String) {
    Row {
        Text(value, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(4.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailAction(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = {}) { Icon(icon, null, tint = tint) }
}
