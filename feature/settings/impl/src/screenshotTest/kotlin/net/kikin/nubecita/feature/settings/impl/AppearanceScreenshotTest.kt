package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Baselines for the Appearance screen.
 *
 * One fixture per selectable option, so the set discriminates on the thing that
 * actually varies: which row carries the radio and the selected tint. A
 * regression that stuck the selection on the first row, or dropped the
 * indicator entirely, changes at least two of these — a single fixture could
 * pin a missing indicator as correct.
 *
 * The dark fixture doubles as the check that the screen's own chrome reads
 * correctly under a dark scheme; the option it selects is `Dark`, which is also
 * the row most likely to be exercised with the OS in light mode.
 */
@PreviewTest
@Preview(name = "appearance-dynamic-selected-light", showBackground = true, heightDp = 520)
@Composable
private fun AppearanceDynamicSelectedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            AppearanceContent(
                state = AppearanceState(selected = ThemePreference.DYNAMIC),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "appearance-light-selected-light", showBackground = true, heightDp = 520)
@Composable
private fun AppearanceLightSelectedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            AppearanceContent(
                state = AppearanceState(selected = ThemePreference.LIGHT),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "appearance-dark-selected-dark",
    showBackground = true,
    heightDp = 520,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppearanceDarkSelectedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            AppearanceContent(
                state = AppearanceState(selected = ThemePreference.DARK),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
