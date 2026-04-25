// ============================================================
// Nubecita — ThreadFeedScreen
// Renders a self-thread (same author, multiple linked posts)
// with a connector line + "View full thread" fold.
// ============================================================
package app.nubecita.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.nubecita.data.FakeData
import app.nubecita.data.Post
import app.nubecita.ui.components.PostCard
import app.nubecita.ui.components.ThreadFold
import app.nubecita.ui.components.ThreadPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadFeedScreen(
    onOpenPost: (Post) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threads",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Menu, "Menu") }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Notifications, "Notifications") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Render the self-thread cluster
            items(FakeData.selfThread, key = {
                when (it) {
                    is FakeData.ThreadItem.PostItem -> it.post.id
                    is FakeData.ThreadItem.Fold     -> it.id
                }
            }) { item ->
                when (item) {
                    is FakeData.ThreadItem.PostItem -> ThreadPost(
                        post = item.post,
                        connectAbove = item.post.connectAbove,
                        connectBelow = item.post.connectBelow,
                        onOpen = { onOpenPost(item.post) },
                    )
                    is FakeData.ThreadItem.Fold -> ThreadFold(count = item.count)
                }
            }

            // A normal post follows, to show the visual contrast
            item("divider") {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(FakeData.posts.take(2), key = { it.id }) { post ->
                PostCard(post = post, onOpen = { onOpenPost(post) })
            }
        }
    }
}
