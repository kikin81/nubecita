package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "recent-chip-strip-light", showBackground = true)
@Preview(name = "recent-chip-strip-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecentSearchChipStripScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            RecentSearchChipStrip(
                items = persistentListOf("kotlin", "compose", "room", "bluesky", "navigation 3"),
                onChipTap = {},
                onChipRemove = {},
                onClearAll = {},
            )
        }
    }
}
