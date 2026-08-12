package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.preferences.AutoplayPreference
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Baselines for the Media and animations screen.
 *
 * One fixture per selectable autoplay option, so the set discriminates on the
 * thing that actually varies: which row carries the radio and the selected
 * tint. A regression that stuck the selection on the first row, or dropped the
 * indicator, changes at least two of these — one fixture could pin a missing
 * indicator as correct.
 *
 * The GIF switch is deliberately driven to BOTH positions across the set (off
 * in the Wi-Fi fixture, on in the others). A single always-on set would render
 * an inverted or dead switch identically to a correct one.
 */
@PreviewTest
@Preview(name = "media-always-gifs-on-light", showBackground = true, heightDp = 700)
@Composable
private fun MediaAlwaysScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.ALWAYS, autoplayGifs = true),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "media-wifi-only-gifs-off-light", showBackground = true, heightDp = 700)
@Composable
private fun MediaWifiOnlyScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.WIFI_ONLY, autoplayGifs = false),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "media-never-gifs-on-light", showBackground = true, heightDp = 700)
@Composable
private fun MediaNeverScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.NEVER, autoplayGifs = true),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "media-wifi-only-gifs-off-dark",
    showBackground = true,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MediaWifiOnlyDarkScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            MediaAndAnimationsContent(
                state = MediaAndAnimationsState(autoplay = AutoplayPreference.WIFI_ONLY, autoplayGifs = false),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
