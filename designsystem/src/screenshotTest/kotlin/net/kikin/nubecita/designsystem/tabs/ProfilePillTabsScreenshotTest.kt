package net.kikin.nubecita.designsystem.tabs

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.designsystem.preview.ProfilePillTabsCatalog

/**
 * Visual baseline for [ProfilePillTabs] across each of the three
 * active states (one snapshot per active pill). The fixture is the
 * regression net for the pill-shape `tabIndicatorOffset` wiring +
 * the [NubecitaIcon] `filled = isSelected` FILL-axis toggle.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun ProfilePillTabsScreenshotPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ProfilePillTabsCatalog()
        }
    }
}
