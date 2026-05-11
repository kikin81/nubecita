package net.kikin.nubecita.designsystem.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews

/**
 * Visual baselines for every [NubecitaIconName] entry in both
 * outlined and filled states. The fixture is intentionally exhaustive
 * — adding a new enum entry without refreshing this baseline shows up
 * as a screenshot diff, surfacing the new icon for visual review.
 *
 * Beyond glyph coverage, the showcase is the safety net for the
 * baseline-alignment gotcha documented in the design doc:
 * `Text`-rendered glyphs can drift vertically inside their box if
 * font metrics or `includeFontPadding` aren't tamed. Eyeball the
 * generated baseline once after migration; subtle off-center
 * regressions surface here before they reach production.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun NubecitaIconShowcasePreviews() {
    NubecitaTheme(dynamicColor = false) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(NubecitaIconName.entries) { entry ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NubecitaIcon(
                            name = entry,
                            contentDescription = "${entry.name} outlined",
                            filled = false,
                        )
                        NubecitaIcon(
                            name = entry,
                            contentDescription = "${entry.name} filled",
                            filled = true,
                        )
                    }
                    Text(text = entry.name)
                }
            }
        }
    }
}
