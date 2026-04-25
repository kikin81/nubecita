// ============================================================
// Nubecita — FeedScreen
// Mirrors ui_kits/nubecita-android/Screens.jsx → FeedScreen.
// ============================================================
package app.nubecita.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nubecita.data.FakeData
import app.nubecita.data.Post
import app.nubecita.ui.components.PostCard
import app.nubecita.ui.theme.NubecitaShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenPost: (Post) -> Unit,
    onOpenCompose: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    var feedId by remember { mutableStateOf("fyp") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "nubecita ☁︎",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.Menu, "Menu") }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Outlined.Search, "Search") }
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Outlined.Notifications, "Notifications")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenCompose,
                shape = NubecitaShape.FAB,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Edit, "Compose")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Feed chip row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FakeData.feeds.forEach { f ->
                    FilterChip(
                        selected = feedId == f.id,
                        onClick = { feedId = f.id },
                        label = { Text(f.name) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn {
                items(FakeData.posts, key = { it.id }) { post ->
                    PostCard(post = post, onOpen = { onOpenPost(post) })
                }
            }
        }
    }
}
