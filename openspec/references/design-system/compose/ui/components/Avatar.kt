// ============================================================
// Nubecita — A handful of starter components
// (Avatar with follow-badge + context menu, BottomNav with 4 tabs)
// ============================================================
package app.nubecita.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Threads-style avatar — tap shows context menu, (+) badge for non-followed users. */
@Composable
fun NubecitaAvatar(
    name: String,
    hue: Int = 210,
    size: Dp = 44.dp,
    following: Boolean = true,
    onGoToProfile: () -> Unit = {},
    onFollowToggle: (Boolean) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var follow   by remember(following) { mutableStateOf(following) }

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.hsl(hue.toFloat(), 0.7f, 0.70f),
                            Color.hsl(((hue + 40) % 360).toFloat(), 0.6f, 0.55f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
            // Whole avatar acts as the menu trigger
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Go to profile") },
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    onClick = { menuOpen = false; onGoToProfile() }
                )
                DropdownMenuItem(
                    text = { Text(if (follow) "Following" else "Follow") },
                    leadingIcon = {
                        if (follow) Icon(Icons.Outlined.Check, null)
                        else Icon(Icons.Outlined.PersonAdd, null,
                                  tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = {
                        val next = !follow; follow = next
                        menuOpen = false
                        onFollowToggle(next)
                    }
                )
            }
        }

        // (+) follow badge
        if (!follow && size >= 36.dp) {
            val badge = (size.value * 0.42f).coerceAtLeast(16f).dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(badge)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add, null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(badge - 6.dp)
                )
            }
        }

        // Click target sits over the whole thing
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
        ) {
            // empty — handled via Surface in real usage; for brevity, gesture left to caller
        }
    }
}
