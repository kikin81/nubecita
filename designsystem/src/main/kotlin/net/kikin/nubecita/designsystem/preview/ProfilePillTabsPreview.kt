package net.kikin.nubecita.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.tabs.PillTab
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs

/**
 * Catalog preview for [ProfilePillTabs]. Renders the row in each
 * of its three active states — Posts / Replies / Media — at a
 * representative width.
 */
@Composable
fun ProfilePillTabsCatalog(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Three snapshots — one with each tab active. Stateless so the
        // preview pane shows all three simultaneously without an
        // interactive selection.
        tabs.forEach { active ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Active: ${active.label}",
                    style = MaterialTheme.typography.labelSmall,
                )
                ProfilePillTabs(
                    tabs = tabs,
                    selectedValue = active.value,
                    onSelect = { },
                )
            }
        }

        // Interactive row at the bottom so the IDE preview lets the
        // designer click between pills.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Interactive", style = MaterialTheme.typography.labelSmall)
            var selected by remember { mutableStateOf(SamplePillValue.Posts) }
            ProfilePillTabs(
                tabs = tabs,
                selectedValue = selected,
                onSelect = { selected = it },
            )
        }
    }
}

private enum class SamplePillValue { Posts, Replies, Media }

// Sample-only — the real Profile screen (Bead D / E) picks the
// canonical icons. `Article` is a placeholder for the Media tab here
// because `Image` isn't in NubecitaIconName yet; adding it requires
// re-subsetting the icon font (see NubecitaIconName.kt KDoc) and is
// out of scope for Bead B (BoldHeroGradient + ProfilePillTabs).
private val tabs: List<PillTab<SamplePillValue>> =
    listOf(
        PillTab(SamplePillValue.Posts, "Posts", NubecitaIconName.Home),
        PillTab(SamplePillValue.Replies, "Replies", NubecitaIconName.ChatBubble),
        PillTab(SamplePillValue.Media, "Media", NubecitaIconName.Article),
    )

@Preview(name = "Light", showBackground = true)
@Composable
private fun ProfilePillTabsPreviewLight() {
    NubecitaTheme(darkTheme = false, dynamicColor = false) {
        Surface {
            ProfilePillTabsCatalog()
        }
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun ProfilePillTabsPreviewDark() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            ProfilePillTabsCatalog()
        }
    }
}
