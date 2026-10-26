package net.kikin.nubecita.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Brand colors for the watch: the phone's dark-scheme primary tones on Wear
 * Material 3's pure-black background (Wear quality guideline WO-V13). The watch
 * deliberately doesn't use `:designsystem` — that is phone Material 3.
 */
private val NubecitaWearColors =
    ColorScheme(
        primary = Color(0xFFA0C9FF), // NubecitaPalette.Sky80
        onPrimary = Color(0xFF003259), // Sky20
        primaryContainer = Color(0xFF00497F), // Sky30
        onPrimaryContainer = Color(0xFFD2E4FF), // Sky90
        background = Color.Black,
    )

@Composable
fun NubecitaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NubecitaWearColors, content = content)
}
